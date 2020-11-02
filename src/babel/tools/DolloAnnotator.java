package babel.tools;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import babel.util.NexusParser;
import beast.app.treeannotator.TreeAnnotator;
import beast.app.treeannotator.TreeAnnotator.FastTreeSet;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Citation;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.Input.Validate;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.evolution.alignment.FilteredAlignment;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;


@Description("Annotate tree with k-Dollo information")
@Citation("Combinatorial perspectives on Dollo-k characters in phylogenetics. 2020")
public class DolloAnnotator extends Runnable {
	public Input<File> nexusFileInput = new Input<>("nexus", "nexus file with binary alignment", Validate.REQUIRED);
	public Input<TreeFile> treesInput = new Input<>("tree","NEXUS file containing a tree", Validate.REQUIRED);
	public Input<OutFile> outputInput = new Input<>("out", "file to write annotated tree into, or stdout if not specified", new OutFile("[[none]]"));
    final public Input<String> filterInput = new Input<>("filter", "specifies which of the sites in the input alignment should be selected " +
            "First site is 1." +
            "Filter specs are comma separated, either a singleton, a range [from]-[to] or iteration [from]:[to]:[step]; " +
            "1-100 defines a range, " +
            "1-100\3 or 1:100:3 defines every third in range 1-100, " +
            "1::3,2::3 removes every third site. " +
            "Default for range [1]-[last site], default for iterator [1]:[last site]:[1]");

	@Override
	public void initAndValidate() {
	}

	private Alignment data;
	private int[][] seqs;
	private int[] DolloK;
    private boolean [] nodesTraversed;
    
    private int nodesVisited;

	@Override
	public void run() throws Exception {
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}

		
		NexusParser nexus = new NexusParser();
		nexus.parseFile(nexusFileInput.get());
		data = nexus.m_alignment;
		if (data == null || !data.getDataType().getTypeDescription().equals("binary")) {
			throw new IllegalArgumentException("Expected a binary alignment in the NEXUS file");			
		}
		
		if (filterInput.get() != null) {
			FilteredAlignment filtered = new FilteredAlignment();
			filtered.initByName("data", data, "filter", filterInput.get());
			data = filtered;
		}
		

