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
import beast.base.core.Log;
import beast.base.core.Input.Validate;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;

@Description("Convert nexus tree file to Newick format")
public class Nexus2Newick extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));
	final public Input<Boolean> makeBinaryInput = new Input<>("makeBinary", "converts tree to a binary tree, so single node child sets are collapsed", false);
	final public Input<Boolean> includeMetaDataInput = new Input<>("includeMetaData", "if true, any available metadata in NEXUS trees is output in Newick trees", false);

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
        trees.reset();
        while (trees.hasNext()) {
            Tree tree = trees.next();
            StringBuilder buf = new StringBuilder();
            if (makeBinaryInput.get()) {
            	Newick2Nexus.makeBinary(tree.getRoot());
            }
            toShortNewick(tree.getRoot(), buf, includeMetaDataInput.get());
        	out.println(buf.toString());
        }
        out.println();
        Log.err.println("Done");
 	}

    static public void toShortNewick(Node node, StringBuilder buf, boolean includeMetaData) {

        if (node.isLeaf()) {
            buf.append(node.getID());
        } else {
            buf.append("(");
            boolean isFirst = true;
            for (Node child : node.getChildren()) {
                if (isFirst)
                    isFirst = false;
                else
                    buf.append(",");
                toShortNewick(child,buf, includeMetaData);
            }
            buf.append(")");
        }
        
        if (includeMetaData) {
        	buf.append(node.getNewickMetaData());
        }
        buf.append(":").append(node.getLength());
    }
    
    public static void main(String[] args) throws Exception {
		new Application(new Nexus2Newick(), "NEXUS2Newick", args);
	}
}
