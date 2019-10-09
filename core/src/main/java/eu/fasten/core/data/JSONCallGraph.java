package eu.fasten.core.data;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URISyntaxException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;




public class JSONCallGraph {
	private static final Logger LOGGER = LoggerFactory.getLogger(JSONCallGraph.class);

	/** A constraint represents an interval of versions. It includes all versions between a given lower and upper bound. */
	public static class Constraint {
		/** Version must be not smaller than this (no lower bound, if <code>null</code>). */
		public final String lowerBound;
		/** Version must be not larger than this (no upper bound, if <code>null</code>). */
		public final String upperBound;

		/** Generate a constraint with given lower and upper bounds.
		 * 
		 * @param lowerBound the lower bound.
		 * @param upperBound the upper bound.
		 */
		public Constraint(final String lowerBound, final String upperBound) {
			this.lowerBound = lowerBound;
			this.upperBound = upperBound;
		}
		
		/** Generate a constraint on the basis of a specification. The spec must:
		 *  <ol>
		 *  	<li>start with a '['
		 *  	<li>end with a ']'
		 *  	<li>it contains at most one substring of the form '..'
		 *  	<li>if it contains no such substring, lower and upper bound coincide and are the trimmed version of whatever is between brackets
		 *      <li>if it contains one such substring, lower and upper bounds are whatever precedes and follows (respectively) the '..'
		 *  </ol>
		 * @param spec the specification.
		 */
		public Constraint(String spec) {
			if ((spec.charAt(0) != '[') || (spec.charAt(spec.length() - 1) != ']')) throw new IllegalArgumentException("Constraints must start with '[' and end with ']'");
			int pos = spec.indexOf("..");
			if (spec.indexOf("..", pos + 1) >= 0) throw new IllegalArgumentException("Constraints must contain exactly one ..");
			String lowerBound = spec.substring(1, pos >= 0? pos : spec.length() - 1).trim();
			String upperBound = spec.substring(pos >= 0? pos + 2 : 1, spec.length() - 1).trim();
			this.lowerBound = lowerBound.length() == 0? null : lowerBound;
			this.upperBound = upperBound.length() == 0? null : upperBound;
		}
		
		/** Given a {@link JSONArray} of specifications of constraints, it returns the corresponding array
		 *  of contraints.
		 *  
		 * @param jsonArray an array of strings, each being the {@linkplain #Constraint(String) specification} of a constraint.
		 * @return the corresponding array of constraints.
		 */
		public static Constraint[] constraints(JSONArray jsonArray) {
			Constraint[] c = new Constraint[jsonArray.length()];
			for (int i = 0; i < c.length; i++) 
				c[i] = new Constraint(jsonArray.getString(i));
			return c;
		}

		@Override
		public String toString() {
			return "[" + 
					(lowerBound == null? "" : lowerBound) +
					".." +
					(upperBound == null? "" : upperBound) +
					"]";
		}
	}
	
	public static class Dependency {
		public final String forge;
		public final String product;
		public final Constraint[] constraints;
		
		/** Create a dependency with given data.
		 * 
		 * @param forge the forge.
		 * @param product the product.
		 * @param constraint the array of constraints.
		 */
		public Dependency(String forge, String product, Constraint[] constraint) {
			this.forge = forge;
			this.product = product;
			this.constraints = constraint;
		}
		
		/** Create a dependency based on the given JSON Object.
		 * 
		 * @param json the JSON dependency object, as specified in Fasten Deliverable 2.1 
		 * @param ignoreConstraints  if <code>true</code>, constraints are specified by a simple string.
		 */
		public Dependency(JSONObject json, boolean ignoreConstraints) {
			this.forge = json.getString("forge");
			this.product = json.getString("product");
			//TODO
			if (ignoreConstraints)
				this.constraints = new Constraint[] {new Constraint(json.getString("constraints"), null)};
			else
				this.constraints = Constraint.constraints(json.getJSONArray("constraints"));
		}
		
		/** Given an JSON array of dependencies (a depset as specified in Fasten Deliverable 2.1), it returns
		 *  the corresponding depset.
		 *   
		 * @param depset the JSON array of dependencies.
		 * @param ignoreConstraints  if <code>true</code>, constraints are specified by a simple string.
		 * @return the corresponding array of dependencies.
		 */
		public static Dependency[] depset(JSONArray depset, boolean ignoreConstraints) {
			Dependency[] d = new Dependency[depset.length()];
			for (int i = 0; i < d.length; i++) 
				d[i] = new Dependency(depset.getJSONObject(i), ignoreConstraints);
			return d;
		}
		
	}
	
	/** The forge. */
	public final String forge;
	/** The product. */
	public final String product;
	/** The version. */
	public final String version;
	/** The timestamp (if specified, or -1) in seconds from UNIX Epoch. */
	public final long timestamp;
	/** The depset. */
	public final Dependency[] depset;
	/** The URI of this revision. */
	public final FastenURI uri;
	/** The forgeless URI of this revision. */
	public final FastenURI forgelessUri;
	/** The graph expressed as a list of pairs of {@link FastenURI}. Recall that, according to D2.1:
	 * <ol>
	 *  <li>they are in schemeless canonical form;
	 *  <li>the entity specified is a function or an attribute (not a type); if it is an attribute, it must appear as the second URI of a pair;
	 *  <li>the forge-product-version, if present, must only contain the product part; this happens exactly when the product is different from the product specified by this JSON object, that is, if and only if the URI is that of an external node;
	 *  <li>the first URI of each pair always refers to this product;
	 *  <li>the namespace is always present.
	 * </ol>
	 */
	public ArrayList<FastenURI[]> graph;
	
	
	/** Creates a JSON call graph with given data.
	 * 
	 * @param forge the forge.
	 * @param product the product.
	 * @param version the version.
	 * @param timestamp the timestamp (in seconds from UNIX epoch); optional: if not present, it is set to -1.
	 * @param depset the depset.
	 * @param graph the call graph (no control is done on the graph).
	 */
	public JSONCallGraph(String forge, String product, String version, long timestamp, Dependency[] depset, ArrayList<FastenURI[]> graph) {
		this.forge = forge;
		this.product = product;
		this.version = version;
		this.timestamp = timestamp;
		this.depset = depset;
		uri = FastenURI.create("fasten://" + forge + "!" + product + "$" + version);
		forgelessUri = FastenURI.create("fasten://" + product + "$" + version);
		this.graph = graph;
	}
	
