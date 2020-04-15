package babel.tools;

import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import beast.app.treeannotator.TreeAnnotator;
import beast.app.treeannotator.TreeAnnotator.FastTreeSet;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.core.Input.Validate;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;



@Description("Convert nexus tree file into JSON and optionally annotates nodes with components of the label names")
public class Nexus2Json extends Runnable {
	

	// Node heights
	protected enum distanceMeasure {
		div, num_date
	}
	
	final public Input<TreeFile> treesInput = new Input<>("trees", "NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<List<AnnotationTuple>> annotationsInput = new Input<>("annotation", "Character to split the sequence names with", new ArrayList<AnnotationTuple>());
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));
	
	final public Input<Boolean> nsMetaInput = new Input<>("ns", "true if nextstrain meta data should be printed also. If this is"
			+ "true then other details are also required", false);
	
	final public Input<String> build_url = new Input<>("build_url", "build URL for NextStrain metadata");
	final public Input<String> description = new Input<>("description", "description for NextStrain metadata (markdown)");
	final public Input<String> title = new Input<>("title", "title of projecr for NextStrain metadata");
	final public Input<String> maintainerName = new Input<>("maintainerName", "your name");
	final public Input<String> maintainerWebsite = new Input<>("maintainerWebsite", "your website/email");
	final public Input<String> geo_resolution = new Input<>("geo_resolution", "the name of the categorical variable that refers to grography");
	final public Input<String> color_by = new Input<>("color_by", "the name of the categorical variable to colour by");
	final public Input<String> distance_measure_input = new Input<>("distance_measure", "the name of the node height variable (num_date or div)");
	
	final public Input<OutFile> printLocationsToInput = new Input<>("printLocationsTo", "where to print all predicted locations (latitude / longitude) to. Please "
			+ "manually inspect/adjust this file after it is done and add it to the input xml to save time on future runs.", Input.Validate.OPTIONAL);;
	final public Input<List<String>> locationPatternInput = new Input<>("locationPattern", "order of looking up locations separated by a | delimiter, eg."
			+ "City|Country|Continent will search for cities using continent + country + city information.", new ArrayList<String>());
	
	final public Input<List<NodeLocation>> nodeLocationsInput = new Input<>("location", "list of locations (for nextstrain). "
			+ "If locations are not provided then beast2 will attempt to find them using the geocodes website.", new ArrayList<NodeLocation>());
	
	final public Input<List<GenomeAnnotation>> genomeAnnotationsInput = new Input<>("genomeAnnotation", "list of genome annotations (for nextstrain).", new ArrayList<GenomeAnnotation>());
	
	
	
	
	protected distanceMeasure distance_measure;
	final String INDENT = "  ";
	
	boolean ns;
	
	public Nexus2Json() {
		
	}
	
	
	@Override
	public void initAndValidate() {
		
		ns = nsMetaInput.get();
		if (ns) {
			
			if (title.get() == null || title.get().isEmpty()) {
				throw new IllegalArgumentException("Please specify title (or set ns to false).");
	        }
			if (geo_resolution.get() == null || geo_resolution.get().isEmpty()) {
				throw new IllegalArgumentException("Please specify geo_resolution (or set ns to false).");
	        }
			if (color_by.get() == null || color_by.get().isEmpty()) {
				throw new IllegalArgumentException("Please specify color_by (or set ns to false).");
	        }
			if (distance_measure_input.get() == null || distance_measure_input.get().isEmpty()) {
				throw new IllegalArgumentException("Please specify distance_measure (or set ns to false).");
	        }

			distance_measure = distanceMeasure.valueOf(distance_measure_input.get());
			
		}
		
	}

	@Override
	public void run() throws Exception {
		
		// Load the tree
        FastTreeSet trees = new TreeAnnotator().new FastTreeSet(treesInput.get().getAbsolutePath(), 0);
        
        // Only 1 tree allowed
        trees.reset();
        int numTrees = 0;
        Tree tree = null;
        while (trees.hasNext()) {
        	tree = trees.next();
        	numTrees++;
        	if (numTrees > 1) break;
        }
        
        if(numTrees > 1) {
        	throw new IllegalArgumentException("Only 1 tree is allowed! Please parse a maximum clade credibility tree for example.");
        }
        if(tree == null) {
        	throw new IllegalArgumentException("Unable to find a tree.");
        }
		
        
        // Annotate the nodes in the tree using sequence accession information
        HashMap<String, String> attrKeys = annotateNodes(tree);
        
        
        // Get geo location data of each node
        HashMap<String, NodeLocation> geoLocations = getGeoLocations(tree, locationPatternInput.get());
        
        // Print location data to file?
        if (printLocationsToInput.get() != null) {
        	
        	PrintStream geo_out = new PrintStream(printLocationsToInput.get());
        	
        	for (NodeLocation nodeLocation : geoLocations.values()) {
        		//if (!nodeLocation.hasCoords()) continue;
        		geo_out.print("<location ");
        		geo_out.print(nodeLocation.getXML());
        		geo_out.println(" />");
        	}
        	
        	geo_out.close();
        	
        }
        
		
		// Open file for writing
        PrintStream out = System.out;
        if (outputInput.get() != null) {
			Log.warning("Writing to file " + outputInput.get().getPath());
        	out = new PrintStream(outputInput.get());
        }
        out.println("{");
        
        

        
        
        // NextStrain metadata
        if (ns) {
        	 out.println(INDENT + "\"meta\":");
        	 metaJSON(tree, out, attrKeys, geoLocations, INDENT + INDENT);
        	 out.println(INDENT + ",");
        	 out.println(INDENT + "\"version\":\"v2\",");
        }
        

        // Trees
        out.println(INDENT + "\"tree\":");
        toJSON(tree.getRoot(), out, INDENT + INDENT, tree.getRoot().getHeight());
        out.println();
        out.println("}");
        
        
        Log.err.println("Done");
        out.close();
        
        //test(tree.getRoot(), "\t", 0);
        

 	}
	
	/*
	private void test(Node node, String indent, double parentTime) {
		

		
		System.out.print(indent + node.getMetaData("num_date") + " " + node.getID());
		
		if (parentTime > (double) node.getMetaData("num_date")) {
			System.out.print(" XXX");
		}
		
		if (node.isLeaf()) {
			System.out.println();
		}
		
		System.out.println();
		
		for (Node c : node.getChildren()) {
			test(c, indent + "\t",  (double) node.getMetaData("num_date"));
		}
		
	}
	*/
	
	
	/**
	 * Print nextstrain meta data in json format 
	 * @param buf
	 * @param indent
	 */
	protected void metaJSON(Tree tree, PrintStream buf, HashMap<String, String> attrKeys,  HashMap<String, NodeLocation> geolocations, String indent) {
		


        // Get the list of values of each annotation
		//List<String> geo_resolution_values = getValuesOfAnnotation(tree, geo_resolution.get());
		boolean first = true;

		
		// String building
		buf.println(indent + "{");
		String indent2 = indent + INDENT;
		
		
		// description
		if (description.get() != null) buf.println(indent2 + "\"description\":\"" + description.get() + "\",");
		
		// build_url
		if (build_url.get() != null) buf.println(indent2 + "\"build_url\":\"" + build_url.get() + "\",");
		
		// title
		buf.println(indent2 + "\"title\":\"" + title.get() + "\",");
		
		// updated
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date date = new Date();
		buf.println(indent2 + "\"updated\":\"" + dateFormat.format(date) + "\",");
		
		// maintainers
		buf.println(indent2 + "\"maintainers\":[");
		if (maintainerName.get() != null && maintainerWebsite.get() != null) {
			buf.println(indent2 + INDENT + "{\"name\":\"" + maintainerName.get() + 
							"\",\"url\":\"" + maintainerWebsite.get() + "\"}");
		}
		buf.println(indent2 + "],");
		
		// colorings
		buf.println(indent2 + "\"colorings\":[");
		first = true;
		for (String key : attrKeys.keySet()) {
			
    		buf.print((first ? "" : ",\n") + indent2 + INDENT + "{"
    				+ "\"key\":\"" + key + "\"," 
					+ "\"title\":\"" + key + "\"," 
					+ "\"type\":\"" + attrKeys.get(key) + "\"}");
    		first = false;
		}
		buf.println("\n" + indent2 + "],");
		
		
		
		// filters
		buf.println(indent2 + "\"filters\":[");
		first = true;
		for (String key : attrKeys.keySet()) {
    		buf.print((first ? "" : ",\n") + indent2 + INDENT + "\"" + key + "\"");
    		first = false;
		}
		buf.println("\n" + indent2 + "],");
		
		// display_defaults
		buf.println(indent2 + "\"display_defaults\":{"
				+ "\"branch_label\":\"none\","
				+ "\"map_triplicate\":true,"
				+ "\"distance_measure\":\"" + distance_measure.toString() + "\","
				+ "\"geo_resolution\":\"" + geo_resolution.get() + "\","
				+ "\"color_by\":\"" + color_by.get() + "\"},");

		
		// genome_annotations
		buf.println(indent2 + "\"genome_annotations\":{");
		first = true;
		for (GenomeAnnotation g : genomeAnnotationsInput.get()) {
			buf.print((first ? "" : ",\n") + indent2 + INDENT + g.getJSON());
			first = false;
		}
		//buf.println(indent2 + "\"nuc\":{}");
		buf.println("\n" + indent2 + "},");
		
		

		
		// geo_resolutions
		buf.println(indent2 + "\"geo_resolutions\":[");
		first = true;
		for (int i = 0; i < annotationsInput.get().size(); i ++) {
			AnnotationTuple annotation = annotationsInput.get().get(i);
			if (!annotation.isLocation()) continue;
			

    		buf.println(indent2 + INDENT + (first ? "" : ",") 
    						+ "{" 
	    					+ "\"key\":\"" + annotation.getName() + "\"," 
	    					+ "\"demes\":{");
    		
    		
    		
    		// Find latitude and longitudes which correspond to this deme
    		boolean firstDeme = true;
    		for (NodeLocation nodeLocation : geolocations.values()) {
    			//System.out.println(nodeLocation.getDemeCat() + " != " + annotation.getName());
    			if (!nodeLocation.getDemeCat().equals(annotation.getName())) continue;
				String deme = nodeLocation.getDemeName();
				if (deme.isEmpty()) continue;
				double latitude = nodeLocation.getLat();
    			double longitude = nodeLocation.getLong();
    			
    			buf.println(indent2 + INDENT + INDENT + (firstDeme ? "" : ",")
    					+ "\"" + deme + "\":{\"latitude\":" + latitude + ",\"longitude\":" + longitude + "}");
    			firstDeme = false;
    				
    		}
    		
    		buf.println(indent2 + INDENT + INDENT + "}"); 
    		buf.println(indent2 + INDENT + "}");
    		
    		first = false;
		}
		buf.println(indent2 + "],");
		
		
		// panels
		buf.println(indent2 + "\"panels\":[\"tree\",\"map\",\"entropy\"]");
		//buf.println(indent2 + "\"panels\":[\"tree\",\"map\"]");

		
		buf.print(indent + "}");
		
	}
	

	
	
	/**
	 * Print this subtree into json format
	 * @param node
	 * @param buf
	 * @param indent
	 */
    public void toJSON(Node node, PrintStream buf, String indent, double treeHeight) {

    	
    	buf.println(indent + "{");
    	String indent2 = indent + INDENT;
    	
    	
    	// Name and meta data
    	String name = node.getID() != null && node.getID() != "" ? node.getID() : "node" + node.getNr();
    	buf.println(indent2 + "\"name\":\"" + name + "\",");
    	buf.print(indent2 + "\"node_attrs\":{");
    	
    	
    	// Annotate with accession / metadata
    	if ( node.getMetaDataNames().size() > 0) {
    		buf.println();
    		boolean first = true;
	    	for (String key : node.getMetaDataNames()) {

	    		Object val = node.getMetaData(key);
	    		if (val.toString().isEmpty()) continue;
	    		buf.println((first ? "" : ",\n") + indent2 + INDENT + "\"" + key + "\":{");
	    		if (val instanceof Double || val instanceof Integer || val instanceof Boolean) {
	    			buf.println(indent2 + INDENT + INDENT + "\"value\":" + val);
	    		}else {
	    			buf.println(indent2 + INDENT + INDENT + "\"value\":\"" + val + "\"");
	    		}
	    		buf.print(indent2 + INDENT + "}");
	    		
	    		first = false;
	    	}
	    	buf.println( "\n" + indent2 + "},");
    	}
    	else {
    		buf.println("},");
    	}
    	
 
    	
    	if (node.isLeaf()) {
    		
    	}
    	
    	else {
    		
    		buf.println(indent2 + "\"children\":[");
    		
    		for (int i = 0; i < node.getChildCount(); i ++) {
    			toJSON(node.getChild(i), buf, indent2 + INDENT, treeHeight);
    			boolean last = i == node.getChildCount()-1;
    			buf.println(last ? "" : ",");
    		}
    		
    		
    		buf.println(indent2 + "],");
    		
    	}
    	
    	//buf.println(indent2 + "\"div\":" + (treeHeight - node.getHeight()) + ",");
    	buf.println(indent2 + "\"branch_attrs\":{}");
    	
    	buf.print(indent + "}");
    	
    }
    
    

    
    /***
     * Annotate all nodes in the tree using their sequence names (ie. accessions) and the list of 'annotations'
     * @param tree
     * @return 
     */
    private HashMap<String, String> annotateNodes(Tree tree) {
    	
    	HashMap<String, String> allKeys = new HashMap<String, String>();
		
		// Post order traversal
		Node[] nodes = new Node[tree.getNodeCount()];
		tree.listNodesPostOrder(tree.getRoot(), nodes);
		
    	
    	for (Node node : nodes) {
    		
    		String seqName = node.getID();

    		
    		// Tidy the current list of annotations
			List<String> keys = new ArrayList<String>();
			for (String s : node.getMetaDataNames()) keys.add(s); 
    		for (String key : keys) {
    			
    			
    			// No arrays allows at this stage (eg. 95% HPDs). May revisit.
        		if (node.getMetaData(key).getClass().isArray()) {
        			node.removeMetaData(key);
        			continue;
        		}
        		
        		// Tidy the key for JSON
        		String key_tidy = key.replaceAll("([.]|[-]|[/]|[&])", "_");
        		if (!key.equals(key_tidy)) {
        			Object val = node.getMetaData(key);
        			node.removeMetaData(key);
        			node.setMetaData(key_tidy, val);
        		}
        		
        		
        		// Categorical or continuous?
        		Object val = node.getMetaData(key_tidy);
        		String type = val instanceof Integer || val instanceof Double ? "continuous" : "categorical";
        		if (!allKeys.containsKey(key_tidy)) allKeys.put(key_tidy, type);
        		
        		
    		}
    		

    		
    		// Annotate with accessions
    		for (AnnotationTuple annotation : annotationsInput.get()) {
    			
    			    			
    			// For the special case of \"distance_measure\", every node must be annotated with this 
    			// Assumed to be a date
    			if (distance_measure.toString().equals(annotation.getName())) {
    				
    				if (node.getMetaData(annotation.getName()) == null){
    					
    					double val = 0;
    					if (seqName != null) {
    						val = Double.parseDouble(annotation.getValue(seqName));
    					}
    					else if (!node.isLeaf()) {
    						val = (double) node.getChild(0).getMetaData(annotation.getName()) - node.getChild(0).getLength();
    						double val2 = (double) node.getChild(1).getMetaData(annotation.getName()) - node.getChild(1).getLength();
    						if (Math.abs(val - val2) > 0.001) {
    							System.out.println(node.getChild(0).getMetaData(annotation.getName()) + "-" + node.getChild(0).getLength() + "=" + val);
    							System.out.println(node.getChild(1).getMetaData(annotation.getName()) + "-" + node.getChild(1).getLength() + "=" + val2 + "\n");
    						}
    					}
    					else {
    						throw new IllegalArgumentException("Cannot assign a node height to " + node.getNr() + " because it has no annotation and no children with annotations.");
    					}
    					
    					node.setMetaData(annotation.getName(), val);
    					if (!allKeys.containsKey(annotation.getName())) allKeys.put(annotation.getName(), annotation.getTypeSimple());
    					
    				}
    				
    			}
    			
    			// General case: parse any annotations which exist
    			else if (seqName != null) {
    			
    				String val = annotation.getValue(seqName);
    				node.setMetaData(annotation.getName(), val);
    				if (!allKeys.containsKey(annotation.getName())) allKeys.put(annotation.getName(), annotation.getTypeSimple());
    			}
    			
    			
    		}
    		
    	}
    	
    	
    	// Return the list of keys
		return allKeys;
    	
    	
    }
    
    
    
    /**
     * Returns a list of node locations (latitude and longitude) for this tree
     * The data is either supplied by the user (in the xml) or where unavailable it is found using the geocodes website
     * The latter approach is more time consuming and the website may enforce user restrictions so please provide the 
     * coordinates as much as possible.
     * 
     * 
     * @param tree
     * @return
     */
    protected HashMap<String, NodeLocation> getGeoLocations(Tree tree, List<String> patterns){
    	
    	
    	// A lookup table of node locations so that we don't search for the coords
    	// of a location more than 1x
    	HashMap<String, NodeLocation> queries = new HashMap<String, NodeLocation>();
    	
    	
    	
    	// Add all the user parsed NodeLocations to the list
    	for (NodeLocation nodeLocation : nodeLocationsInput.get()) {
    		String query = nodeLocation.getQuery();
    		if (!query.isEmpty() && !queries.containsKey(query)) {
    			//System.out.println("Using provided user geodata for " + query);
    			queries.put(query, nodeLocation);
    		}
    		
    	}
    	
    	// Check if there are any missing demes and if so find them on the internet
    	for (Node node : tree.getNodesAsArray()) {
    		
    		for (String pattern : patterns) {
    		
    			NodeLocation nodeLocation = new NodeLocation(pattern, node);
    			String query = nodeLocation.getQuery();
    			
    			//System.out.println("query: " + query);
    			
    			// If the location has unknown coordinates then search for it
        		if (!query.isEmpty() && !queries.containsKey(query)) {
        			//System.out.println("Using searchable user geodata for " + query);
        			nodeLocation.search();
        			queries.put(query, nodeLocation);
        			
        			//return queries;
        		}
    		
        		
        		
    		}
    		
    		
    		
    	}
    	
    	
    	return queries;
    }
    
    
    
    /***
     * Use the geocode website to attempt to find the latitude and longtitude of each deme
     * @param demes
     * @return
     */
    protected HashMap<String, double[]> getGeoCoordinates(List<Object> demes){
    	
    	HashMap<String, double[]> map = new HashMap<String, double[]>();
    	
    	for (Object demeObj : demes) {
    		
    		String deme = demeObj.toString();
    		double[] val = new double[] {0, 0};
    		map.put(deme, val);
    		
    		/*
	    	try {
	    		
	    		
				 URL coords = new URL("https://geocode.xyz/" + deme + "?json=1");
				
				 Scanner sc = new Scanner(coords.openStream());
			    
			     StringBuffer sb = new StringBuffer();
			     while(sc.hasNext()) {
			        sb.append(sc.next());
			       
			     }
			     JSONObject json = new JSONObject(sb.toString());
			     double latitide = Double.parseDouble(json.get("latt").toString());
			     double longitude = Double.parseDouble(json.get("longt").toString());
			     val[0] = latitide;
			     val[1] = longitude;
			    
			     
				
				
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				System.out.println("Cannot locate " + deme);
				//e.printStackTrace();
			} catch (JSONException e) {
				System.out.println("Cannot find the latitude/longitude of " + deme);
				//e.printStackTrace();
			} finally {
				
				map.put(deme, val);
			}
    		 */
    	}
    	
    	return map;
    	
    	
    }
    
    
    
	
	
	/**
	 * Returns a list of categorical values of an annotation retrieved from sequence accession name
	 * @param annotationName
	 * @return
	 */
	protected List<Object> getValuesOfAnnotation(Tree tree, String annotationName) {
		

		
		// Get the value of this annotation from each node
		HashMap<Object, Boolean> map = new HashMap<Object, Boolean>();
		for (Node node : tree.getNodesAsArray()) {
			Object val = node.getMetaData(annotationName);
			
			// No empty values
			if (val == null || val.toString().isEmpty()) continue;
			
			if (!map.containsKey(val)) map.put(val, true);
		}
		
		
		// Check whether this annotation exists
		if (map.keySet().size() == 0) {
			throw new IllegalArgumentException("Could not find any values of " + annotationName + ". Please ensure this"
					+ "variable is correctly specified as an \"annotation\".");
		}
		

		// Return the values
		List<Object> vals = new ArrayList<Object>(); 
	    for (Object s : map.keySet()) vals.add(s); 
		return vals;
		
	}

    
    public static void main(String[] args) throws Exception {
		new Application(new Nexus2Json(), "NEXUS2JSON", args);
	}
    
    
    
    
}










