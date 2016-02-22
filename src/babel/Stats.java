package babel;

import java.io.File;
import java.io.IOException;
import java.util.List;

import babel.util.Application;
import babel.util.ConsoleApp;
import beast.app.beauti.BeautiConfig;
import beast.app.beauti.BeautiDoc;
import beast.app.draw.BEASTObjectDialog;
import beast.app.draw.BEASTObjectPanel;
import beast.core.Description;
import beast.core.Input;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import babel.util.NexusParser;

@Description("Tool to print statistics on cognate data, useful for identifyng potential anomalies in the alignment.")
public class Stats extends beast.core.Runnable {
	final public Input<File> nexusFileInput = new Input<>("nexusFile", "nexus file containing sequence alignment, charsetlabels and partition information", new File("examples/IELex.nex"));
    
	public ConsoleApp consoleApp = null;

    @Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		NexusParser parser = new NexusParser();
		parser.parseFile(nexusFileInput.get());
		Alignment data = parser.m_alignment;
		if (data == null) {
			throw new IOException("No alignment found in nexus file");
		}
		
		String [] cognates = parser.charstatelabels;
		if (cognates.length != data.getSiteCount()) {
			throw new IOException("Found " + cognates.length +" cognate labels, but the alignment contains " + data.getSiteCount() + " sites.");
		}
		
		Log.info.println("Singletons:");
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
				Log.info.println(taxaNames.get(taxonNr) + " " + cognates[i]);
			}
			cognateCounts[oneCount]++;
		}
		Log.info.println(cognateCounts[1] + " singletons in total.\n");
		

		
		
		Log.info.println("Cognate distribution (number of cognates contained in N languages, where N is the first column):");
		for (int i = 0; i < cognateCounts.length; i++) {
			Log.info.println(i + " " + cognateCounts[i]);
		}
		
		
		
		Log.info.println("Duplicates:");
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
		Application main = null;
		try {
			// create the runnable class with application that we want to launch
			Stats analyser = new Stats();
			
			if (args.length == 0) {
				// try the GUI version

				// need to set the ID of the BEAST-object
				analyser.setID("PathSampleAnalyser");
				
				// then initialise
				analyser.initAndValidate();
				
				// create BeautiDoc and beauti configuration
				BeautiDoc doc = new BeautiDoc();
				doc.beautiConfig = new BeautiConfig();
				doc.beautiConfig.initAndValidate();
			
				// create panel with entries for the application
				BEASTObjectPanel panel = new BEASTObjectPanel(analyser, analyser.getClass(), doc);
				
				// wrap panel in a dialog
				BEASTObjectDialog dialog = new BEASTObjectDialog(panel, null);
				if (dialog.showDialog()) {
					dialog.accept(analyser, doc);
					analyser.initAndValidate();

					// create a console to show standard error and standard output
					analyser.consoleApp = new ConsoleApp("Babel stats", // name 
							"Babel stats" // console title
							);

					analyser.run();
				}
				return;
			}

			// continue with the command line version
			main = new Application(analyser);
			main.parseArgs(args, false);
			analyser.initAndValidate();
			analyser.run();
		} catch (Exception e) {
			System.out.println(e.getMessage());
			if (main != null) {
				System.out.println(main.getUsage());
			}
		}
	}

}
