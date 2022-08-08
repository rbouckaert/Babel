package babel.tools;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math.MathException;

import babel.util.NexusParser;
import beastfx.app.treeannotator.TreeAnnotator;
import beastfx.app.treeannotator.TreeAnnotator.FastTreeSet;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.core.Citation;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Input.Validate;
import beast.base.core.Log;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.FilteredAlignment;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;


@Description("Annotate tree with k-Dollo information and calculate Dollo-k characters")
@Citation("Combinatorial perspectives on Dollo-k characters in phylogenetics. 2020")
public class DolloAnnotator extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("tree","NEXUS file containing a tree of interest", Validate.REQUIRED);
	final public Input<File> nexusFileInput = new Input<>("nexus", "nexus file with binary alignment used to annotate tree. If not specified, the tree is not annotated");
	final public Input<OutFile> outputInput = new Input<>("out", "file to write annotated tree into, or stdout if not specified", new OutFile("[[none]]"));
    final public Input<String> filterInput = new Input<>("filter", "specifies which subset (if any) of the sites in the input alignment should be selected. " +
            "First site is 1." +
            "Filter specs are comma separated, either a singleton, a range [from]-[to] or iteration [from]:[to]:[step]; " +
            "1-100 defines a range, " +
            "1-100\3 or 1:100:3 defines every third in range 1-100, " +
            "1::3,2::3 removes every third site. " +
            "Default for range [1]-[last site], default for iterator [1]:[last site]:[1]");
    final public Input<String> rangeInput = new Input<>("range", "range for which to calculate the Dollo-k counts. "
    		+ "Either a single number k to calculate the Dollo-k value, "
    		+ "or a lower and upper bound (inclusive)  separated by a comma for a range. "
    		+ "If not specified, all values are calculated.");
    final public Input<Boolean> verboseInput = new Input<>("verbose", "display extra information, like progress of Dollo counting", false);

	@Override
	public void initAndValidate() {
	}

	private int[][] seqs;
	private int[] DolloK;
    private boolean [] nodesTraversed;
	private int ambiguousSites;

    private int nodesVisited, from, to;

	@Override
	public void run() throws Exception {
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}

		Alignment data = getAlignment();
				
		long start = System.currentTimeMillis();
        FastTreeSet trees = new TreeAnnotator().new FastTreeSet(treesInput.get().getAbsolutePath(), 0);
        trees.reset();
        int treeCount = 0;
        Tree tree = null;
        while (trees.hasNext()) {
            tree = trees.next();
        	Log.warning(tree.getLeafNodeCount() + " taxa");
            if (treeCount == 0) {
            	processRange(tree);
            }
            
            if (data != null) {
            	// annotate tree if binary alignment is available
	            if (treeCount == 0) {
	            	tree.init(out);
	            }
	            out.println();
	            out.print("tree STATE_" + treeCount + " = ");
	            ambiguousSites = 0;
	
	            annotate(tree, data);
	
	            
	            final String newick = tree.getRoot().toSortedNewick(new int[1], true);
	            out.print(newick);
	            out.print(";");
	            
	            if (ambiguousSites > 0) {
					Log.warning("\nWARNING: Encounterd " + ambiguousSites + " ambiguous site" + 
							(ambiguousSites > 1 ? "s" : "")+ "\nWARNING: all such sites are assumed to be 0");
	            }
	            Log.warning("\nPer site Dollo losses (k) = " + Arrays.toString(DolloK));
            }
            treeCount++;
            Log.warning("Dollo-k counts:");
            long [][] ik = null, ek = null;
            BigDecimal [][] ikbig = null, ekbig = null;

            ik = new long[tree.getNodeCount()][to+1];
            ek = new long[tree.getNodeCount()][to+1];
            for (int i = 0; i < ik.length; i++) {
            	Arrays.fill(ik[i], -1);
            	Arrays.fill(ek[i], -1);
            }
            
            for (int k = from; k <= to; k++) {
            	if (ikbig == null) {
	            	try {
		            	long count = dolloKCount(tree, k, ik, ek);
		            	//long count = dolloKCount(tree, k);
		            	if (data != null) {
		            		Log.warning(k + ": " + count);
		            	} else {
		            		out.println(k + ": " + count);
		            	}
	            	} catch (MathException e) {
	            		ikbig = new BigDecimal[tree.getNodeCount()][to+1];
	            		ekbig = new BigDecimal[tree.getNodeCount()][to+1];
	            	}
            	}
            	if (ikbig != null) {
            		BigDecimal count = dolloKCount(tree, k, ikbig, ekbig);
	            	if (data != null) {
	            		Log.warning(k + ": " + count);
	            	} else {
	            		out.println(k + ": " + count);
	            	}
            	}
            }
        }
        if (tree != null && data != null) {
        	tree.close(out);
        }
		long end = System.currentTimeMillis();
        Log.warning("Done in " + (end-start)/1000.0 + " seconds");
	}

	/**
	 * Determine range for which to produce Dollo-k counts
	 * @param tree
	 */
	private void processRange(Tree tree) {
		from = 0;
		to = tree.getNodeCount() - 2;
		
		if (rangeInput.get() != null) {
			String [] strs = rangeInput.get().split(",");
			if (strs.length == 1) {
				from = Integer.parseInt(strs[0]);
				to = from;
			} else if (strs.length == 2) {
				from = Integer.parseInt(strs[0]);
				to = Integer.parseInt(strs[1]);
				if (to < from) {
					throw new IllegalArgumentException("Range has lower bound larger than upper bound");
				}
			} else {
				throw new IllegalArgumentException("Range must consists of at most two numbers");
			}
		}
		
		if (from < 0 || to > tree.getNodeCount() - 2) {
			throw new IllegalArgumentException("Range must be between 0 and " + (tree.getNodeCount() - 2));
		}
	}


	private Alignment getAlignment() throws IOException {
		if (nexusFileInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			NexusParser nexus = new NexusParser();
			nexus.parseFile(nexusFileInput.get());
			Alignment data = nexus.m_alignment;
			if (data == null || !data.getDataType().getTypeDescription().equals("binary")) {
				throw new IllegalArgumentException("Expected a binary alignment in the NEXUS file");			
			}
			
			if (filterInput.get() != null) {
				FilteredAlignment filtered = new FilteredAlignment();
				filtered.initByName("data", data, "filter", filterInput.get());
				data = filtered;
			}
			return data;
		}		
		return null;
	}


	/**
	 * Count the number of possible Dollo-k characters (that is, 
	 * Dollo-assignments requiring k death events) on a given tree
	 * 
	 * Implements Algorithm 2 from the paper.
     *
	 * @param tree: binary tree
	 * @param k: number of death events
	 * @return number of possible Dollo-k characters
	 * throws MathException when encountering underflow
	 */
	public long dolloKCount(Tree tree, int k) throws MathException{
		if (k == 0) {
			return tree.getNodeCount() + 1;
		}
		long count = 0;
		for (int j = tree.getLeafNodeCount(); j < tree.getNodeCount(); j++) {
			if (verboseInput.get()) System.err.print(j % 10 == 0 ? '|' : '.');
			Node node = tree.getNode(j);
			Node left = node.getLeft();
			Node right = node.getRight();
			long ikt = 0;
			for (int i = 0; i <= k; i++) {
				ikt += extended(left, i) * extended(right, k-i);
			}
			count += ikt;
		}
    	if (count < 0) {
    		throw new MathException("Underflow encountered! Count > " + Long.MAX_VALUE + " ");
    	}
		return count;
	}
	
	/** extended method from Algorithm 2 in the paper **/
	private long extended(Node node, int k) {
		if (k == 0) {
			return 1;
		}
		int n = node.getLeafNodeCount();
		if (k > 0 && n == 1) {
			return 0;
		}
		Node left = node.getLeft();
		Node right = node.getRight();
		long ekt = 0;
		for (int i = 0; i <= k; i++) {
			ekt += extended(left, i) * extended(right, k-i);
		}
		ekt = ekt + extended(left, k-1) + extended(right, k-1);
		return ekt;
	}

	
	
	/**
	 * As dolloCount(tree, k), but more efficient with ik and ek cached.
	 * @param tree
	 * @param k
	 * @param ik: independent node set of size k for subtree under node. Must be initialised as -1 at first call.
	 * @param ek: extended independent node set of size k for subtree under node.  Must be initialised as -1 at first call.
	 * @return number of possible Dollo-k characters
	 * throws MathException when encountering underflow
	 */
	public long dolloKCount(Tree tree, int k, long [][] ik, long [][] ek) throws MathException {
		if (k == 0) {
			return tree.getNodeCount() + 1;
		}
		long count = 0;
		for (int j = tree.getLeafNodeCount(); j < tree.getNodeCount(); j++) {
			if (verboseInput.get()) System.err.print(j % 10 == 0 ? '|' : '.');
			Node node = tree.getNode(j);
			Node left = node.getLeft();
			Node right = node.getRight();
			long ikt = ik[node.getNr()][k];
			if (ikt < 0) {
				ikt = 0;
				for (int i = 0; i <= k; i++) {
					ikt += extended(left, i, ek) * extended(right, k-i, ek);
				}					
				ik[node.getNr()][k] = ikt;
			}
			count += ikt;
		}
    	if (count < 0) {
    		throw new MathException("Underflow encountered! Count > " + Long.MAX_VALUE + " ");
    	}
		return count;
	}

	/** as extended(node,k) but with ek cache for extended independent node set of size k for subtree under node **/
	private long extended(Node node, int k, long [][] ek) {
		if (k == 0) {
			return 1;
		}
		int n = node.getLeafNodeCount();
		if (k > 0 && n == 1) {
			return 0;
		}
		Node left = node.getLeft();
		Node right = node.getRight();
		long ekt = ek[node.getNr()][k];
		if (ekt < 0) {
			ekt = 0;
			for (int i = 0; i <= k; i++) {
				ekt += extended(left, i, ek) * extended(right, k-i, ek);
			}
			ekt = ekt + extended(left, k-1, ek) + extended(right, k-1, ek);
			ek[node.getNr()][k] = ekt;
		}
		return ekt;
	}
	
	
	
	
	/**
	 * As dolloCount(tree, k), but more efficient with ik and ek cached and using unlimited numbers of digits through BigDecimal.
	 * @param tree
	 * @param k
	 * @param ik: independent node set of size k for subtree under node. Must be initialised as null at first call.
	 * @param ek: extended independent node set of size k for subtree under node.  Must be initialised as null at first call.
	 * @return number of possible Dollo-k characters
	 * throws MathException when encountering underflow
	 */
	public BigDecimal dolloKCount(Tree tree, int k, BigDecimal [][] ik, BigDecimal [][] ek) {
		if (k == 0) {
			return new BigDecimal(tree.getNodeCount() + 1);
		}
		BigDecimal count = new BigDecimal(0);
		for (int j = tree.getLeafNodeCount(); j < tree.getNodeCount(); j++) {
			if (verboseInput.get()) System.err.print(j % 10 == 0 ? '|' : '.');
			Node node = tree.getNode(j);
			Node left = node.getLeft();
			Node right = node.getRight();
			BigDecimal ikt = ik[node.getNr()][k];
			if (ikt == null) {
				ikt = new BigDecimal(0);
				if (left.isLeaf()) {
					ikt = extended(right, k, ek);
				} else if (right.isLeaf()) {
					ikt = extended(left, k, ek);
				} else {
					for (int i = 0; i <= k; i++) {
						ikt = ikt.add(extended(left, i, ek).multiply(extended(right, k-i, ek)));
					}					
				}
				ik[node.getNr()][k] = ikt;
			}
			count = count.add(ikt);
		}
		return count;
	}

	final private static BigDecimal BigDecimal0 = new BigDecimal(0);
	final private static BigDecimal BigDecimal1 = new BigDecimal(1);
	
	/** as extended(node,k) but with ek cache for extended independent node set of size k for subtree under node **/
	private BigDecimal extended(Node node, int k, BigDecimal [][] ek) {
		if (k == 0) {
			return BigDecimal1;
		}
		int n = node.getLeafNodeCount();
		if (k > 0 && n == 1) {
			return BigDecimal0;
		}
		Node left = node.getLeft();
		Node right = node.getRight();
		BigDecimal ekt = ek[node.getNr()][k];
		if (ekt == null) {
			ekt = new BigDecimal(0);
			if (left.isLeaf()) {
				ekt = extended(right, k, ek);
			} else if (right.isLeaf()) {
				ekt = extended(left, k, ek);
			} else {
				for (int i = 0; i <= k; i++) {
					ekt = ekt.add(extended(left, i, ek).multiply(extended(right, k-i, ek)));
				}
			}
			ekt = ekt.add(extended(left, k-1, ek)).add(extended(right, k-1, ek));
			ek[node.getNr()][k] = ekt;
		}
		return ekt;
	}	
	
	/**
	 * Annotate internal nodes of a tree with Dollo-k characters
	 * 
	 * Implements Algorithm 1 from the paper.
	 * 
	 * @param tree: the tree to be annotated. The "dollo" metadata tag will be set by this method.
	 * @param data: alignment with binary data associated with the tree
	 */
	public void annotate(Tree tree, Alignment data) {
        seqs = new int[tree.getNodeCount()][data.getSiteCount()];
        DolloK = new int [data.getSiteCount()];

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
					ambiguousSites++;
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
        	node.setMetaData("dollo", seqs[node.getNr()]);
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
        if (degree2nodes != nodesVisited - (2*oneLeafs.size()-1)) {
        	Log.warning("Found one");
        }
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
