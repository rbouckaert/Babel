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
import beast.base.evolution.alignment.Sequence;
import beast.base.parser.NexusParser;

@Description("Convert NEXUS alignment file into fasta alignemnt file")
public class Nexus2Fasta extends Runnable {
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

		for (Sequence seq : alignment.sequenceInput.get()) {
			String taxon = seq.taxonInput.get();
			out.println(">" + taxon);
			out.println(seq.dataInput.get());
		}
		Log.err.println("Done");	
	}

	private Alignment loadFile(File file) throws IOException {
		NexusParser parser = new NexusParser();
		parser.parseFile(file);
		return parser.m_alignment;
	}
	
	public static void main(String[] args) throws Exception {
		new Application(new Nexus2Fasta(), "NEXUS alignment to fasta converter", args);

	}

}
