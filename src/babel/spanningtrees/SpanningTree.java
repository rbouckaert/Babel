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
	public Input<File> kmlFileInput = new Input<>("kml","kml file containing point locations of languages",Validate.REQUIRED);
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
		pane.loadLocations(kmlFileInput.get().getPath());
		pane.loadData(nexusFileInput.get().getPath(), cognateFileInput.get().getPath());
		pane.loadBGImage(backgroundFileInput.get().getPath());
		frame.add(pane);
		frame.addKeyListener(pane);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);	
		frame.setVisible(true);
	}

	static ConsoleApp consoleapp;
	public static void main(String[] args) throws Exception {
		new Application(new SpanningTree(), "SpanningTree", args);
	}

}
