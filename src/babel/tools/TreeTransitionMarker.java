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
import beast.core.Input.Validate;
import beast.core.util.Log;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;


@Description("Mark specific transitions of a meta-data attribute on a tree to make it easy to visualise these transitions, e.g. "
		+ "for an ancestral reconstruction analysis")
public class TreeTransitionMarker extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree (set)", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out","output file. Print to stdout if not specified");
	final public Input<String> tagInput = new Input<>("tag","metadata tag to be marked", Validate.REQUIRED);
	final public Input<String> fromInput = new Input<>("from","metadata tag value at top of branch to be marked", Validate.REQUIRED);
	final public Input<String> toInput = new Input<>("to","metadata tag value at bottom of branch to be marked", Validate.REQUIRED);
	final public Input<Boolean> markAllInput = new Input<>("markAll","whether to mark all possible transitions", true);

	String tag, from, to;
	boolean markAll;
	
	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub

	}

	@Override
	public void run() throws Exception {
		tag = tagInput.get();
		from = fromInput.get();
		to = toInput.get();
		markAll = markAllInput.get();
		
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
            out.println();
            out.print("tree STATE_" + i + " = ");
            mark(tree.getRoot());
            final String newick = tree.getRoot().toSortedNewick(new int[1], true);
            out.print(newick);
            out.print(";");
        	i++;
        }
        out.println();
        out.println("end;");

	}

	
	private void mark(Node node) {
		if (node.isLeaf()) {
			return;
		} else {
			String current = node.getMetaData(tag).toString(); 
			for (Node child : node.getChildren()) {
				if (current.equals(from) && child.getMetaData(tag).toString().equals(to)) {
					child.metaDataString += ",mark=1";
				} else {
					child.metaDataString += ",mark=0";
				}
				if (markAll) {
					child.metaDataString += ",mark_" + current + "_to_" + child.getMetaData(tag) + "=1";
				}
				mark(child);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		new Application(new TreeTransitionMarker(), "TreeTransitionMarker", args);
	}
}
