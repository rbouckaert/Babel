package babel.tools;

import java.io.IOException;
import java.io.PrintStream;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.XMLFile;
import beast.core.BEASTInterface;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.Input.Validate;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.evolution.alignment.Sequence;
import beast.util.XMLParser;
import beast.util.XMLParserException;

@Description("Convert BEAST XML file into fasta alignemnt file")
public class XML2Fasta extends Runnable {
	final public Input<XMLFile> xmlInput = new Input<>("xml", "BEAST XML file containing an alignment",
			Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		Alignment alignment = loadFile(xmlInput.get());

		// open file for writing
		PrintStream out = System.out;
		if (outputInput.get() != null) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}

		out.println("#NEXUS");

		for (Sequence seq : alignment.sequenceInput.get()) {
			String taxon = seq.taxonInput.get();
			out.println(">" + taxon);
			out.println(seq.dataInput.get());
		}
		out.println(";\nend;");
		Log.err.println("Done");	
	}

	private Alignment loadFile(XMLFile xmlFile) throws SAXException, IOException, ParserConfigurationException, XMLParserException {
		XMLParser parser = new XMLParser();
		Runnable runnable = parser.parseFile(xmlFile);		
		return findAlignment(runnable);
	}

	private Alignment findAlignment(BEASTInterface bi) {
		if (bi instanceof Alignment) {
			return (Alignment) bi;
		}
		for (BEASTInterface o : bi.listActiveBEASTObjects()) {
			Alignment a = findAlignment(o);
			if (a != null) {
				return a;
			}
		}
		return null;
	}

	public static void main(String[] args) throws Exception {
		new Application(new XML2Fasta(), "XML to fasta converter", args);

	}

}
