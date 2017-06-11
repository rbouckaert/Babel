package babel.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.Input.Validate;
import beast.core.util.Log;

@Description("relabels taxe in a tree file. Usfeful for instance when labels are iso codes and language names are required for visualisation")
public class TreeRelabeller extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out","output file. Print to stdout if not specified");
	final public Input<File> labelMapInput = new Input<>("labelMap","tab delimited text file with list of source and target labels", Validate.REQUIRED);

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		// read label map
		Map<String, String> labelMap = new HashMap<>();
		BufferedReader fin = new BufferedReader(new FileReader(labelMapInput.get()));
		String s;
		while ((s = readLine(fin)) != null) {
			String [] strs = s.split("\t");
			if (strs.length >=2) {
				labelMap.put(strs[0], strs[1]);
			}
		}
		fin.close();
		
		
		
		// open file for writing
        PrintStream out = System.out;
        if (outputInput.get() != null) {
        	out = new PrintStream(outputInput.get());
        }
        out.println("#NEXUS");
        out.println("Begin trees;\n"+
        			"        Translate");

        // process input tree file
		fin = new BufferedReader(new FileReader(treesInput.get()));
        // read to first non-empty line within trees block
        String str = fin.readLine().trim();
        while (str != null && !str.toLowerCase().contains("translate")) {
            str = fin.readLine().trim();
        }

        final Map<String, String> translationMap = new HashMap<>();
        // if first non-empty line is "translate" then parse translate block
        if (str.toLowerCase().contains("translate")) {

            String line = readLine(fin);
            final StringBuilder translateBlock = new StringBuilder();
            while (line != null && !line.trim().toLowerCase().equals(";")) {
                translateBlock.append(line.trim());
                line = readLine(fin);
            }
            final String[] taxaTranslations = translateBlock.toString().split(",");
            for (final String taxaTranslation : taxaTranslations) {
                final String[] translation = taxaTranslation.split("[\t ]+");
                if (translation.length == 2) {
                    translationMap.put(translation[0], translation[1]);
//                    System.out.println(translation[0] + " -> " + translation[1]);
                } else {
                    Log.err.println("Ignoring translation:" + Arrays.toString(translation));
                }
            }
       }
        Object [] indices = translationMap.keySet().toArray();
        Arrays.sort(indices, (o1,o2) -> {
        	return new Integer(o1.toString()) > (new Integer(o2.toString())) ? 1 : -1;
        });
        StringBuilder ignored = new StringBuilder();
        StringBuilder translate = new StringBuilder();
        for (Object key : indices) {
        	String label = translationMap.get(key);
        	if (labelMap.containsKey(label)) {
        		label = labelMap.get(label);
        	} else {
        		ignored.append(" " + label);
        	}
        	if (translate.length() > 0) {
        		translate.append(",\n");
        	}
        	
        	translate.append("\t\t" + key.toString() + " ");
        	if (label.indexOf(' ') >= 0) {
        		translate.append("'" + label + "'");
        	} else {
        		translate.append(label);
        	}
        }
        out.println(translate.toString());
        out.println(";");
        if (ignored.length() > 0) {
        	Log.warning.println("Could not find mapping for following labels: " + ignored.toString());
        }
        
        // process set of trees
		while ((str = readLine(fin)) != null) {
			out.println(str);
		}
		fin.close();
		if (out != System.out) {
			out.close();
		}
		Log.warning("All done. " + (outputInput.get() != null ? "Results in " + outputInput.get().getPath() : ""));

	}

    String readLine(BufferedReader fin) throws IOException {
        if (!fin.ready()) {
            return null;
        }
        //lineNr++;
        return fin.readLine();
    }

	
	public static void main(String[] args) throws Exception {
		new Application(new TreeRelabeller(), "TreeRelabeller", args);
	}
}
