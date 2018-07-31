package babel.tools;

import java.io.PrintStream;
import java.util.Arrays;

import beast.app.treeannotator.TreeAnnotator;
import beast.app.treeannotator.TreeAnnotator.FastTreeSet;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.Input.Validate;
import beast.core.util.Log;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;

@Description("Produces average of number of chagnes of particular tag. "
		+ "Intervals go back in time.")
public class ChangesThroughTimeCounter extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees", "NEXUS file containing a tree set",
			Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file. Print to stdout if not specified");
	final public Input<String> tagInput = new Input<>("tag", "metadata tag to be marked", Validate.REQUIRED);
	final public Input<Double> intervalInput = new Input<>("interval", "time interval for each bin", Validate.REQUIRED);
	final public Input<Double> offsetInput = new Input<>("offset", "time offset for first bin", Validate.REQUIRED);
	
	String tag;

	@Override
	public void initAndValidate() {
	}

	class T {
		public T(Node node, double[] distribution2) {
			distribution = distribution2;
			this.node = node;
		}

		Node node;
		double[] distribution;
	}

	@Override
	public void run() throws Exception {
		tag = tagInput.get();
		
		double max = 0;
		double [] counts = new double[10000];
		double [] intervals = new double[10000];
		intervals[0] = -intervalInput.get() + offsetInput.get();
		for (int i = 1; i < intervals.length; i++) {
			intervals[i] = intervals[i-1] + intervalInput.get();
		}
		

		// open file for writing
		PrintStream out = System.out;
		if (outputInput.get() != null) {
			out = new PrintStream(outputInput.get());
			Log.warning("Writing to file " + outputInput.get().getPath());
		}

		FastTreeSet trees = new TreeAnnotator().new FastTreeSet(treesInput.get().getAbsolutePath(), 0);
		trees.reset();
		int treeCount = 0;
		while (trees.hasNext()) {
			treeCount++;
			Tree tree = trees.next();
			for (Node node : tree.getNodesAsArray()) {
				if (node.isRoot()) {
					if (node.getHeight() > max) {
						max = node.getHeight();
					}
				} else {
					Object o = node.getMetaData(tag);
					Object o2 = node.getParent().getMetaData(tag);
					if (!o.equals(o2)) {
						int i = Arrays.binarySearch(intervals, node.getHeight());
						if (i < 0) {
							i = - i - 1;
						}
						if (i >= 10000) {
							throw new IllegalArgumentException("tree is too large, use a larger interval");
						}
						counts[i]++;
					}
				}
			}
		}
		for (int i = 0; i < counts.length; i++) {
			counts[i] /= treeCount;
		}


		int i = 0;
		out.println("interval\taverage_numer_of_changes");
		while (intervals[i] <= max) {
			out.println(i+"\t" + counts[i]);
			i++;
		}
		out.println();

		Log.warning("Done");

	}
	
	public static void main(String[] args) throws Exception {
		new Application(new ChangesThroughTimeCounter(), "ChangesThroughTimeCounter", args);

	}
}
