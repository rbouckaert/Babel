package babel.tools;



import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import babel.tools.utils.MemoryFriendlyTreeSet;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.inference.Runnable;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;

@Description("Estimate migration speed for a tree posterior sample of phylogeographic analysis (as produced by BEAST)")
public class SpeedCalculator extends Runnable {

	final public Input<TreeFile> srcInput = new Input<>("tree", "1 or more source tree files", new TreeFile("[[none]]"));
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);
	final public Input<OutFile> traceInput = new Input<>("trace", "trace output file that can be processed in Tracer. Not produced if not specified.",
			new OutFile("[[none]]"));
	final public Input<String> tagInput = new Input<>("tag", "metadata-tag used in tree file to encode location in latitude longitude pairs (e.g. [&location={1.2,4.5}]", "location");

	private double EARTHRADIUS = 6371; // mean radius, according to http://en.wikipedia.org/wiki/Earth_radius
	private String tag;

	@Override
	public void initAndValidate() {
	}

	double sumOfTime = 0;
	double sumOfDistance = 0;

	@Override
	public void run() throws Exception {
		tag = tagInput.get();
		
		// process tree files
		List<Double> speed = new ArrayList<>();
		List<Double> time = new ArrayList<>();
		List<Double> distance = new ArrayList<>();
		
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(srcInput.get().getPath(), burnInPercentageInput.get());
		srcTreeSet.reset();
		int n = 0;
		double sum = 0;
		while (srcTreeSet.hasNext()) {
			Tree tree = srcTreeSet.next();
			
			sumOfTime = 0;
			sumOfDistance = 0;
			Node root = tree.getRoot();
			traverse(root);
			double treeSpeed = sumOfDistance / sumOfTime;
			
			time.add(sumOfTime);
			distance.add(sumOfDistance);
			speed.add(treeSpeed);

			sum += treeSpeed;
			n++;
		}
		
		Log.warning("Average speed (in km per time unit of tree) = " + (sum/n));
		
		// save trace?
		if (traceInput.get() != null &&
				!traceInput.get().getName().equals("[[none]]")) {
			saveTrace(distance, time, speed);
		}
		
		Log.warning("Done");
	}
	
	private void traverse(Node node) {
		if (!node.isRoot()) {
			sumOfTime += node.getLength();
			double [] start = getPosition(node.getParent());
			double [] end = getPosition(node);
			sumOfDistance += pairwiseDistance(start, end);
		}

		for (Node child : node.getChildren()) {
			traverse(child);
		}
	}

	private double[] getPosition(Node node) {
		double [] pos = new double[2];
		String metaData = node.metaDataString;
		int i = metaData.indexOf(tag);
		if (i < 0) {
			throw new IllegalArgumentException("Could not find metadata tag \"" + tag + "\" in tree");
		}
		String str = metaData.substring(i + tag.length() + 1);
		if (str.charAt(0) == '{') {
			str = str.substring(1);
		}
		i = str.indexOf("}");
		if (i >= 0) {
			str = str.substring(0, i);
		}
		String [] o2 = str.split(",");
		pos[0] = Double.parseDouble(o2[0]);
		pos[1] = Double.parseDouble(o2[1]);
		return pos;
	}

	
	// returns distance in km
	private double pairwiseDistance(double [] start, double [] end) {
		if (start[0] == end[0] && start[1] == end[1]) {
			return 0.0;
		}
		
		double latitude1 = start[0];
		double longitude1 = start[1];
		double theta1 = (latitude1)*Math.PI/180.0;
		if (longitude1 < 0) longitude1 += 360;
		double phi1 = longitude1 * Math.PI/180;

		double latitude2 = end[0];
		double longitude2 = end[1];
		double theta2 = (latitude2)*Math.PI/180.0;
		if (longitude2 < 0) longitude2 += 360;
		double phi2 = longitude2 * Math.PI/180;
		
		double Deltalambda = phi2 - phi1;
		
		double angle = Math.acos(Math.sin(theta1)*Math.sin(theta2)+Math.cos(theta1) * Math.cos(theta2) * Math.cos(Deltalambda)); 

		//double inverseVariance = 10;
        //double logP =  0.5 * Math.log(angle * Math.sin(angle)) - 0.5 * angle*angle * inverseVariance;
        // double logP = Math.log(Math.sqrt(angle * Math.sin(angle)) * inverseVariance) - 0.5 * angle*angle * inverseVariance;
        //double logP = 0.5 * Math.log(angle * Math.sin(angle)) + 0.5 * Math.log(inverseVariance) - 0.5 * angle*angle * inverseVariance;
        //return logP;

		return angle * EARTHRADIUS;
	}	
	
	/**
	 * save entries as tab separated file, which can be used in Tracer
	 */
	private void saveTrace(List<Double> distance, List<Double> time, List<Double> speed) throws IOException {
		PrintStream out = new PrintStream(traceInput.get());
		
		// header
		out.print("Sample\t");
		out.print("distance\t");
		out.print("time\t");
		out.print("speed\t");
		out.println();
		
		for (int i = 0; i < distance.size(); i++) {
			out.print(i + "\t");
			out.print(distance.get(i) + "\t");
			out.print(time.get(i) + "\t");
			out.print(speed.get(i) + "\t");
			out.println();
		}
		out.close();
	}

	public static void main(String[] args) throws Exception {
		new Application(new SpeedCalculator(), "Geographic speed calculator for phylogegraphical trees", args);		
	}

}
