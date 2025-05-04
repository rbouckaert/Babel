package babel.tools;

import java.io.IOException;
import java.io.PrintStream;
import java.math.RoundingMode;
import java.text.DecimalFormat;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Input.Validate;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.inference.Runnable;
import beastfx.app.tools.Application;
import beastfx.app.treeannotator.TreeAnnotator;
import beastfx.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;

@Description("Reduce resolultion of a tree file so it becomes more managable")
public class TreeResolutionCompressor extends Runnable {
	public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	public Input<OutFile> outputInput = new Input<>("out","output file. Print to stdout if not specified");

    final public Input<Integer> decimalPlacesInput = new Input<>("dp", "the number of decimal places to use writing branch lengths, rates and real-valued metadata, use -1 for full precision (default = full precision)", -1);
    final public Input<Long> deltaSampleInput = new Input<>("deltaSample", "interval between sample nrs", 1l);

    private DecimalFormat df;

	@Override
	public void initAndValidate() {
        int dp = decimalPlacesInput.get();
        if (dp < 0) {
            throw new IllegalArgumentException(" dp should be at least 0");
        }
        // just new DecimalFormat("#.######") (with dp time '#' after the decimal)
        df = new DecimalFormat("#."+new String(new char[dp]).replace('\0', '#'));
        df.setRoundingMode(RoundingMode.HALF_UP);
        
        try {
        	MemoryFriendlyTreeSet srcTreeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(treesInput.get().getPath(), 0);
        	srcTreeSet.reset();
        	Tree tree = srcTreeSet.next();

            PrintStream out = System.out;
            if (outputInput.get() != null) {
    			Log.warning("Writing to file " + outputInput.get().getPath());
            	out = new PrintStream(outputInput.get());
            }
            tree.init(out);

            srcTreeSet.reset();
            long sample = 0;
            while (srcTreeSet.hasNext()) {
            	tree = srcTreeSet.next();

            	Node root = tree.getRoot();
    	        out.print("\ntree STATE_" + sample + " = ");
            	out.print(toNewick(root).toString());
                out.print(";");

            	
            	sample += deltaSampleInput.get();
    		}

            out.print("\n");
            tree.close(out);
            if (outputInput.get() != null) {
            	out.close();
            }
        } catch (IOException e) {
        	e.printStackTrace();
        }
        Log.warning("Done");

	}
	
	
	StringBuilder toNewick(Node node) {
        StringBuilder buf = new StringBuilder();
        if (node.getLeft() != null) {
            buf.append("(");
            buf.append(toNewick(node.getLeft()));
            if (node.getRight() != null) {
                buf.append(',');
                buf.append(toNewick(node.getRight()));
            }
            buf.append(")");
        } else {
            buf.append(node.getNr() + 1);
        }
		if (node.metaDataString != null) {
			buf.append("[&" + node.metaDataString + "]");
		}
        buf.append(":");
        appendDouble(buf, node.getLength());
        return buf;
    }

    private void appendDouble(StringBuilder buf, double d) {
        if (df == null) {
            buf.append(d);
        } else {
            buf.append(df.format(d));
        }
    }

	@Override
	public void run() throws Exception {
		// TODO Auto-generated method stub

	}

	public static void main(String[] args) throws Exception {
		new Application(new TreeResolutionCompressor(), "", args);

	}

}
