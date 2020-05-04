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
	final public Input<String> metadataInput = new Input<>("metadata","tab delimited text file(s) where each row is a taxon and each column is metadata. The first column"
			+ " of each file is assumed to be the node label. Columns separated by +", Validate.REQUIRED);
	final public Input<String> variablesInput = new Input<>("var","the names of the variables (within the metadata) to annotate nodes with, separated by ,. If there are multiple files then"
			+ "separate variable strings with a ';'. ie. tsv1_var1,tsv1_var2,...;tsv2_var1,tsv1_var2,... ",Validate.REQUIRED);
	final public Input<String> labelInput = new Input<>("label", "The name of the column which matches the tree labels (if not specified the 1st column will be used). Separate by '+' for each file ");

	
	List<String[]> variables = new ArrayList<String[]>();
	List<List<String>> annotationNames = new ArrayList<List<String>>();
	List<Map<String, List<String>>> metadata = new ArrayList<Map<String, List<String>>>();
	
	
	@Override
	public void initAndValidate() {
		
	}

	@Override
	public void run() throws Exception {
		
		if (metadataInput.get().isEmpty()) throw new IllegalArgumentException("Please provide 1 or more metadata files");
		String[] filenames = metadataInput.get().split("[+]");

		// Variables
		String[] variablesFiles = variablesInput.get().split("[+]");
		if (variablesFiles.length != filenames.length) throw new IllegalArgumentException("Please ensure there is 1 metadata file (separated by '+' for each variable set (separated by '+')");
      
		
		// Label column
		String[] labelColumn = null;
		if (labelInput.get() != null) {
			labelColumn = labelInput.get().split("[+]");
			if (labelColumn.length != filenames.length) throw new IllegalArgumentException("Please ensure there is 1 metadata file (separated by '+' for each label set (separated by '+')");
		      
		}
		

		// Read metadata file(s)
		for (int fileNum = 0; fileNum < filenames.length; fileNum ++) {
			

			
			String filename = filenames[fileNum];
			File file = new File(filename);
			System.out.println("Reading metadata from " + file.getAbsolutePath());
			BufferedReader fin = new BufferedReader(new FileReader(file));
			String s;
			
			// Read header
			List<String> annotationNamesFile = new ArrayList<String>();
			while ((s = readLine(fin)) != null) {
				
				s = s.trim();
				if (s.isEmpty() || s.substring(0, 1).equals("#")) continue;
				
				String [] strs = s.split("\t");
				if (strs.length < 2) {
					throw new IllegalArgumentException("Only 1 column found in " + file.getName());
				}
				
				// Add all column names except for the 1st one which is assumed to be taxon label
				System.out.print("Column headers:");
				for (int i = 0; i < strs.length; i ++) {
					annotationNamesFile.add(strs[i]);
					System.out.print("\t" + strs[i]);
				}
				System.out.println();
				break;
				
			}
			
			
			
			
			// Read metadata values
			Map<String, List<String>> hashmap = new HashMap<String, List<String>>();
			while ((s = readLine(fin)) != null) {
				s = s.trim();
				if (s.isEmpty() || s.substring(0, 1).equals("#")) continue;
				String [] strs = s.split("\t");
				if (strs.length >=1) {
					
					// Get the metadata of this taxon
					List<String> annotations = new ArrayList<String>();
					for (int i = 0; i < strs.length; i ++) {
						annotations.add(strs[i]);
					}
					
					// Get the taxon out of the tsv
					String taxon = null;
					if (labelColumn == null) taxon = strs[0];
					else {
						for (int colNumber = 0; colNumber < annotationNamesFile.size(); colNumber++) {
							String colName = annotationNamesFile.get(colNumber);
							if (colName.equals(labelColumn[fileNum])) {
								taxon = strs[colNumber];
							}
						}
						
						if (taxon == null) {
							throw new IllegalArgumentException("Cannot find column name " + labelColumn[fileNum] + " in " + file.getAbsolutePath());
						}
						
					}
					
					// Join lists from across files or make a new one
					if (hashmap.containsKey(taxon)) {
						hashmap.get(taxon).addAll(annotations);
					}else {
						hashmap.put(taxon, annotations);
					}
					
				}
			}
			
			fin.close();
			
			
			
			// Variables
			String[] v = variablesFiles[fileNum].split(",");
			
			if (v.length == 0) throw new IllegalArgumentException("Please provide 1 or more variables to annotate for " + filename);
						
			this.metadata.add(hashmap);
			this.variables.add(v);
			this.annotationNames.add(annotationNamesFile);
			
			
		}
		
		
		// Open trees
        MemoryFriendlyTreeSet trees = new TreeAnnotator().new MemoryFriendlyTreeSet(treesInput.get().getAbsolutePath(), 0);

		// Open file for writing
        PrintStream out = System.out;
        if (outputInput.get() != null) {
        	out = new PrintStream(outputInput.get());
			Log.warning("Writing to file " + outputInput.get().getPath());
        }
		
		// Trees
		trees.reset();

        // Annotate nodes
		int i = 0;
        while (trees.hasNext()) {
        	
        	Tree tree = trees.next();
        	
        	if (i == 0) {
        		tree.init(out);
        		out.println();
        	}
        	
        	if (i % 500 == 0) Log.warning("Processing tree " + i);
        	
        	// Annotate
        	for (int fileNum = 0; fileNum < filenames.length; fileNum ++) {
        		this.annotate(tree.getRoot(), annotationNames.get(fileNum), metadata.get(fileNum), variables.get(fileNum));
        	}
        	
        	
        	// Build Metadata string
    		processMetaData(tree.getRoot());
    		
            out.println();
            out.print("tree STATE_" + i + " = ");
            final String newick = tree.getRoot().toSortedNewick(new int[1], true);
            out.print(newick);
            out.print(";");
        	i++;
        }
		
      
        // Close out file
        out.println();
        out.println("end;");
        
		if (out != System.out) {
			out.close();
		}

		
		Log.warning("All done. " + (outputInput.get() != null ? "Results in " + outputInput.get().getPath() : ""));


	}

    private void annotate(Node node, List<String> annotationNames, Map<String, List<String>> metadata, String[] variables) {
    	
    	// Annotate this node if it has a label
		if (node.getID() != null && !node.getID().isEmpty() && metadata.containsKey(node.getID())) {
			

			
			for (String variable : variables) {
				
				// Node.java will try to convert into a double if the variable is called 'date'
				String variableToPrint = variable;
				if (variable.equals(TraitSet.DATE_TRAIT) || variable.equals(TraitSet.DATE_FORWARD_TRAIT) || variable.equals(TraitSet.DATE_BACKWARD_TRAIT)) {
					variableToPrint = "num_" + variableToPrint;
				}
				
				// Column index of variable
				int columnIndex = 0;
				for (columnIndex = 0; columnIndex <  annotationNames.size(); columnIndex ++) {
					if (annotationNames.get(columnIndex).equals(variable)) break;
				}
				
				if (columnIndex == annotationNames.size()) {
					Log.warning("Warning: cannot find " + variable + " in the column headers.");
					continue;
				}
				
				String value = metadata.get(node.getID()).get(columnIndex);
				
				
				node.setMetaData(variableToPrint, value);
				
			}
			
		}
		
		
		// Annotate children
		for (Node child : node.getChildren()) {
			this.annotate(child, annotationNames, metadata, variables);
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
		
		
		
		// Annotate children
		for (Node child : node.getChildren()) {
			processMetaData(child);
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
