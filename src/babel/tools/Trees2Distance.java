package babel.tools;


import java.io.PrintStream;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import babel.tools.utils.MemoryFriendlyTreeSet;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.inference.Runnable;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beastlabs.evolution.tree.RNNIMetric;
import beast.base.evolution.tree.Tree;

@Description("Create pairwise distance matrix for trees in a set")
public class Trees2Distance extends Runnable {
	final public Input<TreeFile> srcInput = new Input<>("tree", "1 or more source tree files", new TreeFile("[[none]]"));
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);
	final public Input<Integer> threadsInput = new Input<>("threads", "number of threads to use. Ignored if 1 or less", -1);
	final public Input<OutFile> outputInput = new Input<>("out", "output file containing distance matrix.",
			new OutFile("[[none]]"));

    private ExecutorService exec;
    private CountDownLatch countDown;
    private double [][] distances;
    private List<Tree> trees;
    private long start;
    private int distancesCalctulated, totalDistances;

    @Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		// collect trees
		trees = new ArrayList<>(); 
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(srcInput.get().getPath(), burnInPercentageInput.get());
		srcTreeSet.reset();
		while (srcTreeSet.hasNext()) {
			trees.add(srcTreeSet.next());
		}
		totalDistances = trees.size() * (trees.size()-1)/2;
		distancesCalctulated = 0;
		Log.warning(totalDistances + " distances to calculate");
		
		// reserve memory for distance matrix
		distances = new double[trees.size()][trees.size()];
		int threadCount = threadsInput.get();
		if (threadCount > 1) {
		     exec = Executors.newFixedThreadPool(threadCount);
		}

		start = System.currentTimeMillis();
		if (threadCount <= 1) {
			int i = 0;
			while (i < trees.size()) {
				distances[i][i] = 0.0;
				
				process(i, trees, distances);
				distancesCalctulated += i;
				i++;
				
				Log.warning.print('.');
				if (true || i % 10 == 0) {
					long time = System.currentTimeMillis() - start;
					Log.warning(" " + distancesCalctulated + " in " + time/1000 + " seconds = " + time/distancesCalctulated + " ms per distance " + ((totalDistances-distancesCalctulated)*time/(1000*distancesCalctulated)) + " seconds to go");
				}
				System.gc();
			}
		} else {
            countDown = new CountDownLatch(threadCount);
            // kick off the threads
        	boolean [] done = new boolean[trees.size()];
            for (int i = 0; i < threadCount; i++) {
                CoreRunnable coreRunnable = new CoreRunnable(i, done);
                exec.execute(coreRunnable);
            }
            countDown.await();
		}
		
		// save to file?
		PrintStream out = System.out;
		if (outputInput.get() != null &&
				!outputInput.get().getName().equals("[[none]]")) {
			out = new PrintStream(outputInput.get());
		}		
		int n = trees.size();
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				double d = distances[i][j];
				out.print(d + ",");
			}
			out.println();
		}
		
		if (outputInput.get() != null &&
				!outputInput.get().getName().equals("[[none]]")) {
			out.close();
		}
		
//		double [][] mds = MDSJ.classicalScaling(distances, 2);
		
		
		Log.warning("Done");
	}

    class CoreRunnable implements java.lang.Runnable {
    	int i;
    	boolean [] done;
    	
        CoreRunnable(int start, boolean [] done) {
            i = start;
            this.done = done;
        }

        @Override
		public void run() {
            try {
            	while (i < done.length) {
    				distances[i][i] = 0.0;
    				
    				process(i, trees, distances);
    				synchronized (this) {
    					distancesCalctulated += i;
    				}
    				done[i] = true;
    				Log.warning.print('.');
    				if (i > 0 && i % 10 == 0) {
    					long time = System.currentTimeMillis() - start;
    					try {
    						Log.warning(" " + distancesCalctulated + " in " + time/1000 + " seconds = " + time/distancesCalctulated + " ms per distance " + ((totalDistances-distancesCalctulated)*time/(1000*distancesCalctulated)) + " seconds to go");
    					} catch (ArithmeticException e) {
    						// ignore
    					}
    				}
    				i += threadsInput.get();
    				System.gc();
            	}
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
            countDown.countDown();
        }

    } // CoreRunnable
	
	
	private int process(int i, List<Tree> trees, double[][] distances) {
		Tree tree = trees.get(i);
		RNNIMetric metric = new RNNIMetric(tree.getTaxaNames());
		int j = 0;
		while (j < i) { 
			Tree tree2 = trees.get(j);
			double d = RNNIDistance(metric, tree, tree2);
			distances[i][j] = d;
			distances[j][i] = d;
			j++;
		}
		return j;
	}

	private double RNNIDistance(RNNIMetric metric, Tree tree1, Tree tree2) {
		if (tree1.getRoot().getNr() == 0) {
			renumberInternal(tree1.getRoot(), new int[]{tree1.getLeafNodeCount()});
		}
		if (tree2.getRoot().getNr() == 0) {
			renumberInternal(tree2.getRoot(), new int[]{tree2.getLeafNodeCount()});
		}
		return metric.distance(tree1, tree2);
	}

	private int renumberInternal(Node node, int[] nr) {
		for (Node child : node.getChildren()) {
			renumberInternal(child, nr);
		}
		if (!node.isLeaf()) {
			node.setNr(nr[0]);
			nr[0]++;
		}
		return nr[0];
	}


	public static void main(String[] args) throws Exception {
		new Application(new Trees2Distance(), "Trees2Distance", args);		
	}

}
