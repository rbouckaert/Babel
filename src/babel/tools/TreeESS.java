package babel.tools;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import babel.tools.utils.MemoryFriendlyTreeSet;
import beastfx.app.inputeditor.BeautiDoc;
import beastfx.app.treeannotator.CladeSystem;
import beastfx.app.treeannotator.CladeSystem.Clade;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.inference.Runnable;
import beast.base.core.Citation;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beastlabs.evolution.tree.RNNIMetric;
import beast.base.evolution.tree.Tree;

@Description("Estimate ESS for a tree posterior sample (as produced by BEAST)")
@Citation(value="Lanfear R, Hua X, Warren DL. Estimating the effective sample size of tree topologies from Bayesian phylogenetic analyses. Genome Biology and Evolution. 2016 Aug 1;8(8):2319-32.", year=2016, DOI="10.1093/gbe/evw171")
public class TreeESS extends Runnable {
	final public Input<TreeFile> focalTreeInput = new Input<>("focalTree", "focal tree file with tree in NEXUS or Newick format. "
			+ "This is useful for comparing traces between different runs."
			+ "If not specified, the first tree after burnin is used as focal tree", new TreeFile("[[none]]"));
	
	final public Input<List<TreeFile>> srcInput = new Input<>("tree", "1 or more source tree files", new ArrayList<>());
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);
	final public Input<OutFile> traceInput = new Input<>("trace", "trace output file that can be processed in Tracer. Not produced if not specified.",
			new OutFile("[[none]]"));
	final public Input<Boolean> logCladesInput = new Input<>("logClades", "add 0/1 logs for clades in the focalTree being present in tree file", false);
	final public Input<Double> cladeSupportThresholdInput = new Input<>("cladeSupportThreshold", "if positive, use all clades with support above threshold instead of those from a focal tree", -1.0);

	final public Input<OutFile> cladeSetOutputInput = new Input<>("cladeSetOutput", "output file with clade information. Not produced if not specified.",
			new OutFile("[[none]]"));
	final public Input<File> cladeSetInputInput = new Input<>("cladeSetInput", "source clades from file save via cladeSetOutput", new File("[[none]]"));

	Set<BitSet> focalClades;
	BitSet[] focalCladeArray;
	List<List<?>> traces;
	Tree focalTree;
	boolean logClades;
	String [] taxa;

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		focalClades = new HashSet<>();
		traces = new ArrayList<>();
		List<List<Double>> cladeTraces = null;
		logClades = logCladesInput.get();
		
		// determine clades to be considered (if logged)
		if (logClades) {
			if (cladeSetInputInput.get() != null && !cladeSetInputInput.get().getName().equals("[[none]]")) {
				loadClades();
			} else if (cladeSupportThresholdInput.get() > 0) {
				if (focalTreeInput.get() != null &&
						!focalTreeInput.get().getName().equals("[[none]]")) {
					throw new IllegalArgumentException("Either focal tree, or cladeSupportThreshold should be specified, not both");
				}
				findCladesAboveThreshold();
			} else if (focalTreeInput.get() != null && !focalTreeInput.get().getName().equals("[[none]]")) {
				// get external focal tree, if any
				processFocalTree();
			} else {
				// first tree after burnin as focal tree
				setFocalTreeFromTreeSet();
			}
		} else if (focalTreeInput.get() != null && !focalTreeInput.get().getName().equals("[[none]]")) {
			// get external focal tree, if any
			processFocalTree();
		} else {
			// first tree after burnin as focal tree
			setFocalTreeFromTreeSet();
		}
		
		// process tree files
		for (TreeFile f : srcInput.get()) {
			List<Double> traceRF = new ArrayList<>();
			List<Double> traceRNNI = new ArrayList<>();
			List<Double> traceNNI = new ArrayList<>();
			List<Double> traceRankSwitches = new ArrayList<>();
			List<Double> traceNNI2 = new ArrayList<>();
			List<Double> traceRankSwitches2 = new ArrayList<>();
			traces.add(traceRF);
			traces.add(traceRNNI);
			traces.add(traceNNI);
			traces.add(traceRankSwitches);
			traces.add(traceNNI2);
			traces.add(traceRankSwitches2);
			if (logClades) {
				cladeTraces = new ArrayList<>();
				int n = focalCladeArray.length;
//				if (focalTreeInput.get() != null) {
//					n = focalTree.getInternalNodeCount();
//				} else {
//					MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(f.getPath(), burnInPercentageInput.get());
//					srcTreeSet.reset();
//					Tree tree = srcTreeSet.next();
//					n = tree.getInternalNodeCount();
//				}
				for (int i = 0; i < n; i++) {
					List<Double> cladeTrace = new ArrayList<>();
					cladeTraces.add(cladeTrace);
					traces.add(cladeTrace);
				}
			}
			processTreeFile(f, traceRF, traceRNNI, traceNNI, traceRankSwitches, traceNNI2, traceRankSwitches2, cladeTraces);
		}
		
		// save trace?
		if (traceInput.get() != null &&
				!traceInput.get().getName().equals("[[none]]")) {
			saveTrace();
		}
		
		Log.warning("Done");
	}
	

	private void loadClades() throws IOException {
		String [] strs = BeautiDoc.load(cladeSetInputInput.get()).split("\n");
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(srcInput.get().get(0).getPath(), burnInPercentageInput.get());
		srcTreeSet.reset();
		String [] taxa = srcTreeSet.next().getTaxaNames();
				

		List<BitSet> focalClades = new ArrayList<>();

		for (String str : strs) {
			BitSet clade = new BitSet();
			for (String taxon : str.split("\\s+")) {
				int i = indexOf(taxon, taxa);
				clade.set(i);
			}
			focalClades.add(clade);
		}
		focalCladeArray = focalClades.toArray(new BitSet[]{});				
		
		printClades(taxa);
	}

	private int indexOf(String taxon, String[] taxa) {
		for (int i = 0; i < taxa.length; i++) {
			if (taxa[i].equals(taxon)) {
				return i;
			}
		}
		throw new IllegalArgumentException("Taxon " + taxon + " could not be foudn in tree set");
	}

	private void findCladesAboveThreshold() throws IOException {
		double threshold = cladeSupportThresholdInput.get();
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(srcInput.get().get(0).getPath(), burnInPercentageInput.get());
		srcTreeSet.reset();
		CladeSystem clades = new CladeSystem();
		int k = 0;
		while (srcTreeSet.hasNext()) {
			clades.add(srcTreeSet.next(), false);
			k++;
		}
		
		List<BitSet> focalClades = new ArrayList<>();
		for (BitSet bits : clades.getCladeMap().keySet()) {
			Clade clade = clades.getCladeMap().get(bits);
			if (clade.getCount() >= threshold * k && // add clades with required level of support
					clade.getCount() != k) {         // but don't add 100% clades
				BitSet halfset = new BitSet();
				for (int i = 0; i < bits.length(); i += 2) {
					if (bits.get(i)) {
						halfset.set(i/2);
					}
				}
				focalClades.add(halfset);
			}
		}
		focalCladeArray = focalClades.toArray(new BitSet[]{});		

		srcTreeSet.reset();
		printClades(srcTreeSet.next().getTaxaNames());
	}

	private void processTreeFile(TreeFile f, List<Double> traceRF, List<Double> traceRNNI,
			List<Double> traceNNI, List<Double> traceRankSwitches,
			List<Double> traceNNI2, List<Double> traceRankSwitches2,
			List<List<Double>> cladeTraces) throws IOException {
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(f.getPath(), burnInPercentageInput.get());
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
			if (cladeSupportThresholdInput.get() < 0 && 
					!(cladeSetInputInput.get() != null && !cladeSetInputInput.get().getName().equals("[[none]]"))) {
				focalCladeArray = focalClades.toArray(new BitSet[]{});
			}
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
			RFDistance(tree.getRoot(), distance, map, cladeTraces);
			traceRF.add(distance[0]);
			Double [] d = RNNIDistance(focalTree, tree);
			traceRNNI.add(d[0]);
			traceNNI.add(d[1]);
			traceRankSwitches.add(d[0] - d[1]);
			d = RNNIDistance(tree, focalTree);
			traceNNI2.add(d[1]);
			traceRankSwitches2.add(d[0] - d[1]);

			if (logClades) {
				for (int i = 0; i < focalCladeArray.length; i++) {
					if (cladeTraces.get(i).size() != 1) {
						cladeTraces.get(i).add(0.0);
					}
				}
			}
		}
		
		while (srcTreeSet.hasNext()) {
			Tree tree = srcTreeSet.next();
			double [] distance = new double[1]; 
			RFDistance(tree.getRoot(), distance, map, cladeTraces);
			traceRF.add(distance[0]);
			Double [] d = RNNIDistance(focalTree, tree);
			traceRNNI.add(d[0]);
			traceNNI.add(d[1]);
			traceRankSwitches.add(d[0] - d[1]);
			d = RNNIDistance(tree, focalTree);
			traceNNI2.add(d[1]);
			traceRankSwitches2.add(d[0] - d[1]);
			
			if (logClades) {
				int n = traceRF.size();
				for (int i = 0; i < focalCladeArray.length; i++) {
					if (cladeTraces.get(i).size() != n) {
						cladeTraces.get(i).add(0.0);
					}
				}
			}
		}
		
		double ESSRF = beast.base.inference.util.ESS.calcESS(traceRF);
		Log.info("ESS(" + srcInput.getName() + "-RF) = " + ESSRF);
		double ESSRNNI = beast.base.inference.util.ESS.calcESS(traceRNNI);
		Log.info("ESS(" + srcInput.getName() + "-RNNI) = " + ESSRNNI);
		
		double ESSNNI = beast.base.inference.util.ESS.calcESS(traceNNI);
		Log.info("ESS(" + srcInput.getName() + "-NNI) = " + ESSNNI);
		double ESSRankSwitches = beast.base.inference.util.ESS.calcESS(traceRankSwitches);
		Log.info("ESS(" + srcInput.getName() + "-RankSwitches) = " + ESSRankSwitches);

		double ESSNNI2 = beast.base.inference.util.ESS.calcESS(traceNNI2);
		Log.info("ESS(" + srcInput.getName() + "-NNI-backward) = " + ESSNNI2);
		double ESSRankSwitches2 = beast.base.inference.util.ESS.calcESS(traceRankSwitches2);
		Log.info("ESS(" + srcInput.getName() + "-RankSwitches-backward) = " + ESSRankSwitches2);
		if (logClades) {
			double sum = 0;
			double min = Double.MAX_VALUE;
			int k = 0;
			Log.warning.print("Clade ESSs: ");
			for (int i = 0; i < cladeTraces.size(); i++) {
				double ESS = beast.base.inference.util.ESS.calcESS(cladeTraces.get(i));
				Log.warning.print(" " + ESS);
				if (Double.isFinite(ESS)) {
					sum += ESS;
					k++;
					min = Math.min(min,ESS);
				}
			}
			Log.warning.print("\n");
			sum /= k;
			Log.info("mean clade ESS(" + srcInput.getName() + ") = " + sum);
			Log.info("minimum clade ESS(" + srcInput.getName() + ") = " + min);

		
			sum = 0;
			min = Double.MAX_VALUE;
			k = 0;
			for (int i = 0; i < cladeTraces.size(); i++) {
				double entropy = calcEntropy(cladeTraces.get(i));
				Log.warning.print(" " + entropy);
				if (Double.isFinite(entropy)) {
					sum += entropy;
					k++;
					min = Math.min(min,entropy);
				}
			}
			Log.warning.print("\n");
			sum /= k;
			Log.info("mean clade entropy(" + srcInput.getName() + ") = " + sum);
			Log.info("minimum clade entropy(" + srcInput.getName() + ") = " + min);
		}
	}
	
	private Double [] RNNIDistance(Tree tree1, Tree tree2) {
		if (tree1.getRoot().getNr() == 0) {
			renumberInternal(tree1.getRoot(), new int[]{tree1.getLeafNodeCount()});
		}
		if (tree2.getRoot().getNr() == 0) {
			renumberInternal(tree2.getRoot(), new int[]{tree2.getLeafNodeCount()});
		}
		RNNIMetric metric = new RNNIMetric(tree1.getTaxaNames());
		return new Double[]{metric.distance(tree1, tree2), (double) metric.getNNICount()};
	}

	private double calcEntropy(List<Double> list) {
	    int p = 0, observedFlips = 0, n = list.size();
	    if (list.get(0) > 0.5) {
	    	p++;
	    }
		for (int i = 1; i < list.size(); i++) {
			boolean prev = list.get(i-1) > 0.5;
			boolean cur  = list.get(i) > 0.5;
			if (cur) {
				p++;
			}
			if (prev != cur) {
				observedFlips++;
			}
		}

		int q = p;
		if (q > n/2) {
			q = n - q;
		}
		
		double maxFlips = 2*q;
		
		Log.warning.print("\n" + ((p+0.0)/n)+ " ");
		return (n * observedFlips / maxFlips)/ ((double)(n-q)/n); 
	}
	
	private double calcEntropyx(List<Double> list) {
	    StringBuilder b = new StringBuilder();
	    int p = 0;
		for (Double d : list) {
			if (d > 0.5) {
				b.append('1');
				p++;
			} else {
				b.append('0');    
			}
		}
			
	    byte [] bytes = b.toString().getBytes();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    
	    try {
            Deflater decompressor = new Deflater();
            DeflaterOutputStream inflaterOutputStream = new DeflaterOutputStream(baos, decompressor);
            inflaterOutputStream.write(bytes);
            inflaterOutputStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
	    
	    double n = b.toString().length();
	    double expected = -(p) * Math.log(p/n) - (n-p)*Math.log((n-p)/n);	    
        double obtained = 8*baos.toByteArray().length;
        
        double diff = n * obtained/expected;
        return diff;
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
	private BitSet RFDistance(Node node, double [] distance, int [] map, List<List<Double>> cladeTraces) {
		if (node.isLeaf()) {
        	BitSet bitset = new BitSet();
        	bitset.set(map[node.getNr()]);
            return bitset;
		}
        BitSet left = RFDistance(node.getLeft(), distance, map, cladeTraces);
        BitSet right = RFDistance(node.getRight(), distance, map, cladeTraces);
        BitSet clade = new BitSet();
        clade.or(left);
        clade.or(right);
		if (focalClades.contains(clade)) {
			distance[0] += 2;
		}
		if (logClades) {
			int index = indexOf(clade);
			if (index >= 0 && index < cladeTraces.size()) {
				cladeTraces.get(index).add(1.0);
			}
		}
		return clade;
	}

	private int indexOf(BitSet clade) {
		for (int i = 0; i < focalCladeArray.length; i++) {
			if (focalCladeArray[i].equals(clade)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * save entries as tab separated file, which can be used in Tracer
	 */
	private void saveTrace() throws IOException {
		PrintStream out = new PrintStream(traceInput.get());
		
		// header
		out.print("Sample\t");
		for (TreeFile f : srcInput.get()) {
			out.print(f.getName() + "-RF\t");
			out.print(f.getName() + "-RNNI\t");
			out.print(f.getName() + "-NNI\t");
			out.print(f.getName() + "-RankSwitches\t");
			out.print(f.getName() + "-NNI-backward\t");
			out.print(f.getName() + "-RankSwitches-backward\t");
			if (logClades) {
				String prefix = f.getName();
				if (prefix.contains(".")) {
					prefix = prefix.substring(0, prefix.indexOf('.'));
				}
				for (int i = 0; i < focalCladeArray.length; i++) {
					out.print(prefix + '.' + getCladeName(focalCladeArray[i], taxa) + '\t');
				}
			}
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

	private String getCladeName(BitSet bitSet, String [] taxa) {
		String name = "";
		for (int i = 0; i < bitSet.length(); i++) {
			if (bitSet.get(i)) {
				name += taxa[i]+"_";
			}
		}
		// remove trailing underscore
		name = name.substring(0, name.length()-1);
		return name;
	}

	private void processFocalTree(MemoryFriendlyTreeSet srcTreeSet) throws IOException {
		srcTreeSet.reset();
		focalTree = srcTreeSet.next();
		renumberInternal(focalTree.getRoot(), new int[]{focalTree.getLeafNodeCount()});
		// sort(focalTree.getRoot());
		addFocalClades(focalTree.getRoot());
		focalCladeArray = focalClades.toArray(new BitSet[]{});
		printClades(focalTree.getTaxaNames());
	}

	private void processFocalTree() throws IOException {
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(focalTreeInput.get().getPath(), 0);
		processFocalTree(srcTreeSet);
	}

	private void setFocalTreeFromTreeSet() throws IOException {
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(srcInput.get().get(0).getPath(), burnInPercentageInput.get());
		processFocalTree(srcTreeSet);
	}

	
	
	private int renumberInternal(Node node, int[] nr) {
		for (Node child : node.getChildren()) {
			renumberInternal(child, nr);
		}
		if (!node.isLeaf()) {
			node.setNr(nr[0]);
			nr[0]++;
		}
		return nr[0];
	}
	
	private void printClades(String [] taxa) throws IOException {
		this.taxa = taxa;

		int k = 0;
		StringBuilder b = new StringBuilder();
		for (BitSet clade : focalCladeArray) {
			String set = "";
			for (int i = 0; i < clade.length(); i++) {
				if (clade.get(i)) {
					set += taxa[i]+" ";
				}
			}
			if (k != focalCladeArray.length) {
				System.err.println(++k + ":" + set);
				b.append(set);
				b.append('\n');
			}
		}
		
		if (cladeSetOutputInput.get() != null && !cladeSetOutputInput.get().getName().equals("[[none]]")) {
			PrintStream out = new PrintStream(cladeSetOutputInput.get());
			out.print(b.toString());
			out.close();
		}
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
