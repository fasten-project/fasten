package eu.fasten.core.data;

import java.io.FileNotFoundException;
import java.io.FileReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class JSONCallGraph {

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
		 */
		public Dependency(JSONObject json) {
			this.forge = json.getString("forge");
			this.product = json.getString("product");
			this.constraints = Constraint.constraints(json.getJSONArray("constraints"));
		}
		
		/** Given an JSON array of dependencies (a depset as specified in Fasten Deliverable 2.1), it returns
		 *  the corresponding depset.
		 *   
		 * @param depset the JSON array of dependencies.
		 * @return the corresponding array of dependencies.
		 */
		public static Dependency[] depset(JSONArray depset) {
			Dependency[] d = new Dependency[depset.length()];
			for (int i = 0; i < d.length; i++) 
				d[i] = new Dependency(depset.getJSONObject(i));
			return d;
		}
		
	}
	
	public final String forge;
	public final String product;
	public final String version;
	public final long timestamp;
	public final Dependency[] depset;
	
	
	/** Creates a JSON call graph with given data.
	 * 
	 * @param forge the forge.
	 * @param product the product.
	 * @param version the version.
	 * @param timestamp the timestamp (in seconds from UNIX epoch); optional: if not present, it is set to -1.
	 * @param depset the depset.
	 */
	public JSONCallGraph(String forge, String product, String version, long timestamp, Dependency[] depset) {
		this.forge = forge;
		this.product = product;
		this.version = version;
		this.timestamp = timestamp;
		this.depset = depset;
	}
	
	public JSONCallGraph(JSONObject json) {
		this.forge = json.getString("forge");
		this.product = json.getString("product");
		this.version = json.getString("version");
		long ts;
		try {
			ts = json.getLong("timestamp");
		} catch (JSONException exception) {
			ts = -1;
		}
		this.timestamp = ts;
		this.depset = Dependency.depset(json.getJSONArray("depset"));
	}



	public static void main(String[] args) throws JSONException, FileNotFoundException {
		JSONObject json = new JSONObject(new JSONTokener(new FileReader("/Users/boldi/Desktop/can_cgraph.json")));
		JSONCallGraph callGraph = new JSONCallGraph(json);
		System.out.println(callGraph.forge);
	}

}
