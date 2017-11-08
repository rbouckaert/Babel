package babel.tools.dplace;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import babel.tools.TreeCombiner;
import beast.app.beauti.BeautiDoc;
import beast.app.treeannotator.TreeAnnotator;
import beast.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
import beast.app.util.Application;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.util.Log;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;
//import beast.util.TreeParser;

@Description("Grafts nodes into a tree above the MRCA of a set of nodes")
public class TreeGrafter extends TreeCombiner {
	final public Input<File> cfgFileInput = new Input<>("cfg", "tab separated configuration file containing three columns: "
			+ "column 1: name of taxon\n"
			+ "column 2: height (age) of taxon\n"
			+ "column 3: a comma separated list of taxa determining MRCA to graft above in source tree (if no constraints have been specified).");
	final public Input<TreeFile> constraintsFileInput = new Input<>("constraints","newick tree file with constraints on where to insert leaf nodes");
	
	String [] taxonName;
	double [] taxonHeight;
	Set<String> [] subTaxonSets;
	
	// for debugging:
	int found = 0;
	
	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		MemoryFriendlyTreeSet srcTreeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(srcInput.get().getPath(), 0);
		srcTreeSet.reset();
		Tree tree = srcTreeSet.next();
		
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}

		Set<String> taxa = new LinkedHashSet<>();
		for (String taxon : tree.getTaxaNames()) {
			taxa.add(taxon);
		}
		processCfgFile(taxa);

		srcTreeSet.reset();
		int n = taxonName.length;
		while (srcTreeSet.hasNext()) {
			tree = srcTreeSet.next();
			for (int i = 0; i < n; i++) {
				Node src = getMRCA(tree, subTaxonSets[i]);
				Node parent = src.getParent();
				double len = src.getLength();
				// create intermediary node on branch
				double newHeight = src.getHeight() + Randomizer.nextDouble() * len;
				while (newHeight <= taxonHeight[i]) {
					newHeight = src.getHeight() + Randomizer.nextDouble() * len;
				}
				
				Node newNode = new Node();
				newNode.setHeight(newHeight);
				newNode.setParent(parent);
				for (int j = 0; j < parent.getChildCount(); j++) {
					if (parent.getChild(j) == src) {
						parent.setChild(j, newNode);
					}
				}
				newNode.addChild(src);
				src.setParent(newNode);
				
				// create new leaf node
				Node leaf = new Node();
				leaf.setID(taxonName[i]);
				Log.warning("Adding " + taxonName[i]);
				leaf.setHeight(taxonHeight[i]);
				newNode.addChild(leaf);
				leaf.setParent(newNode);
			}
			out.print(tree.getRoot().toNewick());
			out.println(";");
		}
		 
		Log.err("Done!");
		out.close();
	}

	private void processCfgFile(Set<String> taxa) throws IOException {
		boolean hasConstraints = constraintsFileInput.get() != null && !constraintsFileInput.get().getName().equals("[[none]]");
		
		String cfg = BeautiDoc.load(cfgFileInput.get());
		String [] strs = cfg.split("\n");
		int n = 0;
		for (String str : strs) {
			if (!str.matches("^\\s*$")) {
				n++;
			}
		}
		subTaxonSets = new Set[n];
		taxonName = new String[n];
		taxonHeight = new double[n];
		int i = 0;
		for (String str : strs) {
			if (!str.matches("^\\s*$")) {
				String [] strs2 = str.split("\t");
				taxonName[i] = strs2[0];
				taxonHeight[i] = strs2.length > 1 ? Double.parseDouble(strs2[1]) : 0.0;
				subTaxonSets[i] = new HashSet<>();
				if (!hasConstraints) {
					for (String taxon : strs2[2].split(",")) {
						subTaxonSets[i].add(taxon);					
					}
				} else {
					taxa.add(taxonName[i]);
				}
				i++;
			}
		}
		
		if (hasConstraints) {
			String constraints = BeautiDoc.load(constraintsFileInput.get());
			ISOTreeParser parser = new ISOTreeParser();
			Node root = parser.parse(constraints);
			found = 0;
			root = filterTaxa(root, taxa);
			Log.warning(toShortNewick(root));
			for (i = 0; i < n; i++) {
				findTaxa(taxonName[i], root, subTaxonSets[i]);
			}
		}		
	}

	private String toShortNewick(Node node) {
		if (node.isLeaf()) {
			return node.getID();
		} else {
			String n = "(";
			for (Node child : node.getChildren()) {
				n += toShortNewick(child) + ",";
			}
			return n.substring(0, n.length()-1) + ")";
		}
	}

	/** find node with id == taxon, and add all its parents leaf nodes to taxonSet **/
	private void findTaxa(String taxon, Node node, Set<String> taxonSet) {
		if (node.isLeaf()) {
			if (node.getID().equals(taxon)) {
				for (Node leaf : node.getParent().getAllLeafNodes()) {
					taxonSet.add(leaf.getID());
				}
			}
		} else {
			for (Node child : node.getChildren()) {
				findTaxa(taxon, child, taxonSet);
			}
		}		
	}

	/** return tree with leaf nodes that are not in taxa pruned from the tree **/
	private Node filterTaxa(Node node, Set<String> taxa) {
		if (node.isLeaf()) {
			if (taxa.contains(node.getID())) {
				taxa.remove(node.getID());
				found++;
				return node;
			} else {
				return null;
			}
		} else {
			List<Node> children = new ArrayList<>();
			if (node.getID() != null && taxa.contains(node.getID())) {

				for (Node child : node.getChildren()) {
					Node c = filterTaxa(child, taxa);
					if (c != null) {
						children.add(c);
					}
				}
				if (children.size() > 0) {
					if (children.size() == 1) {
						return children.get(0);
					}
					node.removeAllChildren(false);
					for (Node child : children) {
						node.addChild(child);
					}
					return node;
				}
				found++;
				node.removeAllChildren(false);
				taxa.remove(node.getID());
				return node;
			}
			for (Node child : node.getChildren()) {
				Node c = filterTaxa(child, taxa);
				if (c != null) {
					children.add(c);
				}
			}
			if (children.size() == 0) {
				return null;
			}
			if (children.size() == 1) {
				return children.get(0);
			}
			node.removeAllChildren(false);
			for (Node child : children) {
				node.addChild(child);
			}
			return node;
		}
	}

	public static void main(String[] args) throws Exception {
		new Application(new TreeGrafter(), "Tree Grafter", args);
	}

}
