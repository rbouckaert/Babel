package babel.tools;


import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Input.Validate;
import beast.base.core.Log;
import beast.base.evolution.alignment.Alignment;
import beast.base.parser.NexusParser;
import beast.base.parser.XMLProducer;

@Description("Convert NEXUS alignment file into json file with alignemnt that can be used in BEAST with the -DF option")
public class Nexus2AlignmentInJson extends Runnable {
	final public Input<File> nexusInput = new Input<>("in", "NEXUS file containing an alignment",
			Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		Alignment alignment = loadFile(nexusInput.get());

		// open file for writing
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}


		out.print("{\"data\":\"");
		XMLProducer xml = new XMLProducer();
		String x = xml.toRawXML(alignment);
		x = x.replaceAll("\n", "");
		out.print(x);
		out.println("\"}");
	}

	private Alignment loadFile(File file) throws IOException {
		NexusParser parser = new NexusParser();
		parser.parseFile(file);
		return parser.m_alignment;
	}
	
	public static void main(String[] args) throws Exception {
		new Application(new Nexus2AlignmentInJson(), "NEXUS to json alignment converter", args);

	}

}
