package babel.tools;


import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import babel.tools.utils.MemoryFriendlyTreeSet;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.evolution.tree.Tree;
import beast.evolution.tree.TreeDistanceLogger;
import beast.util.ClusterTree;
import beast.util.ClusterTree.Type;
import beast.util.NexusParser;
@Description("A post-processing version of TreeDistanceLogger. Logs the mean and variance distance of each tree in a tree file to a series of bootstrap reference trees")
public class TreeBootstrapESS extends Runnable {
	
	
	final public Input<TreeFile> srcInput = new Input<>("tree","A source tree file", new TreeFile("[[None]]") );
	final public Input<List<TreeDistanceLogger>> loggersInput = new Input<>("log","1 or more TreeDistanceLoggers", new ArrayList<>());
	final public Input<OutFile> traceInput = new Input<>("trace", "trace output file that can be processed in Tracer. Not produced if not specified.");
	final public Input<File> nexusFileInput = new Input<>("nexus", "file with alignment, used if log-input is not specified", new File("[[None]]"));
	final public Input<ClusterTree.Type> clusterTypeInput = new Input<>("clusterType", "type of clustering algorithm used for generating initial beast.tree. " +
            "Should be one of " + Arrays.toString(Type.values()) + " (default " + Type.upgma + ")", Type.upgma, Type.values());
	final public Input<Integer> bootStrapCountInput = new Input<>("bootStrapCount", "number of bootstrap replicates", 100);
	
	
	List<TreeDistanceLogger> distanceLoggers;
	
	@Override
	public void initAndValidate() {
		
		
	}

	@Override
	public void run() throws Exception {
		distanceLoggers = loggersInput.get();
		if (distanceLoggers.size() == 0) {
			createDistanceLoggerFromAlignment();
		}
		
		// Save trace
		PrintStream out = System.out;
		if (traceInput.get() != null) {
			out = new PrintStream(traceInput.get());
		}
		
		
		// Iterate through all trees
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(srcInput.get().getPath(), 0);
		srcTreeSet.reset();
		long traceNum = 0;
		while (srcTreeSet.hasNext()) {
			
			Tree tree = srcTreeSet.next();
			
			// Prepare loggers
			for (TreeDistanceLogger logger : distanceLoggers) {
				logger.setTree(tree);
			}
			
			
			// Print header?
			if (traceNum == 0) {
				out.print("Sample\t");
				for (TreeDistanceLogger logger : distanceLoggers) {
					logger.init(out);
				}
				out.println();
			}
			
			
			
			// Print log line
			out.print(traceNum + "\t");
			for (TreeDistanceLogger logger : loggersInput.get()) {
				logger.log(traceNum, out);
			}
			out.println();
			
			traceNum++;
			
		}
		
		
		
		// Close
		out.close();
		
		Log.warning("Done");
	}
	

	private void createDistanceLoggerFromAlignment() throws IOException {
		NexusParser parser = new NexusParser();
		parser.parseFile(nexusFileInput.get());
		Alignment data = parser.m_alignment;
		if (data == null) {
			throw new IllegalArgumentException("Expected to find an alignment in the nexus file " + nexusFileInput.get());
		}
		
		ClusterTree clusterTree = new ClusterTree();
		clusterTree.initByName("clusterType", clusterTypeInput.get(), "taxa", data);
		
		TreeDistanceLogger logger = new TreeDistanceLogger();
		logger.initByName("tree", clusterTree, "ref", clusterTree, "bootstraps", bootStrapCountInput.get(), "psites", 0.0);
		
		distanceLoggers.add(logger);
	}

	public static void main(String[] args) throws Exception {
		new Application(new TreeBootstrapESS(), "TreeBootstrapESS", args);
	}
	

}
