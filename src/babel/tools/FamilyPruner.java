package babel.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import beast.app.beauti.BeautiConfig;
import beast.app.beauti.BeautiDoc;
import beast.app.draw.BEASTObjectDialog;
import beast.app.draw.BEASTObjectPanel;
import beast.app.util.Application;
import beast.app.util.ConsoleApp;
//import beast.app.util.ConsoleApp;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.evolution.alignment.TaxonSet;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.math.distributions.MRCAPrior;
import beast.util.NexusParser;
import beast.core.Input.Validate;

@Description("Cut out all branches underneath clades, leaving only family MRCA nodes")
public class FamilyPruner extends Runnable {
	public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	public Input<File> familyInput = new Input<>("families","NEXUS file containing taxon sets", Validate.REQUIRED);
	//public Input<File> subsetInput = new Input<>("subset","text file with list of clades (defined in families) to include", Validate.REQUIRED);
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
        	out = new PrintStream(outputInput.get());
        }
        
		for (Tree tree : parser.trees) {
			filter(tree, taxonsets);
			Node root = tree.getRoot();
        	out.println(toNewick(root));
		}
		
        if (outputInput.get() != null) {
        	out.close();
        }
        Log.warning.println("Done.");
	}

    public String toNewick(Node node) {
    	if (node.getChildCount() == 1) {
    		return toNewick(node.getChild(0));
    	}
        final StringBuilder buf = new StringBuilder();
        if (node.getLeft() != null && node.getID() == null) {
            buf.append("(");
            buf.append(toNewick(node.getLeft()));
            if (node.getRight() != null) {
                buf.append(',');
                buf.append(toNewick(node.getRight()));
            }
            buf.append(")");
            if (node.getID() != null) {
                buf.append(node.getID());
            }
        } else {
            if (node.getID() == null) {
                buf.append(node.getNr());
            } else {
                buf.append(node.getID());
            }
        }
        buf.append(":").append(node.getLength());
        return buf.toString();
    }


	
	private void filter(Tree tree, List<TaxonSet> taxonsets) {
		for (TaxonSet set : taxonsets) {
			MRCAPrior prior = new MRCAPrior();
			prior.initByName("tree", tree, "taxonset", set, "monophyletic", true);
			Node node = prior.getCommonAncestor();
			node.setID(set.getID());
		}

	}


	static ConsoleApp consoleapp;
	public static void main(String[] args) throws Exception {
		FamilyPruner app = new FamilyPruner();
		app.setID("Filter clades from tree set");
	
		if (args.length == 0) {
			// create BeautiDoc and beauti configuration
			BeautiDoc doc = new BeautiDoc();
			doc.beautiConfig = new BeautiConfig();
			doc.beautiConfig.initAndValidate();
					
			// create panel with entries for the application
			BEASTObjectPanel panel = new BEASTObjectPanel(app, app.getClass(), doc);
			
			// wrap panel in a dialog
			BEASTObjectDialog dialog = new BEASTObjectDialog(panel, null);
	
			// show the dialog
			if (dialog.showDialog()) {
				dialog.accept(app, doc);
				// create a console to show standard error and standard output
				consoleapp = new ConsoleApp("FamilyFilter", "FamilyFilter", null);
				app.initAndValidate();
				app.run();
			}
			return;
		}

		Application main = new Application(app);
		main.parseArgs(args, false);
		app.initAndValidate();
		app.run();
	}

}
