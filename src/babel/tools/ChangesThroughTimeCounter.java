package babel.tools;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

@Description("Produces average of number of changes of particular tag -- assumes tag changes "
		+ "are uniformly distributed along a branch where parent and node have different tags. "
		+ "Intervals go back in time.")
public class ChangesThroughTimeCounter extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees", "NEXUS file containing a tree set",
			Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file. Print to stdout if not specified");
	final public Input<String> tagInput = new Input<>("tag", "metadata tag for which changes will be counted", Validate.REQUIRED);
	final public Input<Double> intervalInput = new Input<>("interval", "time interval for each bin", Validate.REQUIRED);
	final public Input<Double> offsetInput = new Input<>("offset", "time offset for first bin", Validate.REQUIRED);
	final public Input<Boolean> averageByLineageInput = new Input<>("averageByLineage", "divide count by number of lineages", false);
	
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
		double [] linCount = new double[10000];
		double [] intervals = new double[10000];

		List<Double>[] distrs = new List[10000 + 1];
		for (int i = 0; i < distrs.length; i++) {
			distrs[i] = new ArrayList<>();
		}

		
		double stepSize = intervalInput.get();
		intervals[0] = -stepSize + offsetInput.get();
		for (int i = 1; i < intervals.length; i++) {
			intervals[i] = intervals[i-1] + stepSize;
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
					int start = Arrays.binarySearch(intervals, node.getHeight());
					if (start < 0) {
						start = - start - 2;
					}
					if (start >= 10000) {
						throw new IllegalArgumentException("tree is too large, use a larger interval");
					}
					Object o = node.getMetaData(tag);
					Object o2 = node.getParent().getMetaData(tag);
//					if (!o.equals(o2)) {
//						counts[start]++;
//					}
					int end = Arrays.binarySearch(intervals, node.getParent().getHeight());
					if (end < 0) {
						end = - end - 2;
					}
					if (end >= 10000) {
						throw new IllegalArgumentException("tree is too large, use a larger interval");
					}
					
					if (start == end) {
						linCount[start] += node.getLength(); 
						if (!o.equals(o2)) {
							counts[start] += 1.0/node.getLength();
						}
					} else {
						double delta = (intervals[start+1] - node.getHeight())/stepSize;
						linCount[start] += delta;
						if (!o.equals(o2)) {
							counts[start] += delta * stepSize/node.getLength();
						}
						for (int i = start+1; i < end; i++) {
							linCount[i]++;
							if (!o.equals(o2)) {
								counts[i] += stepSize/node.getLength();
							}
						}
						
						delta = (node.getParent().getHeight() - intervals[end]) /stepSize;
						linCount[end] += delta;
						if (!o.equals(o2)) {
							counts[end] += delta * stepSize/node.getLength();
						}
					}
					
				}
			}
			for (int i = 0; i < counts.length; i++) {
				if (averageByLineageInput.get()) {
					distrs[i].add(counts[i]/linCount[i]);
				} else {
					distrs[i].add(counts[i]);
				}
			}
		}
		
//		for (int i = 0; i < counts.length; i++) {
//			if (averageByLineageInput.get()) {
//				// both counts[i] and linCount[i] are multiples of treeCount,
//				// so these divide out
//				counts[i] /= linCount[i]; 
//			} else {
//				counts[i] /= treeCount;
//			}
//		}


		int i = 0;
		out.println("interval\taverage_numer_of_changes\t2.5%_quantile\t97.5%_quantile");
		while (intervals[i] <= max) {
			double[] bounds = bounds(distrs[i]);
			out.println(i+"\t" + mean(distrs[i]) + "\t" + bounds[0] + "\t" + bounds[1]);
			i++;
		}
		out.println();

		Log.warning("Done");

	}
	
	private double[] bounds(List<Double> counts) {
		Collections.sort(counts);
		int lower = counts.size() * 25 / 1000;
		int upper = counts.size() * 975 / 1000;
		return new double[] { counts.get(lower), counts.get(upper) };
	}

	private double mean(List<Double> counts) {
		double sum = 0;
		for (double i : counts) {
			sum += i;
		}
		return sum / counts.size();
	}
	
	public static void main(String[] args) throws Exception {
		new Application(new ChangesThroughTimeCounter(), "ChangesThroughTimeCounter", args);

	}
}