        FastTreeSet trees = new TreeAnnotator().new FastTreeSet(treesInput.get().getAbsolutePath(), 0);
        trees.reset();
        int k = 0;
        Tree tree = null;
        Log.warning("\n");
        while (trees.hasNext()) {
            tree = trees.next();
            seqs = new int[tree.getNodeCount()][data.getSiteCount()];
            DolloK = new int [data.getSiteCount()];
            
            
            if (k == 0) {
            	tree.init(out);
            }
            out.println();
            out.print("tree STATE_" + k + " = ");

            annotate(tree);

            
            final String newick = tree.getRoot().toSortedNewick(new int[1], true);
            out.print(newick);
            out.print(";");
            k++;
            Log.warning("\nPer site Dollo losses (k)=" + Arrays.toString(DolloK));
        }
        if (tree != null) {
        	tree.close(out);
        }
        Log.warning("Done");
	}

	
	private void annotate(Tree tree) {
		// collect leaf data
		for (int k = 0; k < tree.getLeafNodeCount(); k++) {
			Node node = tree.getNode(k);
			int id = data.getTaxonIndex(node.getID());
			if (id == -1) {
				throw new IllegalArgumentException("Could not find data for " + node.getID());
			}
			int [] seq = seqs[node.getNr()];
			for (int i = 0; i < seq.length; i++) {
				int site = data.getPattern(id, data.getPatternIndex(i));
				if (site != 1 && site !=0) {
					Log.warning("Don't know how to handle ambiguous site:" + site + " -- assume it is a 0");
				}
				seq[i] = site == 1 ? 1 : 0;
			}
		}
		
		// annotate, site by site
		for (int i = 0; i < seqs[0].length; i++) {
			calc1Tree(tree, i);
		}
		
		// set meta data in tree
        for (Node node : tree.getNodesAsArray()) {
        	node.metaDataString = "dollo=\"" + Arrays.toString(seqs[node.getNr()]) + "\"";
        }
	}
	
	/**
	 * Calculate the 1-tree annotations (stored in seqs) for site siteIndex in the alignment
	 */
	private void calc1Tree(Tree tree, int siteIndex) {
		// find nodes that are 1 in the alignments
		List<Node> oneLeafs = new ArrayList<>();
		for (int i = 0; i < tree.getLeafNodeCount(); i++) {
			if (seqs[i][siteIndex] == 1) {
				oneLeafs.add(tree.getNode(i));
			}
		}

		if (oneLeafs.size() <= 1) {
			return;
		}
		
		// find common ancestor for all nodes in oneLeafs
        nodesTraversed = new boolean[tree.getNodeCount()];
        nodesVisited = 0;
        Node cur = oneLeafs.get(0);
        for (int k = 1; k < oneLeafs.size(); ++k) {
            cur = getCommonAncestor(cur, oneLeafs.get(k));
        }
        
        // store results in seqs
        for (int i = tree.getLeafNodeCount(); i< tree.getNodeCount(); i++) {
        	if (nodesTraversed[i]) {
        		seqs[i][siteIndex] = 1;
        	}
        }

        int degree2nodes = 0;
        for (int i = tree.getLeafNodeCount(); i< tree.getNodeCount(); i++) {
        	Node node = tree.getNode(i);
        	if (nodesTraversed[i] && (!nodesTraversed[node.getLeft().getNr()] || !nodesTraversed[node.getRight().getNr()])) {
        		degree2nodes++;
        	}
        }

        DolloK[siteIndex] = degree2nodes;
	}
	
	
    private Node getCommonAncestor(Node n1, Node n2) {
        // assert n1.getTree() == n2.getTree();
        if( ! nodesTraversed[n1.getNr()] ) {
            nodesTraversed[n1.getNr()] = true;
            nodesVisited++;
        }
        if( ! nodesTraversed[n2.getNr()] ) {
            nodesTraversed[n2.getNr()] = true;
            nodesVisited++;
        }
        while (n1 != n2) {
	        double h1 = n1.getHeight();
	        double h2 = n2.getHeight();
	        if ( h1 < h2 ) {
	            n1 = n1.getParent();
	            if( ! nodesTraversed[n1.getNr()] ) {
	                nodesTraversed[n1.getNr()] = true;
	                nodesVisited++;
	            }
	        } else if( h2 < h1 ) {
	            n2 = n2.getParent();
	            if( ! nodesTraversed[n2.getNr()] ) {
	                nodesTraversed[n2.getNr()] = true;
	                nodesVisited++;
	            }
	        } else {
	            //zero length branches hell
	            Node n;
	            double b1 = n1.getLength();
	            double b2 = n2.getLength();
	            if( b1 > 0 ) {
	                n = n2;
	            } else { // b1 == 0
	                if( b2 > 0 ) {
	                    n = n1;
	                } else {
	                    // both 0
	                    n = n1;
	                    while( n != null && n != n2 ) {
	                        n = n.getParent();
	                    }
	                    if( n == n2 ) {
	                        // n2 is an ancestor of n1
	                        n = n1;
	                    } else {
	                        // always safe to advance n2
	                        n = n2;
	                    }
	                }
	            }
	            if( n == n1 ) {
                    n = n1 = n.getParent();
                } else {
                    n = n2 = n.getParent();
                }
	            if( ! nodesTraversed[n.getNr()] ) {
	                nodesTraversed[n.getNr()] = true;
	                nodesVisited++;
	            } 
	        }
        }
        return n1;
    }


	
	public static void main(String[] args) throws Exception {
		new Application(new DolloAnnotator(), "Dollo Annotator", args);		
	}
}
