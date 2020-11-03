package babel.tools;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintStream;

import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.util.TreeParser;
import beast.util.TreeParser.TreeParsingException;
import beast.core.Input.Validate;
import beast.core.util.Log;
import beast.evolution.tree.Node;

@Description("Convert Newick tree file to NEXUS format")
public class Newick2Nexus extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees","Newick file containing a tree set", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));
	
	final public Input<Boolean> makeBinaryInput = new Input<>("makeBinary", "converts tree to a binary tree, so single node child sets are collapsed", false);

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}

		BufferedReader fin = new BufferedReader(new FileReader(treesInput.get()));
        String str = null;
        int k = 0;
        TreeParser parser = null;
        int line = 0;
        while (fin.ready()) {
        	line++;
            str = fin.readLine();
            if (!str.matches("\\s*")) {
            	try {
		            parser = new TreeParser(str);
		            if (k == 0) {
		            	parser.init(out);
		            }
		            out.println();
		            out.print("tree STATE_" + k + " = ");
		            if (makeBinaryInput.get()) {
		            	makeBinary(parser.getRoot());
		            }
		            final String newick = parser.getRoot().toSortedNewick(new int[1], true);
		            out.print(newick);
		            out.print(";");
		            k++;
            	} catch (TreeParsingException e) {
            		Log.warning("Skipping line " + line + ": " + e.getMessage());
            	}
            }
        }
        fin.close();

        out.println();
        parser.close(out);
        Log.err.println("Done");
	}

	public static void makeBinary(Node node) {
		if (node.isRoot()) {
			if (node.getChildCount() != 2) {
				Log.warning("Not implemented yet: root node with " + node.getChildCount() + " children is not binarised");
			}
			for (int i = node.getChildCount() - 1; i >= 0; i--) {
				Node child = node.getChild(i);
				makeBinary(child);
			}
			return;
		}
		switch (node.getChildCount()) {
		case 0:
			break;
		case 1:
			Node child = node.getChild(0);
			Node parent = node.getParent();
			parent.removeChild(node);
			parent.addChild(child);
			makeBinary(child);
			break;
		case 2:
			Node left = node.getLeft();
			Node right = node.getRight();
			makeBinary(left);
			makeBinary(right);
			break;
		default:
			Node newNode = new Node();
			newNode.setHeight(node.getHeight());
			Node parent2 = node.getParent();
			parent2.removeChild(node);
			parent2.addChild(newNode);

			Node newChild = node.getChild(0);
			node.removeChild(newChild);
			newNode.addChild(newChild);
			newNode.addChild(node);
			
			makeBinary(node);
			makeBinary(newChild);
		}
	}

	public static void main(String[] args) throws Exception {
		new Application(new Newick2Nexus(), "Newick 2 Nexus", args);
	}

}
