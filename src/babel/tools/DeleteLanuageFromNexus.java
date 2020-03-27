package babel.tools;


import java.io.File;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import beast.app.beauti.BeautiDoc;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.util.NexusParser;

@Description("Remove languages from nexus file, remove sites that are all zeros or question marks")
public class DeleteLanuageFromNexus extends Runnable {
	public Input<File> nexusInput = new Input<>("nex", "nexus file with charsetlabels encoding for character sets",
			new File("file.nex"));
	public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));
	public Input<String> taxonInput = new Input<>("taxa","comma delimited list of taxa to delete", Validate.REQUIRED);
	public Input<Boolean> ignoreFirstInput = new Input<>("ignoreFirstColumn","if true, the first column is considered ascertainment correction column and therefore will not be deleted", true);
	
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
		Alignment data = parser.m_alignment;
		List<String> taxa = data.getTaxaNames();
		String seq  = data.getSequenceAsString(taxa.get(0));
		char[][] seqs = new char[seq.length()][data.getTaxonCount()];
		for (int i = 0; i < taxa.size(); i++) {
			seq  = data.getSequenceAsString(taxa.get(i));
			for (int j = 0; j < seq.length(); j++) {
				seqs[j][i] = seq.charAt(j);
			}
		}

		int n = seqs.length;
		Set<String> tabuTaxa = new HashSet<>();
		for (String s : taxonInput.get().split(",")) {
			tabuTaxa.add(s);
		}
		
		StringBuilder [] newSeqs = new StringBuilder[taxa.size()];
		for (int i = 0; i < newSeqs.length; i++) {
			if (!tabuTaxa.contains(taxa.get(i))) {
				newSeqs[i] = new StringBuilder();
			} else {
				newSeqs[i] = null;
			}
		}
		
		// process
		StringBuilder buf = new StringBuilder();
		
		int start = 0;
		int k = 0;
		for (Alignment filter: parser.filteredAlignments) {
			
		}
		for (int i = 1; i < n; i++) {
			if (!matches(seqs, i, i-1)) {
				buf.append("charset concept" + k + " = " + (start+1) + "-" + i + ";\n");
				start = i;
				k++;
			}
		}
		buf.append("charset concept" + k + " = " + (start+1) + "-" + n + ";\n");
		
		// output
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getName());
			out = new PrintStream(outputInput.get());
		}

//		if (sortInput.get()) {
//			out.print("#NEXUS\n");
//			out.print("BEGIN DATA;\n");
//			out.print("DIMENSIONS NTAX=" + taxa.size() + " NCHAR=" + n + ";\n");
//			out.print("FORMAT DATATYPE=STANDARD MISSING=? GAP=-  SYMBOLS=\"01\";\n");
//			out.print("MATRIX\n");
//			for (int i = 0; i < taxa.size(); i++) {
//				out.print(data.getTaxaNames().get(i) + " ");
//				for (int j = 0; j < n; j++) {
//					out.print(seqs[j][i]);
//				}
//				out.println();
//			}
//			out.println(";\nEND;\n");
//
//		} else {
			String old = BeautiDoc.load(nexusInput.get());
			out.println(old);
//		}
		out.println("begin assumptions;");
		out.print(buf.toString());
		out.println("end;");

		Log.warning(k + " charsets");
		Log.warning("Done!");
	}

	private boolean matches(char[][] seqs, int i, int j) {
		for (int k = 0; k < seqs[0].length; k++) {
			char a = seqs[i][k];
			char b = seqs[j][k];
			if (a == '?' && b != '?') {
				System.out.println(a + " =/= " + b);
				return false;
			}
			if (a != '?' && b == '?') {
				System.out.println(a + " =/= " + b);
				return false;
			}
		}
		return true;
	}

	
	public static void main(String[] args) throws Exception {
		new Application(new DeleteLanuageFromNexus(), "Add Nexus Charsets", args);
	}
}

