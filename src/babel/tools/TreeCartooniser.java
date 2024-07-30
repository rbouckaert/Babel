package babel.tools;

import java.io.File;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beastfx.app.inputeditor.BeautiDoc;
import beastfx.app.tools.Application;
import beastfx.app.treeannotator.TreeAnnotator;
import babel.tools.utils.MemoryFriendlyTreeSet;

@Description("Add `cartoon` annotations to summary tree, so FigTree shows them as triangle")
public class TreeCartooniser extends TreeCombiner {
	final public Input<File> cgfFileInput = new Input<>("cfg", "comma separated list of taxa to form a triangle,  one tree set per line.");

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(srcInput.get().getPath(), 0);
		srcTreeSet.reset();
		Tree tree = srcTreeSet.next();

		String cfg = BeautiDoc.load(cgfFileInput.get());
		String [] strs = cfg.split("\n");
		for (String str : strs) {
			if (!(str.startsWith("#") || str.trim().length() == 0)) {
				addCartoon(tree, str);
			}
		}
		
		
		
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getName());
			out = new PrintStream(outputInput.get());
		}
		
		tree.init(out);
        out.print("tree STATE_" + 1 + " = ");
        final String newick = toShortNewick(tree.getRoot());
        out.print(newick);
        out.print(";");

		tree.log(0, out);
		tree.close(out);
		
		Log.err("Done!");
		out.close();
	}

	
    private String toShortNewick(Node node) {
        final StringBuilder buf = new StringBuilder();

        if (!node.isLeaf()) {
            buf.append("(");
            boolean isFirst = true;
            for (Node child : node.getChildren()) {
                if (isFirst)
                    isFirst = false;
                else
                    buf.append(",");
                buf.append(toShortNewick(child));
            }
            buf.append(")");
        }

        if (node.isLeaf()) {
            buf.append(node.getNr()+1);
        }

        buf.append(node.getNewickMetaData());
        buf.append(":").append(node.getNewickLengthMetaData()).append(node.getLength());
        return buf.toString();
    }

	
	private void addCartoon(Tree tree, String str) {
		Set<String> cartoonTaxa = new HashSet<>();
		for (String taxon : str.split(",")) {
			cartoonTaxa.add(taxon);
		}
		Node node = getMRCA(tree, cartoonTaxa);
		if (node.metaDataString !=null && node.metaDataString.length() > 0) {
			node.metaDataString += ",";
		} else {
			node.metaDataString = "";
		}
		node.metaDataString += "!cartoon={" + cartoonTaxa.size() + ",0.0}";
	}

	public static void main(String[] args) throws Exception {
		new Application(new TreeCartooniser(), "Tree Cartooniser", args);
	}

}
