package babel.tools;

import java.io.IOException;
import java.io.PrintStream;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.XMLFile;
import beast.base.core.BEASTInterface;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Input.Validate;
import beast.base.core.Log;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.parser.XMLParser;
import beast.base.parser.XMLParserException;

@Description("Convert BEAST XML file into NEXUS alignemnt file")
public class XML2Nexus extends Runnable {
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
		String dataType = alignment.getDataType().getTypeDescription();
		int ntax = alignment.sequenceInput.get().size();
		int nchar = alignment.sequenceInput.get().get(0).dataInput.get().replaceAll("\\s","").length();
		out.println("begin data;\n" + 
				"dimensions ntax=" + ntax + " nchar=" + nchar + ";\n" + 
				"format datatype=" + dataType + 
				" interleave=yes gap=- missing=?;\n" + "matrix");

		for (Sequence seq : alignment.sequenceInput.get()) {
			String taxon = seq.taxonInput.get();
			out.print(taxon);
			if (taxon.length() < Fasta2Nexus.spaces.length()) {
				out.print(Fasta2Nexus.spaces.substring(taxon.length()));
			}
			out.print(" ");
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
		new Application(new XML2Nexus(), "XML to Nexus converter", args);

	}

}
