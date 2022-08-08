package babel.tools;



import java.io.File;
import java.io.PrintStream;
import beast.base.parser.NexusParser;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Log;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.datatype.DataType;

@Description("Add ascertainment column with all zeros except for entries with missing data")
public class AddAscertainmentColumn extends Runnable {
	public Input<File> nexusInput = new Input<>("nex", "nexus file with binary data",
			new File("file.nex"));
	public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		if (nexusInput.get() == null || nexusInput.get().getName().equals("[[none]]")) {
			throw new IllegalArgumentException("A valid nesus file must be specified");
		}
		NexusParser parser = new NexusParser();
		parser.parseFile(nexusInput.get());
		if (parser.filteredAlignments.size() == 0) {
			throw new IllegalArgumentException("Expected data split into charsets, but none found (if there is a single alignment you can search repace to add a 0-column in a text editor)");
		}
		
		// process
		Alignment data = parser.m_alignment;
		int taxonCount = data.getTaxonCount();
		int seqLen = data.getSiteCount() + parser.filteredAlignments.size();
		
		StringBuilder buf = new StringBuilder();
		buf.append("#NEXUS\n");
		buf.append("BEGIN DATA;\n");
		buf.append("DIMENSIONS NTAX=" + taxonCount + " NCHAR=" + seqLen + ";\n");
		buf.append("FORMAT DATATYPE=STANDARD MISSING=? GAP=-  SYMBOLS=\"01\";\n");
		buf.append("MATRIX\n");
		
		
		for (int i = 0; i < taxonCount; i++) {
			String name = data.getTaxaNames().get(i);
			buf.append(name + " ");
			if (name.length() < 30) {
				buf.append("                              ".substring(0, 30-name.length()));
			}
			for (int j = 0; j < parser.filteredAlignments.size(); j++) {
				Alignment subData = parser.filteredAlignments.get(j);
				DataType dataType = subData.getDataType();
				if (dataType.isAmbiguousState(subData.getPattern(0)[i])) {
					buf.append("?");
				} else {
					buf.append("0");
				}
				for (int k = 0; k < subData.getSiteCount(); k++) {
					int site = subData.getPattern(i, subData.getPatternIndex(k));
					buf.append(dataType.getCode(site));
				}
				buf.append(" ");
			}
			buf.append("\n");
		}		
		buf.append(";\nEND\n");
		
		buf.append("begin assumptions;\n");
		int start = 1;
		for (int j = 0; j < parser.filteredAlignments.size(); j++) {
			Alignment subData = parser.filteredAlignments.get(j);
			int end = start + subData.getSiteCount();
			buf.append("charset " + subData.getID() + " = " + start + "-" + end + ";\n");
			start = end + 1;
		}
		buf.append("end;\n");
		
		// output
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getName());
			out = new PrintStream(outputInput.get());
		}

		out.print(buf.toString());

		Log.warning("Done!");
	}

	public static void main(String[] args) throws Exception {
		new Application(new AddAscertainmentColumn(), "Add Ascertainment Column", args);
	}
}
