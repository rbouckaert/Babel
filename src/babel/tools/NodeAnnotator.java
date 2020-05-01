package babel.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import beast.app.treeannotator.TreeAnnotator;
import beast.app.treeannotator.TreeAnnotator.FastTreeSet;
import beast.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.Input.Validate;
import beast.core.util.Log;
import beast.evolution.tree.Node;
import beast.evolution.tree.TraitSet;
import beast.evolution.tree.Tree;

@Description("Annotates nodes in a tree.")
public class NodeAnnotator extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out","output file. Print to stdout if not specified");
	final public Input<List<File>> metadataInput = new Input<>("metadata","tab delimited text file(s) where each row is a taxon and each column is metadata. The first column"
			+ " of each file is assumed to be the node label.", new ArrayList<File>());
	final public Input<String> variablesInput = new Input<>("var","the names of the variables (within the metadata) to annotate nodes with, separated by ,",Validate.REQUIRED);
	

	private String[] variables;
	
	@Override
	public void initAndValidate() {
		
	}

	@Override
	public void run() throws Exception {
		
		if (metadataInput.get().isEmpty()) throw new IllegalArgumentException("Please provide 1 or more metadata files");
		this.variables = variablesInput.get().split(",");
		if (this.variables.length == 0) throw new IllegalArgumentException("Please provide 1 or more variables to annotate");
		
		// Read metadata file(s)
		List<String> annotationNames = new ArrayList<String>();
		Map<String, List<String>> metadata = new HashMap<String, List<String>>();
		for (File file : metadataInput.get()) {
			BufferedReader fin = new BufferedReader(new FileReader(file));
			String s;
			
			// Read header
			while ((s = readLine(fin)) != null) {
				
				s = s.trim();
				if (s.isEmpty() || s.substring(0, 1).equals("#")) continue;
				
				String [] strs = s.split("\t");
				if (strs.length < 2) {
					throw new IllegalArgumentException("Only 1 column found in " + file.getName());
				}
				
				// Add all column names except for the 1st one which is assumed to be taxon label
				System.out.print("Column headers:");
				for (int i = 1; i < strs.length; i ++) {
					annotationNames.add(strs[i]);
					System.out.print("\t" + strs[i]);
				}
				System.out.println();
				break;
				
			}
			
			
			// Read metadata values
			while ((s = readLine(fin)) != null) {
				s = s.trim();
				if (s.isEmpty() || s.substring(0, 1).equals("#")) continue;
				String [] strs = s.split("\t");
				if (strs.length >=2) {
					
					// Get the metadata of this taxon
					String taxon = strs[0];
					List<String> annotations = new ArrayList<String>();
					for (int i = 1; i < strs.length; i ++) {
						annotations.add(strs[i]);
					}
					
					// Join lists from across files or make a new one
					if (metadata.containsKey(taxon)) {
						metadata.get(taxon).addAll(annotations);
					}else {
						metadata.put(taxon, annotations);
					}
					
				}
			}
			
			fin.close();
		}
		

		
		// Open file for writing
        PrintStream out = System.out;
        if (outputInput.get() != null) {
        	out = new PrintStream(outputInput.get());
			Log.warning("Writing to file " + outputInput.get().getPath());
        }

        // Open trees
        FastTreeSet trees = new TreeAnnotator().new FastTreeSet(treesInput.get().getAbsolutePath(), 0);
        trees.reset();
        Tree tree = trees.next();
        tree.init(out);
        out.println();
        
        
        // Annotate nodes
        trees.reset();
        int i = 0;
        while (trees.hasNext()) {
        	tree = trees.next();
        	this.annotate(tree.getRoot(), annotationNames, metadata);
            out.println();
            out.print("tree STATE_" + i + " = ");
            final String newick = tree.getRoot().toSortedNewick(new int[1], true);
            out.print(newick);
            out.print(";");
        	i++;
        }
        out.println();
        out.println("end;");
        
		if (out != System.out) {
			out.close();
		}
		Log.warning("All done. " + (outputInput.get() != null ? "Results in " + outputInput.get().getPath() : ""));

	}

    private void annotate(Node node, List<String> annotationNames, Map<String, List<String>> metadata) {
    	
    	// Annotate this node if it has a label
		if (node.getID() != null && !node.getID().isEmpty() && metadata.containsKey(node.getID())) {
			

			
			for (String variable : this.variables) {
				
				// Node.java will try to convert into a double if the variable is called 'date'
				String variableToPrint = variable;
				if (variable.equals(TraitSet.DATE_TRAIT) || variable.equals(TraitSet.DATE_FORWARD_TRAIT) || variable.equals(TraitSet.DATE_BACKWARD_TRAIT)) {
					variableToPrint = "the_" + variableToPrint;
				}
				
				// Column index of variable
				int columnIndex = 0;
				for (columnIndex = 0; columnIndex <  annotationNames.size(); columnIndex ++) {
					if (annotationNames.get(columnIndex).equals(variable)) break;
				}
				
				if (columnIndex == annotationNames.size()) {
					throw new IllegalArgumentException("Cannot find " + variable + " in the column headers!");
				}
				
				String value = metadata.get(node.getID()).get(columnIndex);
				
				
				node.setMetaData(variableToPrint, value);
				
			}
			
		}
		
		
		// Annotate children
		for (Node child : node.getChildren()) {
			this.annotate(child, annotationNames, metadata);
		}
		
		
		// Built metadata string
		processMetaData(node);
			
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
				
				
				Object value = node.getMetaData(name);
				
				if (value instanceof Object[]) {
					Object [] values = (Object[]) value;
					metadata += name + "={";
					for (int i = 0; i < values.length; i++) {
						metadata += values[i].toString();
						if (i < values.length - 1) {
							metadata += ",";
						}
					}
					metadata += "},";
				}else {
				
					String valueStr = value.toString();
					boolean num = tryParseNum(valueStr);
					if (num) metadata += name + "=" + valueStr + ",";
					else {
						valueStr = valueStr.replace("\"", "'");
						metadata += name + "=\"" + valueStr + "\",";
					}
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
        	Double.parseDouble(value);
            return true;  
         } catch (NumberFormatException e) {  
            return false;  
         }  
    }
    
    

    
	String readLine(BufferedReader fin) throws IOException {
        if (!fin.ready()) {
            return null;
        }
        //lineNr++;
        return fin.readLine();
    }

	
	public static void main(String[] args) throws Exception {
		new Application(new NodeAnnotator(), "Node Annotator", args);
	}
}
