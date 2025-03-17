package babel.tools;

import java.io.PrintStream;

import beastfx.app.treeannotator.TreeAnnotator;
import babel.tools.utils.MemoryFriendlyTreeSet;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;

@Description("Finds MRCA of set of nodes in a tree log."
		+ "produces a text file with MRCA heights + "
		+ "a tree file with nodes annotated with traversed=true/false (if tree output is specified)")
public class MRCAFinder extends GetCladeMetaData {
	final public Input<OutFile> treeoutputInput = new Input<>("treeout", "tree output file with branches marked to show MRCA",
			new OutFile("[[none]]"));

	
	
	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		PrintStream dataout = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			dataout = new PrintStream(outputInput.get());
		}

		PrintStream out = null;
		if (treeoutputInput.get() != null && !treeoutputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + treeoutputInput.get().getPath());
			out = new PrintStream(treeoutputInput.get());
		}

		processCfgFile();
		
        // read trees one by one, find MRCA, label nodes in MRCA path
        MemoryFriendlyTreeSet trees = new MemoryFriendlyTreeSet(srcInput.get().getAbsolutePath(), 0);
        trees.reset();
    	Tree tree = trees.next();
        trees.reset();
        int k = 0;
        
        if (out != null) {
        	tree.init(out);
    		out.println();
        }
        
        long sample = 0;
        while (trees.hasNext()) {
        	k++;
        	if (k % 100 == 0) {
        		Log.warning("tree " + k);
        	}
        	tree = trees.next();
            Node node = getMRCA(tree, taxa);

            Node [] nodes = tree.getNodesAsArray();            
        	nodesTraversed[node.getNr()] = true;
            if (originateInput.get()) {
            	node = node.getParent();
            }
            for (int i = 0; i < nodes.length; i++) {
            	if (nodes[i] != null) {
	            	if (nodes[i].metaDataString == null) {
	                	nodes[i].metaDataString = "traversed=" + nodesTraversed[i];            		
	            	} else {
	            		nodes[i].metaDataString += ",traversed=" + nodesTraversed[i];
	            	}
            	} else {
            		int h = 3;
            		h++;
            			
            	}
            }

            if (out != null) {
    	        out.print("tree STATE_" + sample + " = ");
    	        out.print(tree.getRoot().toSortedNewick(new int[1], true));
    			out.println(";");
            	//out.println(tree.getRoot().toNewick());
            }
            boolean isMonophyletic = (nseen == 2 * taxa.size() - 1); 
            dataout.println(node.getHeight() + "\t" + isMonophyletic);
        }
        if (out != null) {
            tree.close(out);
		}
        
        Log.warning("Done");
	}

	public static void main(String[] args) throws Exception {
		new Application(new MRCAFinder(), "MRCA Finder", args);
	}

}
