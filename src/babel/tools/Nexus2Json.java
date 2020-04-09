package babel.tools;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

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
	

	
	final public Input<TreeFile> treesInput = new Input<>("trees", "NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<List<AnnotationTuple>> annotationsInput = new Input<>("annotation", "Character to split the sequence names with", new ArrayList<AnnotationTuple>());
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));

	final String INDENT = "  ";
	
	public Nexus2Json() {
		
	}
	
	
	@Override
	public void initAndValidate() {
		
		
		
	}

	@Override
	public void run() throws Exception {
		// open file for writing
        PrintStream out = System.out;
        if (outputInput.get() != null) {
			Log.warning("Writing to file " + outputInput.get().getPath());
        	out = new PrintStream(outputInput.get());
        }
        
        

        FastTreeSet trees = new TreeAnnotator().new FastTreeSet(treesInput.get().getAbsolutePath(), 0);
        
        // One tree or a list of trees?
        trees.reset();
        int numTrees = 0;
        while (trees.hasNext()) {
        	Tree tree = trees.next();
        	numTrees++;
        	if (numTrees > 1) break;
        	
        }
        boolean manyTrees = numTrees > 1;
        
        out.println("{");
        
       
        if (manyTrees) {
        	out.println(INDENT + "trees:[");
        }else {
        	out.println(INDENT + "tree:");
        }
        

        
        trees.reset();
        while (trees.hasNext()) {
            Tree tree = trees.next();
            toJSON(tree.getRoot(), out, INDENT + INDENT);
            out.println();
        }
        
        
        if (manyTrees) {
        	out.println(INDENT + "]");
        }
        
        
        out.println("}");
        
        Log.err.println("Done");
 	}

    public void toJSON(Node node, PrintStream buf, String indent) {

    	
    	buf.println(indent + "{");
    	
    	String indent2 = indent + INDENT;
    	
    	
    	// Name and meta data
    	String name = node.getID() != null && node.getID() != "" ? node.getID() : "node" + node.getNr();
    	buf.println(indent2 + "name:'" + name + "',");
    	//buf.println(indent2 + "node_attrs:{" + node.getNewickMetaData() + "},");
    	buf.print(indent2 + "node_attrs:{");
    	
    	
    	// Optionally annotate with accession / metadata
    	String metadata = node.getNewickMetaData();
    	String seqName = node.getID();
    	boolean treeHasMetadata = false; // metadata != "";
    	boolean accessionHasMetadata = seqName != null && annotationsInput.get().size() > 0;
    	if (treeHasMetadata || accessionHasMetadata) { 
    		buf.println();
    		
    		// TODO Annotations from tree file
    		if (treeHasMetadata) {
    			
    			buf.print(indent2 + INDENT + "metadata:'" + metadata + "'");
    			
    			// Split on ,
    			String[] bits = metadata.split(",");
    			for (int i = 0; i < bits.length; i ++) {
    				
    				String bit = bits[i].trim();
    				
    				// Remove the first &
    				if (i == 0) bit = bit.substring(1);
    				
    				System.out.println(bit);
    				
    				// Split the =
    				String[] tuple = bit.split("=");
    				String var = tuple[0];
    				String val = tuple[1];
    				
    				// Is it a number?
    				boolean itsANumber = tryParseNum(val);
    				
    				
    				// To JSON
    				buf.print(var + ":" + (itsANumber ? "" : "'") + val + (itsANumber ? "" : "'"));
    				boolean last = i == bits.length-1 && !accessionHasMetadata;
    				
    			}
    			
    			
    		}
    		
    		
    		// Annotations from accession
    		if (accessionHasMetadata) {
    		
	    		for (int i = 0; i < annotationsInput.get().size(); i ++) {
	    			AnnotationTuple a = annotationsInput.get().get(i);
	    			boolean last = i == annotationsInput.get().size()-1;
		    		buf.println(indent2 + INDENT + a.toJSON(seqName) + (last ? "" : ","));
		    	}
    		
    		}
    		buf.println(indent2 + "},");
    	}else {
    		buf.println("},");
    	}
    	
    	
    	
    	if (node.isLeaf()) {
    		
    	}
    	
    	else {
    		
    		buf.println(indent2 + "children:[");
    		
    		for (int i = 0; i < node.getChildCount(); i ++) {
    			toJSON(node.getChild(i), buf, indent2 + INDENT);
    			boolean last = i == node.getChildCount()-1;
    			buf.println(last ? "" : ",");
    		}
    		
    		
    		buf.println(indent2 + "],");
    		
    	}
    	

    	buf.println(indent2 + "div: " + node.getHeight());
    	buf.print(indent + "}");
    	
    }
    
    
    /**
     * Figure out whether this string is a number (int or double)
     * @param value
     * @return
     */
    private boolean tryParseNum(String value) {  
        try {  
            Integer.parseInt(value);  
            Double.parseDouble(value);
            return true;  
         } catch (NumberFormatException e) {  
            return false;  
         }  
    }

    
    public static void main(String[] args) throws Exception {
		new Application(new Nexus2Json(), "NEXUS2JSON", args);
	}
    
    
    
    
}










