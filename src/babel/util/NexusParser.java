package babel.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import beast.base.core.Log;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.FilteredAlignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.evolution.alignment.Taxon;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.datatype.DataType;
import beast.base.evolution.datatype.StandardData;
import beast.base.evolution.datatype.UserDataType;
import beast.base.evolution.tree.TraitSet;
import beast.base.evolution.tree.Tree;
import beast.base.inference.distribution.Exponential;
import beast.base.inference.distribution.Gamma;
import beast.base.inference.distribution.LogNormalDistributionModel;
import beast.base.evolution.tree.MRCAPrior;
import beast.base.inference.distribution.Normal;
import beast.base.inference.distribution.ParametricDistribution;
import beast.base.inference.distribution.Uniform;
import beast.base.parser.NexusParserListener;
import beast.base.evolution.tree.TreeParser;
import beast.base.parser.XMLProducer;


/**
 * parses nexus file and grabs alignment and calibration from the file *
 */
public class NexusParser {
    /**
     * keep track of nexus file line number, to report when the file does not parse *
     */
    int lineNr;

    /**
     * Beast II objects reconstructed from the file*
     */
    public Alignment m_alignment;
    public String [] charstatelabels;
    public List<Alignment> filteredAlignments = new ArrayList<>();
    public TraitSet traitSet;
    public List<MRCAPrior> calibrations;

    
    public List<String> taxa = new ArrayList<>();
    public List<Tree> trees;

    static Set<String> g_sequenceIDs;

    public Map<String, String> translationMap = null;

    static {
        g_sequenceIDs = new HashSet<>();
    }

    public List<TaxonSet> taxonsets = new ArrayList<>();

    private List<NexusParserListener> listeners = new ArrayList<>();

    /**
     * Adds a listener for client classes that want to monitor progress of the parsing.
     * @param listener
     */
    public void addListener(final NexusParserListener listener) {
        listeners.add(listener);
    }

    /**
     * Try to parse BEAST 2 objects from the given file
     *
     * @param file the file to parse.
     */
    public void parseFile(final File file) throws IOException {
        final String fileName = file.getName().replaceAll(".*[\\/\\\\]", "").replaceAll("\\..*", "");

        parseFile(fileName, new FileReader(file));
    }

