package babel.tools;

import java.io.File;
import java.io.PrintStream;
import java.util.*;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beastfx.app.inputeditor.BeautiDoc;
import beastfx.app.tools.Application;
import beastfx.app.treeannotator.TreeAnnotator;
import babel.tools.utils.MemoryFriendlyTreeSet;

@Description("Produce statistics on taxa that form the sister clade of a given clade")
public class SisterCladeCounter extends TreeCombiner {
	final public Input<File> cgfFileInput = new Input<>("cfg", "comma separated list of taxa to form a clade,  one clade set per line.");
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);
	final public Input<Integer> ancestorCountInput = new Input<>("ancestor", "number of nodes above clade used as MRCA for all sibling taxa."
			+ "if negative, interpret it as the age of the split above the MRCA time of the clade", 1);

	
	Map<String, Integer> map;
	
	@Override
	public void run() throws Exception {
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(srcInput.get().getPath(), burnInPercentageInput.get());

		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getName());
			out = new PrintStream(outputInput.get());
		}

		String cfg = BeautiDoc.load(cgfFileInput.get());
		String [] strs = cfg.split("\n");
		for (String str : strs) {
			if (!(str.startsWith("#") || str.trim().length() == 0)) {
				srcTreeSet.reset();
				map = new HashMap<>();
				int n = 0;
				while (srcTreeSet.hasNext()) {
					Tree tree = srcTreeSet.next();
					collectStats(tree, str);
					n++;
				}
				report(n, out);
			}
		}
				
		Log.err("Done!");
		out.close();
	}

	class Pair {
		public Pair(String taxon, Integer count) {
			this.taxon = taxon;
			this.count = count;
		}
		String taxon;
		int count;
		
	}
	private void report(int n, PrintStream out) {
		List<Pair> pairs = new ArrayList<>();
		for (String taxon : map.keySet()) {
			pairs.add(new Pair(taxon, map.get(taxon)));
		}
		Collections.sort(pairs, (o1, o2) -> {
			if (o1.count > o2.count) {
				return 1;
			} else if (o1.count < o2.count) {
				return -1;
			}
			return o1.taxon.compareTo(o2.taxon);
		});
		
		for (Pair pair : pairs) {
			out.println(pair.taxon + " " + ((double)pair.count)/n);
		}
	}

	private void collectStats(Tree tree, String str) {
		Set<String> taxa = new HashSet<>();
		for (String taxon : str.split(",")) {
			taxa.add(taxon);
		}
		Node node = getMRCA(tree, taxa);

		// determine ancestor
		Node ancestor = node;
		if (ancestorCountInput.get() < 0) {
			ancestor = node;
			double target = node.getHeight() + (-ancestorCountInput.get());
			while (!ancestor.isRoot() && ancestor.getParent().getHeight() < target) {
				ancestor = ancestor.getParent();
			}
		} else {
			ancestor = node.getParent();
			for (int i = 1; !ancestor.isRoot() && i < ancestorCountInput.get(); i++) {
				ancestor = ancestor.getParent();
			}
		}
		
		// process clade below ancestor
		for (Node leaf : ancestor.getAllLeafNodes()) {
			String taxon = leaf.getID();
			if (!taxa.contains(taxon)) {
				if (!map.containsKey(taxon)) {
					map.put(taxon, 0);
				}
				map.put(taxon, map.get(taxon) + 1);
			}
		}		
	}

	public static void main(String[] args) throws Exception {
		new Application(new SisterCladeCounter(), "SisterCladeCounter", args);

	}

}
