package babel.tools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import beast.app.treeannotator.TreeAnnotator;
import beast.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.util.Log;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;

@Description("Counts transitions of tags along branches of a tree")
public class StateTransitionCounter extends MatrixVisualiserBase {	
	final public Input<TreeFile> src1Input = new Input<>("in","source tree (set) file");
    final public Input<String> tagInput = new Input<String>("tag","label used to report trait", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);
	final public Input<Integer> resolutionInput = new Input<>("resolution", "number of steps in lineages through time table", 100);
	final public Input<Double> maxXInput = new Input<>("maxHeight", "maximum tree height, if specified the number of intervals is 365 * maxHeight (i.e. intervals represent 1 day) and 'resolution' is ignored. Should be larger than largest tree.");
    final public Input<String> epochInput = new Input<String>("epoch", "comma separated string of breakpoint, going backward in time", "");
	final public Input<OutFile> svgInput = new Input<>("svg", "svg output file for graph visualisation of transitions",
			new OutFile("[[none]]"));
    
	final public Input<Double> svgScaleInput = new Input<>("svgScale", "scale factor to be used for svg output. auto-scale when <= 0",
			-1.0);

    double [][] migrations;
    String [] tags;
    
	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		if (epochInput.get().trim().length() == 0) {
			PrintStream out = System.out;
			if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
				String str = outputInput.get().getPath();			
				Log.warning("Writing to file " + str);
				out = new PrintStream(str);
			}
			
			
			process(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, out);
			
			
			if (svgInput.get() != null && !svgInput.get().getName().equals("[[none]]")) {
				// produce SVG visualisation
				String str = svgInput.get().getPath();
				Log.warning("Writing to file " + str);
				try {
					File tmpFile0 = new File(str);
					FileWriter outfile = new FileWriter(tmpFile0);
					outfile.write(getSVG(migrations, tags, svgScaleInput.get()));
					outfile.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
				out.close();
			}			
		} else {
			String [] strs = epochInput.get().split(",");
			double [] times = new double[strs.length + 2];
			times[0] = Double.NEGATIVE_INFINITY;
			times[times.length-1] = Double.POSITIVE_INFINITY;
			for (int i = 0; i < strs.length; i++) {
				times[i+1] = Double.parseDouble(strs[i]);
			}
			
			for (int i = 0; i < times.length-1; i++) {
				PrintStream out = System.out;
				if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
					String str = outputInput.get().getPath();			
					if (str.contains(".")) {
						int k = str.lastIndexOf('.');
						str = str.substring(0, k) + i + str.substring(k);
					}
					Log.warning("Writing to file " + str);
					out = new PrintStream(str);
				}
				
				
				process(times[i], times[i+1], out);
				
				
				if (svgInput.get() != null && !svgInput.get().getName().equals("[[none]]")) {
					// produce SVG visualisation
					String str = svgInput.get().getPath();
					if (str.contains(".")) {
						int k = str.lastIndexOf('.');
						str = str.substring(0, k) + i + str.substring(k);
					}
					Log.warning("Writing to file " + str);
					try {
						File tmpFile0 = new File(str);
						FileWriter outfile = new FileWriter(tmpFile0);
						outfile.write(getSVG(migrations, tags, svgScaleInput.get()));
						outfile.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
					out.close();
				}			
			}
		}
		
		
		Log.info.println("Done");
	}

	private void process(double tLo, double tHi, PrintStream out) throws Exception {
		// find all tag values
		MemoryFriendlyTreeSet srcTreeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(src1Input.get().getPath(), burnInPercentageInput.get());
		srcTreeSet.reset();
		String tag = tagInput.get();
		Set<String> tagSet = new HashSet<>();
		collectTags(srcTreeSet.next().getRoot(), tagSet, tag);
		tags = tagSet.toArray(new String[] {});
		Arrays.sort(tags);
		int m = tags.length;
		Map<String, List<Double>> transitionDistributions = new HashMap<>();
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < m; j++) {
				String id = tags[i] + "=>" + tags[j];
				transitionDistributions.put(id, new ArrayList<>());
			}
		}
		String [] transitionKeys = transitionDistributions.keySet().toArray(new String []{});
		Arrays.sort(transitionKeys);
		
		// collect data from trees
		srcTreeSet.reset();
		int n = 0;
		double maxX = 0;
		int treeCount = 0;
		while (srcTreeSet.hasNext()) {
			Tree tree = srcTreeSet.next();
			treeCount++;
			maxX = Math.max(maxX, tree.getRoot().getHeight());
			Map<String, Double> transitionCounts = new HashMap<>();
			collectTags(tree.getRoot(), transitionCounts, tag, tLo, tHi);
			
			for (int i = 0; i < tags.length; i++) {
				for (int j = 0; j < tags.length; j++) {
					String id = tags[i] + "=>" + tags[j];

					if (!transitionDistributions.containsKey(id)) {
						Log.warning("Unrecognised tag: " + id);
						transitionDistributions.put(id, new ArrayList<>());
					}
					if (transitionCounts.containsKey(id)) {
						transitionDistributions.get(id).add(transitionCounts.get(id));
					} else {
						transitionDistributions.get(id).add(0.0);
					}
				}
			}
			n++;
		}
		
		
		int N = resolutionInput.get();
		double stepSize = maxX / N;
		if (maxXInput.get() != null) {
			if (maxXInput.get() < maxX) {
				throw new IllegalArgumentException("maxHeight should be at least as large as the largest tree: " + maxX);
			}
			maxX = maxXInput.get();
			N = (int)(maxX * 365.0);
			stepSize = 1.0 / 365.0;
		}
		srcTreeSet.reset();

		double [][] linCount = new double[m][N + 1];
		double [][][] introductionCount = new double[m][N + 1][treeCount];
		treeCount = 0;
		while (srcTreeSet.hasNext()) {
			Tree tree = srcTreeSet.next();
			for (Node node : tree.getNodesAsArray()) {
				if (!node.isRoot()) {
					String value = (String) node.getMetaData(tag);
					int k = indexOf(tags, value);
					double [] tagLinCount = linCount[k];
					double [][] introductionCount2 = introductionCount[k];
					int start = (int) (node.getHeight() * N / maxX);// + 0.5);
					int end = (int) (node.getParent().getHeight() * N / maxX);// + 0.5);
					if (start == end) {
						tagLinCount[start] += node.getLength();
						
						if (!value.equals(node.getParent().getMetaData(tag))) {
							// double d = stepSize/node.getLength();
							introductionCount2[start][treeCount] += 1;
						}
					} else {
						tagLinCount[start] += ((start+1) * stepSize  - node.getHeight())/stepSize;
						for (int i = start+1; i < end; i++) {
							tagLinCount[i]++;
						}
						tagLinCount[end] += node.getParent().getHeight() - end * stepSize;

						if (!value.equals(node.getParent().getMetaData(tag))) {
							double d = stepSize/node.getLength();
							introductionCount2[start][treeCount] += d * ((start+1) * stepSize  - node.getHeight());
							for (int i = start+1; i < end; i++) {
								introductionCount2[i][treeCount] += d;
							}
							introductionCount2[end][treeCount] += d * (node.getParent().getHeight() - end * stepSize);
						}
					}
				}
			}
			treeCount++;
		}
		// create report
		
		// output main statistics
		out.println("Transition" + "\t" +"mean" + "\t" + "95%Low" + "\t" + "95%High");
		int [][] histograms = new int[transitionKeys.length][];
		int k = 0;		
		migrations = new double[tags.length][tags.length];
		for (String id : transitionKeys) {
			List<Double> counts = transitionDistributions.get(id);
			Collections.sort(counts);
			double sum = 0;
			for (double d : counts) {
				sum +=d;
			}
			double mean = sum / n;
			double lo = lowerOf95PercentHPD(counts);//sorted)counts.get((int) (0.025 * counts.size())); 
			double hi = upperOf95PercentHPD(counts);//counts.get((int) (0.975 * counts.size())); 			
			out.println(id + "\t" + mean + "\t" + lo + "\t" + hi);
			String tag1 = id.substring(0, id.indexOf("="));
			String tag2 = id.substring(id.indexOf("=")+2);
			migrations[indexOfTag(tag1)][indexOfTag(tag2)] = mean;
			histograms[k] = histogram(counts);
			k++;
		}
		
		// output histogram
		out.println("\nHistogram");
		int max = 0;
		for (int i = 0; i < transitionKeys.length; i++) {
			max = Math.max(max, histograms[i].length);
		}
		out.print("Transition\t");
		for (int i = 0; i <= max; i++) {
			out.print(i + "\t");
		}
		out.println();
		for (int i = 0; i < transitionKeys.length; i++) {
			out.print(transitionKeys[i] + "\t");
			for (int j : histograms[i]) {
				out.print(j + "\t");
			}
			out.println();
		}
		
		// output ltt
		out.println("\nLineages through time");
		out.print("Transition\t");
		for (int i = 0; i <= N; i++) {
			out.print((maxX * i) / N + "\t");
		}
		out.println();
		for (int i = 0; i < m; i++) {
			out.print(tags[i] + "\t");
			for (int j = N; j >= 0; j--) {
				out.print(linCount[i][j]/treeCount + "\t");
			}
			out.println();
		}
		out.print("Total\t");
		for (int j = N; j >= 0; j--) {
			double sum = 0;
			for (int i = 0; i < m; i++) {
				sum += linCount[i][j];
			}
			out.print(sum/treeCount + "\t");
		}
		out.println();
		
		// output introductionCount
		out.println("\nIntroduction Count through time");
		out.print("Transition\t");
		for (int i = 0; i <= N; i++) {
			out.print((maxX * i) / N + "\t");
		}
		out.println();
		for (int i = 0; i < m; i++) {
			out.print(tags[i] + "\t");
			for (int j = N; j >= 0; j--) {
				out.print(mean(introductionCount[i][j]) + "\t");
			}
			out.println();
			out.print(tags[i] + "-95%HPD-lower\t");
			for (int j = N; j >= 0; j--) {
				out.print(lowerOf95PercentHPD(introductionCount[i][j]) + "\t");
			}
			out.println();
			out.print(tags[i] + "-95%HPD-upper\t");
			for (int j = N; j >= 0; j--) {
				out.print(upperOf95PercentHPD(introductionCount[i][j]) + "\t");
			}
			out.println();
		}
	}

	
	private double mean(double[] d) {
		double sum = 0;
		for (double x : d) {
			sum += x;
		}
		return sum / d.length;
	}

	private double lowerOf95PercentHPD(List<Double> x) {
		double [] y = new double[x.size()];
		for (int i = 0; i < y.length; i++) {
			y[i] = x.get(i);
		}
		return lowerOf95PercentHPD(y);
	}
	
	private double lowerOf95PercentHPD(double[] sorted) {
		Arrays.sort(sorted);
        int n = (int) ((sorted.length - 1) * 95.0 / 100.0);
        double minRange = Double.MAX_VALUE;
        int hpdIndex = 0;
        for (int k = 0; k < sorted.length - n; k++) {
            double range = sorted[k + n] - sorted[k];
            if (range < minRange) {
                minRange = range;
                hpdIndex = k;
            }
        }
        return sorted[hpdIndex];
	}
	
	private double upperOf95PercentHPD(List<Double> x) {
		double [] y = new double[x.size()];
		for (int i = 0; i < y.length; i++) {
			y[i] = x.get(i);
		}
		return upperOf95PercentHPD(y);
	}

	private double upperOf95PercentHPD(double[] sorted) {
		Arrays.sort(sorted);
        int n = (int) ((sorted.length - 1) * 95.0 / 100.0);
        double minRange = Double.MAX_VALUE;
        int hpdIndex = 0;
        for (int k = 0; k < sorted.length - n; k++) {
            double range = sorted[k + n] - sorted[k];
            if (range < minRange) {
                minRange = range;
                hpdIndex = k;
            }
        }
        return sorted[hpdIndex + n];
	}

	private int indexOfTag(String tag1) {
		for (int i = 0; i < tags.length; i++) {
			if (tags[i].equals(tag1)) {
				return i;
			}
		}
		return -1;
	}

	private int indexOf(String[] keys, String value) {
		for (int i = 0; i < keys.length; i++) {
			if (keys[i].equals(value)) {
				return i;
			}
		}
		throw new IllegalArgumentException("value " + value + " should be in keys " + Arrays.toString(keys));
	}

	private int [] histogram(List<Double> counts) {
		int max = 0;
		for (double i : counts) {
			max = Math.max(max, (int)(i+0.5));
		}
		int [] n = new int[max + 1];
		for (double i : counts) {
			n[(int)(i+0.5)]++;
		}
		return n;
	}

	private void collectTags(Node root, Set<String> tags, String tag) {
		for (Node leaf : root.getAllLeafNodes()) {
			tags.add((String) leaf.getMetaData(tag));
		}
		
	}

	private void collectTags(Node node, Map<String, Double> transitionCounts, String tag, final double tLo, final double tHi) {
		if (!node.isRoot()) {
			if (node.getHeight() >= tLo && node.getHeight() < tHi || 
				node.getHeight() < tLo && node.getParent().getHeight() > tLo) {
	 			String parentTag = (String) node.getParent().getMetaData(tag);
				String nodeTag = (String) node.getMetaData(tag);
				String id = parentTag+"=>" + nodeTag;
				double weight = 1.0;
				if (node.getHeight() >= tLo) {
					if (node.getParent().getHeight() > tHi) {
						// branch ends in epoch, sticks out above
						weight = (tHi - node.getHeight()) / node.getLength();
					}
				} else { // node.getHeight() < tLo
					if (node.getParent().getHeight() < tHi) {
						// branch starts in epoch, sticks out below
						weight = (node.getParent().getHeight() - tLo) / node.getLength();
					} else {
						// branch overlaps complete epoch
						weight = (tHi - tLo) / node.getLength();
					}
				}
				if (!transitionCounts.containsKey(id)) {
					transitionCounts.put(id, weight);
				} else {
					transitionCounts.put(id,  transitionCounts.get(id) + weight);
				}
			}
		}
		for (Node child : node.getChildren()) {
			collectTags(child, transitionCounts, tag, tLo, tHi);
		}		
	}

	@Override
	public double[][] getMatrix() {
		return migrations;
	}

	@Override
	public String[] getLabels(double[][] rates) {
		return tags;
	}

	public static void main(String[] args) throws Exception {
		new Application(new StateTransitionCounter(), "State transition counter", args);
	}
}
