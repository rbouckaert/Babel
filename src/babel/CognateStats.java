package babel;

import java.io.File;
import java.io.IOException;
import java.util.List;

import beastfx.app.inputeditor.BeautiConfig;
import beastfx.app.inputeditor.BeautiDoc;
import beastfx.app.inputeditor.BEASTObjectDialog;
import beastfx.app.inputeditor.BEASTObjectPanel;
import beastfx.app.tools.Application;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.evolution.alignment.Alignment;
import babel.util.NexusParser;

@Description("Tool to print statistics on cognate data, useful for identifyng potential anomalies in the alignment.")
public class CognateStats extends beast.base.inference.Runnable {
	final public Input<File> nexusFileInput = new Input<>("nexusFile", "nexus file containing sequence alignment, charsetlabels and partition information", new File("examples/nexus/ringe.nex"));
    

    @Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		Log.warning.println("Languages (language, # columns, # states):");

		NexusParser parser = new NexusParser();
		parser.parseFile(nexusFileInput.get());
		Alignment data = parser.m_alignment;
		if (data == null) {
			throw new IOException("No alignment found in nexus file");
		}
		
		String [] cognates = parser.charstatelabels;
		if (cognates != null && cognates.length != data.getSiteCount()) {
			throw new IOException("Found " + cognates.length +" cognate labels, but the alignment contains " + data.getSiteCount() + " sites.");
		}
		
		Log.warning.println(" ");
		Log.warning.println("Singletons (language, column):");
		List<String> taxaNames = data.getTaxaNames();
		
		int [] cognateCounts = new int[taxaNames.size() + 1];
		
		for (int i = 0; i < data.getSiteCount(); i++) {
			int [] pattern = data.getPattern(data.getPatternIndex(i));
			int oneCount = 0;
			int taxonNr = -1;
			for (int k = 0; k <pattern.length; k++) {
				if (pattern[k] == 1) {
					oneCount++;
					taxonNr = k;
				}
			}
			if (oneCount == 1) {
				Log.info.println(taxaNames.get(taxonNr) + " " + (cognates == null ? i : cognates[i]));
			}
			cognateCounts[oneCount]++;
		}
		Log.info.println(cognateCounts[1] + " singletons in total.\n");
		

		
		
		Log.warning.println(" \nCognate distribution (number of cognates contained in N languages, where N is the first column):");
		for (int i = 0; i < cognateCounts.length; i++) {
			Log.info.println(i + " " + cognateCounts[i]);
		}
		
		
		
		Log.warning.println(" \nDuplicates:");
		int dupCount = 0;
		for (Alignment d : parser.filteredAlignments) {
			for (int i = 0; i < d.getSiteCount(); i++) {
				int [] pattern1 = d.getPattern(d.getPatternIndex(i));
				for (int j = i + 1; j < d.getSiteCount(); j++) {
					int [] pattern2 = d.getPattern(d.getPatternIndex(j));
					if (equals(pattern1, pattern2)) {
						dupCount++;
					}
				}
			}
		}
		Log.info.println(dupCount + " duplicates in total.\n");
	}
	

	private boolean equals(int[] pattern1, int[] pattern2) {
		for (int i = 0; i < pattern1.length; i++) {
			if (pattern1[i] != pattern2[i]) {
				return false;
			}
		}
		return true;
	}

	public static void main(final String[] args) throws Exception {
		new Application(new CognateStats(), "Babel Stats", args);
	}

}
