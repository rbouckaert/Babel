package babel.tools;

import java.io.File;
import java.io.PrintStream;
import java.util.List;

import beastfx.app.inputeditor.BeautiConfig;
import beastfx.app.inputeditor.BeautiDoc;
import beastfx.app.inputeditor.BEASTObjectDialog;
import beastfx.app.inputeditor.BEASTObjectPanel;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Log;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.MRCAPrior;
import beast.base.parser.NexusParser;
import beast.base.core.Input.Validate;

@Description("Cut out all branches underneath clades, leaving only family MRCA nodes")
public class FamilyPruner extends Runnable {
	public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	public Input<File> familyInput = new Input<>("families","NEXUS file containing taxon sets", Validate.REQUIRED);
	public Input<OutFile> outputInput = new Input<>("out","output file. Print to stdout if not specified");
	public Input<Boolean> verboseInput = new Input<>("verbose","print out extra information while processing", true);

	
	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		if (!verboseInput.get()) {
			Log.setLevel(Log.Level.error);
		}
		
		// get families
		NexusParser parser2 = new NexusParser();
		parser2.parseFile(familyInput.get());
		List<TaxonSet> taxonsets = parser2.taxonsets;
        for (TaxonSet taxonset : taxonsets) {
        	taxonset.initAndValidate();
        }

		// get trees
		NexusParser parser = new NexusParser();
		parser.parseFile(treesInput.get());
		if (parser.trees == null || parser.trees.size() == 0) {
			Log.err.println("File does not contain any trees " + treesInput.get().getName());
			return;
		}
        
        // filter trees, and print out newick trees
        PrintStream out = System.out;
        if (outputInput.get() != null) {
			Log.warning("Writing to file " + outputInput.get().getPath());
        	out = new PrintStream(outputInput.get());
        }
        
		for (Tree tree : parser.trees) {
			filter(tree, taxonsets);
			Node root = tree.getRoot();
        	out.println(TaxonFilter.toNewick(root));
		}
		
        if (outputInput.get() != null) {
        	out.close();
        }
        Log.warning.println("Done.");
	}


	
	private void filter(Tree tree, List<TaxonSet> taxonsets) {
		for (TaxonSet set : taxonsets) {
			MRCAPrior prior = new MRCAPrior();
			prior.initByName("tree", tree, "taxonset", set, "monophyletic", true);
			Node node = prior.getCommonAncestor();
			node.setID(set.getID());
		}

	}


	public static void main(String[] args) throws Exception {
		new Application(new FamilyPruner(), "Filter clades from tree set", args);
	}

}
