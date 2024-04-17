package babel.tools.dplace;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import babel.tools.TreeCombiner;
import beastfx.app.inputeditor.BeautiDoc;
import beastfx.app.treeannotator.TreeAnnotator;
import beastfx.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
import beastfx.app.tools.Application;
import beastfx.app.util.TreeFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;
import beast.base.util.Randomizer;
//import beast.base.evolution.tree.TreeParser;

@Description("Grafts nodes into a tree above the MRCA of a set of nodes")
public class TreeGrafter extends TreeCombiner {
	final public Input<File> cfgFileInput = new Input<>("cfg", "tab separated configuration file containing three columns: "
			+ "column 1: name of taxon\n"
			+ "column 2: height (age) of taxon -- a range for the insertion age can be specified by adding the lower and upper bound with commas in between (i.e. \"<tip age>,<lower parent age>,<upper parent age>\")\n"
			+ "column 3: a comma separated list of taxa determining MRCA to graft above in source tree (if no constraints have been specified)."
			+ "If lists of taxa are separated by a bar \"|\" instead of using the MRCA of all taxa, a taxon is randomly selected above the "
			+ "MRCA of the individual sets separated by bars, and below the MRCA of all of these sets.");
	final public Input<TreeFile> constraintsFileInput = new Input<>("constraints", "newick tree file with constraints on where to insert leaf nodes");
	final public Input<Double> minTimeInput = new Input<>("minTime", "minimum time before sample time of grafted taxon.", 0.0);
	final public Input<Long> seedInput = new Input<>("seed", "random number seed -- ignored if 0.", 0L);

	
	String [] taxonName;
	double [] taxonHeight;
	double [] taxonParentLowerHeight;
	double [] taxonParentUpperHeight;
	boolean [] needsRefresh;
	double minTime;
	Set<String> [] subTaxonSets;
	Set<String> [][] subTaxonSets2;
	
	// for debugging:
	int found = 0;
	
	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		if (seedInput.get() != 0) {
			Randomizer.setSeed(seedInput.get());
		}
		minTime = minTimeInput.get();
		MemoryFriendlyTreeSet srcTreeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(srcInput.get().getPath(), 0);
		srcTreeSet.reset();
		Tree tree = srcTreeSet.next();
		
