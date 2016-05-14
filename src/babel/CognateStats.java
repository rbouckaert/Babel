package babel;

import java.io.File;
import java.io.IOException;
import java.util.List;

import beast.app.beauti.BeautiConfig;
import beast.app.beauti.BeautiDoc;
import beast.app.draw.BEASTObjectDialog;
import beast.app.draw.BEASTObjectPanel;
import beast.app.util.Application;
import beast.app.util.ConsoleApp;
import beast.core.Description;
import beast.core.Input;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import javafx.embed.swing.JFXPanel;
import babel.util.NexusParser;

@Description("Tool to print statistics on cognate data, useful for identifyng potential anomalies in the alignment.")
public class CognateStats extends beast.core.Runnable {
	final public Input<File> nexusFileInput = new Input<>("nexusFile", "nexus file containing sequence alignment, charsetlabels and partition information", new File("examples/nexus/ringe.nex"));
    
	public static ConsoleApp app = null;

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
		{
			// initialise JavaFX for console
			JFXPanel jfxPanel = new JFXPanel();
		}
		Application main = null;
		try {
			// create the class with application that we want to launch
			CognateStats sampler = new CognateStats();
			
			if (args.length == 0) {
				// try the GUI version
				
				// need to set the ID of the BEAST-object
				sampler.setID("PathSampler");
				
				// then initialise
				sampler.initAndValidate();
				
				// create BeautiDoc and beauti configuration
				BeautiDoc doc = new BeautiDoc();
				doc.beautiConfig = new BeautiConfig();
				doc.beautiConfig.initAndValidate();				
			
				// create panel with entries for the application
				BEASTObjectPanel panel = new BEASTObjectPanel(sampler, sampler.getClass(), doc);
				
				// wrap panel in a dialog
				BEASTObjectDialog dialog = new BEASTObjectDialog(panel, null);

				// show the dialog
				if (dialog.showDialog()) {
					dialog.accept(sampler, doc);
					// create a console to show standard error and standard output
					app = new ConsoleApp("Babel Stats", "Babel Stats", null);
					sampler.initAndValidate();
					sampler.run();
				}
				return;
			}

			// continue with the command line version
			main = new Application(sampler);
			main.parseArgs(args, false);
			sampler.initAndValidate();
			sampler.run();
			
		} catch (Exception e) {
			// error handling
			System.out.println(e.getMessage());
			if (main != null) {
				System.out.println(main.getUsage());
			}
		}
	}

}
