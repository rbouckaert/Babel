package babel.tools.centriod;

import beast.base.core.Description;
import beastlabs.evolution.tree.RNNIMetric;
import beast.base.evolution.tree.Tree;
import beast.base.util.Randomizer;

@Description("Sort in random bins, take halfway tree from bins, then halfway tree of halfway trees")
public class BinnedMeanTree extends Tree {
	final static public int BIN_COUNT = 8;

	private RNNIMetric metric = new RNNIMetric();

	BinnedMeanTree() {}
	
	BinnedMeanTree(Tree [] trees) {
		int n = trees.length;
		Tree [][] bins = new Tree[BIN_COUNT][n];
		int [] binsize = new int[BIN_COUNT];
		boolean [] done = new boolean[n];
		for (int i = 0; i < BIN_COUNT; i++) {
			double bestDist = 0;
			int bestTree = -1;
			
			for (int k = 0; k < BIN_COUNT; k++) {
				int j = Randomizer.nextInt(n);
				while (done[j]) {
					j = (j+1) % n;
				}
				Tree tree = trees[j];
				double dist = 0;
				for (int d = 0; d < i; d++) {
					 double d0 = metric.distance(tree, bins[d][0]);
					 dist += d0 * d0;
				}
				if (dist > bestDist || i == 0) {
					bestDist = dist;
					bestTree = j;
				}
				
			}
			
			bins[i][0] = trees[bestTree];
			binsize[i] = 1;
			done[bestTree] = true;
		}
		
		
		
		
		for (int i = 0; i < n; i++) {
			if (!done[i]) {
				Tree tree = trees[i];
				int bestBin = -1;
				double bestDist = Double.MAX_VALUE;
				for (int j = 0; j < BIN_COUNT; j++) {
					double dist = metric.distance(bins[j][0], tree);
					if (dist < bestDist) {
						bestDist = dist;
						bestBin = j;
					}
				}
				bins[bestBin][binsize[bestBin]] = tree;
				binsize[bestBin]++;
			}
		}
		
//		System.err.println(java.util.Arrays.toString(binsize));
		
		Tree [] binTree = new Tree[BIN_COUNT];
		for (int i = 0; i < BIN_COUNT; i++) {
			binTree[i] = calcMeanTree(bins[i], 0, binsize[i]);
			//binTree[i] = calcFrechetMean(bins[i], 0, binsize[i]);
		}
		
		Tree tree = calcMeanTree(binTree, 0, BIN_COUNT);
		assignFrom(tree);
	}

	private Tree calcMeanTree(Tree [] trees, int start, int end) {
		if (end - start <= 1 ) {
			return trees[start];
		}
		
		if (end - start == 2) {
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


	private Tree calcFrechetMean(Tree [] trees, int start, int end) {
		if (trees.length == 0) {
			throw new IllegalArgumentException("expected at least one tree");
		}
		
		Tree tree = trees[start];
		RNNIMetric metric = new RNNIMetric();
		boolean progress = true;
		int i = start;
		
		//while (progress && i < trees.length) {
		while (i < end) {
			double d = metric.distance(tree, trees[i]);
			progress = ((int)d/(i+1)) > 0;
			if (progress) {
				tree = metric.pathelement(tree, trees[i], (int)d/(i+1));
			}
			i++;
		}
		
		return tree;
	}
}
