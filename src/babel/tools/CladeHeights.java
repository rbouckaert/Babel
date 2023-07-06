package babel.tools;



import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import babel.tools.utils.MemoryFriendlyTreeSet;
import beastfx.app.tools.Application;
import beastfx.app.util.TreeFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;

@Description("Extract clade heights from tree posterior sample (as produced by BEAST)")
public class CladeHeights extends TreeCombiner {
	final public Input<TreeFile> focalTreeInput = new Input<>("focalTree", "focal tree file with tree in NEXUS or Newick format. "
			+ "This is useful for comparing traces between different runs."
			+ "If not specified, the first tree after burnin is used as focal tree", new TreeFile("[[none]]"));
	

	List<String> focalClades;
	String [] taxa;

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {

		if (srcInput.get() == null || srcInput.get().getName().equals("[[none]]")) {
			throw new IllegalArgumentException("input tree must be specified");
		}

		if (focalTreeInput.get() != null && !focalTreeInput.get().getName().equals("[[none]]")) {
			// get external focal tree, if any
			processFocalTree();
		} else {
			// first tree as focal tree
			Log.warning("Taking first tree as focal tree");
			setFocalTreeFromTreeSet();
		}
		
		
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			out = new PrintStream(outputInput.get());
		}
		
		// header
		out.print("Sample\t");
		String prefix = srcInput.get().getName();
		if (prefix.contains(".")) {
			prefix = prefix.substring(0, prefix.indexOf('.'));
		}
		for (int i = 0; i < focalClades.size(); i++) {
			out.print(prefix + '.' + focalClades.get(i) + '\t');
		}
		out.println();
		
		// process tree files
		processTreeFile(srcInput.get(), out);
		
		// close
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			out.close();
		}
		
		Log.warning("Done");
	}
	
	
	private void processTreeFile(TreeFile f, PrintStream out) throws IOException {
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(f.getPath(), 0);
		srcTreeSet.reset();

		int k = 0;
		while (srcTreeSet.hasNext()) {
			out.print(k+"\t");
			Tree tree = srcTreeSet.next();
			for (int i = 0; i < focalClades.size(); i++) {
				double height = getMRCAHeight(tree, focalClades.get(i));
				out.print(height+"\t");
			}
			out.println();
			k++;
		}
	}
	

	private double getMRCAHeight(Tree tree, String taxa) {
		Set<String> taxonSet = new HashSet<>();
		for (String taxon : taxa.split(",")) {
			taxonSet.add(taxon);
		}
		Node node = getMRCA(tree, taxonSet);
		return node.getHeight();
	}

	/** add clades to focalClade set as comma separted Strings of taxon names **/
	private String addFocalClades(Node node) {
        if (node.isLeaf()) {
            return node.getID();
        }
		
        String left = addFocalClades(node.getLeft());
        String right = addFocalClades(node.getRight());
        focalClades.add(left +","+right);
        return left +","+right;
	}

	private void processFocalTree(MemoryFriendlyTreeSet srcTreeSet) throws IOException {
		srcTreeSet.reset();
		Tree focalTree = srcTreeSet.next();
		focalClades = new ArrayList<>();
		addFocalClades(focalTree.getRoot());
	}

	private void processFocalTree() throws IOException {
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(focalTreeInput.get().getPath(), 0);
		processFocalTree(srcTreeSet);
	}

	private void setFocalTreeFromTreeSet() throws IOException {
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(srcInput.get().getPath(), 0);
		processFocalTree(srcTreeSet);
	}


	public static void main(String[] args) throws Exception {
		new Application(new CladeHeights(), "Clade Heights", args);		
	}

}
