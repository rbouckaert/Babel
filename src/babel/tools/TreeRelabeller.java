package babel.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import beastfx.app.treeannotator.TreeAnnotator;
import beastfx.app.treeannotator.TreeAnnotator.FastTreeSet;
import babel.tools.utils.MemoryFriendlyTreeSet;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Input.Validate;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;

@Description("Relabels taxa in a tree file. Usfeful for instance when labels are iso codes and language names are required for visualisation")
public class TreeRelabeller extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out","output file. Print to stdout if not specified");
	final public Input<File> labelMapInput = new Input<>("labelMap","tab delimited text file with list of source and target labels", Validate.REQUIRED);

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		// read label map
		Map<String, String> labelMap = new HashMap<>();
		BufferedReader fin = new BufferedReader(new FileReader(labelMapInput.get()));
		String s;
		while ((s = readLine(fin)) != null) {
			String [] strs = s.split("\t");
			if (strs.length >=2) {
				labelMap.put(strs[0], strs[1]);
			}
		}
		fin.close();
		
		
		
		// open file for writing
        PrintStream out = System.out;
        if (outputInput.get() != null) {
        	out = new PrintStream(outputInput.get());
			Log.warning("Writing to file " + outputInput.get().getPath());
        }

        
        FastTreeSet trees = new TreeAnnotator().new FastTreeSet(treesInput.get().getAbsolutePath(), 0);
        trees.reset();
        Tree tree = trees.next();
        relabel(tree.getRoot(), labelMap);
        tree.init(out);
        out.println();

        trees.reset();
        int i = 0;
        while (trees.hasNext()) {
        	tree = trees.next();
            out.println();
            out.print("tree STATE_" + i + " = ");
            final String newick = tree.getRoot().toSortedNewick(new int[1], true);
            out.print(newick);
            out.print(";");
        	i++;
        }
        out.println();
        out.println("end;");
        
if (false) {        
		fin = new BufferedReader(new FileReader(treesInput.get()));
        // read to first non-empty line within trees block
        String str = fin.readLine().trim();
        while (str != null && !str.toLowerCase().contains("translate")) {
            str = fin.readLine().trim();
        }
        while (!str.trim().equals(";")) {
            str = readLine(fin);
        }

        /*
        out.println("#NEXUS");
        out.println("Begin trees;\n"+
        			"        Translate");

        // process input tree file
		fin = new BufferedReader(new FileReader(treesInput.get()));
        // read to first non-empty line within trees block
        String str = fin.readLine().trim();
        while (str != null && !str.toLowerCase().contains("translate")) {
            str = fin.readLine().trim();
        }

        final Map<String, String> translationMap = new HashMap<>();
        // if first non-empty line is "translate" then parse translate block
        if (str.toLowerCase().contains("translate")) {

            String line = readLine(fin);
            final StringBuilder translateBlock = new StringBuilder();
            while (line != null && !line.trim().toLowerCase().equals(";")) {
                translateBlock.append(line.trim());
                line = readLine(fin);
            }
            final String[] taxaTranslations = translateBlock.toString().split(",");
            for (final String taxaTranslation : taxaTranslations) {
                final String[] translation = taxaTranslation.split("[\t ]+");
                if (translation.length == 2) {
                    translationMap.put(translation[0], translation[1]);
//                    System.out.println(translation[0] + " -> " + translation[1]);
                } else {
                    Log.err.println("Ignoring translation:" + Arrays.toString(translation));
                }
            }
       }
        Object [] indices = translationMap.keySet().toArray();
        Arrays.sort(indices, (o1,o2) -> {
        	return new Integer(o1.toString()) > (new Integer(o2.toString())) ? 1 : -1;
        });
        StringBuilder ignored = new StringBuilder();
        StringBuilder translate = new StringBuilder();
        for (Object key : indices) {
        	String label = translationMap.get(key);
        	if (labelMap.containsKey(label)) {
        		label = labelMap.get(label);
        	} else {
        		ignored.append(" " + label);
        	}
        	if (translate.length() > 0) {
        		translate.append(",\n");
        	}
        	
        	translate.append("\t\t" + key.toString() + " ");
        	if (label.indexOf(' ') >= 0) {
        		translate.append("'" + label + "'");
        	} else {
        		translate.append(label);
        	}
        }
        out.println(translate.toString());
        out.println(";");
        if (ignored.length() > 0) {
        	Log.warning.println("Could not find mapping for following labels: " + ignored.toString());
        }
        */
        // process set of trees
		while ((str = readLine(fin)) != null) {
			out.println(str);
		}
//        trees.reset();
//        int i = 0;
//        while (trees.hasNext()) {
//        	tree = trees.next();
//        	tree.log(i, out);
//        	i++;
//        }
//        out.println();
	}
		fin.close();
		if (out != System.out) {
			out.close();
		}
		Log.warning("All done. " + (outputInput.get() != null ? "Results in " + outputInput.get().getPath() : ""));

	}

    private void relabel(Node node, Map<String, String> labelMap) {
		if (labelMap.containsKey(node.getID())) {
			node.setID(labelMap.get(node.getID()));
		} else if (node.isLeaf()) {
			Log.warning("No label for " + node.getID());
		}
		if (!node.isLeaf()) {
			for (Node child : node.getChildren()) {
				relabel(child, labelMap);
			}
		}		
	}

	String readLine(BufferedReader fin) throws IOException {
        if (!fin.ready()) {
            return null;
        }
        //lineNr++;
        return fin.readLine();
    }

	
	public static void main(String[] args) throws Exception {
		new Application(new TreeRelabeller(), "TreeRelabeller", args);
	}
}
