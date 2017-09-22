package babel.tools;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import beast.util.NexusParser;
import beast.util.Randomizer;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.evolution.alignment.Sequence;

@Description("Randomly knock out cognates from cognate alignment")
public class CognateSetRandomizer extends Runnable {
	final public Input<File> nexusInput = new Input<>("nex", "nexus file with charsetlabels encoding for character sets",
			new File("file.nex"));
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));
	final public Input<Double> percentageInput = new Input<>("percentage","percentage of cognatges to knock out", 5.0);
	final public Input<Boolean> isAscertainedInput = new Input<>("isAscertained", "if true, assume first column in each alignmnet is ascertainemnet column that should not be removed", true);

	@Override
	public void initAndValidate() {
	}

	class OnePosition{
		int i, j;
		boolean done;
		OnePosition(int i, int j) {
			this.i = i;
			this.j = j;
			done = false;
		}
	}
	
	@Override
	public void run() throws Exception {
		if (nexusInput.get() == null || nexusInput.get().getName().equals("[[none]]")) {
			throw new IllegalArgumentException("A valid nexus file must be specified");
		}
		NexusParser parser = new NexusParser();
		parser.parseFile(nexusInput.get());
		Alignment data = parser.m_alignment;
		List<String> taxa = data.getTaxaNames();
		String seq  = data.getSequenceAsString(taxa.get(0));
		char[][] seqs = new char[seq.length()][data.getTaxonCount()];
		List<OnePosition> ones = new ArrayList<>();
		for (int i = 0; i < taxa.size(); i++) {
			seq  = data.getSequenceAsString(taxa.get(i));
			for (int j = 0; j < seq.length(); j++) {
				seqs[j][i] = seq.charAt(j);
				if (seqs[j][i] == '1') {
					ones.add(new OnePosition(i,j));
				}
			}
		}
		
		// knock out fraction of ones
		int oneCount = ones.size();
		int knockOuts = (int) (percentageInput.get() * oneCount / 100.0);
		if (knockOuts >= oneCount) {
			throw new IllegalArgumentException("percentage to knock out should be less than 100%");			
		}
		Log.warning("Knocking out " + knockOuts + " ones (" + percentageInput.get() + "% of the ones)");
		for (int i = 0; i < knockOuts; i++) {
			int k = -1;
			do {
				k = Randomizer.nextInt(oneCount);
			} while (ones.get(k).done);
			OnePosition p = ones.get(k);
			seqs[p.j][p.i] = '0';
			p.done = true;
		}
		
		// reconstruct sequences
		List<Sequence> ss = data.sequenceInput.get();
		for (int i = 0; i < taxa.size(); i++) {
			StringBuilder buf = new StringBuilder();
			for (int j = 0; j < seqs.length; j++) {
				buf.append(seqs[j][i]);
			}
			ss.get(i).dataInput.set(buf.toString());
		}
		
		
		data.initAndValidate();
		for (Alignment f : parser.filteredAlignments) {
			f.initAndValidate();
		}

		
		// build new sequences
		StringBuilder [] newSeqs = new StringBuilder[taxa.size()];
		for (int i = 0; i < taxa.size(); i++) {
			newSeqs[i] = new StringBuilder();
		}
		
		int [] boundaries = new int[parser.filteredAlignments.size()];
		int k = 0;
		for (Alignment f : parser.filteredAlignments) {
			int [] pattern = f.getPattern(f.getPatternIndex(0));
			for (int j = 0; j < taxa.size(); j++) {
				if (isAscertainedInput.get()) {
					if (pattern[j] == 0 || pattern[j] == 1) {
						newSeqs[j].append(" 0");					
					} else {
						newSeqs[j].append(" ?");					
					}
				} else {
					newSeqs[j].append(" ");		
				}
			}
			
			for (int i = 0; i < f.getSiteCount(); i++) {
				boolean include = true;
				pattern = f.getPattern(f.getPatternIndex(i));
				if (isAscertainedInput.get() || i > 0) {
					boolean allZero = true;
					for (int d : pattern) {
						if (d == 1) {
							allZero = false;
							break;
						}
					}
					include = !allZero;
				}
				if (include) {
					for (int j = 0; j < taxa.size(); j++) {
						switch (pattern[j]) {
							case 0: newSeqs[j].append("0");break;
							case 1: newSeqs[j].append("1");break;
							default: newSeqs[j].append("?");
						}
					}
				}
			}
			boundaries[k] = newSeqs[0].length() - k - 1;
			k++;
		}
		
		
		StringBuilder charSets = new StringBuilder();
		int prev = 1;
		for (k = 0; k < parser.filteredAlignments.size(); k++) {
			Alignment f = parser.filteredAlignments.get(k);
			charSets.append("charset " + f.getID() + " = " + prev + "-" + boundaries[k]);
			charSets.append(";\n");
			prev = boundaries[k] + 1;
		}
		
		// output
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getName());
			out = new PrintStream(outputInput.get());
		}

		out.print("#NEXUS\n");
		out.print("BEGIN DATA;\n");
		out.print("DIMENSIONS NTAX=" + taxa.size() + " NCHAR=" + (newSeqs[0].length() - k) + ";\n");
		out.print("FORMAT DATATYPE=BINARY MISSING=? GAP=-  SYMBOLS=\"01\";\n");
		out.print("MATRIX\n");
		for (int i = 0; i < taxa.size(); i++) {
			out.print(data.getTaxaNames().get(i) + " ");
			if (data.getTaxaNames().get(i).length() < 30) {
				out.print("                              ".substring(data.getTaxaNames().get(i).length()));
			}
			out.print(newSeqs[i].toString());
			out.println();
		}
		out.println(";\nEND;\n");
		
		out.print("BEGIN ASSUMPTIONS;\n");
		out.print(charSets.toString());
		out.println("END;\n");


		Log.warning("Done!");	}

	public static void main(String[] args) throws Exception {
		new Application(new CognateSetRandomizer(), "CognateSetRandomizer", args);
	}

}
