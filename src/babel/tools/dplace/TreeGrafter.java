package babel.tools.dplace;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
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

@Description("Grafts nodes into a tree above the MRCA of a set of nodes")
public class TreeGrafter extends TreeCombiner {
	final public Input<File> cgfFileInput = new Input<>("cfg", "tab separated configuration file containing three columns: "
			+ "column 1: name of taxon\n"
			+ "column 2: height (age) of taxon\n"
			+ "column 3: a comma separated list of taxa determining MRCA to graft above in source tree.");
	
	String [] taxonName;
	double [] taxonHeight;
	Set<String> [] subTaxonSets;
	
	
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
			Log.warning("Writing to file " + outputInput.get().getName());
			out = new PrintStream(outputInput.get());
		}

		processCfgFile();
		
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
				leaf.setHeight(taxonHeight[i]);
				newNode.addChild(leaf);
				leaf.setParent(newNode);
			}
			out.print(tree.getRoot().toNewick());
		}
		 
		Log.err("Done!");
		out.close();
	}

	private void processCfgFile() throws IOException {
		String cfg = BeautiDoc.load(cgfFileInput.get());
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
				taxonName[i] = strs[0];
				taxonHeight[i] = Double.parseDouble(strs[1]);
				subTaxonSets[i] = new HashSet<>();
				for (String taxon : strs2[2].split(",")) {
					subTaxonSets[i].add(taxon);					
				}
			}
		}
	}

	public static void main(String[] args) throws Exception {
		new Application(new TreeGrafter(), "Tree Grafter", args);
	}

}
