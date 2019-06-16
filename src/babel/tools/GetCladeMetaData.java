package babel.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import beast.app.treeannotator.TreeAnnotator;
import beast.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
import beast.app.util.Application;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.util.Log;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;

public class GetCladeMetaData extends TreeCombiner {
	final public Input<File> cfgFileInput = new Input<>("cfg", "configuration file containing name of taxa, one on each line", Validate.REQUIRED);
	final public Input<Boolean> originateInput = new Input<>("originate", "use originate of clade instead of MRCA of clade", false);

	Set<String> taxa;
	
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
        MemoryFriendlyTreeSet trees = new TreeAnnotator().new MemoryFriendlyTreeSet(srcInput.get().getAbsolutePath(), 0);
        trees.reset();
    	Tree tree = trees.next();
        trees.reset();
        int k = 0;
        while (trees.hasNext()) {
        	Log.warning("tree " + k++);
        	tree = trees.next();
            Node node = getMRCA(tree, taxa);
            if (originateInput.get()) {
            	node = node.getParent();
            }
            out.println("height=" + node.getHeight() + "," + node.metaDataString);
        }
        out.println(); 
        
        Log.warning("Done");
	}

	protected void processCfgFile() throws IOException {
		taxa = new HashSet<>();
		BufferedReader fin = new BufferedReader(new FileReader(cfgFileInput.get()));
        String str = null;
        while (fin.ready()) {
            str = fin.readLine();
            if (!str.matches("\\s*") && !str.startsWith("#")) {
            	taxa.add(str.trim());
            }
        }
        fin.close();	
	}	
		
	public static void main(String[] args) throws Exception {
		new Application(new GetCladeMetaData(), "Clage Meta Data", args);
	}

}
