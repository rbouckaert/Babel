package babel.tools;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
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

@Description("Produce table for lineages through time plot with 95%HPD bounds")
public class LineagesThroughTimeCounter extends Runnable {
	final public Input<List<TreeFile>> treesInput = new Input<>("trees", "NEXUS file containing a tree set",
			new ArrayList<>(), Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file. Print to stdout if not specified");
	final public Input<OutFile> svgOutputInput = new Input<>("svgout", "if specified, produce SVG file with graph");
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin",
			"percentage of trees to used as burn-in (and will be ignored)", 10);
	final public Input<Integer> resolutionInput = new Input<>("resolution", "number of steps in table", 1000);
	final public Input<Boolean> reverseSVGAxisInput = new Input<>("reverseSVGAxis",
			"reverse x-axis, that is go forward in time instead of backward", true);
	final public Input<Double> maxXInput = new Input<>("maxX", "maximum value for x-axis. Automaticlly deduced if < 0", -1.0);
	final public Input<Double> maxYInput = new Input<>("maxY", "maximum value for y-axis. Automaticlly deduced if < 0", -1.0);

	int N = resolutionInput.get(); // number of steps in history
	double maxX = 0;

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		N = resolutionInput.get();

		// open file for writing
		PrintStream out = System.out;
		if (outputInput.get() != null) {
			out = new PrintStream(outputInput.get());
			Log.warning("Writing to file " + outputInput.get().getPath());
		}

		double [][][] dataX = new double[treesInput.get().size()][][];
		int n = 0;
		for (File treeFile : treesInput.get()) {
			Log.warning(treeFile.getPath());
			dataX[n] = processFile(treeFile);
			
			out.println("age\tmean\t95%HPD_low\t95%HPD_high");
			for (int i = 0; i < N; i++) {
				out.println(dataX[n][i][0] + "\t" + dataX[n][i][1] + "\t" + dataX[n][i][2] + "\t" + dataX[n][i][3]);
			}
			
			n++;
		}


		if (svgOutputInput.get() != null) {
			PrintStream svg = new PrintStream(svgOutputInput.get());
			Log.warning("Writing to file " + svgOutputInput.get().getPath());
			svg.println("<svg xmlns=\"http://www.w3.org/2000/svg\">");
			svg.println("<style type=\"text/css\">");
			svg.println(".mean {stroke-width:1;stroke:#0000ff;fill:none;}");
			svg.println(".intervals {stroke-width:1;opacity:0.2;}");
			svg.println("</style>");

			svg.println("<rect width='400' height='400' style='fill:none;stroke-width:0.5;stroke:#000;'/>");
			for (int k = 0; k < n; k++) {
				double fx = 400 / maxX;
				double maxY = 0;
				if (maxYInput.get() > 0) {
					maxY = maxYInput.get();
				} else {
					for (double[] d : dataX[k]) {
						maxY = Math.max(maxY, d[3]);
					}
				}
				double y = 400;
				double fy = y / maxY;

				// 95% hpd polygon
				svg.print("<polygon class=\"intervals\" points=\"");
				for (int i = 0; i < N; i++) {
					svg.print(dataX[k][i][0] * fx + "," + (y - dataX[k][i][2] * fy) + " ");
				}
				for (int i = N - 1; i >= 0; i--) {
					svg.print(dataX[k][i][0] * fx + "," + (y - dataX[k][i][3] * fy) + " ");
				}
				svg.println("\"/>");

				// mean polyline
				svg.print("<polyline class=\"mean\" points=\"");
				for (int i = 0; i < N; i++) {
					svg.print(dataX[k][i][0] * fx + "," + (y - dataX[k][i][1] * fy) + " ");
				}
				svg.println("\"/>");
			}
			svg.println("</svg>");
			svg.close();
		}
		Log.warning("Done");
	}

	private double[][] processFile(File treeFile) throws IOException {
		FastTreeSet trees = new TreeAnnotator().new FastTreeSet(treeFile.getAbsolutePath(),
				burnInPercentageInput.get());
		trees.reset();

		List<Double>[] distrs = new List[N + 1];
		for (int i = 0; i < distrs.length; i++) {
			distrs[i] = new ArrayList<>();
		}

		trees.reset();
		maxX = 0;
		while (trees.hasNext()) {
			Tree tree = trees.next();
			maxX = Math.max(tree.getRoot().getHeight(), maxX);
		}
		Log.warning("Maximum height = " + maxX);

		trees.reset();

		while (trees.hasNext()) {
			double [] linCount = new double[N + 1];
			double stepSize = maxX / N;
			Tree tree = trees.next();
			for (Node node : tree.getNodesAsArray()) {
				if (!node.isRoot()) {
					int start = (int) (node.getHeight() * N / maxX + 0.5);
					int end = (int) (node.getParent().getHeight() * N / maxX + 0.5);
					if (start == end) {
						linCount[start] += node.getParent().getHeight() - node.getHeight();
					} else {
						linCount[start] += ((start+1) * stepSize  - node.getHeight())/stepSize;
						for (int i = start+1; i < end; i++) {
							linCount[i]++;
						}
						linCount[end] += node.getParent().getHeight() - end * stepSize;
					}
				}
			}
			for (int i = 0; i < N; i++) {
				distrs[i].add(linCount[i]);
			}
		}

		double[][] data = new double[N][4];
		for (int i = 0; i < N; i++) {
			data[i][0] = i * maxX / N;
			List<Double> counts = distrs[i];
			data[i][1] = mean(counts);
			double[] bounds = bounds(counts);
			data[i][2] = bounds[0];
			data[i][3] = bounds[1];
		}

		smooth(data, 1);
		smooth(data, 2);
		smooth(data, 3);

		if (maxXInput.get() > 0) {
			maxX = maxXInput.get();
		}
		if (reverseSVGAxisInput.get()) {
			for (int i = 0; i < N; i++) {
				data[i][0] = maxX - data[i][0];
			}
		}
		return data;
	}

	private void smooth(double[][] data, int column) {
		int window = 5;
		int N = data.length;
		double[] smoothed = new double[N];
		for (int i = window; i < N - window - 1; i++) {
			double sum = 0;
			for (int j = i - window; j <= i + window; j++) {
				sum += data[j][column];
			}
			sum /= 2 * window + 1;
			smoothed[i] = sum;
		}
		for (int i = window; i < N - window - 1; i++) {
			data[i][column] = smoothed[i];
		}
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
		new Application(new LineagesThroughTimeCounter(), "LineageThroughTimeCounter", args);

	}
}
