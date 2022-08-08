package babel.tools;

import java.io.File;
import java.io.PrintStream;
import babel.util.NexusParser;
import beastfx.app.inputeditor.BeautiDoc;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Log;

@Description("Load nexus file and based on CharsetLabels, produce nexus file with charsets")
public class CharsetlabelsToCharsets extends Runnable {
	public Input<File> nexusInput = new Input<>("nex", "nexus file with charsetlabels encoding for character sets",
			new File("file.nex"));
	public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		if (nexusInput.get() == null || nexusInput.get().getName().equals("[[none]]")) {
			throw new IllegalArgumentException("A valid nexus file must be specified");
		}
		NexusParser parser = new NexusParser();
		parser.parseFile(nexusInput.get());
		if (parser.charstatelabels == null || parser.charstatelabels.length == 0) {
			throw new IllegalArgumentException("Charsetlabels in nesus file must be specified (but could not find any)");
		}
		
		// process
		StringBuilder buf = new StringBuilder();
		int start = 0;
		String prevName = sanitise(parser.charstatelabels[0]);
		int k = 0;
		for (int i = 0; i < parser.charstatelabels.length; i++) {
			String name = sanitise(parser.charstatelabels[i]);
			if (!name.equals(prevName)) {
				buf.append("charset " + prevName + " = " + (start+1) + "-" + i + ";\n");
				start = i;
				prevName = name;
				k++;
			}
		}
		buf.append("charset " + prevName + " = " + (start+1) + "-" + parser.charstatelabels.length + ";\n");
		
		// output
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getName());
			out = new PrintStream(outputInput.get());
		}
		String old = BeautiDoc.load(nexusInput.get());
		out.println(old);
		out.println("begin assumptions;");
		out.print(buf.toString());
		out.println("end;");

		Log.warning(k + " charsets");
		Log.warning("Done!");
	}

	private String sanitise(String str) {
		// remove digits
		str = str.replaceAll("[0-9]", "");
		// remove whitespace
		str = str.replaceAll("\\s", "");
		str = str.replaceAll("_.*", "");
		return str;
	}

	public static void main(String[] args) throws Exception {
		new Application(new CharsetlabelsToCharsets(), "Add Nexus Charsets", args);
	}
}
