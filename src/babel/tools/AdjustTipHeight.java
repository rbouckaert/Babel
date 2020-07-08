package babel.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

import beast.app.treeannotator.TreeAnnotator;
import beast.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
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

@Description("Set tip heights of existing tree to match the heights from a list")
public class AdjustTipHeight extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees","Newick file containing a tree set", Validate.REQUIRED);
	final public Input<File> cfgFileInput = new Input<>("cfg", "tab separated configuration file containing two columns: "
			+ "column 1: name of taxon\n"
			+ "column 2: height (age) of taxon\n");
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));
	final public Input<Boolean> convertYearToMilleniumInput = new Input<>("year2millenium", "interpret heights in file as"
			+ "years (forward in time) and convet to millenium (backward in time from 2000)", false);
	final public Input<Boolean> includeMetaDataInput = new Input<>("includeMetaData", "if true, any available metadata is output in trees", true);

	Map<String,Double> heightMap = new LinkedHashMap<>();
	
	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}

		
		processCfgFile();
		
        // read trees one by one, adjust tip heights and write out relabeled tree in newick format
        MemoryFriendlyTreeSet trees = new TreeAnnotator().new MemoryFriendlyTreeSet(treesInput.get().getAbsolutePath(), 0);
        trees.reset();
    	Tree tree = trees.next();
        trees.reset();
        while (trees.hasNext()) {
        	tree = trees.next();
            adjustHeights(tree.getRoot());
            StringBuilder buf = new StringBuilder();
            Nexus2Newick.toShortNewick(tree.getRoot(), buf, includeMetaDataInput.get());
        	out.println(buf.toString());
        }
        out.println(); 
        
        Log.warning("Done");

	}

	private void adjustHeights(Node node) {
		if (node.isLeaf()) {
			if (heightMap.get(node.getID()) == null) {
				Log.warning("ERROR: Cannot find height for " + node.getID() + " in height-map");
				return;
			}
			double h = heightMap.get(node.getID());
			if (convertYearToMilleniumInput.get()) {
				h = (2000.0 - h) / 1000.0;
			}
			if (node.getParent().getHeight() < h) {
				Log.warning("Cannot set height of " + node.getID() + " to " + h + " since parent is too young " + 
						node.getParent().getHeight());
				h = Math.max(node.getParent().getHeight() - 0.001, 0.0);
			}
			node.setHeight(h);
		} else {
			for (Node child : node.getChildren()) {
				adjustHeights(child);
			}
		}
	}

	private void processCfgFile() throws IOException {
		BufferedReader fin = new BufferedReader(new FileReader(cfgFileInput.get()));
        String str = null;
        while (fin.ready()) {
            str = fin.readLine();
            if (!str.matches("\\s*") && !str.startsWith("#")) {
            	String [] strs = str.split("\t");
            	if (strs.length >= 2) {
            		heightMap.put(strs[0], Double.parseDouble(strs[1]));
            	}
            }
        }
        fin.close();	}

	public static void main(String[] args) throws Exception {
		new Application(new AdjustTipHeight(), "Adjust Tip Heights", args);

	}

}
