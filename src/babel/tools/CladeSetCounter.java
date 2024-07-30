package babel.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import babel.tools.CladeSetComparator.CladeSetWithHeights;
import beastfx.app.treeannotator.TreeAnnotator;
import babel.tools.utils.MemoryFriendlyTreeSet;
import beastfx.app.tools.Application;
import beastfx.app.util.TreeFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Log;
import beast.base.evolution.tree.Tree;


@Description("Counts number of distinct clades in a tree set")
public class CladeSetCounter extends Runnable {
	final public Input<List<TreeFile>> srcInput = new Input<>("tree","2 or more source tree (set or MCC tree) files", new ArrayList<>());
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);

	@Override
	public void initAndValidate() {
	}

	
	
	@Override
	public void run() throws Exception {
		for (TreeFile f : srcInput.get()) {
			Log.info(f.getName() + "\t" + getCladeSet(f.getPath()));
		}
	}
	
	
	private int getCladeSet(String path) throws IOException {
		Log.warning("Processing " + path);
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(path, burnInPercentageInput.get());
		srcTreeSet.reset();
		Tree tree = srcTreeSet.next();
		CladeSetWithHeights cladeSet1 = new CladeSetComparator().new CladeSetWithHeights(tree);
		while (srcTreeSet.hasNext()) {
			// System.out.println(n);
			tree = srcTreeSet.next();
			cladeSet1.add(tree);
		}
		return cladeSet1.getCladeCount();
	}
	
	public static void main(String[] args) throws Exception {
		new Application(new CladeSetCounter(), "CladeSetCounter", args);
	}



}
