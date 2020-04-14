package babel.tools;

import java.io.PrintStream;
import java.util.*;

import beast.app.treeannotator.TreeAnnotator;
import beast.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
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

@Description("Counts transitions of tags along branches of a tree")
public class StateTransitionCounter extends Runnable {	
	final public Input<TreeFile> src1Input = new Input<>("tree","source tree (set) file");
    final public Input<String> tagInput = new Input<String>("tag","label used to report trait", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);


	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			String str = outputInput.get().getPath();			
			Log.warning("Writing to file " + str);
			out = new PrintStream(str);
		}

		// find all tag values
		MemoryFriendlyTreeSet srcTreeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(src1Input.get().getPath(), burnInPercentageInput.get());
		srcTreeSet.reset();
		String tag = tagInput.get();
		Set<String> tagSet = new HashSet<>();
		collectTags(srcTreeSet.next().getRoot(), tagSet, tag);
		String [] tags = tagSet.toArray(new String[] {});
		Map<String, List<Integer>> transitionDistributions = new HashMap<>();
		for (int i = 0; i < tags.length; i++) {
			for (int j = 0; j < tags.length; j++) {
				String id = tags[i] + "=>" + tags[j];
				transitionDistributions.put(id, new ArrayList<>());
			}
		}
		srcTreeSet.reset();
		
		int n = 0;
		while (srcTreeSet.hasNext()) {
			Tree tree = srcTreeSet.next();
			Map<String, Integer> transitionCounts = new HashMap<>();
			collectTags(tree.getRoot(), transitionCounts, tag);
			
			for (String id : transitionCounts.keySet()) {
				if (!transitionDistributions.containsKey(id)) {
					Log.warning("Unrecognised tag: " + id);
					transitionDistributions.put(id, new ArrayList<>());
				}
				transitionDistributions.get(id).add(transitionCounts.get(id));
			}
			n++;
		}
		
		String [] keys = transitionDistributions.keySet().toArray(new String []{});
		Arrays.sort(keys);
		out.println("Transition" + "\t" +"mean" + "\t" + "95%Low" + "\t" + "95%High" + "\t" + "histogram");
		for (String id : keys) {
			List<Integer> counts = transitionDistributions.get(id);
			Collections.sort(counts);
			double sum = 0;
			for (double d : counts) {
				sum +=d;
			}
			double mean = sum / n;
			double lo = counts.get((int) (0.025 * counts.size())); 
			double hi = counts.get((int) (0.975 * counts.size())); 			
			out.println(id + "\t" + mean + "\t" + lo + "\t" + hi + "\t" + histogram(counts));
		}
		
		
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			out.close();
		}
		Log.info.println("Done");
	}

	
	private String histogram(List<Integer> counts) {
		int max = 0;
		for (int i : counts) {
			max = Math.max(max, i);
		}
		int [] n = new int[max + 1];
		for (int i : counts) {
			n[i]++;
		}
		StringBuilder b = new StringBuilder();
		for (int i = 0; i <= max; i++) {
			b.append(n[i]).append('\t');
		}
		return b.toString();
	}

	private void collectTags(Node root, Set<String> tags, String tag) {
		for (Node leaf : root.getAllLeafNodes()) {
			tags.add((String) leaf.getParent().getMetaData(tag));
		}
		
	}

	private void collectTags(Node node, Map<String, Integer> transitionCounts, String tag) {
		if (!node.isRoot()) {
			String parentTag = (String) node.getParent().getMetaData(tag);
			String nodeTag = (String) node.getMetaData(tag);
			String id = parentTag+"=>" + nodeTag;
			if (!transitionCounts.containsKey(id)) {
				transitionCounts.put(id, 1);
			} else {
				transitionCounts.put(id,  transitionCounts.get(id) + 1);
			}
		}
		for (Node child : node.getChildren()) {
			collectTags(child, transitionCounts, tag);
		}		
	}

	public static void main(String[] args) throws Exception {
		new Application(new StateTransitionCounter(), "State transition counter", args);
	}
}
