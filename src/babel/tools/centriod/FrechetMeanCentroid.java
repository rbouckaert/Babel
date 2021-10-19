package babel.tools.centriod;

import java.io.IOException;
import java.io.PrintStream;

import beast.app.treeannotator.TreeAnnotator;
import beast.app.treeannotator.TreeAnnotator.FastTreeSet;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.evolution.tree.RNNIMetric;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;

@Description("Calculate centroid of tree set based on Frechet mean")
public class FrechetMeanCentroid extends Runnable {
	final public Input<TreeFile> treeFileInput = new Input<>("treefile", "file containing tree set to form Frechet mean tree from", new TreeFile("[[none]]"));
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);
	final public Input<Integer> randomizationAttemptsInput = new Input<>("trials", "number of randomisations of trees before picking centroid with lowest sum of square distance", 1);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified", new OutFile("[[none]]"));

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		Tree [] trees = getTrees();
		Tree centroid = new FrechetMeanTree(trees);

		if (randomizationAttemptsInput.get() > 1) {
			double bestSoS = sumOfSquaredDistances(trees, centroid);
			
			for (int i = 1; i < randomizationAttemptsInput.get(); i++) {
				randomise(trees);
				Tree newCentroid = new FrechetMeanTree(trees);
				double sos = sumOfSquaredDistances(trees, newCentroid);
				if (sos < bestSoS) {
					bestSoS = sos;
					centroid = newCentroid;
				}
			}
		}
		
		
		
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}
		
		centroid.init(out);
		centroid.log(0l, out);
		centroid.close(out);
	}


	private void randomise(Tree[] trees) {
		for (int i = 0; i < trees.length; i++) {
			int j = Randomizer.nextInt(trees.length);
			Tree tmp = trees[i];
			trees[i] = trees[j];
			trees[j] = tmp;
		}
	}

	double sumOfSquaredDistances(Tree [] trees, Tree tree) {
		double sos = 0;
		RNNIMetric metric = new RNNIMetric();
		metric.setReference(tree);

		for (Tree tree2 : trees) {
			double distance = metric.distance(tree2);
			sos += distance * distance;
		}
		return sos;
	}

	
	private Tree [] getTrees() {
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
			return trees;
		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalArgumentException(e.getMessage());
		}
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
