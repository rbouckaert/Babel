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

@Description("Converts a rooted tree (set) to an ultrametric tree (set), "
		+ "i.e. make all leafs have same distance to root by extending leaf "
		+ "branches so the height of all leaf nodes is zero.")
public class MakeUltraMetric extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out","output file. Print to stdout if not specified");

	@Override
	public void initAndValidate() {

	}

	@Override
	public void run() throws Exception {
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
            makeUltrametric(tree.getRoot());
            out.println();
            out.print("tree STATE_" + i + " = ");
            final String newick = tree.getRoot().toSortedNewick(new int[1], true);
            out.print(newick);
            out.print(";");
        	i++;
        }
        out.println();
        out.println("end;");
        Log.warning("Done");
	}

	private void makeUltrametric(Node node) {
		if (node.isLeaf()) {
			node.setHeight(0);
		} else {
			for (Node child : node.getChildren()) {
				makeUltrametric(child);
			}
		}
	}


	public static void main(String[] args) throws Exception{
		new Application(new MakeUltraMetric(), "Make Ultrametric", args);

	}

}
