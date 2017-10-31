package babel.tools;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintStream;

import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.util.TreeParser;
import beast.core.Input.Validate;
import beast.core.util.Log;

@Description("Convert Newick tree file ")
public class Newick2Nexus extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees","Newick file containing a tree set", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));

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

		BufferedReader fin = new BufferedReader(new FileReader(treesInput.get()));
        String str = null;
        int k = 0;
        TreeParser parser = null;
        while (fin.ready()) {
            str = fin.readLine();
            if (!str.matches("\\s*")) {
	            parser = new TreeParser(str);
	            if (k == 0) {
	            	parser.init(out);
	            }
	            out.println();
	            parser.log(k, out);
	            k++;
            }
        }
        fin.close();

        out.println();
        parser.close(out);
        Log.err.println("Done");
	}

	public static void main(String[] args) throws Exception {
		new Application(new Newick2Nexus(), "Newick 2 Nexus", args);
	}

}