	/** Creates a JSON call graph for a given JSON Object, as specified in Deliverable D2.1.
	 *  The timestamp is optional (if not specified, it is set to -1). 
	 *  Moreover, the list of arcs is checked in that all involved URIs must be:
	 * <ol>
	 *  <li>they are in schemeless canonical form;
	 *  <li>the entity specified is a function or an attribute (not a type); if it is an attribute, it must appear as the second URI of a pair;
	 *  <li>the forge-product-version, if present, must only contain the product part; this happens exactly when the product is different from the product specified by this JSON object, that is, if and only if the URI is that of an external node;
	 *  <li>the first URI of each pair always refers to this product;
	 *  <li>the namespace is always present.
	 * </ol>
	 *  Arcs not satisfying these properties are discarded, and a suitable error message is logged.
	 *  
	 * @param json the JSON Object.
	 * @param ignoreConstraints if <code>true</code>, constraints are specified by a simple string.
	 */
	public JSONCallGraph(JSONObject json, boolean ignoreConstraints) throws JSONException, URISyntaxException {
		this.forge = json.getString("forge");
		this.product = json.getString("product");
		this.version = json.getString("version");
		long ts;
		try {
			ts = json.getLong("timestamp");
		} catch (JSONException exception) {
			ts = -1;
			LOGGER.warn("No timestamp provided: assuming -1");
		}
		this.timestamp = ts;
		this.depset = Dependency.depset(json.getJSONArray("depset"), ignoreConstraints);
		uri = FastenURI.create("fasten://" + forge + "!" + product + "$" + version);
		forgelessUri = FastenURI.create("fasten://" + product + "$" + version);
		this.graph = new ArrayList<FastenURI[]>();
		JSONArray jsonArray = json.getJSONArray("graph");
		int numberOfArcs = jsonArray.length();
		for (int i = 0; i < numberOfArcs; i++) {
			JSONArray pair = jsonArray.getJSONArray(i);
			FastenURI[] arc = new FastenURI[] {
					new FastenURI(pair.getString(0)),
					new FastenURI(pair.getString(1)) };
			int correctNodesInArc = 0;
			// Check the graph content
			for (int j = 0; j < arc.length; j++) {
				FastenURI node = arc[j];
				if (FastenURI.NULL_FASTEN_URI.equals(node)) {
					correctNodesInArc++;
					continue;
				}
				// URI in schemeless canonical form
				if (node.getScheme() != null) LOGGER.warn("Ignoring arc " + i + "/" + numberOfArcs + ": node " + node + " should be schemeless");
				else if (!node.toString().equals(node.canonicalize().toString())) LOGGER.warn("Ignoring arc " + i + "/" + numberOfArcs + ": node " + node + " not in canonical form [" + node.canonicalize() + "]");
				// No forge, no version
				else if (node.getForge() != null || node.getVersion() != null) LOGGER.warn("Ignoring arc " + i + "/" + numberOfArcs + ": forges and versions cannot be specified: " + node);
				// Product cannot coincide with this product
				else if (node.getProduct() != null && uri.getProduct().equals(node.getProduct())) LOGGER.warn("Ignoring arc " + i + "/" + numberOfArcs + ": product of node " + node + " equals the product specified by this JSON object, and should hence be omitted");
				// If product is specified, the node must be the source
				else if (node.getProduct() != null  && j == 0) LOGGER.warn("Ignoring arc " + i + "/" + numberOfArcs + ": node " + node + " is external, and cannot appear as source of an arc");
				// Check that namespace is present
				else if (node.getNamespace() == null) LOGGER.warn("Ignoring arc " + i + "/" + numberOfArcs + ": namespace is not present in node " + pair.getString(j));
				// TODO we should also check that it is a function or an attribute, not a type!
				else correctNodesInArc++;
			}
			if (correctNodesInArc == 2) this.graph.add(arc);
		}
		LOGGER.warn("Stored " + this.graph.size() + " arcs of the " + numberOfArcs + " specified");
	}
	
	public static void main(String[] args) throws JSONException, FileNotFoundException, URISyntaxException, JSAPException {
		final SimpleJSAP jsap = new SimpleJSAP( JSONCallGraph.class.getName(), 
				"Reads a file containing a JSON call graph in the format specified by the Deliverable D2.1", 
				new Parameter[] {
					new Switch( "ignore-constraints", 'c', "ignore-constraints", "The constraints are ignored (i.e., they are accepted in the form of a generic string)." ),
					new UnflaggedOption( "filename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The name of the file containing the JSON object." ),
			}
		);

		JSAPResult jsapResult = jsap.parse(args);
		if ( jsap.messagePrinted() ) return;
		String filename = jsapResult.getString("filename");
		boolean ignoreConstraints = jsapResult.getBoolean("ignore-constraints");
		
		JSONObject json = new JSONObject(new JSONTokener(new FileReader(filename)));
		JSONCallGraph callGraph = new JSONCallGraph(json, ignoreConstraints);
		// TODO do something with the graph?
	}
	
	

}
