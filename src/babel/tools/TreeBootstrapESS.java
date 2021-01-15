package babel.tools;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import beast.app.treeannotator.TreeAnnotator;
import beast.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.evolution.tree.Tree;
import beast.evolution.tree.TreeDistanceLogger;
@Description("A post-processing version of TreeDistanceLogger. Logs the mean and variance distance of each tree in a tree file to a series of bootstrap reference trees")
public class TreeBootstrapESS extends Runnable {
	
	
	final public Input<TreeFile> srcInput = new Input<>("tree","A source tree files", Input.Validate.REQUIRED);
	final public Input<List<TreeDistanceLogger>> loggersInput = new Input<>("log","1 or more TreeDistanceLoggers", new ArrayList<>());
	final public Input<OutFile> traceInput = new Input<>("trace", "trace output file that can be processed in Tracer. Not produced if not specified.");


	@Override
	public void initAndValidate() {
		
		
	}

	@Override
	public void run() throws Exception {
		
		
		// Save trace
		PrintStream out = System.out;
		if (traceInput.get() != null) {
			out = new PrintStream(traceInput.get());
		}
		
		
		// Iterate through all trees
		MemoryFriendlyTreeSet srcTreeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(srcInput.get().getPath(), 0);
		srcTreeSet.reset();
		long traceNum = 0;
		while (srcTreeSet.hasNext()) {
			
			Tree tree = srcTreeSet.next();
			
			// Prepare loggers
			for (TreeDistanceLogger logger : loggersInput.get()) {
				logger.setTree(tree);
			}
			
			
			// Print header?
			if (traceNum == 0) {
				out.print("Sample\t");
				for (TreeDistanceLogger logger : loggersInput.get()) {
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
		
	}
	
	
	

}
