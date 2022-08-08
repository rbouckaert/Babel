package babel.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;

import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Input.Validate;
import beast.base.core.Log;

@Description("Convert phyml phy format to nexus alignment file")
public class Phy2Nexus extends Runnable {
	enum TYPE {nucleotide, protein};
	final public Input<File> phyInput = new Input<>("phy","Phyml phy file containing a sequence set", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));
	final public Input<TYPE> typeInput = new Input<>("datatype", "datatype of the sequence data (nucleotide or protein)", TYPE.nucleotide, TYPE.values());

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
        BufferedReader fin = new BufferedReader(new FileReader(phyInput.get()));
        String str = null;
        str = fin.readLine();
        String [] strs = str.trim().split("\\s+");
        String ntax = strs[0];
        String nchar = strs[1];

        // open file for writing
        PrintStream out = System.out;
        if (outputInput.get() != null) {
			Log.warning("Writing to file " + outputInput.get().getPath());
        	out = new PrintStream(outputInput.get());
        }
        
        out.println("#NEXUS");
        out.println("Begin DATA;");
        out.println("Dimensions ntax=" + ntax + " nchar=" + nchar+ ";");
        out.println("Format datatype=" + typeInput.get() +" gap=-;");
        out.println("Matrix");

        while (fin.ready()) {
            str = fin.readLine();
            out.println(str);
        }
        fin.close();

        out.println(";\nEnd;");
	}

	public static void main(String[] args) throws Exception {
		new Application(new Phy2Nexus(), "Phy 2 Nexus converter", args);

	}

}
