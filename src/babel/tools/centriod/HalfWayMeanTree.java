package babel.tools.centriod;

import beast.core.Description;
import beast.evolution.tree.RNNIMetric;
import beast.evolution.tree.Tree;

@Description("Recursively take middle of RNNI path of list of trees")
public class HalfWayMeanTree extends Tree {

	HalfWayMeanTree() {}
	
	HalfWayMeanTree(Tree [] trees) {
		Tree tree = calcMeanTree(trees, 0, trees.length);
		assignFrom(tree);
	}

	private Tree calcMeanTree(Tree [] trees, int start, int end) {
		if (end - start <= 1 ) {
			return trees[start];
		}
		
		if (end - start == 2) {
			RNNIMetric metric = new RNNIMetric();
			double d = metric.distance(trees[start], trees[start+1]);
			Tree tree = metric.pathelement(trees[start], trees[start+1], (int)d/2);
			return tree;
		}

		int mid = start + (end - start)/2;
		Tree tree1 = calcMeanTree(trees, start, mid);
		Tree tree2 = calcMeanTree(trees, mid, end);
		RNNIMetric metric = new RNNIMetric();
		double d = metric.distance(tree1, tree2);
		Tree tree = metric.pathelement(tree1, tree2, (int)d/2);
		return tree;
	}

}
