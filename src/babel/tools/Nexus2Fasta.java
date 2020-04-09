package babel.tools;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.Input.Validate;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.evolution.alignment.Sequence;
import beast.util.NexusParser;

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
		if (outputInput.get() != null) {
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
