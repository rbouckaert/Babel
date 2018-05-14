package babel.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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
import beast.evolution.tree.Tree;

@Description("Scales trees so epochs have the same length")
public class TreeEpochScaler extends Runnable {

	final public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out","output file. Print to stdout if not specified");
	final public Input<String> epochsInput = new Input<>("epochs","space delimited list of epoch boundaries: each epoch will be scaled to the same height as the first epoch", Validate.REQUIRED);

	@Override
	public void initAndValidate() {
	}

	double [] epochs;
	@Override
	public void run() throws Exception {
		// process epochs
		String [] strs = epochsInput.get().trim().split("\\s+");
		epochs = new double[strs.length + 1];
		for (int i = 0; i < strs.length; i++) {
			epochs[i] = Double.parseDouble(strs[i]);
		}
		epochs[strs.length] = Double.POSITIVE_INFINITY;
		
		
		
		
		// open file for writing
        PrintStream out = System.out;
        if (outputInput.get() != null) {
        	out = new PrintStream(outputInput.get());
			Log.warning("Writing to file " + outputInput.get().getPath());
        }

        
        FastTreeSet trees = new TreeAnnotator().new FastTreeSet(treesInput.get().getAbsolutePath(), 0);
        trees.reset();
        Tree tree = trees.next();
        tree.init(out);
        out.println();

        trees.reset();
        int i = 0;
        while (trees.hasNext()) {
        	tree = trees.next();
        	scale(tree);
            out.println();
            out.print("tree STATE_" + i + " = ");
            final String newick = tree.getRoot().toNewick(false);
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

    private void scale(Tree tree) {
    	epochs[epochs.length - 1] = tree.getRoot().getHeight();
    	for (Node node : tree.getNodesAsArray()) {
    		double h = node.getHeight();
    		int i = 0; 
    		while (h > epochs[i]) {
    			i++;
    		}
    		if (i > 0) {
    			double f = (h - epochs[i-1])/(epochs[i] - epochs[i-1]);
    			h = epochs[0] * (i + f);
    			node.setHeight(h);
    		}
    	}
	}

	
	public static void main(String[] args) throws Exception {
		new Application(new TreeEpochScaler(), "Tree Epoch Scaler", args);
	}
}
