package babel.tools.centriod;

import java.util.ArrayList;
import java.util.List;

import beast.base.evolution.distance.Distance;
import beast.base.evolution.tree.Node;
import beastlabs.evolution.tree.RNNIMetric;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.ClusterTree;

public class ClusterRankTree  extends ClusterTree {

	
	private double [][] matrix;
	
	ClusterRankTree(Tree [] trees) {
		Distance distanceMatrix = createDistanceMatrix(trees);
		initByName("distance", distanceMatrix, 
				"clusterType", "upgma",
				"taxonset", trees[0].m_taxonset.get()
				);
	}

	private Distance createDistanceMatrix(Tree[] trees) {
		int n = trees[0].getLeafNodeCount();
		matrix = new double[n][n];
		for (Tree tree : trees) {
			Integer [] ranking = RNNIMetric.getRankedClades(tree);
			traverse(tree.getRoot(), ranking, new ArrayList<>(), n);
		}

		return new Distance() {
			@Override
			public double pairwiseDistance(int taxon1, int taxon2) {
				return matrix[taxon1][taxon2];
			}
			
		};
	}

	private void traverse(Node node, Integer[] ranking, List<Integer> clade, int n) {
		if (node.isLeaf()) {
			clade.add(node.getNr());
		} else {
			List<Integer> leftClade = new ArrayList<>();
			traverse(node.getLeft(), ranking, leftClade, n);
			List<Integer> rightClade = new ArrayList<>();
			traverse(node.getRight(), ranking, rightClade, n);
			double d = ranking[node.getNr() - n];
			// d = d*d;
			// d = node.getHeight();
			for (int i : leftClade) {
				for (int j : rightClade) {
					matrix[i][j] += d;
					matrix[j][i] += d;
				}
			}
			clade.addAll(leftClade);
			clade.addAll(rightClade);
		}
	}
	
}
