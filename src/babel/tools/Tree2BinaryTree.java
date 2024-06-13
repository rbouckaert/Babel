package babel.tools;

import java.io.File;
import java.io.PrintStream;

import javax.management.RuntimeErrorException;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.core.Input.Validate;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;
import beast.base.inference.Runnable;
import beastfx.app.inputeditor.BeautiDoc;
import beastfx.app.tools.Application;
import beastfx.app.treeannotator.TreeAnnotator;
import beastfx.app.treeannotator.TreeAnnotator.FastTreeSet;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;

@Description("Makes a tree with single child nodes or multifurcations into a binary tree")
public class Tree2BinaryTree extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out","output file. Print to stdout if not specified");
	
	

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
        PrintStream out = System.out;
        if (outputInput.get() != null) {
        	out = new PrintStream(outputInput.get());
			Log.warning("Writing to file " + outputInput.get().getPath());
        }

        FastTreeSet trees = new TreeAnnotator().new FastTreeSet(treesInput.get().getAbsolutePath(), 0);
        trees.reset();
        int i = 0;
        while (trees.hasNext()) {
        	Tree tree = trees.next();
            Node root = normalise(tree.getRoot());
            final String newick = root.toNewick();
            out.print(newick);
            out.print(";");
        	i++;
        }
        out.println();
	
        if (outputInput.get() != null) {
        	out.close();
        }
        Log.warning("Done");
	}

	private Node normalise(Node node) {
		if (node.isLeaf()) {
			return node;
		} else {
			if (node.getChildCount() == 1) {
				Node child = normalise(node.getLeft());
				return child;
			} else if (node.getChildCount() == 2) {
				node.setLeft(normalise(node.getLeft()));
				node.setRight(normalise(node.getRight()));
				return node;
			} else {
				throw new RuntimeException("Not implemented yet");
			}
		}
	}

	public static void main(String[] args) throws Exception {
		new Application(new Tree2BinaryTree(), "Tree2BinaryTree", args);

	}

}
