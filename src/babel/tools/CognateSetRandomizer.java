package babel.tools;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
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
	final public Input<Double> percentageInput = new Input<>("percentage","percentage of cognates to knock out", 0.0);
	final public Input<Double> mergePercentageInput = new Input<>("mpercentage","percentage of cognates for which to merge columns", 0.0);
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
	
	List<String> taxa;
	char[][] seqs;
	Alignment data;
	NexusParser parser;
	char[][] seqs2;
	int [] boundaries0;
	int [] boundaries;
	StringBuilder [] newSeqs;
	int k = 0;
	int n = 0;

	@Override
	public void run() throws Exception {
		if (nexusInput.get() == null || nexusInput.get().getName().equals("[[none]]")) {
			throw new IllegalArgumentException("A valid nexus file must be specified");
		}
		parser = new NexusParser();
		parser.parseFile(nexusInput.get());
		data = parser.m_alignment;
		taxa = data.getTaxaNames();
		String seq  = data.getSequenceAsString(taxa.get(0));
		seqs = new char[seq.length()][data.getTaxonCount()];
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
		reconstructSeqs();

		
		// merge columns for fraction of ones
		int mergers = (int) (mergePercentageInput.get() * oneCount / 100.0);
		if (mergers >= oneCount) {
			throw new IllegalArgumentException("percentage to mergers should be less than 100%");			
		}
		Log.warning("Merging  " + mergers + " ones (" + mergePercentageInput.get() + "% of the ones)");

		
		int merged = 0;
		while (merged < mergers) {
			int c1 = Randomizer.nextInt(n);
			int lo = Arrays.binarySearch(boundaries0, c1);
			if (lo < 0 || !isAscertainedInput.get()) {
				if (lo < 0) {
					lo = - lo - 2;
				}

				int hi = boundaries0[lo + 1];
				int c2 = lo + Randomizer.nextInt(hi - lo) + (isAscertainedInput.get() ? 1 : 0);
				for (int j = 0; j < taxa.size(); j++) {
					if (seqs2[j][c1] == '1' || seqs2[j][c2] == '2') {
						merged++;
						seqs2[j][c1] = '1';
						seqs2[j][c2] = '0';
					}
				}
			}
		}
		
				
		
		// reconstruct sequences
		reconstructSeqs2();
		
		output();

		Log.warning("Done!");	
	}

	private void output() throws IOException {
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
	}

	private void reconstructSeqs2() {
		newSeqs = new StringBuilder[taxa.size()];
		for (int i = 0; i < taxa.size(); i++) {
			newSeqs[i] = new StringBuilder();
		}
		
		k = 0;
		boundaries = new int[parser.filteredAlignments.size()];
		for (int r = 0; r < n; r++) {
			int [] pattern = getPattern(seqs2, r);
			if (r == 0 || r == boundaries0[k-1]) {
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
				if (r > 0) {
					boundaries[k-1] = newSeqs[0].length() - k-2;
				}
				k++;
			}
			
			boolean include = false;
			for (int d : pattern) {
				if (d == 1) {
					include = true;
					break;
				}
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
		boundaries[k - 1] = newSeqs[0].length() - k;
	}

	private void reconstructSeqs() {
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
		String seq  = data.getSequenceAsString(taxa.get(0));

		seqs2 = new char[data.getTaxonCount()][seq.length()];
		boundaries0 = new int[parser.filteredAlignments.size()];
		for (Alignment f : parser.filteredAlignments) {
			int [] pattern = f.getPattern(f.getPatternIndex(0));
			if (isAscertainedInput.get()) {
				for (int j = 0; j < taxa.size(); j++) {
					if (pattern[j] == 0 || pattern[j] == 1) {
						seqs2[j][n] = '0';					
					} else {
						seqs2[j][n] = '?';					
					}
				}
				n++;
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
							case 0: seqs2[j][n] = '0';break;
							case 1: seqs2[j][n] = '1';break;
							default: seqs2[j][n] = '?';
						}
					}
					n++;
				}
			}
			boundaries0[k] = n;
			k++;
		}	
	}

	private int[] getPattern(char[][] seqs2, int r) {
		int [] pattern = new int[seqs2.length];
		for (int i = 0; i < pattern.length; i++) {
			switch (seqs2[i][r]) {
			case '0': pattern[i] = 0; break;
			case '1': pattern[i] = 1; break;
			default: pattern[i] = -1; break;
			}
		}
		return pattern;
	}

	public static void main(String[] args) throws Exception {
		new Application(new CognateSetRandomizer(), "CognateSetRandomizer", args);
	}

}
