package babel.spanningtrees;


import java.io.File;

import javax.swing.JFrame;

import beast.app.beauti.BeautiConfig;
import beast.app.beauti.BeautiDoc;
import beast.app.draw.BEASTObjectDialog;
import beast.app.draw.BEASTObjectPanel;
import beast.app.util.Application;
import beast.app.util.ConsoleApp;
import beast.core.Input;
import beast.core.Input.Validate;
//import javafx.embed.swing.JFXPanel;
import beast.core.Runnable;

public class SpanningTree extends Runnable {
	public Input<File> nexusFileInput = new Input<>("nexus","nexus file containing cognate data in binary format",Validate.REQUIRED);
	public Input<File> kmlFileInput = new Input<>("kml","kml file containing point locations of languages",Validate.OPTIONAL);
	public Input<File> cognateFileInput = new Input<>("cognate","cognate file listing labels for each column",Validate.REQUIRED);
	public Input<File> backgroundFileInput = new Input<>("background","image map in mercator projection used for background", Validate.REQUIRED);
	public Input<Double> maxDistInput = new Input<>("maximumDistance", "maximum distance to split on", CognateIO.COGNATE_SPLIT_THRESHOLD);
	
	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
	}

	@Override
	public void run() throws Exception {
		JFrame frame = new JFrame();
		frame.setSize(1024, 728);
		Panel pane = new Panel(new String[]{});
		CognateIO.COGNATE_SPLIT_THRESHOLD = maxDistInput.get();
		//Parsing data:
		NexusParser nexus = NexusParser.parseFile(nexusFileInput.get());
		LocationParser locations = LocationParser.parseNexus(nexus);
		if(locations.getLocationNames().size() == 0){
			//Only parsing KML if nexus didn't provide locations.
			locations = LocationParser.parseKMLFile(kmlFileInput.get().getPath());
		}
		//Pane setup:
		pane.loadLocations(locations);
		pane.loadData(nexusFileInput.get().getPath(), cognateFileInput.get().getPath());
		pane.loadBGImage(backgroundFileInput.get().getPath());
		// Frame setup:
		frame.add(pane);
		frame.addKeyListener(pane);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);	
		frame.setVisible(true);
	}

	static ConsoleApp consoleapp;
	public static void main(String[] args) throws Exception {
		{
			// initialise JavaFX for console
			//JFXPanel jfxPanel = new JFXPanel();
		}
		SpanningTree app = new SpanningTree();
		app.setID("Filter clades from tree set");
		Application main = new Application(app);
	
		main.parseArgs(args, false);

		// create BeautiDoc and beauti configuration
		BeautiDoc doc = new BeautiDoc();
		doc.beautiConfig = new BeautiConfig();
		doc.beautiConfig.initAndValidate();
				
		// create panel with entries for the application
		BEASTObjectPanel panel = new BEASTObjectPanel(app, app.getClass(), doc);
		
		// wrap panel in a dialog
		BEASTObjectDialog dialog = new BEASTObjectDialog(panel, null);

		// show the dialog
		if (dialog.showDialog()) {
			dialog.accept(app, doc);
			// create a console to show standard error and standard output
			consoleapp = new ConsoleApp("Cognate Spanning Tree", "SpanningTree", null);
			app.initAndValidate();
			app.run();
		}
	}

}