    /**
     * try to reconstruct Beast II objects from the given reader
     *
     * @param id     a name to give to the parsed results
     * @param reader a reader to parse from
     * throws IOException now instead of just Exception. 
     * java.text.ParseException seems more appropriate, but requires keeping track of the position in the file, which is non-trivial 
     */
    public void parseFile(final String id, final Reader reader) throws IOException {
        lineNr = 0;
        final BufferedReader fin;
        if (reader instanceof BufferedReader) {
            fin = (BufferedReader) reader;
        } else {
            fin = new BufferedReader(reader);
        }
        try {
            while (fin.ready()) {
                final String str = nextLine(fin);
                if (str == null) {
                    return;
                }
                final String lower = str.toLowerCase();
                if (lower.matches("^\\s*begin\\s+data;\\s*$") || lower.matches("^\\s*begin\\s+characters;\\s*$")) {
                    m_alignment = parseDataBlock(fin);
                    m_alignment.setID(id);
                } else if (lower.matches("^\\s*begin\\s+calibration;\\s*$")) {
                    traitSet = parseCalibrationsBlock(fin);
                } else if (lower.matches("^\\s*begin\\s+assumptions;\\s*$") ||
                        lower.matches("^\\s*begin\\s+sets;\\s*$") ||
                        lower.matches("^\\s*begin\\s+mrbayes;\\s*$")) {
                    parseAssumptionsBlock(fin);
                } else if (lower.matches("^\\s*begin\\s+taxa;\\s*$")) {
                    parseTaxaBlock(fin);
                } else if (lower.matches("^\\s*begin\\s+trees;\\s*$")) {
                    parseTreesBlock(fin);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Around line " + lineNr + "\n" + e.getMessage());
        }
    } // parseFile

    private void parseTreesBlock(final BufferedReader fin) throws Exception {
        trees = new ArrayList<>();
        // read to first non-empty line within trees block
        String str = fin.readLine().trim();
        while (str.equals("")) {
            str = fin.readLine().trim();
        }

        int origin = -1;

        // if first non-empty line is "translate" then parse translate block
        if (str.toLowerCase().contains("translate")) {
            translationMap = parseTranslateBlock(fin);
            origin = getIndexedTranslationMapOrigin(translationMap);
            if (origin != -1) {
                taxa = getIndexedTranslationMap(translationMap, origin);
            }
        }

        // read trees
        while (str != null) {
            if (str.toLowerCase().startsWith("tree ")) {
                final int i = str.indexOf('(');
                if (i > 0) {
                    str = str.substring(i);
                }
                TreeParser treeParser;

                if (origin != -1) {
                    treeParser = new TreeParser(taxa, str, origin, false);
                } else {
                    try {
                        treeParser = new TreeParser(taxa, str, 0, false);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        treeParser = new TreeParser(taxa, str, 1, false);
                    }
                }
//                catch (NullPointerException e) {
//                    treeParser = new TreeParser(m_taxa, str, 1);
//                }

                for (final NexusParserListener listener : listeners) {
                    listener.treeParsed(trees.size(), treeParser);
                }

                if (translationMap != null) treeParser.translateLeafIds(translationMap);

                trees.add(treeParser);

//				Node tree = treeParser.getRoot();
//				tree.sort();
//				tree.labelInternalNodes(nrOfLabels);
            }
            str = fin.readLine();
            if (str != null) str = str.trim();
        }
    }

    private List<String> getIndexedTranslationMap(final Map<String, String> translationMap, final int origin) {

        Log.info.println("translation map size = " + translationMap.size());

        final String[] taxa = new String[translationMap.size()];

        for (final String key : translationMap.keySet()) {
            taxa[Integer.parseInt(key) - origin] = translationMap.get(key);
        }
        return Arrays.asList(taxa);
    }

    /**
     * @param translationMap
     * @return minimum key value if keys are a contiguous set of integers starting from zero or one, -1 otherwise
     */
    private int getIndexedTranslationMapOrigin(final Map<String, String> translationMap) {

        final SortedSet<Integer> indices = new TreeSet<>();

        int count = 0;
        for (final String key : translationMap.keySet()) {
            final int index = Integer.parseInt(key);
            indices.add(index);
            count += 1;
        }
        if ((indices.last() - indices.first() == count - 1) && (indices.first() == 0 || indices.first() == 1)) {
            return indices.first();
        }
        return -1;
    }

    /**
     * @param reader a reader
     * @return a map of taxa translations, keys are generally integer node number starting from 1
     *         whereas values are generally descriptive strings.
     * @throws IOException
     */
    private Map<String, String> parseTranslateBlock(final BufferedReader reader) throws IOException {

        final Map<String, String> translationMap = new HashMap<>();

        String line = reader.readLine();
        final StringBuilder translateBlock = new StringBuilder();
        while (line != null && !line.trim().toLowerCase().equals(";")) {
            translateBlock.append(line.trim());
            line = reader.readLine();
        }
        final String[] taxaTranslations = translateBlock.toString().split(",");
        for (final String taxaTranslation : taxaTranslations) {
            final String[] translation = taxaTranslation.split("[\t ]+");
            if (translation.length == 2) {
                translationMap.put(translation[0], translation[1]);
//                Log.info.println(translation[0] + " -> " + translation[1]);
            } else {
                Log.warning.println("Ignoring translation:" + Arrays.toString(translation));
            }
        }
        return translationMap;
    }

    private void parseTaxaBlock(final BufferedReader fin) throws IOException {
        taxa = new ArrayList<>();
        int expectedTaxonCount = -1;
        String str;
        do {
            str = nextLine(fin);
            if (str.toLowerCase().matches("\\s*dimensions\\s.*")) {
                str = str.substring(str.toLowerCase().indexOf("ntax=") + 5);
                str = str.replaceAll(";", "");
                expectedTaxonCount = Integer.parseInt(str.trim());
            } else if (str.toLowerCase().trim().equals("taxlabels")) {
                do {
                    str = nextLine(fin);
                    str = str.replaceAll(";", "");
                    str = str.trim();
                    if (str.length() > 0 && !str.toLowerCase().equals("end")) {
                    	String [] strs = str.split("\\s+");
                    	for (int i = 0; i < strs.length; i++) {
                        	String taxon = strs[i];
                            if (taxon.charAt(0) == '\'' || taxon.charAt(0) == '\"') {
                            	while (i < strs.length && taxon.charAt(0) != taxon.charAt(taxon.length() - 1)) {
                            		i++;
                            		if (i == strs.length) {
                            			throw new IOException("Unclosed quote starting with " + taxon);
                            		}
                            		taxon += " " + strs[i];
                            	}
                            	taxon = taxon.substring(1, taxon.length() - 1);
                            }
                            taxa.add(taxon);
                    	}
                    }
                } while (!str.toLowerCase().equals("end"));
            }
        } while (!str.toLowerCase().equals("end"));
        if (expectedTaxonCount >= 0 && taxa.size() != expectedTaxonCount) {
            throw new IOException("Number of taxa (" + taxa.size() + ") is not equal to 'dimension' " +
            		"field (" + expectedTaxonCount + ") specified in 'taxa' block");
        }
    }

    /**
     * parse calibrations block and create TraitSet *
     */
    TraitSet parseCalibrationsBlock(final BufferedReader fin) throws Exception {
        final TraitSet traitSet = new TraitSet();
        traitSet.traitNameInput.setValue("date", traitSet);
        String str;
        do {
            str = nextLine(fin);
            if (str.toLowerCase().contains("options")) {
                String scale = getAttValue("scale", str);
                if (scale.endsWith("s")) {
                    scale = scale.substring(0, scale.length() - 1);
                }
                traitSet.unitsInput.setValue(scale, traitSet);
            }
        } while (str.toLowerCase().contains("tipcalibration"));

        String text = "";
        while (true) {
            str = nextLine(fin);
            if (str.contains(";")) {
                break;
            }
            text += str;
        }
        final String[] strs = text.split(",");
        text = "";
        for (final String str2 : strs) {
            final String[] parts = str2.split(":");
            final String date = parts[0].replaceAll(".*=\\s*", "");
            final String[] taxa = parts[1].split("\\s+");
            for (final String taxon : taxa) {
                if (!taxon.matches("^\\s*$")) {
                    text += taxon + "=" + date + ",\n";
                }
            }
        }
        text = text.substring(0, text.length() - 2);
        traitSet.traitsInput.setValue(text, traitSet);
        final TaxonSet taxa = new TaxonSet();
        taxa.initByName("alignment", m_alignment);
        traitSet.taxaInput.setValue(taxa, traitSet);

        traitSet.initAndValidate();
        return traitSet;
    } // parseCalibrations


    /**
     * parse data block and create Alignment *
     */
    public Alignment parseDataBlock(final BufferedReader fin) throws Exception {

        final Alignment alignment = new Alignment();

        String str;
        int taxonCount = -1;
        int charCount = -1;
        int totalCount = 4;
        String missing = "?";
        String gap = "-";
        // indicates character matches the one in the first sequence
        String matchChar = null;
        do {
            str = nextLine(fin);

            //dimensions ntax=12 nchar=898;
            if (str.toLowerCase().contains("dimensions")) {
                while (str.indexOf(';') < 0) {
                    str += nextLine(fin);
                }
                str = str.replace(";", " ");

                final String character = getAttValue("nchar", str);
                if (character == null) {
                    throw new IOException("nchar attribute expected (e.g. 'dimensions nchar=123') expected, not " + str);
                }
                charCount = Integer.parseInt(character);
                final String taxa = getAttValue("ntax", str);
                if (taxa != null) {
                    taxonCount = Integer.parseInt(taxa);
                }
            } else if (str.toLowerCase().contains("charstatelabels")) {
            	StringBuilder buf = new StringBuilder();
            	do {
                	str = nextLine(fin);
                    buf.append(str).append('\n');
            	} while (str.indexOf(';') < 0);
                charstatelabels = buf.toString().replaceAll("[,;]", "").split("\\s*\\n+");
            } else if (str.toLowerCase().contains("format")) {
                while (str.indexOf(';') < 0) {
                    str += nextLine(fin);
                }
                str = str.replace(";", " ");

                //format datatype=dna interleave=no gap=-;
                final String dataTypeName = getAttValue("datatype", str);
                final String symbols;
                if (getAttValue("symbols", str) == null) {
                    symbols = getAttValue("symbols", str);
                } else {
                    symbols = getAttValue("symbols", str).replaceAll("\\s", "");
                }
                if (dataTypeName == null) {
                    Log.warning.println("Warning: expected datatype (e.g. something like 'format datatype=dna;') not '" + str + "' Assuming integer dataType");
                    alignment.dataTypeInput.setValue("integer", alignment);
                    if (symbols != null && (symbols.equals("01") || symbols.equals("012"))) {
                        totalCount = symbols.length();
                    }
                } else if (dataTypeName.toLowerCase().equals("rna") || dataTypeName.toLowerCase().equals("dna") || dataTypeName.toLowerCase().equals("nucleotide")) {
                    alignment.dataTypeInput.setValue("nucleotide", alignment);
                    totalCount = 4;
                } else if (dataTypeName.toLowerCase().equals("aminoacid") || dataTypeName.toLowerCase().equals("protein")) {
                    alignment.dataTypeInput.setValue("aminoacid", alignment);
                    totalCount = 20;
                } else if (dataTypeName.toLowerCase().equals("standard")) {
                    alignment.dataTypeInput.setValue("standard", alignment);
                    totalCount = symbols.length();
//                    if (symbols == null || symbols.equals("01")) {
//                        alignment.dataTypeInput.setValue("binary", alignment);
//                        totalCount = 2;
//                    }  else {
//                        alignment.dataTypeInput.setValue("standard", alignment);
//                        totalCount = symbols.length();
//                    }
                } else if (dataTypeName.toLowerCase().equals("binary")) {
                    alignment.dataTypeInput.setValue("binary", alignment);
                    totalCount = 2;
                } else {
                    alignment.dataTypeInput.setValue("integer", alignment);
                    if (symbols != null && (symbols.equals("01") || symbols.equals("012"))) {
                        totalCount = symbols.length();
                    }
                }
                final String missingChar = getAttValue("missing", str);
                if (missingChar != null) {
                    missing = missingChar;
                }
                final String gapChar = getAttValue("gap", str);
                if (gapChar != null) {
                    gap = gapChar;
                }
                matchChar = getAttValue("matchchar", str);
            }
        } while (!str.trim().toLowerCase().startsWith("matrix") && !str.toLowerCase().contains("charstatelabels"));

        if (alignment.dataTypeInput.get().equals("standard")) {
        	StandardData type = new StandardData();
            type.setInputValue("nrOfStates", totalCount);
        	type.initAndValidate();
            alignment.setInputValue("userDataType", type);
        }


        //skipping before MATRIX block
        while (!str.toLowerCase().contains(("matrix"))) {
            str = nextLine(fin);
        }

        // read character data
        // Use string builder for efficiency
        final Map<String, StringBuilder> seqMap = new HashMap<>();
        final List<String> taxa = new ArrayList<>();
        String prevTaxon = null;
        int seqLen = 0;
        while (true) {
            str = nextLine(fin);
            if (str.contains(";")) {
                break;
            }

            int start = 0, end;
            final String taxon;
            while (Character.isWhitespace(str.charAt(start))) {
                start++;
            }
            if (str.charAt(start) == '\'' || str.charAt(start) == '\"') {
                final char c = str.charAt(start);
                start++;
                end = start;
                while (str.charAt(end) != c) {
                    end++;
                }
                taxon = str.substring(start, end);
                seqLen = 0;
                end++;
            } else {
                end = start;
                while (end < str.length() && !Character.isWhitespace(str.charAt(end))) {
                    end++;
                }
                if (end < str.length()) {
                    taxon = str.substring(start, end);
                    seqLen = 0;
                } else if ((prevTaxon == null || seqLen == charCount) && end == str.length()) {
                    taxon = str.substring(start, end);
                    seqLen = 0;
                } else {
                    taxon = prevTaxon;
                    if (taxon == null) {
                        throw new IOException("Could not recognise taxon");
                    }
                    end = start;
                }
            }
            prevTaxon = taxon;
            final String data = str.substring(end);
            for (int k = 0; k < data.length(); k++) {
            	if (!Character.isWhitespace(data.charAt(k))) {
            		seqLen++;
            	}
            }
            // Do this once outside loop- save on multiple regex compilations
            //data = data.replaceAll("\\s", "");

//			String [] strs = str.split("\\s+");
//			String taxon = strs[0];
//			for (int k = 1; k < strs.length - 1; k++) {
//				taxon += strs[k];
//			}
//			taxon = taxon.replaceAll("'", "");
//			Log.warning.println(taxon);
//			String data = strs[strs.length - 1];

            if (seqMap.containsKey(taxon)) {
                seqMap.put(taxon, seqMap.get(taxon).append(data));
            } else {
                seqMap.put(taxon, new StringBuilder(data));
                taxa.add(taxon);
            }
        }
        if (taxonCount > 0 && taxa.size() > taxonCount) {
            throw new IOException("Wrong number of taxa. Perhaps a typo in one of the taxa: " + taxa);
        }

        HashSet<String> sortedAmbiguities = new HashSet<>();
        for (final String taxon : taxa) {
            final StringBuilder bsData = seqMap.get(taxon);
            String data = bsData.toString().replaceAll("\\s", "");
            seqMap.put(taxon, new StringBuilder(data));

            //collect all ambiguities in the sequence
            List<String> ambiguities = new ArrayList<>();
            Matcher m = Pattern.compile("\\{(.*?)\\}").matcher(data);
            while (m.find()) {
                int mLength = m.group().length();
                ambiguities.add(m.group().substring(1, mLength-1));
            }

            //sort elements of ambiguity sets
            String data_without_ambiguities = data.replaceAll("\\{(.*?)\\}", "?");
            for (String amb : ambiguities) {
                List<Integer> ambInt = new ArrayList<>();
                for (int i=0; i<amb.length(); i++) {
                	char c = amb.charAt(i);
                	if (c >= '0' && c <= '9') {
                		ambInt.add(Integer.parseInt(amb.charAt(i) + ""));
                	} else {
                		// ignore
                		if (data != data_without_ambiguities) {
                			Log.warning.println("Ambiguity found in " + taxon + " that is treated as missing value");
                		}
                		data = data_without_ambiguities; 
                	}
                }
                Collections.sort(ambInt);
                String ambStr = "";
                for (int i=0; i<ambInt.size(); i++) {
                    ambStr += Integer.toString(ambInt.get(i));
                }
                sortedAmbiguities.add(ambStr);
            }

            //check the length of the sequence (treat ambiguity sets as single characters)
            if (data_without_ambiguities.length() != charCount) {
                throw new IOException(str + "\nExpected sequence of length " + charCount + " instead of " + data.length() + " for taxon " + taxon);
            }

            // map to standard missing and gap chars
            data = data.replace(missing.charAt(0), DataType.MISSING_CHAR);
            data = data.replace(gap.charAt(0), DataType.GAP_CHAR);

            // resolve matching char, if any
            if (matchChar != null && data.contains(matchChar)) {
                final char cMatchChar = matchChar.charAt(0);
                final String baseData = seqMap.get(taxa.get(0)).toString();
                for (int i = 0; i < data.length(); i++) {
                    if (data.charAt(i) == cMatchChar) {
                        final char cReplaceChar = baseData.charAt(i);
                        data = data.substring(0, i) + cReplaceChar + (i + 1 < data.length() ? data.substring(i + 1) : "");
                    }
                }
            }

            if (alignment.dataTypeInput.get().equals("nucleotide") || 
            	alignment.dataTypeInput.get().equals("binary")  ||
            	alignment.dataTypeInput.get().equals("aminoacid") ) {
            	alignment.setInputValue(taxon, data);
            } else {
	            final Sequence sequence = new Sequence();
	            sequence.init(totalCount, taxon, data);
	            sequence.setID(generateSequenceID(taxon));
	            alignment.sequenceInput.setValue(sequence, alignment);
            }
        }


        if (alignment.dataTypeInput.get().equals("standard")) {
            //convert sortedAmbiguities to a whitespace separated string of ambiguities
            String ambiguitiesStr = "";
            for (String amb: sortedAmbiguities) {
                ambiguitiesStr += amb + " ";
            }
            if (ambiguitiesStr.length() > 0) {
            	ambiguitiesStr = ambiguitiesStr.substring(0, ambiguitiesStr.length()-1);
            }
            alignment.userDataTypeInput.get().initByName("ambiguities", ambiguitiesStr);
        }

        alignment.initAndValidate();
        for (String taxon : alignment.getTaxaNames()) {
        	if (this.taxa.indexOf(taxon) == -1) {
        		this.taxa.add(taxon);
        	}
        }
        
        if (taxonCount > 0 && taxonCount != alignment.getTaxonCount()) {
            throw new IOException("dimensions block says there are " + taxonCount + " taxa, but there were " + alignment.getTaxonCount() + " taxa found");
        }
        return alignment;
    } // parseDataBlock


    /**
     * parse assumptions block
     * begin assumptions;
     * charset firsthalf = 1-449;
     * charset secondhalf = 450-898;
     * charset third = 1-457\3 662-896\3;
     * end;
     * 
     * begin assumptions;
     * wtset MySoapWeights (VECTOR) = 13 13 13 50 50 88 8
     * end;
     * 
     */
    void parseAssumptionsBlock(final BufferedReader fin) throws Exception {
    	if (true) {
    		return;
    	}
        String str;
        do {
            str = nextLine(fin);
            if (str.toLowerCase().matches("\\s*charset\\s.*")) {
            	// remove text in brackets (as TreeBase files are wont to contain)
                str = str.replaceAll("\\(.*\\)", "");
                // clean up spaces
                str = str.replaceAll("^\\s+", "");
                str = str.replaceAll("\\s*-\\s*", "-");
                str = str.replaceAll("\\s*\\\\\\s*", "\\\\");
                str = str.replaceAll("\\s*;", "");
                final String[] strs = str.trim().split("\\s+");
                final String id = strs[1];
                String rangeString = "";
                for (int i = 3; i < strs.length; i++) {
                    rangeString += strs[i] + " ";
                }
                rangeString = rangeString.trim().replace(' ', ',');
                final FilteredAlignment alignment = new FilteredAlignment();
                alignment.setID(id);
                alignment.alignmentInput.setValue(m_alignment, alignment);
                alignment.filterInput.setValue(rangeString, alignment);
                alignment.initAndValidate();
                filteredAlignments.add(alignment);
            } else if (str.toLowerCase().matches("\\s*wtset\\s.*")) {
            	String [] strs = str.split("=");
            	if (strs.length > 1) {
            		str = strs[strs.length - 1].trim();
            		strs = str.split("\\s+");
            		int [] weights = new int[strs.length];
            		for (int i = 0; i< strs.length; i++) {
            			weights[i] = Integer.parseInt(strs[i]);
            		}
            		if (m_alignment != null) {
            			if (weights.length != m_alignment.getSiteCount()) {
            				throw new RuntimeException("Number of weights (" + weights.length+ ") " +
            						"does not match number of sites in alignment(" + m_alignment.getSiteCount()+ ")");
            			}
            			StringBuilder weightStr = new StringBuilder();
            			for (String str2 : strs) {
            				weightStr.append(str2);
            				weightStr.append(',');
            			}
            			weightStr.delete(weightStr.length() - 1, weightStr.length());
            			m_alignment.siteWeightsInput.setValue(weightStr.toString(), m_alignment);
            			m_alignment.initAndValidate();
            		} else {
            			Log.warning.println("WTSET was specified before alignment. WTSET is ignored.");
            		}
            	}
            } else if (str.toLowerCase().matches("^\\s*taxset\\s.*")) {
            	// define taxon set, e.g.
                // begin set;
            	// taxset germanic = english german dutch;
            	// taxset romance = italian spanish rumenian;
            	// end;
            	String [] strs = str.split("=");
            	if (strs.length > 1) {
            		String str0 = strs[0].trim();
            		String [] strs2 = str0.split("\\s+");
            		if (strs2.length != 2) {
            			throw new RuntimeException("expected 'taxset <name> = ...;' but did not get two words before the = sign: " + str);
            		}
            		String taxonSetName = strs2[1];
            		str0 = strs[strs.length - 1].trim();
            		if (!str0.endsWith(";")) {
            			throw new RuntimeException("expected 'taxset <name> = ...;' semi-colin is missing: " + str);
            		}
            		str0 = str0.replaceAll(";", "");
            		String [] taxonNames = str0.split("\\s+");
            		TaxonSet taxonset = new TaxonSet();
            		for (String taxon : taxonNames) {
            			taxonset.taxonsetInput.get().add(new Taxon(taxon));
            		}
            		taxonset.setID(taxonSetName);
            		taxonsets.add(taxonset);
            	}
            } else if (str.toLowerCase().matches("^\\s*calibrate\\s.*")) {
            	// define calibration represented by an MRCAPRior, 
            	// taxon sets need to be specified earlier, but can also be a single taxon
            	// e.g.
            	// begin mrbayes;
            	// calibrate germanic = normal(1000,50)
            	// calibrate hittite = normal(3450,100)
            	// calibrate english = fixed(0)
            	// end;
            	String [] strs = str.split("=");
            	if (strs.length > 1) {
            		String str0 = strs[0].trim();
            		String [] strs2 = str0.split("\\s+");
            		if (strs2.length != 2) {
            			throw new RuntimeException("expected 'calibrate <name> = ...' but did not get two words before the = sign: " + str);
            		}
            		// first, get the taxon
            		String taxonSetName = strs2[1].replaceAll("'\"", "");
            		Taxon taxonset = null;
            		for (Taxon t : taxonsets) {
            			if (t.getID().equals(taxonSetName)) {
            				taxonset = t;
            			}
            		}
            		if (taxonset == null) {
            			for (String t : taxa) {
            				if (t.equals(taxonSetName)) {
            					taxonset = new Taxon(t);
            				}
            			}
            		}
            		if (taxonset == null) {
            			throw new RuntimeException("Could not find taxon/taxonset " + taxonSetName + " in calibration: " + str);
            		}
            		
            		// next get the calibration
            		str0 = strs[strs.length - 1].trim();
            		String [] strs3 = str0.split("[\\(,\\)]");
            		ParametricDistribution distr  = null;
            		switch (strs3[0]) {
            		case "normal":
            			distr = new Normal();
            			distr.initByName("mean", strs3[1], "sigma", strs3[2]);
            			distr.setID("Normal.0");
            			break;
            		case "uniform":
            			distr = new Uniform();
            			distr.initByName("lower", strs3[1], "upper", strs3[2]);
            			distr.setID("Uniform.0");
            			break;
            		case "fixed":
            			// uniform with lower == upper
            			distr = new Uniform();
            			distr.initByName("lower", strs3[1], "upper", strs3[1]);
            			distr.setID("Uniform.0");
            			break;
            		case "offsetlognormal":
            			distr = new LogNormalDistributionModel();
            			distr.initByName("offset", strs3[1], "M", strs3[2], "S", strs3[3], "meanInRealSpace", true);
            			distr.setID("LogNormal.0");
            			break;
            		case "lognormal":
            			distr = new LogNormalDistributionModel();
            			distr.initByName("M", strs3[1], "S", strs3[2], "meanInRealSpace", true);
            			distr.setID("LogNormal.0");
            			break;
            		case "offsetexponential":
            			distr = new Exponential();
            			distr.initByName("offset", strs3[1], "mean", strs3[2]);
            			distr.setID("Exponential.0");
            			break;
            		case "gamma":
            			distr = new Gamma();
            			distr.initByName("alpha", strs3[1], "beta", strs3[2]);
            			distr.setID("Gamma.0");
            			break;
            		case "offsetgamma":
            			distr = new Gamma();
            			distr.initByName("offset", strs3[1], "alpha", strs3[2], "beta", strs3[3]);
            			distr.setID("Gamma.0");
            			break;
            		default:
            			throw new RuntimeException("Unknwon distribution "+ strs3[0] +"in calibration: " + str);
            		}
            		MRCAPrior prior = new MRCAPrior();
            		prior.isMonophyleticInput.setValue(true, prior);
            		prior.distInput.setValue(distr, prior);
            		prior.taxonsetInput.setValue(taxonset, prior);
            		prior.setID(taxonset.getID() + ".prior");
            		// should set Tree before initialising, but we do not know the tree yet...
            		calibrations.add(prior);
            	}
            }

        } while (!str.toLowerCase().contains("end;"));
    }

    /**
     * parse sets block
     * BEGIN Sets;
     * TAXSET 'con' = 'con_SL_Gert2' 'con_SL_Tran6' 'con_SL_Tran7' 'con_SL_Gert6';
     * TAXSET 'spa' = 'spa_138a_Cerb' 'spa_JB_Eyre1' 'spa_JB_Eyre2';
     * END; [Sets]
     */
    void parseSetsBlock(final BufferedReader fin) throws IOException {
        String str;
        do {
            str = nextLine(fin);
            if (str.toLowerCase().matches("\\s*taxset\\s.*")) {
            	String [] strs = str.split("=");
            	if (strs.length > 1) {
            		String str0 = strs[0].trim();
            		String [] strs2 = str0.split("\\s+");
            		if (strs2.length != 2) {
            			throw new RuntimeException("expected 'taxset <name> = ...;' but did not get two words before the = sign: " + str);
            		}
            		String taxonSetName = strs2[1];
            		str0 = strs[strs.length - 1].trim();
            		if (!str0.endsWith(";")) {
            			Log.warning.println("expected 'taxset <name> = ...;' semi-colin is missing: " + str + "\n"
            					+ "Taxa from following lines may be missing.");
            		}
            		str0 = str0.replaceAll(";", "");
            		String [] taxonNames = str0.split("\\s+");
            		TaxonSet taxonset = new TaxonSet();
            		for (String taxon : taxonNames) {
            			taxonset.taxonsetInput.get().add(new Taxon(taxon.replaceAll("'\"", "")));
            		}
            		taxonset.setID(taxonSetName.replaceAll("'\"", ""));
            		taxonsets.add(taxonset);
            	}
            }
        } while (!str.toLowerCase().contains("end;"));
    }

    public static String generateSequenceID(final String taxon) {
        String id = "seq_" + taxon;
        int i = 0;
        while (g_sequenceIDs.contains(id + (i > 0 ? i : ""))) {
            i++;
        }
        id = id + (i > 0 ? i : "");
        g_sequenceIDs.add(id);
        return id;
    }

    /**
     * read line from nexus file *
     */
    String readLine(final BufferedReader fin) throws IOException {
        if (!fin.ready()) {
            return null;
        }
        lineNr++;
        return fin.readLine();
    }

    /**
     * read next line from nexus file that is not a comment and not empty *
     */
    String nextLine(final BufferedReader fin) throws IOException {
        String str = readLine(fin);
        if (str == null) {
            return null;
        }
        if (str.contains("[")) {
            final int start = str.indexOf('[');
            int end = str.indexOf(']', start);
            while (end < 0) {
                str += readLine(fin);
                end = str.indexOf(']', start);
            }
            str = str.substring(0, start) + str.substring(end + 1);
            if (str.matches("^\\s*$")) {
                return nextLine(fin);
            }
        }
        if (str.matches("^\\s*$")) {
            return nextLine(fin);
        }
        return str;
    }

    /**
     * return attribute value as a string *
     */
    String getAttValue(final String attribute, final String str) {
        final Pattern pattern = Pattern.compile(".*" + attribute + "\\s*=\\s*([^\\s;]+).*");
        final Matcher matcher = pattern.matcher(str.toLowerCase());
        if (!matcher.find()) {
            return null;
        }
        String att = matcher.group(1);
        if (att.startsWith("\"") && att.endsWith("\"")) {
            final int start = matcher.start(1);
            att = str.substring(start + 1, str.indexOf('"', start + 1));
        }
        return att;
    }

    private ArrayList<String> readInCharstatelablesTokens(final BufferedReader fin) throws IOException {

        ArrayList<String> tokens = new ArrayList<>();
        String token="";
        final int READING=0, OPENQUOTE=1, WAITING=2;
        int mode = WAITING;
        int numberOfQuotes=0;
        boolean endOfBlock=false;
        String str;

        while (!endOfBlock) {
            str = nextLine(fin);
            Character nextChar;
            for (int i=0; i< str.length(); i++) {
                nextChar=str.charAt(i);
                switch (mode) {
                    case WAITING:
                        if (!Character.isWhitespace(nextChar)) {
                            if (nextChar == '\'') {
                                mode=OPENQUOTE;
                            } else if (nextChar == '/' || nextChar == ',') {
                                tokens.add(nextChar.toString());
                                token="";
                            } else if (nextChar == ';') {
                                endOfBlock = true;
                            } else {
                                token=token+nextChar;
                                mode=READING;
                            }
                        }
                        break;
                    case READING:
                        if (nextChar == '\'') {
                            tokens.add(token);
                            token="";
                            mode=OPENQUOTE;
                        } else if (nextChar == '/' || nextChar == ',') {
                            tokens.add(token);
                            tokens.add(nextChar.toString());
                            token="";
                            mode=WAITING;
                        } else if (nextChar == ';') {
                            tokens.add(token);
                            endOfBlock = true;
                        } else if (Character.isWhitespace(nextChar)) {
                            tokens.add(token);
                            token="";
                            mode=WAITING;
                        } else {
                            token=token+nextChar;
                        }
                        break;
                    case OPENQUOTE:
                        if (nextChar == '\'') {
                            numberOfQuotes++;
                        } else {
                            if (numberOfQuotes % 2 == 0) {
                                for (int ind=0; ind< numberOfQuotes/2; ind++) {
                                    token=token+"'";
                                }
                                token=token+nextChar;
                            } else {
                                for (int ind=0; ind< numberOfQuotes/2; ind++) {
                                    token=token+"'";
                                }
                                tokens.add(token);
                                token="";
                                if (nextChar == '/' || nextChar == ',') {
                                    tokens.add(nextChar.toString());
                                    mode=WAITING;
                                } else if (nextChar == ';') {
                                    endOfBlock = true;
                                } else if (Character.isWhitespace(nextChar)) {
                                    mode=WAITING;
                                } else {
                                    token=token+nextChar;
                                    mode=READING;
                                }
                            }
                            numberOfQuotes=0;
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        if (!tokens.get(tokens.size()-1).equals(",")) {
            tokens.add(",");
        }

        return tokens;
    }

    private ArrayList<UserDataType> processCharstatelabelsTokens(ArrayList<String> tokens, int[] maxNumberOfStates) throws IOException {

        ArrayList<UserDataType> charDescriptions = new ArrayList<>();

        final int CHAR_NR=0, CHAR_NAME=1, STATES=2;
        int mode = CHAR_NR;
        int charNumber = -1;
        String charName = "";
        ArrayList<String> states = new ArrayList<>();

        for (String token:tokens) {
            switch (mode) {
                case CHAR_NR:
                    charNumber = Integer.parseInt(token);
                    mode = CHAR_NAME;
                    break;
                case CHAR_NAME:
                    if (token.equals("/")) {
                        mode = STATES;
                    } else if (token.equals(",")) {
                        if (charNumber > charDescriptions.size()+1) {
                            throw new IOException("Character descriptions should go in the ascending order and there " +
                                    "should not be any description missing.");
                        }
                        charDescriptions.add(new UserDataType(charName, states));
                        maxNumberOfStates[0] = Math.max(maxNumberOfStates[0], states.size());
                        charNumber = -1;
                        charName = "";
                        states = new ArrayList<>();
                        mode = CHAR_NR;
                    } else {
                        charName = token;
                    }
                    break;
                case STATES:
                    if (token.equals(",")) {
                        if (charNumber > charDescriptions.size()+1) {
                            throw new IOException("Character descriptions should go in the ascending order and there " +
                                    "should not be any description missing.");
                        }
                        charDescriptions.add(new UserDataType(charName, states));
                        maxNumberOfStates[0] = Math.max(maxNumberOfStates[0], states.size());
                        charNumber = -1;
                        charName = "";
                        states = new ArrayList<>();
                        mode = CHAR_NR;
                    } else {
                        states.add(token);
                    }
                default:
                    break;
            }
        }

        return charDescriptions;

    }

    public static void main(final String[] args) {
        try {
            final NexusParser parser = new NexusParser();
            parser.parseFile(new File(args[0]));
            if (parser.taxa != null) {
                System.out.println(parser.taxa.size() + " taxa");
                System.out.println(Arrays.toString(parser.taxa.toArray(new String[parser.taxa.size()])));
            }
            if (parser.trees != null) {
                System.out.println(parser.trees.size() + " trees");
            }
            if (parser.m_alignment != null) {
                final String xml = new XMLProducer().toXML(parser.m_alignment);
                System.out.println(xml);
            }
            if (parser.traitSet != null) {
                final String xml = new XMLProducer().toXML(parser.traitSet);
                System.out.println(xml);
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    } // main

} // class NexusParser
