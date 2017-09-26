package babel.tools.dplace;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import beast.app.beauti.BeautiDoc;
import beast.app.util.Application;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.evolution.tree.Node;
import beast.util.TreeParser;

@Description("Pre-process glottolog files exported from http://glottolog.org/meta/downloads")
public class TreeConstraintProvider extends Runnable {
	final public Input<File> treeFileInput = new Input<>("treeFile","location of classification as text file in newick tree-glottolog-newick.txt", new File("tree-glottolog-newick.txt"));
	final public Input<File> taxaFileInput = new Input<>("taxaFile", "file containing list of glottolog codes, one per line", new File("taxa.txt"));
	
	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		String constraints = processTreeFile();
		Set<String> taxa = processTaxaFile();
		
		String newick = filterNewick(constraints, taxa);
		Map<String,String> taxonSets = filterTaxonSets(taxa);
		Log.warning(newick);
		for (String fam : taxonSets.keySet()) {
			Log.warning(fam + " = " + taxonSets.get(fam));
		}
	}

	private Map<String, String> filterTaxonSets(Set<String> taxa) throws IOException {
        BufferedReader fin = new BufferedReader(new FileReader(treeFileInput.get()));
        String str = null;
        Map<String,String> taxonSets = new LinkedHashMap<>();
        while (fin.ready()) {
            str = fin.readLine();
            String famName = str.substring(str.lastIndexOf(')') + 2);
            str = str.substring(0, str.lastIndexOf(')') + 1);
            famName = famName.substring(0, famName.indexOf('[')).trim();
            str = cleanUp(str);
            String matches = matches(str, taxa);
            if (matches.length() > 0) {
            	taxonSets.put(famName, matches);
            }
        }
        fin.close();
        return taxonSets;
	}

	private String cleanUp(String str) {
        str = str.replaceAll(";", "");
        str = str.replaceAll("-l-", "");
        str = str.replaceAll("\\[...\\]", "");
        str = str.replaceAll("]':1", "");
        str = str.replaceAll("'[^\\[]+\\[", "");
		return str;
	}

	private String matches(String str, Set<String> taxa) {
		String matches ="";
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (!(c == '(' || c == ')' || c ==',')) {
				String t = str.substring(i, i+8);
				if (taxa.contains(t)) {
					if (matches.length() > 7) {
						matches += ",";
					}
					matches += t;
				}
				i+=7;
			}
		}		
		return matches;
	}

	private String filterNewick(String constraints, Set<String> taxa) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < constraints.length(); i++) {
			char c = constraints.charAt(i);
			if (c == '(' || c == ')' || c ==',') {
				buf.append(c);
			} else {
				String t = constraints.substring(i, i+8);
				if (taxa.contains(t)) {
					buf.append(t);
				}
				i+=7;
			}
		}
		
		String c2 = buf.toString();
		int len = c2.length();
		do {
			len = c2.length();
			c2 = c2.replaceAll("\\(\\)", "");
			c2 = c2.replaceAll("\\(,", "(");
			c2 = c2.replaceAll(",\\)", ")");
			c2 = c2.replaceAll(",,", ",");
			c2 = c2.replaceAll("\\(([a-z][a-z][a-z][a-z][0-9][0-9][0-9][0-9])\\)", "$1");
			c2 = c2.replaceAll("\\(\\(([^\\(\\)])\\)\\)", "$1");
		} while (len > c2.length());
		
		TreeParser parser = new TreeParser(c2, false, true, true, 0, false);
		Node root = parser.getRoot();
		buf = new StringBuilder();
		toNewick(root, buf);
		c2 = buf.toString();
		
		return c2; 
	}

	private void toNewick(Node node, StringBuilder buf) {
		if (node.isLeaf()) {
			buf.append(node.getID());
		} else {
			if (node.getChildCount() == 1) {
				toNewick(node.getChild(0), buf);
			} else {
				buf.append('(');
				for (Node child : node.getChildren()) {
					toNewick(child, buf);
					buf.append(',');
				}
		        buf.replace(buf.length()-1, buf.length(), ")");
			}
		}
		
	}

	private Set<String> processTaxaFile() throws IOException {
		String str = BeautiDoc.load(taxaFileInput.get());
		str = str.replaceAll(" ", "");
		String [] taxa = str.split("\n");
		Set<String> taxaSet = new LinkedHashSet<>();
		for (String t : taxa) {
			if (t.length() == 8) {
				taxaSet.add(t);
			} else {
				Log.warning("Skipping >" + t + "< since length != 8");
			}
		}
		return taxaSet;
	}

	private String processTreeFile() throws IOException {
        BufferedReader fin = new BufferedReader(new FileReader(treeFileInput.get()));
        String str = null;
        StringBuilder buf = new StringBuilder();
        buf.append('(');
        while (fin.ready()) {
            str = fin.readLine();
            String famName = str.substring(str.lastIndexOf(')') + 2);
            str = str.substring(0, str.lastIndexOf(')') + 1);
            famName = famName.substring(0, famName.indexOf('['));
            str = cleanUp(str);
            buf.append(str);
            buf.append(',');
            //Log.warning(famName + " " + str);
        }
        // replace comma at end of string
        buf.replace(buf.length()-1, buf.length(), ")");
        fin.close();
        return buf.toString();
	}

	public static void main(String[] args) throws Exception {
		new Application(new TreeConstraintProvider(), "DPLACE pre-processor", args);
	}

}
