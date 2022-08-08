package babel.spanningtrees;


import java.io.File;

import javax.swing.JFrame;

import beastfx.app.tools.Application;
import beast.base.core.Input;
import beast.base.core.Input.Validate;
//import javafx.embed.swing.JFXPanel;
import beast.base.inference.Runnable;

public class SpanningTree extends Runnable {
	public Input<File> nexusFileInput = new Input<>("nexus","nexus file containing cognate data in binary format",Validate.REQUIRED);
	public Input<File> kmlFileInput = new Input<>("kml", "kml file containing point locations of languages");
	public Input<File> cognateFileInput = new Input<>("cognate","cognate file listing labels for each column");
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
		NexusBlockParser nexus = NexusBlockParser.parseFile(nexusFileInput.get());

		LocationParser locations = null;
		if(kmlFileInput.get() == null){
			locations = LocationParser.parseNexus(nexus);
		}else{
			locations = LocationParser.parseKMLFile(kmlFileInput.get());
		}

		CharstatelabelParser charstatelabels;
		if(cognateFileInput.get() == null){
			charstatelabels = CharstatelabelParser.parseNexus(nexus);
		}else{
			charstatelabels = CharstatelabelParser.parseCognateFile(cognateFileInput.get());
		}
		//Pane setup:
		pane.loadLocations(locations);
		pane.loadData(nexus, charstatelabels);
		pane.loadBGImage(backgroundFileInput.get().getPath());
		// Frame setup:
		frame.add(pane);
		frame.addKeyListener(pane);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);	
		frame.setVisible(true);
	}

	public static void main(String[] args) throws Exception {
		new Application(new SpanningTree(), "SpanningTree", args);
	}

}
