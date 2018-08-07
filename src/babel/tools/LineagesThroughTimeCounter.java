package babel.tools;

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
	final public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out","output file. Print to stdout if not specified");
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);
	final public Input<Integer> resolutionInput = new Input<>("resolution", "number of steps in table", 1000);

	int N = resolutionInput.get(); // number of steps in history
	
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

        
        FastTreeSet trees = new TreeAnnotator().new FastTreeSet(treesInput.get().getAbsolutePath(), burnInPercentageInput.get());
        trees.reset();
               
        
        List<Integer> [] distrs = new List[N + 1];
        for (int i = 0; i < distrs.length; i++) {
        	distrs[i] = new ArrayList<>();
        }
        
        trees.reset();
        double h = 0;
        while (trees.hasNext()) {
        	Tree tree = trees.next();
        	h = Math.max(tree.getRoot().getHeight(), h);
        }
        Log.warning("Maximum height = " + h);

        trees.reset();

        while (trees.hasNext()) {
            int [] linCount = new int[N + 1];
        	Tree tree = trees.next();
        	for (Node node : tree.getNodesAsArray()) {
        		if (!node.isRoot()) {
        			int start = (int) (node.getHeight() * N / h + 0.5);
        			int end = (int) (node.getParent().getHeight() * N / h + 0.5);
        			for (int i = start; i <= end; i++) {
        				linCount[i]++;
        			}
        		}
        	}
        	for (int i = 0; i < N; i++) {
        		distrs[i].add(linCount[i]);
        	}        	
        }
        
        for (int i = 0; i < N; i++) {
        	out.print(i*h/N + " \t");
        	List<Integer> counts = distrs[i];
        	out.print(mean(counts) + "\t");
        	double [] bounds = bounds(counts);
        	out.print(bounds[0] + "\t");
        	out.print(bounds[1] + "\t");        	
        	out.println();
        }
        
        
        Log.warning("Done");
        
       }


	private double[] bounds(List<Integer> counts) {
		Collections.sort(counts);
		int lower = counts.size() * 25 / 1000;
		int upper = counts.size() * 975 / 1000;
		return new double[]{counts.get(lower), counts.get(upper)};
	}

	private double mean(List<Integer> counts) {
		double sum = 0;
		for (int i : counts) {
			sum += i;
		}
		return sum / counts.size();
	}


	public static void main(String[] args) throws Exception {
		new Application(new LineagesThroughTimeCounter(), "LineageThroughTimeCounter", args);
		
	}
}