		Set<String> taxa = new LinkedHashSet<>();
		for (String taxon : tree.getTaxaNames()) {
			taxa.add(taxon);
		}
		processCfgFile(taxa);
		
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}

		srcTreeSet.reset();
		int n = taxonName.length;
		int k = 0;
		long start = System.currentTimeMillis();
		while (srcTreeSet.hasNext()) {
			tree = srcTreeSet.next();
			System.err.println("Processing tree " + (k++) + " at " + (System.currentTimeMillis()-start)/1000 + " seconds");
			for (int i = 0; i < n; i++) {
				if (needsRefresh[i]) {
					String newick = tree.getRoot().toNewick();
					tree = new TreeParser(newick);
				}
				Node src = null;
				if (subTaxonSets2[i] == null) {
					src = getMRCA(tree, subTaxonSets[i]);
				} else {
					src = getRandomNodeAbove(tree, subTaxonSets2[i]);
				}
				Node parent = src.getParent();
				if (parent == null) {
					int h  = 3;
					h++;
					src = getMRCA(tree, subTaxonSets[i]);
				}
				double len = parent.getHeight() - minTime;
				if (len < 0) {
					len = 0;
				}
				 
				// create intermediary node on branch
 				double heightLower = Math.max(src.getHeight() + minTime, taxonParentLowerHeight[i]);
				double heightUpper = Math.min(src.getHeight()+len, taxonParentUpperHeight[i]);
				
				double newHeight = heightLower + Randomizer.nextDouble() * (heightUpper - heightLower);
				if (src.getHeight() + minTime + len > taxonHeight[i]) {
					while (newHeight <= taxonHeight[i]) {
						newHeight = heightLower + Randomizer.nextDouble() * (heightUpper - heightLower);
					}
				} else {
					newHeight = src.getHeight() + len;
				}
				if (Double.isNaN(newHeight)) {
					System.out.println("NaN tree height found -- fixing it to " + src.getHeight());
					newHeight = src.getHeight();
				}
				
//				double newHeight = src.getHeight() + minTime + Randomizer.nextDouble() * len;
//				if (src.getHeight() + minTime + len > taxonHeight[i]) {
//					while (newHeight <= taxonHeight[i]) {
//						newHeight = src.getHeight() + minTime + Randomizer.nextDouble() * len;
//					}
//				} else {
//					newHeight = src.getHeight() + len;
//				}
				
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
				leaf.setHeight(taxonHeight[i]);
				newNode.addChild(leaf);
				leaf.setParent(newNode);
				Log.warning("Adding " + taxonName[i]);				
				
				// make sure no negative branches are introduced
				while (newNode.getHeight() > newNode.getParent().getHeight()) {
					newNode.getParent().setHeight(newNode.getHeight() + 1e-10);
					newNode = newNode.getParent();
				}
			}
			out.print(tree.getRoot().toNewick());
			out.println(";");
		}
		 
		Log.err("Done!");
		out.close();
	}

	private Node getRandomNodeAbove(Tree tree, Set<String>[] sets) {
		// find MRCA of each individual set
		List<Node> mrcas = new ArrayList<>();
		for (Set<String> set : sets) {
			mrcas.add(getMRCA(tree, set));
		}
		
		// find MRCA of all sets, starting at MRCA of each individual set (i.e. excluding nodes below MRCAs)
        nodesTraversed = new boolean[tree.getRoot().getAllChildNodesAndSelf().size()];
        nseen = 0;
        Node cur = mrcas.get(0);
        for (int k = 1; k < mrcas.size(); ++k) {
            cur = getCommonAncestor(cur, mrcas.get(k));
        }
        
        // collect nodes from MRCAs of each individual set to MRCA of all sets
        List<Node> candidates = new ArrayList<>();
        for (int i = 0; i < nodesTraversed.length; i++) {
        	if (nodesTraversed[i] && i != cur.getNr()) {
        		candidates.add(tree.getNode(i));
        	}
        }
        
        int k = Randomizer.nextInt(candidates.size());
        
        return candidates.get(k);
	}

	private void processCfgFile(Set<String> taxa) throws IOException {
		boolean hasConstraints = constraintsFileInput.get() != null && !constraintsFileInput.get().getName().equals("[[none]]");
		
		String cfg = BeautiDoc.load(cfgFileInput.get());
		String [] strs = cfg.split("\n");
		int n = 0;
		for (String str : strs) {
			if (!(str.startsWith("#") || str.matches("^\\s*$"))) {
				n++;
			}
		}
		subTaxonSets = new Set[n];
		subTaxonSets2 = new Set[n][];
		taxonName = new String[n];
		taxonHeight = new double[n];
		taxonParentLowerHeight = new double[n];
		Arrays.fill(taxonParentLowerHeight, Double.NEGATIVE_INFINITY);
		taxonParentUpperHeight = new double[n];
		Arrays.fill(taxonParentUpperHeight, Double.POSITIVE_INFINITY);
		int i = 0;
		for (String str : strs) {
			if (!(str.startsWith("#") || str.matches("^\\s*$"))) {
				String [] strs2 = str.split("\t");
				taxonName[i] = strs2[0];
				if (strs2.length > 1) {
					String str3 = strs2[1];
					if (str3.contains(",")) {
						String [] strs3 = str3.split(",");
						taxonHeight[i] = Double.parseDouble(strs3[0]);
						taxonParentLowerHeight[i] = Double.parseDouble(strs3[1]);
						taxonParentUpperHeight[i] = Double.parseDouble(strs3[2]);
					} else {
						taxonHeight[i] = Double.parseDouble(strs2[1]);
					}
				} else {
					taxonHeight[i] = 0.0;
				}
				subTaxonSets[i] = new HashSet<>();
				if (strs2[2].indexOf('|') == -1) {
					if (!hasConstraints) {
						for (String taxon : strs2[2].split(",")) {
							subTaxonSets[i].add(taxon);					
						}
					} else {
						taxa.add(taxonName[i]);
					}
				} else {
					String [] strs3 = strs2[2].split("\\|");
					subTaxonSets2[i] = new Set[strs3.length];
					for (int j = 0; j < strs3.length; j++) {
						subTaxonSets2[i][j] = new HashSet<>();
						for (String taxon : strs3[j].split(",")) {
							subTaxonSets2[i][j].add(taxon);	
							subTaxonSets[i].add(taxon);
						}
					}
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
		
		
		needsRefresh = new boolean[n];
		for (i = 1; i < n-1; i++) {
			Set<String> taxa2 = new HashSet<>();
			for (String s : subTaxonSets[i]) {
				taxa2.add(s);
			}
			for (int j = 0; j < i; j++) {
				if (taxa2.contains(taxonName[j])) {
					needsRefresh[i] = true;
				}
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
