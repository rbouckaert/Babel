package babel.tools.dplace;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import babel.tools.TreeCombiner;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.util.Randomizer;
import beastfx.app.inputeditor.BeautiDoc;
import beastfx.app.tools.Application;
import beastfx.app.treeannotator.TreeAnnotator;
import beastfx.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;

@Description("Replace taxon in a tree with a subtree. Internal node heights will be randomised.")
public class TaxonReplacer extends TreeCombiner {
	final public Input<File> cfgFileInput = new Input<>("cfg", "tab separated configuration file containing two columns: "
			+ "first column is taxon name, second column is newick tree containing replacement taxa, "
			+ "or single taxa name if only one taxon name is used.");
	
	final public Input<File> timeFileInput = new Input<>("times", "file containing age of replacement taxa. "
			+ "Format is one item per line containing <replacement taxon name><tab><age>", new File("[[none]]"));
	
	String [] taxonName;
	double [] oldestTaxon;
	String [] replacementSets;
	Map<String,Double> replacementTaxonHeight;

	// minimum height difference between replacement nodes
	final static double EPSILON = 1e-4;
	
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
		int k = 0;
		while (srcTreeSet.hasNext()) {
			tree = srcTreeSet.next();
			Map<String, Node> taxonMap = new HashMap<>();
			Node [] nodes = tree.getNodesAsArray();
			for (int i= 0; i < tree.getLeafNodeCount(); i++) {
				taxonMap.put(nodes[i].getID(), nodes[i]);
			}
			
			System.err.println("Processing tree " + (k++));
			for (int i = 0; i < n; i++) {
				Node src = taxonMap.get(taxonName[i]);
				Node parent = src.getParent();
				if (replacementSets[i].startsWith("(")) {
					ISOTreeParser p = new ISOTreeParser();
					Node root = p.parse(replacementSets[i]);
					p.toRandomBinary(root);
					
					// make sure parent is old enough to fit tip dates
					Node node = parent;
					double limit = oldestTaxon[i];
					while (node.getHeight() <  limit) {
						node.setHeight(limit + EPSILON);
						limit = node.getHeight();
						node = node.getParent();
					}
					
					// choose root height in between parent and oldest tip
					root.setHeight(oldestTaxon[i] + (parent.getHeight() - oldestTaxon[i]) * Randomizer.nextDouble());
					// set internal node heights below subtree root
					traverse(root, root.getHeight());
					parent.removeChild(src);
					if (parent.getChildCount() != 1) {
						Log.warning("deleting child failed");
					}
					parent.addChild(root);
				} else {
					// simple replacement of taxon name and height
					String replacement = replacementSets[i];
					src.setID(replacement);
					src.setHeight(replacementTaxonHeight.get(replacement));
					while (src.getParent().getHeight() < src.getHeight()) {
						src.getParent().setHeight(src.getHeight() + EPSILON);
						src = src.getParent();
					}
				}
			}
			out.print(tree.getRoot().toNewick());
			out.println(";");
		}
		 
		Log.err("Done!");
		out.close();
	}

	private double traverse(Node node, double upper) {
		if (node.isLeaf()) {
			node.setHeight(replacementTaxonHeight.get(node.getID()));
			return node.getHeight();
		} else {
			double lower = traverse(node.getLeft(), upper);
			lower = Math.max(traverse(node.getRight(), upper), lower);
			
			double h = lower + Randomizer.nextDouble() * (upper - lower);
			node.setHeight(h);
			return h;
		}
	}

	private void processCfgFile(Set<String> taxa) throws IOException {
		// get taxon replacement information
		taxonName = new String[taxa.size()];
		replacementSets = new String[taxa.size()];
		String str = BeautiDoc.load(cfgFileInput.get());
		String [] strs = str.split("\n");
		Set<String> replacementTaxa = new HashSet<>();
		int i = 0;
		for (String str2 : strs) {
			String [] strs2 = str2.split("\t");
			String taxon = strs2[0].trim();
			if (taxa.contains(taxon)) {
				taxonName[i] = taxon;
				replacementSets[i] = strs2[1].trim();
				i++;
			} else {
				Log.warning("taxon found in " + cfgFileInput.get().getName() + "  that is not in tree");
			}
			// sanity check for duplicates replacement taxa
			String [] strs3 = strs2[1].trim().split("[\\(\\),]");
			for (String s : strs3) {
				if (s.trim().length() > 0) {
					if (replacementTaxa.contains(s)) {
						Log.warning("Duplicate replacement taxon " + s + " found");
					}
					replacementTaxa.add(s);
				}
			}
		}		
		
		// get timing information
		replacementTaxonHeight = new HashMap<>();
		str = BeautiDoc.load(timeFileInput.get());
		strs = str.split("\n");
		for (String str2 : strs) {
			String [] strs2 = str2.split("\t");
			String replacement = strs2[0].trim();
			if (replacementTaxa.contains(replacement)) {
				replacementTaxonHeight.put(replacement, Double.parseDouble(strs2[1].trim()));
			} else {
				Log.warning("mild warning: time for taxon " + replacement + " found that is not a replacement taxon and will not be used");
			}
		}
		
		oldestTaxon = new double[replacementSets.length];
		for (int j = 0; j < oldestTaxon.length; j++) {
			double age = 0;
			
			String [] strs3 = replacementSets[j].trim().split("[\\(\\),]");
			for (String s : strs3) {
				if (s.trim().length() > 0) {
					double time = replacementTaxonHeight.get(s);
					age = Math.max(age, time);
				}
			}
			oldestTaxon[j] = age;
		}
		
		Log.warning(taxa.size() + " taxa in original tree found\n" + i + " replacement subtrees found");
		Log.warning(replacementTaxa.size() + " replacement taxa found");
	}

	public static void main(String[] args) throws Exception {
		new Application(new TaxonReplacer(), "TaxonReplacer", args);
	}

}
