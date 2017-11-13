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
import beast.core.Description;
import beast.core.Input;
import beast.core.util.Log;
import beast.evolution.alignment.TaxonSet;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.util.NexusParser;
import beast.core.Input.Validate;

@Description("filters all leafs from specified taxon sets out of a tree file based on clade membership")
public class FamilyFilter extends TaxonFilter {
	public Input<File> familyInput = new Input<>("families","NEXUS file containing taxon sets", Validate.REQUIRED);
//	public Input<File> subsetInput = new Input<>("subset","text file with list of clades (defined in families) to include", Validate.REQUIRED);

	
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

		// get taxa in subsets
		Set<String> taxaToInclude = new HashSet<>();
		BufferedReader fin = new BufferedReader(new FileReader(subsetInput.get()));
        String str = null;
        while (fin.ready()) {
            str = fin.readLine();
            str = str.trim();
            boolean found = false;
            for (TaxonSet taxonset : taxonsets) {
            	if (taxonset.getID().equals(str)) {
            		for (String taxon : taxonset.asStringList()) {
            			taxaToInclude.add(taxon);
            		}
            		found = true;
            	}
            }
            if (!found) {
            	Log.warning.println("Could not find taxonset " + str + ". Typo perhaps?");
            	Log.warning.print("available taxon sets:");
                for (TaxonSet taxonset : taxonsets) {
                	Log.warning.print(taxonset.getID() + " ");
                }
            	Log.warning.println();
            }
        }
        fin.close();

		// get trees
		NexusParser parser = new NexusParser();
		parser.parseFile(treesInput.get());
		if (parser.trees == null || parser.trees.size() == 0) {
			Log.err.println("File does not contain any trees " + treesInput.get().getName());
			return;
		}

		// sanity check
        Set<String> taxaInTree = new HashSet<>();
        for (String taxon :	parser.trees.get(0).getTaxaNames()) {
        	taxaInTree.add(taxon);
        }
        StringBuilder buf = new StringBuilder();
        buf.append("Taxa in subset, but not in tree:");
        for (String taxon : taxaToInclude) {
        	if (!taxaInTree.contains(taxon)) {
        		buf.append(' ');
        		buf.append(taxon);
        	}
        }
        Log.warning.println(buf.toString());
        buf = new StringBuilder();
        buf.append("Taxa to be removed:");
        StringBuilder buf2 = new StringBuilder();
        int k = 0;
        for (String taxon : taxaInTree) {
        	if (!taxaToInclude.contains(taxon)) {
        		buf.append(' ');
        		buf.append(taxon);
        	} else {
        		buf2.append(' ');
        		buf2.append(taxon);
        		k++;
        	}
        }
        Log.warning.println(buf.toString());
		
        Log.warning.println("Expecting " + k + " taxa to be left:" + buf2.toString());
		
        
        // filter trees, and print out newick trees
        PrintStream out = System.out;
        if (outputInput.get() != null) {
        	out = new PrintStream(outputInput.get());
        }
        
		for (Tree tree : parser.trees) {
			Node root = tree.getRoot();
			root = filter(root, taxaToInclude);
			// print filtered tree
        	out.println(toNewick(root));
		}
		
        if (outputInput.get() != null) {
        	out.close();
        }
        Log.warning.println("Done.");
	}

	static ConsoleApp consoleapp;
	public static void main(String[] args) throws Exception {
		FamilyFilter app = new FamilyFilter();
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
