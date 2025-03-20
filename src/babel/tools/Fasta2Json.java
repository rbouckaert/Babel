package babel.tools;

import java.io.PrintStream;

import beast.base.core.Description;
import beast.base.core.Log;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beastfx.app.tools.Application;

@Description("Convert FASTA file into JSON alignment file")
public class Fasta2Json extends Fasta2Nexus {

	@Override
	public void run() throws Exception {
		Alignment alignment = loadFile(fastaInput.get());

		// open file for writing
		PrintStream out = System.out;
		if (outputInput.get() != null) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}

		out.println("{\"data\":\"");
		int totalcount = -1;
		if (dataTypeInput.get().toLowerCase().equals("nucleotide")) {
			totalcount = 4;
		} else if (dataTypeInput.get().toLowerCase().equals("aminoacid")) {
			totalcount = 20;
		}
		for (Sequence seq : alignment.sequenceInput.get()) {
			String taxon = seq.taxonInput.get();
			out.print("<sequence taxon='" + taxon +"'");
			if (totalcount > 0) {
				out.print(" totalcount='" + totalcount +"'");
			}
			if (taxon.length() < spaces.length()) {
				out.print(spaces.substring(taxon.length()));
			}
			out.println(">" + seq.dataInput.get() + "</sequence>");
		}
		out.println("\"\n}");
		out.close();
		Log.err.println("Done");
	}

	public static void main(String[] args) throws Exception {
		new Application(new Fasta2Json(), "Fasta 2 JSON", args);		
	}
}
