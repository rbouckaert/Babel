package babel.tools.dplace;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import babel.spanningtrees.Location;
import beast.app.beauti.BeautiDoc;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.XMLFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.Input.Validate;
import beast.core.util.Log;
import beast.evolution.tree.Node;
import beast.util.TreeParser;

@Description("Pre-process glottolog files exported from http://glottolog.org/meta/downloads")
public class TreeConstraintProvider extends Runnable {
	final static String TREE_FILE_NAME = "/tree-glottolog-newick.txt";
	final static String GEO_FILE_NAME = "/languages-and-dialects-geo.csv";
	final public Input<File> glottoSourceDirInput = new Input<>("srcDir","source directory containing glottolog exports. "
			+ "expected files: " + TREE_FILE_NAME + " " + GEO_FILE_NAME + ".", new File("glotto"));
	final public Input<File> taxaFileInput = new Input<>("taxaFile", "file containing list of glottolog codes, one per line", new File("taxa.txt"));
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));

	final public Input<XMLFile> templateFileInput = new Input<>("template","name of template file. " +
			"The template file should be a valid beast2 XML file with a single (possibly empty) alignment.\n" +
			"$(newick) will be replaced by glottolog constraints\n" +
			"$(taxonsets) will be replaced by taxon sets for all taxa + all families\n" +
			"$(geo) will be replaced by geography locations\n", Validate.REQUIRED);

	
	@Override
	public void initAndValidate() {
	}

	Set<String> taxa;
	
	@Override
	public void run() throws Exception {
		String constraints = processTreeFile();
		taxa = processTaxaFile();
		
		String newick = filterNewick(constraints, taxa);
		Map<String,String> taxonSets = filterTaxonSets(taxa);
		Map<String,Location> geo = filterGeo(taxa);

		
		Log.warning(newick);
		for (String fam : taxonSets.keySet()) {
			Log.warning(fam + " = " + taxonSets.get(fam));
		}
		
		toXML(newick, taxonSets, geo);
		Log.warning("Done");
	}


	private Map<String, Location> filterGeo(Set<String> taxa2) throws IOException {
		String path = glottoSourceDirInput.get().getAbsolutePath() + GEO_FILE_NAME;
        BufferedReader fin = new BufferedReader(new FileReader(path));
        String str = null;
        Map<String,Location> geoMap = new LinkedHashMap<>();
        str = fin.readLine();
        while (fin.ready()) {
            str = fin.readLine();
            String [] strs = str.split(",");
            if (strs.length >5 && strs[5].length() > 0 && taxa.contains(strs[0])) {
            	Location loc = new Location();
            	loc.latitude = Double.parseDouble(strs[5]);
            	loc.longitude = Double.parseDouble(strs[6]);
            	geoMap.put(strs[0], loc);
            }
        }
        fin.close();
        return geoMap;
	}


	private void toXML(String newick, Map<String, String> taxonSets, Map<String, Location> geo) throws IOException {
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getName());
			out = new PrintStream(outputInput.get());
		}
		
		String xml = "Taxonsets:\n$(taxonsets)\n\nNewick:\n$(newick)\n\nGeography:\n$(geo)\n";
		if (templateFileInput.get() != null && !templateFileInput.get().getName().equals("[[none]]")) {
			xml = BeautiDoc.load(templateFileInput.get());
		}
		String taxonSetXML = toXML(taxonSets);
		String geoXML = toXMLGeo(geo);
		xml = xml.replace("$(taxonsets)", taxonSetXML);
		xml = xml.replace("$(newick)", newick);
		xml = xml.replace("$(geo)", geoXML);
		
		out.println(xml);
	}

	private String toXMLGeo(Map<String, Location> geo) {
		StringBuilder buf = new StringBuilder();
		String [] taxa2 = taxa.toArray(new String[]{});		
		Arrays.sort(taxa2);
		for (String t : taxa2) {
			if (geo.containsKey(t)) {
				buf.append("\n" + t + " = ");
				Location loc = geo.get(t);
				buf.append(loc.latitude);
				buf.append(' ');
				buf.append(loc.longitude);
				buf.append(",");
			} else {
				Log.warning("No location found for " + t);
			}
		}		
		return buf.substring(0, buf.length()-1);
	}


	private String toXML(Map<String, String> taxonSets) {
		StringBuilder buf = new StringBuilder();
		// first, the set with all taxa
		buf.append("<taxonset id=\"ALL\" spec=\"TaxonSet\">\n");
		buf.append("  <plate var=\"n\" range=\"");		
		for (String t : taxa) {
			buf.append(t);
			buf.append(',');
		}
		buf.replace(buf.length()-1, buf.length(),"\">\n");
		buf.append("    <taxon id=\"$(n)\" spec=\"Taxon\"/>\n");
		buf.append("  </plate>\n");
		buf.append("</taxonset>\n");
		
		// next, the families
		String [] fams = taxonSets.keySet().toArray(new String[]{});
		Arrays.sort(fams);
		for (String fam : fams) {
			buf.append("<taxonset id=\"" + fam + "\" spec=\"TaxonSet\">");
			buf.append("<plate var=\"n\" range=\"" + taxonSets.get(fam) + "\">");		
			buf.append("<taxon id=\"$(n)\" spec=\"Taxon\"/>");
			buf.append("</plate>");
			buf.append("</taxonset>\n");
		}
		return buf.toString();
	}

	private Map<String, String> filterTaxonSets(Set<String> taxa) throws IOException {
		String path = glottoSourceDirInput.get().getAbsolutePath() + TREE_FILE_NAME;
        BufferedReader fin = new BufferedReader(new FileReader(path));
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
		String path = glottoSourceDirInput.get().getAbsolutePath() + TREE_FILE_NAME;
        BufferedReader fin = new BufferedReader(new FileReader(path));
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
