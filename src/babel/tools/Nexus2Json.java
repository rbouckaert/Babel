package babel.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import babel.nshelpers.AnnotationTuple;
import babel.nshelpers.AuthorMaintainer;
import babel.nshelpers.GenomeAnnotation;
import babel.nshelpers.NodeLocation;
import beastfx.app.treeannotator.TreeAnnotator;
import beastfx.app.treeannotator.TreeAnnotator.FastTreeSet;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Log;
import beast.base.core.Input.Validate;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;



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
	
	final public Input<File> demes_col = new Input<>("demes_col", " 2-column tsv file (with a header) containing deme colours in the format demename\tcol");
	
	
	final public Input<Boolean> nsMetaInput = new Input<>("ns", "true if nextstrain meta data should be printed also. If this is"
			+ "true then other details are also required", false);
	

	final public Input<String> build_url = new Input<>("build_url", "build URL for NextStrain metadata");
	final public Input<String> description = new Input<>("description", "description for NextStrain metadata (markdown)");
	final public Input<String> title = new Input<>("title", "title of projecr for NextStrain metadata");
	final public Input<String> geo_resolution = new Input<>("geo_resolution", "the name of the categorical variable that refers to grography");
	final public Input<String> locationTagsInput = new Input<>("locationTags", "list of annotations which are also locations (separated by ,)");
	
	final public Input<String> color_by = new Input<>("color_by", "the name of the categorical variable to colour by");
	final public Input<String> distance_measure_input = new Input<>("distance_measure", "the name of the node height variable (num_date or div)");
	
	
	
	final public Input<OutFile> printLocationsToInput = new Input<>("printLocationsTo", "where to print all predicted locations (latitude / longitude) to. Please "
			+ "manually inspect/adjust this file after it is done and add it to the input xml to save time on future runs.", Input.Validate.OPTIONAL);;
	final public Input<String> locationPatternInput = new Input<>("locationPattern", "order of looking up locations separated by a | delimiter, eg."
			+ "City|Country|Continent will search for cities using continent + country + city information.");
	
	final public Input<List<NodeLocation>> nodeLocationsInput = new Input<>("location", "list of locations (for nextstrain). "
			+ "If locations are not provided then beast2 will attempt to find them using the geocodes website.", new ArrayList<NodeLocation>());
	
	final public Input<List<GenomeAnnotation>> genomeAnnotationsInput = new Input<>("genomeAnnotation", "list of genome annotations (for nextstrain).", new ArrayList<GenomeAnnotation>());
	
	final public Input<List<AuthorMaintainer>> maintainersInput = new Input<>("maintainer", "list of authors/maintainers (for nextstrain).", new ArrayList<AuthorMaintainer>());
	
	String[] locationPattern;
	protected distanceMeasure distance_measure;
	final String INDENT = "  ";
	HashMap<String, String> demeColouring;
	
	boolean ns;
	List<String> locationTags = new ArrayList<String>();
	
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
			if (locationPatternInput.get() != null) {
				locationPattern = locationPatternInput.get().split(",");
			}
			if (locationTagsInput.get() != null) {
				locationTags = Arrays.asList(locationTagsInput.get().split(","));
			}
			if (annotationsInput.get() != null) {
				for (AnnotationTuple annotation : annotationsInput.get()) {
					if (annotation.isLocation()) locationTags.add(annotation.getName());
				}
			}
			
			
			
			
			// Read in deme colouring map
			if (demes_col.get() != null) {
				try {
					File file = demes_col.get();
					demeColouring = new HashMap<String, String>();
				
					Scanner scanner = new Scanner(file);
					
					// First line
					String line = scanner.nextLine();
					
					while (scanner.hasNext()) {
						
						line = scanner.nextLine().trim();
						if (line.isEmpty()) continue;
						
						String[] split = line.split("\t");
						if (split.length < 2) {
							throw new IllegalArgumentException("Cannot parse " + file.getName() + ". Line '" + line + "' has less than 2 elements. Ensure format is" +
									" 'demename\tcol' delimted by a tab");
						}
						String deme = split[0];
						String col = split[1];
						demeColouring.put(deme, col);
						
						
					}
					
				} catch (FileNotFoundException e) {
					e.printStackTrace();
					demeColouring = new HashMap<String, String>();
				}
				
				
				
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
        HashMap<String, NodeLocation> geoLocations = getGeoLocations(tree, locationPattern);
        
        
        for (String key : geoLocations.keySet()) {
        	System.out.println(key + " -> " + geoLocations.get(key).getDemeCat() + "," + geoLocations.get(key).getDemeName() + "," + geoLocations.get(key).getLat());
        }
        
        
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
		first = true;
		for (AuthorMaintainer person : maintainersInput.get()) {
			buf.print((first ? "" : ",\n") + indent2 + INDENT + person.getJSON());
			first = false;
		}
		buf.println("\n" + indent2 + "],");
		
		
		// colorings
		buf.println(indent2 + "\"colorings\":[");
		first = true;
		for (String key : attrKeys.keySet()) {
			
			if (attrKeys.get(key).equals("array")) continue;
			
    		buf.print((first ? "" : ",\n") + indent2 + INDENT + "{"
    				+ "\"key\":\"" + key + "\"," 
					+ "\"title\":\"" + key + "\"," 
					+ "\"type\":\"" + attrKeys.get(key) + "\"");
    		
    		
    		// Scale
    		if (color_by.get().equals(key)) {
    			buf.println(",\"scale\":[");
    			boolean firstLoc = true;
    			for (NodeLocation nodeLocation : geolocations.values()) {
    				
    				if (!nodeLocation.getDemeCat().equals(key)) continue;
    				String location = demeColouring.containsKey(nodeLocation.getDemeName()) ? nodeLocation.getDemeName() : "*";
        			String col = demeColouring.get(location);
        			if (col == null || col.isEmpty()) continue;
        			buf.print((firstLoc ? "" : ",\n") + indent2 + INDENT + INDENT +  "[\"" + nodeLocation.getDemeName() + "\", \"" + col + "\"]");
    				
        			firstLoc = false;
    			}
    			buf.print("\n" + indent2 + "]");
    			
    			
    		}
    		
    		
    		buf.print("}");
    		
    		
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
		
		

		// Get a list of all location tags
		//locationTags
		
		
		// geo_resolutions
		buf.println(indent2 + "\"geo_resolutions\":[");
		first = true;
		//for (int i = 0; i < annotationsInput.get().size(); i ++) {
			//AnnotationTuple annotation = annotationsInput.get().get(i);
			//if (!annotation.isLocation()) continue;
		for (int i = 0; i < locationTags.size(); i ++) {
			
			String location = locationTags.get(i);
			

    		buf.println(indent2 + INDENT + (first ? "" : ",") 
    						+ "{" 
	    					+ "\"key\":\"" + location + "\"," 
	    					+ "\"demes\":{");
    		
    		
    		
    		// Find latitude and longitudes which correspond to this deme
    		boolean firstDeme = true;
    		//for (NodeLocation nodeLocation : geolocations.values()) {
			for (String key : geolocations.keySet()) {
				NodeLocation nodeLocation = geolocations.get(key);
    			System.out.println(nodeLocation.getDemeCat() + " | " + nodeLocation.getDemeName());
    			if (!nodeLocation.getDemeCat().equals(location)) continue;
				String deme = nodeLocation.getDemeName();
				if (deme.isEmpty()) continue;
				deme = deme.replace("_", " ");
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
    private void toJSON(Node node, PrintStream buf, String indent, double treeHeight) {

    	
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
	    		if (val.getClass().isArray()) continue;
	    		buf.println((first ? "" : ",\n") + indent2 + INDENT + "\"" + key + "\":{");
	    		if (val instanceof Double || val instanceof Integer || val instanceof Boolean) {
	    			buf.print(indent2 + INDENT + INDENT + "\"value\":" + val);
	    			
	    			// Confidence?
	    			if (node.getMetaData(key + "_95%_HPD") != null) {
	    				Object confidence = node.getMetaData(key + "_95%_HPD");
	    				if (confidence instanceof Double[]) {
	    					Double[] conf = (Double[])confidence;
	    					buf.println(",\"confidence\":[" + conf[0] + "," + conf[1] + "]");
	    				}
	    				
	    			}
	    			buf.println();
	    			
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
    			

        		
        		// Tidy the key for JSON
        		String key_tidy = key.replaceAll("([.]|[-]|[/]|[&])", "_");
        		if (!key.equals(key_tidy)) {
        			Object val = node.getMetaData(key);
        			node.removeMetaData(key);
        			node.setMetaData(key_tidy, val);
        		}
        		
        		
        		// Categorical or continuous?
        		Object val = node.getMetaData(key_tidy);
        		String type = val.getClass().isArray() ? "array" : val instanceof Integer || val instanceof Double ? "continuous" : "categorical";
        		if (!allKeys.containsKey(key_tidy)) allKeys.put(key_tidy, type);
        		
        		
    		}
    		

    		
    		// Annotate with accessions
    		for (AnnotationTuple annotation : annotationsInput.get()) {
    			
    			    			
    			// For the special case of "distance_measure", every node must be annotated with this 
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
    protected HashMap<String, NodeLocation> getGeoLocations(Tree tree, String[] patterns){
    	
    	
    	// A lookup table of node locations so that we don't search for the coords
    	// of a location more than 1x
    	HashMap<String, NodeLocation> queries = new HashMap<String, NodeLocation>();
    	
    	
    	
    	// Add all the user parsed NodeLocations to the list
    	for (NodeLocation nodeLocation : nodeLocationsInput.get()) {
    		String query = nodeLocation.getQuery();
    		if (!query.isEmpty() && !queries.containsKey(query)) {
    			//System.out.println("Using provided user geodata for " + query + " -> " + nodeLocation.getDemeName());
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










