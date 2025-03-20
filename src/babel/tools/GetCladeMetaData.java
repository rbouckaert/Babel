package babel.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import babel.tools.utils.MemoryFriendlyTreeSet;
import beastfx.app.tools.Application;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Input.Validate;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;

@Description("Get meta data from clade into trace log file")
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
        MemoryFriendlyTreeSet trees = new MemoryFriendlyTreeSet(srcInput.get().getAbsolutePath(), 0);
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
		new Application(new GetCladeMetaData(), "Clade Meta Data", args);
	}

}
