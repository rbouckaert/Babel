package babel.tools;


import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import babel.tools.utils.MemoryFriendlyTreeSet;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.inference.Runnable;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beastlabs.evolution.tree.RNNIMetric;
import beast.base.evolution.tree.Tree;

@Description("Create pairwise distance matrix for trees in a set")
public class Trees2Distance extends Runnable {
	final public Input<TreeFile> srcInput = new Input<>("tree", "1 or more source tree files", new TreeFile("[[none]]"));
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);
	final public Input<OutFile> outputInput = new Input<>("out", "output file containing distance matrix.",
			new OutFile("[[none]]"));

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(srcInput.get().getPath(), burnInPercentageInput.get());
		srcTreeSet.reset();

		MemoryFriendlyTreeSet srcTreeSet2 = new MemoryFriendlyTreeSet(srcInput.get().getPath(), burnInPercentageInput.get());
		
		int [] map;
		
		List<List<Double>> distances = new ArrayList<>();

		int i = 0;
		while (srcTreeSet.hasNext()) {
			// set up mapping of taxon names in this tree set to that of the focal tree
			Tree tree = srcTreeSet.next();
			List<Double> d = new ArrayList<>();
			distances.add(d);
			srcTreeSet2.reset();
			int j = 0;
			while (j < i && srcTreeSet2.hasNext()) {
				Tree tree2 = srcTreeSet2.next();
				
//				map = new int[tree2.getLeafNodeCount()];
//				String [] taxa = tree2.getTaxaNames();
//				for (int k = 0; k < map.length; k++) {
//					String name = tree.getTaxaNames()[k];
//					map[i] = indexOf(taxa, name);
//				}
				d.add(RNNIDistance(tree, tree2));
				j++;
			}
			d.add(0.0);
			i++;
		}
		
		// save to file?
		PrintStream out = System.out;
		if (outputInput.get() != null &&
				!outputInput.get().getName().equals("[[none]]")) {
			out = new PrintStream(outputInput.get());
		}		
		int n = distances.size();
		for (i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				double d = i > j ? distances.get(i).get(j) : distances.get(j).get(i);
				out.print(d + ",");
			}
			out.println();
		}
		
		if (outputInput.get() != null &&
				!outputInput.get().getName().equals("[[none]]")) {
			out.close();
		}
		
		Log.warning("Done");
	}
	
	private int indexOf(String[] focalTaxa, String name) {
		for (int i = 0; i < focalTaxa.length; i++) {
			if (focalTaxa[i].equals(name)) {
				return i;
			}
		}
		throw new IllegalArgumentException("Tree set incompatible with focal tree: could not find taxon " + name + " in focal tree");
	}

	
	private double RNNIDistance(Tree tree1, Tree tree2) {
		if (tree1.getRoot().getNr() == 0) {
			renumberInternal(tree1.getRoot(), new int[]{tree1.getLeafNodeCount()});
		}
		if (tree2.getRoot().getNr() == 0) {
			renumberInternal(tree2.getRoot(), new int[]{tree2.getLeafNodeCount()});
		}
		RNNIMetric metric = new RNNIMetric(tree1.getTaxaNames());
		return metric.distance(tree1, tree2);
	}

	private int renumberInternal(Node node, int[] nr) {
		for (Node child : node.getChildren()) {
			renumberInternal(child, nr);
		}
		if (!node.isLeaf()) {
			node.setNr(nr[0]);
			nr[0]++;
		}
		return nr[0];
	}


	public static void main(String[] args) throws Exception {
		new Application(new Trees2Distance(), "Tree2Distance", args);		
	}

}
