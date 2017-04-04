package babel.tools;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import beast.util.NexusParser;
import beast.app.beauti.BeautiDoc;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;

@Description("Convert binary nexus file to multistate alignment")
public class BinaryToMultiState extends Runnable {
	public Input<File> nexusFileInput = new Input<>("nexus", "nexus file with binary alignment", Validate.REQUIRED);
	public Input<OutFile> outFileInput = new Input<>("out", "file to write multistate nexus into, or stdout if not specified");
	public Input<Boolean> isAscertainedInput = new Input<>("isAscertained", "when true, every first column is interpreted as ascertainent column and ignored", true);

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		File nexusfile = nexusFileInput.get();
		if (nexusfile == null || nexusfile.getName().equals("[[none]]")) {
			throw new IllegalArgumentException("nexus file must be specified");
		}
		if (!nexusfile.exists()) {
			throw new IllegalArgumentException("nexus file must be accessible");
		}
		NexusParser parser = new NexusParser();
		parser.parseFile(nexusfile);
		
		List<String> taxanames = parser.m_alignment.getTaxaNames();
		
		List<String> seqs = new ArrayList<>();
		for (int j = 0; j < taxanames.size(); j++) {
			seqs.add("");
		}
		
		String [] character = new String[65];
		for (int i = 0; i < 10; i++) {
			character[i + 0] = ""+(char)('0'+i);
		}
		for (int i = 0; i < 26; i++) {
			character[i + 10] = "" + (char) ('a'+i);
		}
		for (int i = 0; i < 26; i++) {
			character[i + 36] = ""+(char)('A'+i);
		}
		character[62] = "@";
		character[63] = "!";
		character[64] = "~";
		
		String symbols = "";
		for (String s : character) {
			symbols += s;
		}
		
		
		int nchar = parser.filteredAlignments.size();
		for (int k = 0; k < parser.filteredAlignments.size(); k++) {
			Alignment a = parser.filteredAlignments.get(k);
			int i0 = 0;
			boolean [] used = new boolean[65];
			List<String> sites = new ArrayList<>();
			for (int j = 0; j < taxanames.size(); j++) {
				sites.add("");
			}
			for (int i = (isAscertainedInput.get() ? 1 : 0); i < Math.min(65, a.getSiteCount()); i++) {
				int [] pattern = a.getPattern(a.getPatternIndex(i));

				boolean atLeastUsedOnce = false;
				for (int j = 0; j < pattern.length; j++) {
					if (pattern[j] == 1) {
						String site = sites.get(j);
						site = site + character[i0];
						sites.set(j, site);
						used[i0] = true;
						atLeastUsedOnce = true;
//						} else {
//							Log.warning("ambiguity found at " + a.getID() + " " + taxanames.get(j));
//						}
					}
				}
				if (!atLeastUsedOnce && isAscertainedInput.get()) {
					Log.warning("Column " + i + " in " + a.getID() + " contains no data");
				}
				i0++;				
			}
			int usedCount = 0;
			for (boolean b : used) {
				if (b) usedCount++;
			}
			if (usedCount <= 1) {
				Log.warning("removing column \"" + a.getID() + "\" since it only has a single entry");
				nchar--;
			} else {
				// update sites
				for (int j = 0; j < taxanames.size(); j++) {
					if (sites.get(j).length() == 0) {
						sites.set(j, "?");
					}
					if (sites.get(j).length() > 1) {
						sites.set(j, "{" + sites.get(j)+ "}");
					}
				}
				// sanity check
				boolean [] b = new boolean[65];
				for (String s : sites) {
					for (int j = 0; j < s.length(); j++) {
						char c = s.charAt(j);
						int code = indexOf(c, character);
						if (code >= 0) {
							b[code] = true;
						}
					}
				}
				for (int j = 1; j < b.length; j++) {
					if (b[j] && ! b[j-1]) {
						Log.warning("non-consecutive code");
					}
				}
				
				// update sequences
				for (int j = 0; j < taxanames.size(); j++) {
					seqs.set(j, seqs.get(j) + sites.get(j));
				}
			}
		}
		
		StringBuilder xml = new StringBuilder();
		StringBuilder nexus = new StringBuilder();
		
		nexus.append("\n\nBEGIN CHARACTERS;\nDIMENSIONS NTAX=" + taxanames.size() + " NCHAR=" + nchar + 
				";\nFORMAT DATATYPE=Standard SYMBOLS=\"" + symbols + "\" MISSING=? GAP=- ;\nMATRIX\n");
		
		xml.append("<data id=\"data\">\n" +
				"<userDataType id=\"MultiStateType\" spec=\"beast.evolution.datatype.UserDataType\"\n" + 
                "states='65'\n" +
                "codelength='1'\n" + 
                "codeMap='");
		for (int i = 0; i < 65; i++) {
			xml.append(character[i] + " = " + i + (i < 64 ? ", " : ""));
		}
		xml.append("'/> ");
		
		for (int i = 0; i < taxanames.size(); i++) { 
			xml.append("<sequence taxon=\"" + taxanames.get(i) + "\" " + 
"                              ".substring(taxanames.get(i).length(), 30)+ "value=\"" + seqs.get(i) + "\"/>\n");
			nexus.append(taxanames.get(i) + 
					"                              ".substring(taxanames.get(i).length(), 30)+ " " + seqs.get(i) + "\n");
		}
				
		xml.append("</data>\n");
		nexus.append(";\nEND;\n");
		
		PrintStream out = System.out;
		if (outFileInput.get() != null) {
			out = new PrintStream(outFileInput.get());
		}

		String oldNexus = BeautiDoc.load(nexusFileInput.get());
		oldNexus = oldNexus.replaceFirst("(?is)begin characters.*?end;", nexus.toString());
		oldNexus = oldNexus.replaceAll("charset.*?;", "");
		
		
		
		out.println(oldNexus);
		
		Log.warning("Done!");
	}

	private int indexOf(char c, String[] character) {
		String ch = c + "";
		for (int i = 0; i < character.length; i++) {
			if (character[i].equals(ch)) {
				return i;
			}
		}
		return -1;
	}

	public static void main(String[] args) throws Exception {
		new Application(new BinaryToMultiState(), "BinaryToMultiState", args);

	}

}
