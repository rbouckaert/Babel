package babel.tools;


import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import beast.app.beauti.BeautiDoc;
import beast.app.treeannotator.TreeAnnotator;
import beast.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;

@Description("Merge sub-trees into skeleton tree")
public class TreeMerger extends Runnable {
	final public Input<TreeFile> srcInput = new Input<>("src","source tree file used as skeleton");
	final public Input<File> cgfFileInput = new Input<>("cfg", "tab separated configuration file containing info for one tree set per line. "
			+ "Firts column is name of tree file, second column a comma separated list of taxa to be transfered to source tree.");
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));

	int subTreeCount;
	MemoryFriendlyTreeSet [] subTreeSet;
	Set<String> [] subTaxonSets;
	
	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		MemoryFriendlyTreeSet srcTreeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(srcInput.get().getPath(), 0);
		srcTreeSet.reset();
		Tree tree = srcTreeSet.next();
		
		processCfgFile();
		
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getName());
			out = new PrintStream(outputInput.get());
		}

		srcTreeSet.reset();
		while (srcTreeSet.hasNext()) {
			tree = srcTreeSet.next();
			for (int i = 0; i < subTreeCount; i++) {
				Node src = getMRCA(tree, subTaxonSets[i]);
				Node parent = src.getParent();
				if (subTreeSet[i].hasNext()) {
					Tree subTree = subTreeSet[i].next();
					
					Node replacement = getMRCA(subTree, subTaxonSets[i]);
					boolean replaced = false;
					for (int j = 0; j < parent.getChildCount(); j++) {
						if (parent.getChild(j) == src) {
							src.getParent().setChild(j, replacement);
							replacement.setParent(parent);
							replaced = true;
						}
					}
					if (!replaced) {
						throw new RuntimeException("Something went wrong replacing node");
					}
				} else {
					throw new IllegalArgumentException("Tree sets are of different sizes: treeset " + i + " is smaler than source set");
				}
			}
			out.print(tree.getRoot().toNewick());
		}
		 
		Log.err("Done!");
		out.close();
	}
	
	
    boolean [] nodesTraversed;
    int nseen;

    protected Node getCommonAncestor(Node n1, Node n2) {
        // assert n1.getTree() == n2.getTree();
        if( ! nodesTraversed[n1.getNr()] ) {
            nodesTraversed[n1.getNr()] = true;
            nseen += 1;
        }
        if( ! nodesTraversed[n2.getNr()] ) {
            nodesTraversed[n2.getNr()] = true;
            nseen += 1;
        }
        while (n1 != n2) {
	        double h1 = n1.getHeight();
	        double h2 = n2.getHeight();
	        if ( h1 < h2 ) {
	            n1 = n1.getParent();
	            if( ! nodesTraversed[n1.getNr()] ) {
	                nodesTraversed[n1.getNr()] = true;
	                nseen += 1;
	            }
	        } else if( h2 < h1 ) {
	            n2 = n2.getParent();
	            if( ! nodesTraversed[n2.getNr()] ) {
	                nodesTraversed[n2.getNr()] = true;
	                nseen += 1;
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
	                nseen += 1;
	            } 
	        }
        }
        return n1;
    }

	private Node getMRCA(Tree tree, Set<String> taxa) {
		List<Node> leafs = new ArrayList<>();
		for (Node node : tree.getExternalNodes()) {
			if (taxa.contains(node.getID())) {
				leafs.add(node);
			}
		}

        nodesTraversed = new boolean[tree.getNodeCount()];
        Node cur = leafs.get(0);

        for (int k = 1; k < leafs.size(); ++k) {
            cur = getCommonAncestor(cur, leafs.get(k));
        }
		return cur;
	}


	
	
	private void processCfgFile() throws IOException {
		String cfg = BeautiDoc.load(cgfFileInput.get());
		String [] strs = cfg.split("\n");
		subTreeCount = 0;
		for (String str : strs) {
			if (!str.matches("^\\s*$")) {
				subTreeCount++;
			}
		}
		subTreeSet = new MemoryFriendlyTreeSet[subTreeCount];
		subTaxonSets = new Set[subTreeCount];
		int i = 0;
		for (String str : strs) {
			if (!str.matches("^\\s*$")) {
				String [] strs2 = str.split("\t");
				subTreeSet[i] = new TreeAnnotator().new MemoryFriendlyTreeSet(strs2[0], 0);
				subTaxonSets[i] = new HashSet<>();
				for (String taxon : strs2[1].split(",")) {
					subTaxonSets[i].add(taxon);					
				}
			}
		}
	}

	public static void main(String[] args) throws Exception {
		new Application(new TreeMerger(), "Tree Merger", args);
	}

}
