package babel.tools;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import beastfx.app.tools.Application;
import beastfx.app.treeannotator.TreeAnnotator;
import beastfx.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Input.Validate;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;

@Description("filters trees from of a tree file containing a specified clade split ")
public class CladeSplitFilter extends TreeCombiner {
	public Input<File> splitInput = new Input<>("split","text file with list of taxa to consider (one per line) with en empty line to indicate the split", Validate.REQUIRED);
	
	public Input<Boolean> includeInput = new Input<>("include", "whether to keep trees that include the split or exclude them", true);
	public Input<Boolean> verboseInput = new Input<>("verbose","print out extra information while processing", true);

	
	protected boolean include;
	
	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		if (!verboseInput.get()) {
			Log.setLevel(Log.Level.error);
		}
		
		include = includeInput.get();
		
		// get taxa in subsets
		Set<String> taxaSubSet1 = new HashSet<>();
		Set<String> taxaSubSet2 = new HashSet<>();
		BufferedReader fin = new BufferedReader(new FileReader(splitInput.get()));
        String str = null;
        Set<String> t = taxaSubSet1;
        while (fin.ready()) {
            str = fin.readLine();
            if (str.trim().length() == 0) {
            	t = taxaSubSet2;
            } else {
            	t.add(str.trim());
            }
        }
        fin.close();

		Set<String> taxaSubSet = new HashSet<>();
		taxaSubSet.addAll(taxaSubSet1);
		taxaSubSet.addAll(taxaSubSet2);
        
        
		// get trees
		MemoryFriendlyTreeSet srcTreeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(srcInput.get().getPath(), 0);
		srcTreeSet.reset();
		Tree tree = srcTreeSet.next();

		// sanity check
        Set<String> taxaInTree = new HashSet<>();
        for (String taxon :	tree.getTaxaNames()) {
        	taxaInTree.add(taxon);
        }
               
        // filter trees, and print out newick trees
        PrintStream out = System.out;
        if (outputInput.get() != null) {
			Log.warning("Writing to file " + outputInput.get().getPath());
        	out = new PrintStream(outputInput.get());
        }
        
        srcTreeSet.reset();
        int k = 0;
        while (srcTreeSet.hasNext()) {
        	tree = srcTreeSet.next();
			
			if (include) {
				Node n1 = getMRCA(tree, taxaSubSet1);
				if (n1.getLeafNodeCount() == taxaSubSet1.size()) {
					Node n2 = getMRCA(tree, taxaSubSet2);
					if (n2.getLeafNodeCount() == taxaSubSet2.size()) {
						Node n = getMRCA(tree, taxaSubSet);
						if (n.getLeafNodeCount() == taxaSubSet.size()) {			
							Node root = tree.getRoot();
				        	out.println(TaxonFilter.toNewick(root));
				        	k++;
						}
					}
				}
			} else {
				Node n1 = getMRCA(tree, taxaSubSet1);
				Node n2 = getMRCA(tree, taxaSubSet2);
				Node n = getMRCA(tree, taxaSubSet);
				if (n1.getLeafNodeCount() != taxaSubSet1.size() ||
					n2.getLeafNodeCount() == taxaSubSet2.size() ||
				    n.getLeafNodeCount() == taxaSubSet.size()) {			
						Node root = tree.getRoot();
			        	out.println(TaxonFilter.toNewick(root));
			        	k++;
				}
			}
		}
		
        if (outputInput.get() != null) {
        	out.close();
        }
        Log.warning.println(k + " trees saved.");
        Log.warning.println("Done.");
       }


	public static void main(String[] args) throws Exception {
		new Application(new CladeSplitFilter(), "Taxon Filter", args);
	}

}
