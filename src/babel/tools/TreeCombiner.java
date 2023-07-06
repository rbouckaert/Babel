package babel.tools;


import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;

abstract public class TreeCombiner extends Runnable {
	final public Input<TreeFile> srcInput = new Input<>("src","source tree (set) file used as skeleton");
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));

    boolean [] nodesTraversed;
    protected int nseen;
    
	@Override
	public void initAndValidate() {
	}
	
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

	protected Node getMRCA(Tree tree, Set<String> taxa) {
		List<Node> leafs = new ArrayList<>();
		for (Node node : tree.getRoot().getAllLeafNodes()) {
			if (taxa.contains(node.getID())) {
				leafs.add(node);
			}
		}

        nodesTraversed = new boolean[tree.getRoot().getAllChildNodesAndSelf().size()];
        nseen = 0;
        Node cur = leafs.get(0);

        for (int k = 1; k < leafs.size(); ++k) {
            cur = getCommonAncestor(cur, leafs.get(k));
        }
		return cur;
	}
	
	
}
