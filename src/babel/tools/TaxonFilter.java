package babel.tools;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

//import babel.tools.utils.MemoryFriendlyTreeSet;
import beastfx.app.tools.Application;
import beastfx.app.treeannotator.TreeAnnotator;
import beastfx.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Input.Validate;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;

@Description("filters all leafs from specified taxon sets out of a tree file")
public class TaxonFilter extends Runnable {
	public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	public Input<File> subsetInput = new Input<>("subset","text file with list of taxa to consider (one per line)", Validate.REQUIRED);
	public Input<Boolean> includeInput = new Input<>("include", "whether to include the taxa listed or exclude them", true);
	public Input<OutFile> outputInput = new Input<>("out","output file. Print to stdout if not specified");
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
		Set<String> taxaSubSet = new HashSet<>();
		BufferedReader fin = new BufferedReader(new FileReader(subsetInput.get()));
        String str = null;
        while (fin.ready()) {
            str = fin.readLine();
			taxaSubSet.add(str.trim());
        }
        fin.close();

		// get trees
		MemoryFriendlyTreeSet srcTreeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(treesInput.get().getPath(), 0);
		srcTreeSet.reset();
		Tree tree = srcTreeSet.next();

		// sanity check
        Set<String> taxaInTree = new HashSet<>();
        for (String taxon :	tree.getTaxaNames()) {
        	taxaInTree.add(taxon);
        }
        
        StringBuilder buf = new StringBuilder();
        buf.append("Taxa in subset, but not in tree:");
        for (String taxon : taxaSubSet) {
        	if (!taxaInTree.contains(taxon)) {
        		buf.append(' ');
        		buf.append(taxon);
        	}
        }
        Log.warning.println(buf.toString());
        
        Set<String> taxaToInclude = new HashSet<>();
        if (include) {
        	taxaToInclude.addAll(taxaSubSet);
        } else {
        	// we want to exclude the taxa in the subset
        	taxaToInclude.addAll(taxaInTree);
        	taxaToInclude.removeAll(taxaSubSet);
        }

        buf = new StringBuilder();
        buf.append("Taxa to be removed:");
        StringBuilder buf2 = new StringBuilder();
        int k = 0;
        for (String taxon : taxaInTree) {
        	if (taxaToInclude.contains(taxon)) {
        		buf2.append(' ');
        		buf2.append(taxon);
        		k++;
        	} else {
        		buf.append(' ');
        		buf.append(taxon);
        	}
        }
        Log.warning.println(buf.toString());
		
        Log.warning.println("Expecting " + k + " taxa to be left:" + buf2.toString());
        
        // filter trees, and print out newick trees
        PrintStream out = System.out;
        if (outputInput.get() != null) {
			Log.warning("Writing to file " + outputInput.get().getPath());
        	out = new PrintStream(outputInput.get());
        }
        
        srcTreeSet.reset();
        while (srcTreeSet.hasNext()) {
        	tree = srcTreeSet.next();
			Node root = tree.getRoot();
			root = filter(root, taxaToInclude);
			// print filtered tree
        	out.println(toNewick(root));
        	//out.println(root.toNewick());
		}
		
        if (outputInput.get() != null) {
        	out.close();
        }
        Log.warning.println("Done.");
       }


		static public String toNewick(Node node) {
	    	if (node.getChildCount() == 1) {
	    		return toNewick(node.getChild(0));
	    	}
	        final StringBuilder buf = new StringBuilder();
	        if (node.getLeft() != null) {
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
	                buf.append(normalise(node.getID()));
	            }
	        }
	        if (node.metaDataString != null) {
	            buf.append("[&" + node.metaDataString + ']');
	        }
	        
	        buf.append(":").append(node.getLength());
	        return buf.toString();
	    }


	    // wrap ID in quotes if it contains special characters
		static public String normalise(String id) {
			if (id.contains(":") || id.contains("(") || id.contains("[")) {
				return "\"" + id + "\"";
			}
			return id;
		}

		protected Node filter(Node node, Set<String> taxaToInclude) {
			if (node.isLeaf()) {
				if (taxaToInclude.contains(node.getID())) {
					return node;
				} else {
					return null;
				}
			} else {
				Node left_ = node.getLeft(); 
				Node right_ = node.getRight(); 
				left_ = filter(left_, taxaToInclude);
				right_ = filter(right_, taxaToInclude);
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
	
	public static void main(String[] args) throws Exception {
		new Application(new TaxonFilter(), "Taxon Filter", args);
	}

}
