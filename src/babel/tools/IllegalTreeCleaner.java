package babel.tools;

import java.io.PrintStream;

import beastfx.app.treeannotator.TreeAnnotator;
import beastfx.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Log;
import beast.base.core.Input.Validate;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;

/**
 * Reports all trees which throw a parsing error and optionally logs the legal subset of trees to a file
 * @author Jordan Douglas
 */
public class IllegalTreeCleaner extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out","output NEXUS file (optional).");
	
	
	
	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void run() throws Exception {
		

		// Iterate through trees
		MemoryFriendlyTreeSet trees = new TreeAnnotator().new MemoryFriendlyTreeSet(treesInput.get().getAbsolutePath(), 0);
		int i = 0;
		int badTrees = 0;
		trees.reset();
		
		// Print good trees to output?
		PrintStream out = null;
		if (outputInput.get() != null) {
        	out = new PrintStream(outputInput.get());
			Log.warning("Writing to file " + outputInput.get().getPath());
        }
		
		Log.warning("Examining trees (count starts at 0) ...");
		while (trees.hasNext()) {
	        	
			try {
				Tree tree = trees.next();
				
				if (tree == null) throw new NullPointerException("Tree is null");
				
				
				if (out != null ) {
					if (i == 0) {
						tree.init(out);
		        		out.println();
					}
					
					out.println();
		            out.print("tree STATE_" + i + " = ");
		            final String newick = tree.getRoot().toSortedNewick(new int[1], true);
		            out.print(newick);
		            out.print(";");
				}
	
				
			}catch (Exception e) {
				Log.warning("Tree number " + i + " cannot be parsed: " + e.getMessage());
				badTrees ++;
			}
			
			i++;
	        	
	        	
		 }
		
		
	      
        // Close out file
		if (out != null ) {
	        out.println();
	        out.println("end;");
	        out.close();

		}
		
		Log.warning("Done! " + badTrees + " out of " + i  + " trees are illegal");
		
		
	}

	
	public static void main(String[] args) throws Exception {
		new Application(new IllegalTreeCleaner(), "Illegal Tree Cleaner", args);
	}

}
