package babel.tools;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import beast.app.treeannotator.TreeAnnotator;
import beast.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
import beast.app.util.Application;
import beast.core.Description;
import beast.core.Input;
import beast.core.util.Log;
import beast.core.Input.Validate;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;

@Description("Relabels leafs of tree set, and splits leafs into random binary sub-tree with branch lengths exponentially distributed."
		+ "Output as newick trees (not nexus). "
		+ "Metadata is not preserved.")
public class LeafSplitter extends Nexus2Newick {
	final public Input<File> labelMapInput = new Input<>("labelMap","space delimited text file with list of source and target labels. "
			+ "For taxa that need splitting, specify a comma separated list of new taxon labels.", Validate.REQUIRED);
	final public Input<Double> meanLengthInput = new Input<>("meanLength","branch lengths are drawn from an exponential with average meanLength", 0.1);
	
	Map<String, String[]> labelMap;
	
	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		processLabelMap();

		// open file for writing
        PrintStream out = System.out;
        if (outputInput.get() != null) {
        	out = new PrintStream(outputInput.get());
        	Log.warning("Writing to file " + outputInput.get().getPath());
        }

        // read trees one by one, relabel and write out relabeled tree in newick format
        MemoryFriendlyTreeSet trees = new TreeAnnotator().new MemoryFriendlyTreeSet(treesInput.get().getAbsolutePath(), 0);
        trees.reset();
        while (trees.hasNext()) {
        	Tree tree = trees.next();
            relabel(tree.getRoot());
            StringBuilder buf = new StringBuilder();
            toShortNewick(tree.getRoot(), buf);
        	out.println(buf.toString());
        }
        out.println(); 
        
        Log.warning("Done");
	}

	private void relabel(Node node) {
		if (node.isLeaf()) {
			String id = node.getID();
			if (!labelMap.containsKey(id)) {
				Log.warning("Could not find new label for " + id + ". Keeping label.");
				return;
			}
			String [] newLabels = labelMap.get(id);
			node.setID(newLabels[0]);
			if (newLabels.length == 1) {
				return;
			}
			
			// System.err.println(node.getParent().getHeight());
			List<Node> children = new ArrayList<>();
			Node parent = node.getParent();
			parent.removeChild(node);
			children.add(node);
			for (int i = 1; i < newLabels.length; i++) {
				Node newLeaf = new Node();
				newLeaf.setID(newLabels[i]);
				newLeaf.setHeight(node.getHeight());
				children.add(newLeaf);
			}
			
			double rate = 1.0 / meanLengthInput.get();
			while (children.size() > 1) {
				Node left = children.get(Randomizer.nextInt(children.size()));
				children.remove(left);
				Node right = children.get(Randomizer.nextInt(children.size()));
				children.remove(right);
	
				Node newNode = new Node();
				newNode.addChild(left);
				left.setParent(newNode);
				newNode.addChild(right);
				right.setParent(newNode);
				children.add(newNode);
				double lo = Math.min(left.getHeight(), right.getHeight());
				double hi = parent.getHeight();
				double h = lo + Randomizer.nextExponential(rate);
				int tries = 1;
				while (h > hi) {
					h = lo + Randomizer.nextExponential(rate * tries);
					tries++;
				}
				newNode.setHeight(h);
			}

			parent.addChild(children.get(0));
			children.get(0).setParent(parent);
			
		} else {
			for (int i = node.getChildCount() - 1; i >= 0; i--) {
				relabel(node.getChild(i));
			}
		}		
	}

	private void processLabelMap() throws IOException {
		labelMap = new LinkedHashMap<>();
		BufferedReader fin = new BufferedReader(new FileReader(labelMapInput.get()));
		String s;
		while ((s = readLine(fin)) != null) {
			String [] strs = s.split("\\s+");
			if (strs.length >=2) {
				labelMap.put(strs[0], strs[1].split(","));
			}
		}
		fin.close();		
	}
	
	private String readLine(BufferedReader fin) throws IOException {
        if (!fin.ready()) {
            return null;
        }
        //lineNr++;
        return fin.readLine();
    }


	public static void main(String[] args) throws Exception {
		new Application(new LeafSplitter(), "Leaf Splitter", args);
	}
}
