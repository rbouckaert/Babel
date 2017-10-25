package babel.tools;

import java.io.PrintStream;

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

@Description("Convert nexus tree file ")
public class Nexus2Newick extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		// open file for writing
        PrintStream out = System.out;
        if (outputInput.get() != null) {
        	out = new PrintStream(outputInput.get());
        }

        
        FastTreeSet trees = new TreeAnnotator().new FastTreeSet(treesInput.get().getAbsolutePath(), 0);
        trees.reset();
        while (trees.hasNext()) {
            Tree tree = trees.next();
            StringBuilder buf = new StringBuilder();
            toShortNewick(tree.getRoot(), buf);
        	out.println(buf.toString());
        }
        out.println();
        Log.err.println("Done");
 	}

    public void toShortNewick(Node node, StringBuilder buf) {

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
                toShortNewick(child,buf);
            }
            buf.append(")");
        }
        
        buf.append(":").append(node.getLength());
    }
    
    public static void main(String[] args) throws Exception {
		new Application(new Nexus2Newick(), "NEXUS2Newick", args);
	}
}
