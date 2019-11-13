package eu.fasten.core.data;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;




public class RevisionCallGraph {
	private static final Logger LOGGER = LoggerFactory.getLogger(RevisionCallGraph.class);

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
		public Constraint(final String spec) {
			if ((spec.charAt(0) != '[') || (spec.charAt(spec.length() - 1) != ']')) throw new IllegalArgumentException("Constraints must start with '[' and end with ']'");
			int pos = spec.indexOf("..");
			if (spec.indexOf("..", pos + 1) >= 0) throw new IllegalArgumentException("Constraints must contain exactly one ..");
			String lowerBound = spec.substring(1, pos >= 0? pos : spec.length() - 1).trim();
			String upperBound = spec.substring(pos >= 0? pos + 2 : 1, spec.length() - 1).trim();
			this.lowerBound = lowerBound.length() == 0? null : lowerBound;
			this.upperBound = upperBound.length() == 0? null : upperBound;
		}
		
		/** Given a {@link JSONArray} of specifications of constraints, it returns the corresponding list
		 *  of contraints.
		 *  
		 * @param jsonArray an array of strings, each being the {@linkplain #Constraint(String) specification} of a constraint.
		 * @return the corresponding list of constraints.
		 */
		public static List<Constraint> constraints(final JSONArray jsonArray) {
			List<Constraint> c = new ObjectArrayList<>();
			for (int i = 0; i < jsonArray.length(); i++) 
				c.add(new Constraint(jsonArray.getString(i)));
			return c;
		}
		
		/** Converts a list of {@link Constraint constraints} to its JSON representation.
		 * 
		 * @param c the list of contraints to be converted.
		 * @return the corresponding JSON representation.
		 */
		public static JSONArray toJSON(final List<Constraint> c) {
			JSONArray result = new JSONArray();
			for (Constraint constraint: c) result.put(constraint.toString());
			return result;
		}

		@Override
		public String toString() {
			if (lowerBound != null && lowerBound.equals(upperBound)) 
				return "[" + lowerBound + "]";
			else 
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
		public final List<Constraint> constraints;
		
		/** Create a dependency with given data.
		 * 
		 * @param forge the forge.
		 * @param product the product.
		 * @param constraint the list of constraints.
		 */
		public Dependency(final String forge, final String product, final List<Constraint> constraint) {
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
				this.constraints = ObjectLists.singleton(new Constraint(json.getString("constraints"), null));
			else
				this.constraints = Constraint.constraints(json.getJSONArray("constraints"));
		}
		
		/** Given an JSON array of dependencies (a depset as specified in Fasten Deliverable 2.1), it returns
		 *  the corresponding depset.
		 *   
		 * @param depset the JSON array of dependencies.
		 * @param ignoreConstraints  if <code>true</code>, constraints are specified by a simple string.
		 * @return the corresponding list of dependencies.
		 */
		public static List<Dependency> depset(JSONArray depset, boolean ignoreConstraints) {
			List<Dependency> d = new ObjectArrayList<>();
			for (int i = 0; i < depset.length(); i++) 
				d.add(new Dependency(depset.getJSONObject(i), ignoreConstraints));
			return d;
		}
		
		/** Produces the JSON representation of this dependency.
		 * 
		 * @return the JSON representation.
		 */
		public JSONObject toJSON() {
			JSONObject result = new JSONObject();
			result.put("forge", forge);
			result.put("product", product);
			result.put("constraints", Constraint.toJSON(constraints));
			return result;
		}
		
		/** Converts a list of {@link Dependency dependencies} to its JSON representation.
		 * 
		 * @param depset the list of dependencies to be converted.
		 * @return the corresponding JSON representation.
		 */
		public static JSONArray toJSON(final List<Dependency> depset) {
			JSONArray result = new JSONArray();
			for (Dependency dep: depset) result.put(dep.toJSON());
			return result;
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
	public final List<Dependency> depset;
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


	private RevisionCallGraph(Builder builder) {
		this.forge = builder.forge;
		this.product = builder.product;
		this.version = builder.version;
		this.timestamp = builder.timestamp;
		this.depset = builder.depset;
		this.uri = builder.uri;
		this.forgelessUri = builder.forgelessUri;
		this.graph = builder.graph;
	}


	/** Creates a JSON call graph with given data.
	 * 
	 * @param forge the forge.
	 * @param product the product.
	 * @param version the version.
	 * @param timestamp the timestamp (in seconds from UNIX epoch); optional: if not present, it is set to -1.
	 * @param depset the depset.
	 * @param graph the call graph (no control is done on the graph).
	 */
	public RevisionCallGraph(String forge, String product, String version, long timestamp, List<Dependency> depset, ArrayList<FastenURI[]> graph) {
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
	public RevisionCallGraph(JSONObject json, boolean ignoreConstraints) throws JSONException, URISyntaxException {
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
		LOGGER.info("Stored " + this.graph.size() + " arcs of the " + numberOfArcs + " specified");
	}
	
	/** Produces the JSON representation of this {@link RevisionCallGraph}.
	 * 
	 * @return the JSON representation.
	 */
	public JSONObject toJSON() {
		JSONObject result = new JSONObject();
		result.put("forge", forge);
		result.put("product", product);
		result.put("version", version);
		if (timestamp >= 0) result.put("timestamp", timestamp);
		result.put("depset", Dependency.toJSON(depset));
		JSONArray graphJSONArray = new JSONArray();
		for (FastenURI[] arc: graph) graphJSONArray.put(new JSONArray(new String[] {arc[0].toString(), arc[1].toString()}));
		result.put("graph", graphJSONArray);
		return result;
	}
	
	public static void main(String[] args) throws JSONException, FileNotFoundException, URISyntaxException, JSAPException {
		final SimpleJSAP jsap = new SimpleJSAP( RevisionCallGraph.class.getName(), 
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
		RevisionCallGraph callGraph = new RevisionCallGraph(json, ignoreConstraints);
		// TODO do something with the graph?
	}

	/**
	 * Creates builder to build {@link RevisionCallGraph}.
	 * @return created builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder to build {@link RevisionCallGraph}.
	 */
	public static final class Builder {
		private String forge;
		private String product;
		private String version;
		private long timestamp;
		private List<Dependency> depset = Collections.emptyList();
		private FastenURI uri;
		private FastenURI forgelessUri;
		private ArrayList<FastenURI[]> graph;

		private Builder() {
		}

		public Builder forge(String forge) {
			this.forge = forge;
			return this;
		}

		public Builder product(String product) {
			this.product = product;
			return this;
		}

		public Builder version(String version) {
			this.version = version;
			return this;
		}

		public Builder timestamp(long timestamp) {
			this.timestamp = timestamp;
			return this;
		}

		public Builder depset(List<Dependency> depset) {
			this.depset = depset;
			return this;
		}

		public Builder uri(FastenURI uri) {
			this.uri = uri;
			return this;
		}

		public Builder forgelessUri(FastenURI forgelessUri) {
			this.forgelessUri = forgelessUri;
			return this;
		}

		public Builder graph(ArrayList<FastenURI[]> graph) {
			this.graph = graph;
			return this;
		}

		public RevisionCallGraph build() {
			return new RevisionCallGraph(this);
		}
	}
	
	

}
