package babel.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import beast.app.util.OutFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.core.Input.Validate;
import beast.core.util.Log;

@Description("Convert JSON trees (from NextStrain) into nexus")
public class Json2Nexus extends Runnable {
	
	final public Input<File> treesInput = new Input<>("json", "JSON file containing a tree", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified", new OutFile("[[none]]"));
	
	static int nodeNum = 0;

	@Override
	public void initAndValidate() {
		
	}

	@Override
	public void run() throws Exception {

		
		// Read in json
		Log.err.println("Loading json file " + treesInput.get());
		String content = new Scanner(treesInput.get()).useDelimiter("\\Z").next();
		JSONObject json = new JSONObject(content);
		//System.out.println(json.toString());
		
		
		// Get the tree
		JSONObject jsonTree = json.getJSONObject("tree");
		Tree tree = parseTree(jsonTree);
		

		// Prepare out file
		Log.err.println("Printing nexus file " + outputInput.get());
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}
		
		
		// Write the nexus
		tree.init(out);
		out.println();
        out.print("tree TREE_1 = ");
        final int[] dummy = new int[1];
        final String newick = tree.getRoot().toSortedNewick(dummy, true);
        out.print(newick);
        out.println(";");
		
		
		tree.close(out);
		out.close();
		
		Log.err.println("Done");
		
	}
	
	
	/***
	 * Builds a beast2 tree from a nextstrain JSON tree
	 * Appropriately labels nodes 0 through to (n-1)
	 * Converts forward to reverse node heights
	 * @param jsonTree
	 * @return
	 * @throws JSONException
	 */
	public static Tree parseTree(JSONObject jsonTree) throws JSONException {
		
		// Create root
		nodeNum = 0;
		Node root = parseNode(jsonTree);
		

		
		// Put root inside tree1
		Tree tree = new Tree(root);
		
		// Number the leaves
		Node[] nodes = tree.getNodesAsArray();
		int nodeNum = 0;
		for (Node node : nodes) {
			if (node.isLeaf()) {
				node.setNr(nodeNum);
				nodeNum ++;
			}
		}
		
		// Number the internal nodes
		for (Node node : nodes) {
			if (!node.isLeaf() && !node.isRoot()) {
				node.setNr(nodeNum);
				nodeNum ++;
			}
		}
		
		
		
		// Most recent node height
		double latestHeight = 0;
		for (Node node : nodes) {
			if (node.getHeight() > latestHeight) latestHeight = node.getHeight();
		}
		
		
		// Adjust all node heights so that root has time T and most recent leaf has time 0 (ie. reverse time)
		for (Node node : nodes) {
			node.setHeight(latestHeight - node.getHeight());
		}
		
		
		return tree;
		
	}
	

	

	/***
	 * Parses this json node into a beast2 subtree. Node numbering is not yet finalised.
	 * @param jsonNode
	 * @return the node
	 * @throws JSONException
	 */
	private static Node parseNode(JSONObject jsonNode) throws JSONException {
		
		// Create node
		Node node = new Node();
		
		// Children
		if (jsonNode.has("children")) {
			JSONArray children = jsonNode.getJSONArray("children");
			for (int i = 0; i < children.length(); i ++) {
				Node child = parseNode(children.getJSONObject(i));
				node.addChild(child);
			}
		}
		
		node.setNr(nodeNum);
		//System.out.println(node.getNr());
		nodeNum ++;
		
		// Name
		String id = jsonNode.has("name") ? jsonNode.getString("name") : null;
		if (id.toLowerCase().matches("node[_]*[0-9]+")) id = null;
		node.setID(id);
		
		JSONObject node_attrs = jsonNode.getJSONObject("node_attrs");

		
		// Node height
		double height = getHeight(jsonNode);
		node.setHeight(height);
		

		
		// Attributes
		Iterator<String> keys = node_attrs.keys();
		while(keys.hasNext()) {
		    String key = keys.next();
		    try {
			    if (node_attrs.getJSONObject(key).has("value")) {
			    	String value = node_attrs.getJSONObject(key).getString("value");
			    	node.setMetaData(key, value);
			    }
		    }catch (JSONException e) {
		    	
		    }
		}
		processMetaData(node);
		

		return node;
		
	}

	
	/***
	 * Returns the height of a JSON node
	 * @param jsonNode - JSON encoding of a node
	 * @return the height of the node (either div or num_date)
	 * @throws JSONException
	 */
	private static double getHeight(JSONObject jsonNode) throws JSONException {
		
		// Get node height (either div or num_date)
		JSONObject node_attrs = jsonNode.getJSONObject("node_attrs");
		if (node_attrs.has("num_date")) {
			return node_attrs.getJSONObject("num_date").getDouble("value");
		}
		else if (jsonNode.has("div")) {
			return jsonNode.getDouble("div");
		}
		else {
			throw new IllegalArgumentException("Nodes in JSON tree must contain either 'num_date' or 'div' to specify node heights");
		}
	}
	
	
	/***
	 * Builds the metadata string of a node
	 * @param node
	 */
    public static void processMetaData(Node node) {
		Set<String> metaDataNames = node.getMetaDataNames(); 
		if (metaDataNames != null && !metaDataNames.isEmpty()) {
			String metadata = "";
			for (String name : metaDataNames) {
				String value = node.getMetaData(name).toString();
				boolean num = tryParseNum(value);
				if (num) metadata += name + "=" + value + ",";
				else {
					value = value.replace("\"", "'");
					metadata += name + "=\"" + value + "\",";
				}
			}
			metadata = metadata.substring(0, metadata.length() - 1);
			node.metaDataString = metadata;
		}		
	}
    
    
    /**
     * Figure out whether this string is a number (int or double)
     * @param value
     * @return
     */
    public static boolean tryParseNum(String value) {  
        try {  
            Integer.parseInt(value);  
            Double.parseDouble(value);
            return true;  
         } catch (NumberFormatException e) {  
            return false;  
         }  
    }
    
    
	
	
}








