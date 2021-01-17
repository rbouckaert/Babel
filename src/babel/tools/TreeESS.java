package babel.tools;


import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import beast.app.treeannotator.TreeAnnotator;
import beast.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.*;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;

@Description("Estimate ESS for a tree posterior sample (as produced by BEAST)")
@Citation(value="Lanfear R, Hua X, Warren DL. Estimating the effective sample size of tree topologies from Bayesian phylogenetic analyses. Genome Biology and Evolution. 2016 Aug 1;8(8):2319-32.", year=2016, DOI="10.1093/gbe/evw171")
public class TreeESS extends Runnable {
	final public Input<TreeFile> focalTreeInput = new Input<>("focalTree", "focal tree file with tree in NEXUS or Newick format. "
			+ "This is useful for comparing traces between different runs."
			+ "If not specified, the first tree after burnin is used as focal tree");
	
	final public Input<List<TreeFile>> srcInput = new Input<>("tree","1 or more source tree files", new ArrayList<>());
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);
	final public Input<OutFile> traceInput = new Input<>("trace", "trace output file that can be processed in Tracer. Not produced if not specified.",
			new OutFile("[[none]]"));

	Set<BitSet> focalClades;
	List<List<Double>> traces;
	Tree focalTree;

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		focalClades = new HashSet<>();
		traces = new ArrayList<>();
		
		// get external focal tree, if any
		if (focalTreeInput.get() != null &&
				!focalTreeInput.get().getName().equals("[[none]]")) {
			processFocalTree();
		}

		// process tree files
		for (TreeFile f : srcInput.get()) {
			List<Double> trace = new ArrayList<>();
			traces.add(trace);
			processTreeFile(f, trace);
		}
		
		// save trace?
		if (traceInput.get() != null &&
				!traceInput.get().getName().equals("[[none]]")) {
			saveTrace();
		}
		
		Log.warning("Done");
	}
	
	private void processTreeFile(TreeFile f, List<Double> trace) throws IOException {
		MemoryFriendlyTreeSet srcTreeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(f.getPath(), burnInPercentageInput.get());
		srcTreeSet.reset();
		int [] map;
		
		// do we need to update the focal tree set?
		if (!(focalTreeInput.get() != null && !focalTreeInput.get().getName().equals("[[none]]"))) {
			if (srcInput.get().size()  > 1) {
				Log.warning("Warning: to make traces comparible, it is recommended to use a single external focal tree for multiple tree sets");
			}
			focalTree = srcTreeSet.next();
			// sort(focalTree.getRoot());
			focalClades = new HashSet<>();
			addFocalClades(focalTree.getRoot());
			// probably not useful to add first tree after burn-in to
			// trace, since the RF distance will be 0
			// trace.add(0.0);
			map = new int[focalTree.getLeafNodeCount()];
			for (int i = 0; i < map.length; i++) {
				map[i] = i;
			}
		} else {
			// set up mapping of taxon names in this tree set to that of the focal tree
			Tree tree = srcTreeSet.next();
			map = new int[focalTree.getLeafNodeCount()];
			String [] focalTaxa = focalTree.getTaxaNames();
			for (int i = 0; i < map.length; i++) {
				String name = tree.getTaxaNames()[i];
				map[i] = indexOf(focalTaxa, name);
			}
			double [] distance = new double[1]; 
			RFDistance(tree.getRoot(), distance, map);
			trace.add(distance[0]);
		}
		
		while (srcTreeSet.hasNext()) {
			Tree tree = srcTreeSet.next();
			double [] distance = new double[1]; 
			RFDistance(tree.getRoot(), distance, map);
			trace.add(distance[0]);
		}
		
		double ESS = beast.core.util.ESS.calcESS(trace);
		Log.info("ESS(" + srcInput.getName() + ") = " + ESS);
	}
	
	private int indexOf(String[] focalTaxa, String name) {
		for (int i = 0; i < focalTaxa.length; i++) {
			if (focalTaxa[i].equals(name)) {
				return i;
			}
		}
		throw new IllegalArgumentException("Tree set incompatible with focal tree: could not find taxon " + name + " in focal tree");
	}

	/** calculates Robinson Faulds distance to focal tree **/
	private BitSet RFDistance(Node node, double [] distance, int [] map) {
		if (node.isLeaf()) {
        	BitSet bitset = new BitSet();
        	bitset.set(map[node.getNr()]);
            return bitset;
		}
        BitSet left = RFDistance(node.getLeft(), distance, map);
        BitSet right = RFDistance(node.getRight(), distance, map);
        BitSet clade = new BitSet();
        clade.or(left);
        clade.or(right);
		if (focalClades.contains(clade)) {
			distance[0] += 2;
		}
		return clade;
	}

	/**
	 * save entries as tab separated file, which can be used in Tracer
	 */
	private void saveTrace() throws IOException {
		PrintStream out = new PrintStream(traceInput.get());
		
		// header
		out.print("Sample\t");
		for (TreeFile f : srcInput.get()) {
			out.print(f.getName() + "\t");
		}
		out.println();
		
		// tab separated data
		int min = Integer.MAX_VALUE;
		for (int j = 0; j < traces.size(); j++) {
			min = Math.min(traces.get(j).size(), min);
		}
		
		for (int i = 0; i < min; i++) {
			out.print(i + "\t");
			for (int j = 0; j < traces.size(); j++) {
				out.print(traces.get(j).get(i) + "\t");
			}
			out.println();
		}
		out.close();
		
		// sanity check
		for (int j = 0; j < traces.size(); j++) {
			if (traces.get(j).size() > min) {
				Log.warning.println("Warning: trace only contains entries for the shortest tree set. " + 
						(traces.get(j).size() - min) + " entries missing for " + srcInput.get().get(j).getPath());
			}
		}

	}

	private void processFocalTree() throws IOException {
		MemoryFriendlyTreeSet srcTreeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(focalTreeInput.get().getPath(), 0);
		srcTreeSet.reset();
		focalTree = srcTreeSet.next();
		// sort(focalTree.getRoot());
		addFocalClades(focalTree.getRoot());
	}
	
	
	
	/** add clades to focalClade set as comma separted Strings of taxon names **/
	private BitSet addFocalClades(Node node) {
        if (node.isLeaf()) {
        	BitSet bitset = new BitSet();
        	bitset.set(node.getNr());
            return bitset;
        }
		
        BitSet left = addFocalClades(node.getLeft());
        BitSet right = addFocalClades(node.getRight());
        BitSet clade = new BitSet();
        clade.or(left);
        clade.or(right);
        focalClades.add(clade);
        return clade;
	}

//	/** sorts tree on taxon names **/
//	private String sort(Node node) {
//        if (node.isLeaf()) {
//            return node.getID();
//        }
//
//        if (node.getChildCount() != 2) {
//        	throw new IllegalArgumentException("Expected binary tree, but found node with " +node.getChildCount() + " children");
//        }
//        
//        String left = sort(node.getLeft());
//        String right = sort(node.getRight());
//        
//        if (left.compareTo(right) < 0) {
//        	Node left_ = node.getLeft();
//        	Node right_ = node.getRight();
//        	node.removeAllChildren(false);
//        	node.addChild(right_);
//        	node.addChild(left_);
//        	return right;
//        }
//        return left;
//
//    } // sort
	
	public static void main(String[] args) throws Exception {
		new Application(new TreeESS(), "Tree set ESS", args);		
	}

}
