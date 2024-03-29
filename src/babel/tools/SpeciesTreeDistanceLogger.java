package babel.tools;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import beastfx.app.inputeditor.BeautiDoc;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beastlabs.evolution.tree.RNNIMetric;
import beastlabs.evolution.tree.RobinsonsFouldMetric;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeMetric;
import beast.base.evolution.tree.TreeParser;
import beast.base.parser.NexusParser;

@Description("Create trace log of tree distances between species tree and its gene trees. "
		+ "Usefull to judge how much gene trees differ from species trees for multi species coalescent analyses.")
public class SpeciesTreeDistanceLogger extends Runnable {
	final public Input<TreeFile> speciesTreeInput = new Input<>("speciesTree", "species trees file with tree in NEXUS format");
	final public Input<List<TreeFile>> srcInput = new Input<>("tree", "1 or more gene tree files in NEXUS format", new ArrayList<>());
	final public Input<File> mapInput = new Input<>("map", "tab delimted file containing two columns that map gene tree taxa (first column) to species tree taxa (second column).");
	final public Input<OutFile> outputInput = new Input<>("output", "trace output file that can be processed in Tracer. Not produced if not specified.");
	final public Input<Boolean> useRNNIInput = new Input<>("useRNNI", "flag to indicate to use RNNI if true, Robinson Faulds if false", true);
	
	private List<List<?>> traces = new ArrayList<>();
	// maps taxon names from gene trees to that of species trees
	private Map<String, String> map = new HashMap<>();
	
	private boolean useRNNI = true;

	@Override
	public void initAndValidate() {
	}
	
	@Override
	public void run() throws Exception {
		// sanity checks
		if (outputInput.get() == null) {
			throw new IllegalArgumentException("output must be specified");
		}
		if (speciesTreeInput.get() == null) {
			throw new IllegalArgumentException("speciesTree must be specified");
		}
		if (srcInput.get().size() == 0) {
			throw new IllegalArgumentException("tree must be specified");
		}
		useRNNI = useRNNIInput.get();
		
		if (mapInput.get() != null) {
			for (String str : BeautiDoc.load(mapInput.get()).split("\n")) {
				String [] strs = str.split("\t");
				if (strs.length >= 2) {
					map.put(strs[0].trim(), strs[1].trim());
				} else {
					Log.warning("Ignoring this line from " + mapInput.get().getName() + " :" + str);
				}
			}
		}

		// load species trees
		NexusParser speciesTreeParser = new NexusParser();
		speciesTreeParser.parseFile(speciesTreeInput.get());
		List<Tree> speciesTrees = speciesTreeParser.trees;
		
		// process gene trees
		for (TreeFile f : srcInput.get()) {
			List<Double> distances = processTree(f, speciesTrees);
			traces.add(distances);
		}
		
		saveTrace();
		
		Log.warning("Done!");
	}

	
	private List<Double> processTree(TreeFile f, List<Tree> speciesTrees) throws IOException {
		List<Double> distances = new ArrayList<>();

		NexusParser geneTreeParser = new NexusParser();
		geneTreeParser.parseFile(f);
		List<Tree> geneTrees = geneTreeParser.trees;

		Set<String> missingTaxa = getMissingTaxa(geneTrees.get(0));
		
		for (int k = 0; k < speciesTrees.size() && k < geneTrees.size(); k++) {
			Tree speciesTree = speciesTrees.get(k);
			if (missingTaxa.size() > 0) {
				speciesTree = filterOutTaxa(speciesTree, missingTaxa);
			}
			Tree geneTree = geneTrees.get(k);
			normaliseLabels(geneTree);
			
			TreeMetric metric =
					useRNNI ?
					new RNNIMetric(speciesTree.getTaxaNames()):
					new RobinsonsFouldMetric(speciesTree.getTaxaNames());
			double d = metric.distance(speciesTree, geneTree);
			distances.add(d);
		}
		
		return distances;
	}
	
	private Tree filterOutTaxa(Tree speciesTree, Set<String> taxaToExclude) {
		Tree copy = speciesTree.copy();
		Node root = filter(copy.getRoot(), taxaToExclude);
		String newick = root.toNewick();
		TreeParser parser = new TreeParser(newick);
		return parser;
	}
	
	private Node filter(Node node, Set<String> taxaToExclude) {
		if (node.isLeaf()) {
			if (taxaToExclude.contains(node.getID())) {
				return null;
			} else {
				return node;
			}
		} else {
			Node left_ = node.getLeft(); 
			Node right_ = node.getRight(); 
			left_ = filter(left_, taxaToExclude);
			right_ = filter(right_, taxaToExclude);
			if (left_ == null && right_ == null) {
				return null;
			}
			if (left_ == null) {
				return right_;
			}
			if (right_ == null) {
				return left_;
			}
			node.removeAllChildren(false);
			node.addChild(left_);
			node.addChild(right_);
			return node;
		}
	}


	private Set<String> getMissingTaxa(Tree geneTree) {
		Node [] nodes = geneTree.getNodesAsArray();
		Set<String> taxaSeen = new HashSet<>();
		for (int i = 0; i < nodes.length/2+1; i++) {
			if (nodes[i].getID() != null) {
				if (map.containsKey(nodes[i].getID())) {
					taxaSeen.add(map.get(nodes[i].getID()));
				} else {
					throw new IllegalArgumentException("Could not find " + nodes[i].getID() + " in map");
				}
			}
		}
		
		Set<String> taxaMissed = new HashSet<>();
		taxaMissed.addAll(map.values()); 
		taxaMissed.removeAll(taxaSeen);
		return taxaMissed;
	}
	
	private void normaliseLabels(Tree geneTree) {
		Node [] nodes = geneTree.getNodesAsArray();
		for (int i = 0; i < nodes.length/2+1; i++) {
			if (nodes[i].getID() != null) {
				if (map.containsKey(nodes[i].getID())) {
					nodes[i].setID(map.get(nodes[i].getID()));
				} else {
					throw new IllegalArgumentException("Could not find " + nodes[i].getID() + " in map");
				}
			}
		}
	}

	/**
	 * save entries as tab separated file, which can be used in Tracer
	 */
	private void saveTrace() throws IOException {
		PrintStream out = new PrintStream(outputInput.get());
		
		// header
		out.print("Sample\t");
		for (TreeFile f : srcInput.get()) {
			String name = f.getName();
			out.print(name+"\t");
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
	}
	
	public static void main(String[] args) throws Exception {
		new Application(new SpeciesTreeDistanceLogger(), "SpeciesTreeDistanceLogger", args);
	}

}
