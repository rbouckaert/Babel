package babel.tools;

import java.io.PrintStream;

import beastfx.app.treeannotator.TreeAnnotator;
import beastfx.app.treeannotator.TreeAnnotator.FastTreeSet;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Input.Validate;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;

@Description("Scales trees with given scale factor or a desired height")
public class TreeScalerApp extends Runnable {

	final public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out","output file. Print to stdout if not specified");
	final public Input<Double> scaleInput = new Input<>("scaleFactor", "scale factor used to multiply heights of internal nodes of the tree with");
	final public Input<Double> heightInput = new Input<>("height", "calculate scale factor to get desired mean tree height", Validate.XOR, scaleInput);

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		double scaleFactor = scaleInput.get() == null ? 1.0 : scaleInput.get();
		
		if (heightInput.get() != null) {
			// calculate scale factor based on heightInput and mean tree height
	        FastTreeSet trees = new TreeAnnotator().new FastTreeSet(treesInput.get().getAbsolutePath(), 0);
	        trees.reset();
	        double sum = 0;
	        int n = 0;
	        while (trees.hasNext()) {
	        	double h = trees.next().getRoot().getHeight();
	        	sum += h;
	        	n++;
	        }
	        double meanHeight = sum / n;
	        scaleFactor = heightInput.get() / meanHeight;
		}
		
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
        	scale(tree, scaleFactor);
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

    private void scale(Tree tree, double scaleFactor) {
    	for (Node node : tree.getInternalNodes()) {
    		double h = node.getHeight();
    		node.setHeight(h * scaleFactor);
    	}
	}

	
	public static void main(String[] args) throws Exception {
		new Application(new TreeScalerApp(), "Tree Scaler", args);
	}
}
