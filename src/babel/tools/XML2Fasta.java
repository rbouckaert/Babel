package babel.tools;


import java.io.PrintStream;

import beastfx.app.inputeditor.BeautiDoc;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.XMLFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Input.Validate;
import beast.base.core.Log;

@Description("Convert BEAST XML file into fasta alignemnt file")
public class XML2Fasta extends Runnable {
	final public Input<XMLFile> xmlInput = new Input<>("xml", "BEAST XML file containing an alignment",
			Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));
	final public Input<Boolean> ignoreEmptySequencesInput = new Input<>("ignoreEmpty", "ignore sequences that have no data",
			true);

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		// open file for writing
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}

		String xml = BeautiDoc.load(xmlInput.get());
		String [] strs = xml.split("<");
		for (String str : strs) {
			if (str.startsWith("sequence")) {
				int i = str.indexOf("taxon=");
				if (i >= 0) {
					char c = str.charAt(i + 6);
					int j = str.indexOf(c, i + 8);
					if (j < 0) {
						throw new IllegalArgumentException("ill formed XML sequence: could not find matching " +c + "-character for taxon");
					}
					String taxon = str.substring(i+7, j);
					
					i = str.indexOf("value=");
					if (i < 0) {
						Log.warning("ill formed XML sequence: sequence without value attribute found");
					} else {
						c = str.charAt(i + 6);
						j = str.indexOf(c, i + 8);
						if (j < 0) {
							throw new IllegalArgumentException("ill formed XML sequence: could not find matching " +c + "-character for value");
						}
						String data = str.substring(i+7, j);
						boolean hasData = data.length() > 1 || (data.charAt(0)!='?' && data.charAt(0)!='-');
						if (hasData || !ignoreEmptySequencesInput.get()) {
							out.println(">" + taxon);
							out.println(data);
						}
					}
				}
			}
		}

		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			out.close();
		}

		Log.err.println("Done");	
	}



	public static void main(String[] args) throws Exception {
		new Application(new XML2Fasta(), "XML to fasta converter", args);

	}

}
