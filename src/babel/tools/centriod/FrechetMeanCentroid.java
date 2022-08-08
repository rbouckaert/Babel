package babel.tools.centriod;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

import babel.util.NexusParser;
import beastfx.app.treeannotator.TreeAnnotator;
import beastfx.app.treeannotator.TreeAnnotator.FastTreeSet;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Log;
import beastlabs.evolution.tree.RNNIMetric;
import beast.base.evolution.tree.Tree;
import beast.base.util.Randomizer;

@Description("Calculate centroid of tree set based on Frechet mean")
public class FrechetMeanCentroid extends Runnable {
	final public Input<TreeFile> treeFileInput = new Input<>("treefile", "file containing tree set to form Frechet mean tree from", new TreeFile("[[none]]"));
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);
	final public Input<Integer> randomizationAttemptsInput = new Input<>("trials", "number of randomisations of trees before picking centroid with lowest sum of square distance", 1);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified", new OutFile("[[none]]"));

	final public Input<TreeFile> focalTreeFileInput = new Input<>("focalTreeFile", "file containing tree(s) to calcalate sumOfSquared RNNI Distances score for", new TreeFile("[[none]]"));

	enum Mode {Frechet,Halfway,Binning,Cluster,MCC}
	final public Input<Mode> modeInput = new Input<>("mode", "algorithm used to for single centroid proposal. Should be one of " + Arrays.toString(Mode.values()), 
			Mode.Frechet, Mode.values());
	
	private Mode mode;
	
	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		long start = System.currentTimeMillis();
		mode = modeInput.get();
		Tree [] trees = getTrees(treeFileInput.get(), burnInPercentageInput.get());
		
		// if focal tree file is specified, print sum of square RNNI distances and quite
		if (focalTreeFileInput.get() != null && !focalTreeFileInput.get().getName().equals("[[none]]")) {
			Tree [] focalTrees = getTrees(focalTreeFileInput.get(), 0);
			for (Tree tree : focalTrees) {
				
				double sos = sumOfSquaredDistances(trees, tree);
				Log.info(tree.getRoot().toNewick());
				Log.info("SoS = " + sos);
			}
			Log.warning("Done");
			return;
		}
		
		
		
		
		Tree centroid = calcCentroid(trees);

		if (randomizationAttemptsInput.get() > 1) {
			double bestSoS = sumOfSquaredDistances(trees, centroid);
			
			int onepercent = 1 + randomizationAttemptsInput.get()/100;
			int tenpercent = 1 + randomizationAttemptsInput.get()/10;
			
			for (int i = 1; i < randomizationAttemptsInput.get(); i++) {
				randomise(trees);
				Tree newCentroid = calcCentroid(trees);
				double sos = sumOfSquaredDistances(trees, newCentroid);
				if (sos < bestSoS) {
					bestSoS = sos;
					centroid = newCentroid;
					Log.warning(i + ": " + sos);
				}
				if (i % onepercent == 0) {
					if (i % tenpercent == 0) {
						System.err.print("|");
					} else {
						System.err.print(".");
					}
				}
			}
		}
		Log.info(centroid.getRoot().toNewick());
		Log.warning("SoS = " + sumOfSquaredDistances(trees, centroid));
		
		
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}
		
		centroid.init(out);
		out.println();
		centroid.log(0l, out);
		out.println();
		centroid.close(out);
		
		long end = System.currentTimeMillis();
		Log.warning("Done " + mode + " in " + (end-start)/1000 + " seconds");
	}


	private Tree calcCentroid(Tree[] trees) {
		switch (mode) {
		case Halfway:			
			return new HalfWayMeanTree(trees);
		case Binning:
			return new BinnedMeanTree(trees);
		case Cluster:
			return new ClusterRankTree(trees);
		case MCC:
			return MCCTree();
		case Frechet:
		default:
			return new FrechetMeanTree(trees);
		}
	}

	private Tree MCCTree() {
		try {
			String output = outputInput.get().getAbsolutePath();
			TreeAnnotator.main(new String[]{"-b",burnInPercentageInput.get() + "",
					treeFileInput.get().getPath(), output});
			NexusParser parser = new NexusParser();
			parser.parseFile(outputInput.get());
			return parser.trees.get(0);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
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
		//metric.setReference(tree);

		for (Tree tree2 : trees) {
			double distance = metric.distance(tree,tree2);
			sos += distance * distance;
		}
		return sos;
	}

	
	private Tree [] getTrees(TreeFile treeFile, int burnInPercentage) {
		try {
			FastTreeSet srcTreeSet = new TreeAnnotator().new FastTreeSet(treeFile.getAbsolutePath(), burnInPercentage);
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
	
	public static void main(String[] args) throws Exception {
		new Application(new FrechetMeanCentroid(), "Frechet Mean Centroid", args);
	}

}
