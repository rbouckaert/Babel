package babel.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.Input.Validate;
import beast.core.util.Log;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.util.NexusParser;

@Description("filters all leafs from specified taxon sets out of a tree file")
public class TaxonFilter extends Runnable {
	public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	public Input<File> subsetInput = new Input<>("subset","text file with list of taxa to include (one per line)", Validate.REQUIRED);
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
		

		// get taxa in subsets
		Set<String> taxaToInclude = new HashSet<>();
		BufferedReader fin = new BufferedReader(new FileReader(subsetInput.get()));
        String str = null;
        while (fin.ready()) {
            str = fin.readLine();
			taxaToInclude.add(str.trim());
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
			Log.warning("Writing to file " + outputInput.get().getPath());
        	out = new PrintStream(outputInput.get());
        }
        
		for (Tree tree : parser.trees) {
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


	   public String toNewick(Node node) {
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
	                buf.append(node.getID());
	            }
	        }
	        if (node.metaDataString != null) {
	            buf.append("[&" + node.metaDataString + ']');
	        }
	        
	        buf.append(":").append(node.getLength());
	        return buf.toString();
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
