package babel.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.Input.Validate;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.evolution.alignment.Sequence;
import beast.evolution.datatype.DataType;
import beast.evolution.datatype.Nucleotide;
import beast.util.NexusParser;

@Description("Convert FASTA file into NEXUS alignemtn file")
public class Fasta2Nexus extends Runnable {
	final public Input<File> fastaInput = new Input<>("in", "fasta file containing an alignment",
			Validate.REQUIRED);
	final public Input<String> dataTypeInput = new Input<>("datatype",
			"data type of data in the fasta file, e.g., nucleotide, or aminoacid",
			new Nucleotide().getTypeDescription());
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));

	@Override
	public void initAndValidate() {
	}

	final public static String spaces = "                                              ";

	@Override
	public void run() throws Exception {
		Alignment alignment = loadFile(fastaInput.get());

		// open file for writing
		PrintStream out = System.out;
		if (outputInput.get() != null) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}

		out.println("#NEXUS");
		int ntax = alignment.sequenceInput.get().size();
		int nchar = alignment.sequenceInput.get().get(0).dataInput.get().length();
		out.println("begin data;\n"
				+ "dimensions ntax=" + ntax + " nchar=" + nchar + ";\n"
		 		+ "format datatype=" + dataTypeInput.get() + " interleave=yes gap=- missing=?;\n" 
		 		+ "matrix");

		for (Sequence seq : alignment.sequenceInput.get()) {
			String taxon = seq.taxonInput.get();
			out.print(taxon);
			if (taxon.length() < spaces.length()) {
				out.print(spaces.substring(taxon.length()));
			}
			out.print(" ");
			out.println(seq.dataInput.get());
		}
		out.println(";\nend;");
		Log.err.println("Done");
	}

	public Alignment loadFile(File file) throws IOException {
		// grab alignment data
		Map<String, StringBuilder> seqMap = new HashMap<>();
		List<String> taxa = new ArrayList<>();
		String currentTaxon = null;
		BufferedReader fin = new BufferedReader(new FileReader(file));
		String missing = "?";
		String gap = "-";
		int totalCount = dataTypeInput.get().equals("nucleotide") ? 4 : 20;

		while (fin.ready()) {
			String line = fin.readLine();
			if (line.startsWith(";")) {
				// it is a comment, ignore
			} else if (line.startsWith(">")) {
				// it is a taxon
				currentTaxon = line.substring(1).trim();
				// only up to first space
				currentTaxon = currentTaxon.replaceAll("\\s.*$", "");
			} else {
				// it is a data line
				if (currentTaxon == null) {
					fin.close();
					throw new RuntimeException("Expected taxon defined on first line");
				}
				if (seqMap.containsKey(currentTaxon)) {
					StringBuilder sb = seqMap.get(currentTaxon);
					sb.append(line);
				} else {
					StringBuilder sb = new StringBuilder();
					seqMap.put(currentTaxon, sb);
					sb.append(line);
					taxa.add(currentTaxon);
				}
			}
		}
		fin.close();

		int charCount = -1;
		Alignment alignment = new Alignment();
		for (final String taxon : taxa) {
			final StringBuilder bsData = seqMap.get(taxon);
			String data = bsData.toString();
			data = data.replaceAll("\\s", "");
			seqMap.put(taxon, new StringBuilder(data));

			if (charCount < 0) {
				charCount = data.length();
			}
			if (data.length() != charCount) {
				throw new IllegalArgumentException("Expected sequence of length " + charCount + " instead of "
						+ data.length() + " for taxon " + taxon);
			}
			// map to standard missing and gap chars
			data = data.replace(missing.charAt(0), DataType.MISSING_CHAR);
			data = data.replace(gap.charAt(0), DataType.GAP_CHAR);

			final Sequence sequence = new Sequence();
			data = data.replaceAll("[Xx]", "?");
			sequence.init(totalCount, taxon, data);
			sequence.setID(NexusParser.generateSequenceID(taxon));
			alignment.sequenceInput.setValue(sequence, alignment);
		}
		String ID = file.getName();
		ID = ID.substring(0, ID.lastIndexOf('.')).replaceAll("\\..*", "");
		alignment.setID(ID);

		alignment.dataTypeInput.setValue(dataTypeInput.get(), alignment);
		alignment.initAndValidate();
		return alignment;
	}
	
	public static void main(String[] args) throws Exception {
		new Application(new Fasta2Nexus(), "Fasta 2 Nexus", args);		
	}
	
}
