package babel.tools;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import beast.app.beauti.BeautiDoc;
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

@Description("Add metadata to tree file marking clades specified in seperate configuration file")
public class CladeMarker extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees", "NEXUS file containing a tree set",
			Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));
	final public Input<File> cladeSetInputInput = new Input<>("cladeSetInput", "specify clades to be marked "
			+ "one clade per line, first column the clade name, (whitespace to separate columns) second column a comma delimted list of taxa "
			+ "When clades are nested, the last clade in this file is used for mark internal nodes. ",
			new File("[[none]]"));
	final public Input<String> tagInput = new Input<>("tag", "meta-data tag used to mark clade",
			"clade");

	private List<String> cladeNames = new ArrayList<>();
	private List<Set<String>> clades = new ArrayList<>();

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {

		processCladeFile();

		// open file for writing
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			out = new PrintStream(outputInput.get());
			Log.warning("Writing to file " + outputInput.get().getPath());
		}

		// read trees one by one, relabel and write out relabeled tree in newick format
		MemoryFriendlyTreeSet trees = new TreeAnnotator().new MemoryFriendlyTreeSet(treesInput.get().getAbsolutePath(),
				0);
		trees.reset();
		Tree tree = trees.next();
		trees.reset();
		while (trees.hasNext()) {
			tree = trees.next();
			for (int i = 0; i < cladeNames.size(); i++) {
				mark(tree, cladeNames.get(i), clades.get(i));
			}
			StringBuilder buf = new StringBuilder();
			Nexus2Newick.toShortNewick(tree.getRoot(), buf, true);
			out.println(buf.toString());
		}
		out.println();

		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			out.close();
		}
		Log.warning("Done");
	}

	private boolean[] nodesTraversed;

	private void mark(Tree tree, String cladeName, Set<String> taxa) {
		List<Node> leafs = new ArrayList<>();
		for (Node node : tree.getExternalNodes()) {
			if (taxa.contains(node.getID())) {
				leafs.add(node);
			}
		}

		nodesTraversed = new boolean[tree.getNodeCount()];
		Node cur = leafs.get(0);

		for (int k = 1; k < leafs.size(); ++k) {
			cur = getCommonAncestor(cur, leafs.get(k), cladeName);
		}
	}

	
	protected Node getCommonAncestor(Node n1, Node n2, String cladeName) {
		// assert n1.getTree() == n2.getTree();
		if (!nodesTraversed[n1.getNr()]) {
			nodesTraversed[n1.getNr()] = true;
		}
		if (!nodesTraversed[n2.getNr()]) {
			nodesTraversed[n2.getNr()] = true;
		}
		while (n1 != n2) {
			double h1 = n1.getHeight();
			double h2 = n2.getHeight();
			if (h1 < h2) {
				setMetaData(n1, cladeName);
				n1 = n1.getParent();
				if (!nodesTraversed[n1.getNr()]) {
					nodesTraversed[n1.getNr()] = true;
				}
			} else if (h2 < h1) {
				setMetaData(n2, cladeName);
				n2 = n2.getParent();
				if (!nodesTraversed[n2.getNr()]) {
					nodesTraversed[n2.getNr()] = true;
				}		   	n1.setMetaData("clade", cladeName);

			} else {
				// zero length branches hell
				Node n;
				double b1 = n1.getLength();
				double b2 = n2.getLength();
				if (b1 > 0) {
					n = n2;
				} else { // b1 == 0
					if (b2 > 0) {
						n = n1;
					} else {
						// both 0
						n = n1;
						while (n != null && n != n2) {
							n = n.getParent();
						}
						if (n == n2) {
							// n2 is an ancestor of n1
							n = n1;
						} else {
							// always safe to advance n2
							n = n2;
						}
					}
				}
				if (n == n1) {
					setMetaData(n1, cladeName);
					n = n1 = n.getParent();
				} else {
					setMetaData(n2, cladeName);
					n = n2 = n.getParent();
				}
				if (!nodesTraversed[n.getNr()]) {
					nodesTraversed[n.getNr()] = true;
				}
			}
		}
		n1.setMetaData("clade", cladeName);
		return n1;
	}

	private void setMetaData(Node n, String cladeName) {
		n.metaDataString = tagInput.get()+"=\"" + cladeName + "\"";		
	}

	private void processCladeFile() throws IOException {
		if (cladeSetInputInput.get() == null || cladeSetInputInput.get().getName().equals("[[none]]")) {
			throw new IllegalArgumentException("cladeSetInput must be specified");
		}

		String str = BeautiDoc.load(cladeSetInputInput.get());
		String[] strs = str.split("\n");
		for (String s : strs) {
			s = s.trim();
			if (s.length() > 0 && !s.startsWith("#")) {
				String[] strs2 = s.split("\\s+");
				String cladeName = strs2[0];
				String[] clade = strs2[1].split(",");
				cladeNames.add(cladeName);

				Set<String> taxa = new HashSet<>();
				for (String taxon : clade) {
					taxa.add(taxon);
				}
				clades.add(taxa);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		new Application(new CladeMarker(), "CladeMarker", args);
	}

}
