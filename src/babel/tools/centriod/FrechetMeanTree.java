package babel.tools.centriod;

import java.io.IOException;

import beastfx.app.treeannotator.TreeAnnotator;
import beastfx.app.treeannotator.TreeAnnotator.FastTreeSet;
import beastfx.app.util.TreeFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beastlabs.evolution.tree.RNNIMetric;
import beast.base.evolution.tree.Tree;

@Description("Tree based on Frechet Mean of a tree set")
public class FrechetMeanTree extends Tree {
	
	final public Input<TreeFile> treeFileInput = new Input<>("treefile", "file containing tree set to form Frechet mean tree from", new TreeFile("[[none]]"));
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);

	
	FrechetMeanTree() {}
	
	FrechetMeanTree(Tree [] trees) {
		calcFrechetMean(trees);
	}
	
	FrechetMeanTree(TreeFile file, int burnInPercentage) {
		initByName("treefile", file, "burnin", burnInPercentage);
	}
	
	@Override
	public void initAndValidate() {
		super.initAndValidate();
		if (treeFileInput.get() != null && !treeFileInput.get().getName().equals("[[none]]")) {
			try {
				FastTreeSet srcTreeSet = new TreeAnnotator().new FastTreeSet(treeFileInput.get().getAbsolutePath(), burnInPercentageInput.get());
				srcTreeSet.reset();
				int n = 0;
				while (srcTreeSet.hasNext()) {
					srcTreeSet.next();
					n++;
				}
				Tree [] trees = new Tree[n];
				srcTreeSet.reset();
				n = 0;
				while (srcTreeSet.hasNext()) {
					trees[n] = srcTreeSet.next();
					n++;
				}
				calcFrechetMean(trees);				
			} catch (IOException e) {
				e.printStackTrace();
				throw new IllegalArgumentException(e.getMessage());
			}
		}
	}
	
	private void calcFrechetMean(Tree [] trees) {
		if (trees.length == 0) {
			throw new IllegalArgumentException("expected at least one tree");
		}
		
		Tree tree = trees[0];
		RNNIMetric metric = new RNNIMetric();
		boolean progress = true;
		int i = 1;
		
		//while (progress && i < trees.length) {
		while (i < trees.length) {
			double d = metric.distance(tree, trees[i]);
			progress = ((int)d/(i+1)) > 0;
			if (progress) {
				tree = metric.pathelement(tree, trees[i], (int)d/(i+1));
			}
			i++;
		}
		
		assignFrom(tree);
	}

}
