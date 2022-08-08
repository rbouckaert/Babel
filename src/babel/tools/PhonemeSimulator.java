package babel.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.PoissonDistribution;
import org.apache.commons.math.distribution.PoissonDistributionImpl;

import babel.evolution.substitutionmodel.BirthDeathModel;
import babel.tools.utils.MemoryFriendlyTreeSet;
import beastfx.app.seqgen.SequenceSimulator;
import beastfx.app.tools.Application;
import beastfx.app.util.LogFile;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.core.BEASTInterface;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Input.Validate;
import beast.base.inference.parameter.RealParameter;
import beast.base.core.Log;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.evolution.branchratemodel.StrictClockModel;
import beast.base.evolution.datatype.Binary;
import beast.base.evolution.datatype.DataType;
import beast.base.evolution.datatype.UserDataType;
import beast.base.evolution.sitemodel.SiteModel;
import beast.base.evolution.substitutionmodel.BinaryCovarion;
import beastlabs.evolution.substitutionmodel.ComplexSubstitutionModel;
import beast.base.evolution.substitutionmodel.Frequencies;
import beast.base.evolution.tree.Tree;
import beastfx.app.tools.LogAnalyser;
import beast.base.util.Randomizer;
import beast.base.parser.XMLProducer;

@Description("Simulate phoneme alignemnts on a tree. "
		+ "1: generate cognates on tree by pseudo Dollo model. "
		+ "2: generate word lengths + vowel or consonant sites. "
		+ "3: generate vowel or consonant alignments. ")
public class PhonemeSimulator extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<Integer> sequenceLengthInput = new Input<>("sequencelength", "nr of phonemes to generate (default 1000).", 1000);
	final public Input<OutFile> outputInput = new Input<>("out","output file. Print to stdout if not specified");
	final public Input<LogFile> logFileInput = new Input<>("log", "trace log file containing model parameter values to use for generating sequence data. Ignored if not specified.");
	final public Input<Boolean> useGammaInput = new Input<>("useGamma", "use gamma rate heterogeneity. If true, trace log file must be specified.", false);

	
	private String words = "";
	private String filters = "";
	private Alignment binaryData;
	private UserDataType vowelDataType,consonantDataType;
	private Double[] shapes = null;
	private Double[] PDCognateDeathRate = null;


	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(treesInput.get().getPath(), 0);
		srcTreeSet.reset();
		Tree tree = srcTreeSet.next();

		
		// get info from trace log
		processTraceLog();

		// get trees
		
		
		if (srcTreeSet.hasNext()) {
			// tree set contains multiple trees
			int k = 0;
			String path = outputInput.get().getAbsolutePath();
			String suffix = "";
			if (path.lastIndexOf('.') > 0) {
				suffix = path.substring(path.lastIndexOf('.'));
				path = path.substring(0, path.lastIndexOf('.'));
			}
			simulateAlignment(tree, new File(path + k + suffix), k);
			while (srcTreeSet.hasNext()) {
				tree = srcTreeSet.next();
				k++;
				simulateAlignment(tree, new File(path + k + suffix), k);
			}
			
		} else {
			// tree set contains single tree
			simulateAlignment(tree, outputInput.get(), 0);
		}
		
        Log.warning.println("Done.");
	}
		
	private void processTraceLog() throws IOException {
		if (logFileInput.get() == null) {
			if (useGammaInput.get()) {
				throw new IllegalArgumentException("trace log must be specified if useGamma=true");
			}
			return;
		}
		LogAnalyser traceLog = new LogAnalyser(logFileInput.get().getPath(), 0, true, false);
		int N = traceLog.getTrace(0).length;
		List<String> labels = traceLog.getLabels();
		
		if (useGammaInput.get()) {
			shapes = traceLog.getTrace(getIndex(labels, "gammaShape"));
		} else {
			shapes = setToDefault(N, 1.0);
		}
		
		int i = getIndex(labels, "PDCognateDeathRate");
		if (i > 0) {
			PDCognateDeathRate = traceLog.getTrace(i);
		} else {
			PDCognateDeathRate = setToDefault(N, 0.05);
		}

	}

	private Double[] setToDefault(int N, double d) {
		Double [] shapes = new Double[N];
		for (int i  = 0; i < N; i++) {
			shapes[i] = d;
		}
		return shapes;
	}

	private int getIndex(List<String> labels, String prefix) {
		for (int i = 0; i < labels.size(); i++) {
			if (labels.get(i).startsWith(prefix)) {
				return i + 1;
			}
		}
		return 0;
	}

	private void simulateAlignment(Tree tree, File outFile, int i) throws FileNotFoundException {
		
//		1: generate cognates on tree by pseudo Dollo model.
		Alignment cognateData = generateCognateAllignment(tree, i);
		
//		2: generate word lengths + vowel or consonant sites.
		int [] wordLengths = generateWordLengths(cognateData);
		
//		3: generate vowel or consonant alignments.
		Alignment phonemeData = generatePhonemeData(tree, cognateData, wordLengths);
		
		
		// output results
        PrintStream out = System.out;
        if (outFile != null) {
			Log.warning("Writing to file " + outFile.getPath());
        	out = new PrintStream(outFile);
        }
        
        XMLProducer producer = new XMLProducer();
        out.println("{\"sequences\":\"");
        String xml = producer.toRawXML(phonemeData).replaceAll("\n *\n","\n");
        out.println(xml);
        out.println("\",");
        
        out.println("\"binsequences\":\"");
        xml = producer.toRawXML(binaryData).replaceAll("\n *\n","\n");
        out.println(xml);
        out.println("\",");
        
        out.println("\"datatype_vowels\":\"" + producer.toRawXML(vowelDataType) + "\",");
        out.println("\"datatype_consonants\":\"" + producer.toRawXML(consonantDataType) + "\",");

        out.println("\"words\":\"" + words + "\",");
        out.println("\"filters\":\"");
        out.println(filters);
        out.println("\"\n}");

        if (outFile != null) {
        	out.close();
        }
	}


	private Alignment generatePhonemeData(Tree tree, Alignment cognateData, int[] wordLengths) {
		UserDataType dataType = new UserDataType();
		dataType.initByName(
				"states", 28, 
				"codelength", 2, 
				"codeMap", "-.=0,Ŋ.=27,_.=2,A.=3,Aː=4,B.=5,E.=6,Eː=7,F.=8,G.=9,H.=10,I.=11,Iː=12,K.=13,L.=14,M.=15,N.=16,O.=17,Oː=18,P.=19,R.=20,S.=21,T.=22,U.=23,Uː=24,V.=25,W.=26,ʔ.=1,..=0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27");
		
		vowelDataType = new UserDataType();
		vowelDataType.setID("vowels");
		vowelDataType.initByName(
				"states", 12, 
				"codelength", 2, 
				"codeMap", "-.=0,Uː=1,_.=2,A.=3,Aː=4,E.=5,Eː=6,I.=7,Iː=8,O.=9,Oː=10,U.=11,..=0 1 2 3 4 5 6 7 8 9 10 11");

		consonantDataType = new UserDataType();
		consonantDataType.setID("consonants");
		consonantDataType.initByName(
				"states", 18, 
				"codelength", 2, 
				"codeMap", "-.=0,ʔ.=1,_.=2,B.=3,F.=4,G.=5,H.=6,K.=7,L.=8,M.=9,N.=10,P.=11,R.=12,S.=13,T.=14,V.=15,W.=16,Ŋ.=17,..=0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17");
		
		Alignment data; // = emptyAlignment(tree, dataType, "..");
		Alignment vowelData = emptyAlignment(tree, vowelDataType, "..");
		Alignment consonantData = emptyAlignment(tree, consonantDataType, "..");
		
		StrictClockModel clockmodel = new StrictClockModel();
		
		Frequencies vowelFrequencies = new Frequencies();
		vowelFrequencies.initByName("frequencies", new RealParameter("0.217059891107078 0.007501512401694 0.01113127646703 0.26497277676951 0.031820931639444 0.089897156684816 0.003871748336358 0.131397459165154 0.00604960677556 0.101028433151845 0.008832425892317 0.126436781609195"));
		
		RealParameter vowelRates = new RealParameter();
		vowelRates.initByName("dimension", vowelDataType.getStateCount() * (vowelDataType.getStateCount()-1), "value", "1.0");
				
		ComplexSubstitutionModel vowelModel = new ComplexSubstitutionModel();
		vowelModel.initByName("rates", vowelRates, "frequencies", vowelFrequencies);

		SiteModel vowelSitemodel = new SiteModel();
		vowelSitemodel.initByName("gammaCategoryCount", 1, "substModel", vowelModel, "shape", "1.0",
				"proportionInvariant", "0.0");

		
		Frequencies consonantFrequencies = new Frequencies();
		consonantFrequencies.initByName("frequencies", new RealParameter("0.243442381373416 0.107427055702918 0.013557323902152 0.000147362216328 0.007662835249042 0.003684055408193 0.067344532861774 0.091069849690539 0.011936339522547 0.073386383731211 0.054966106690245 0.050987326849396 0.102858826996758 0.002652519893899 0.12614205717654 0.017978190391984 0.006336575302093 0.018420277040967"));
		
		RealParameter consonantRates = new RealParameter();
		consonantRates.initByName("dimension", consonantDataType.getStateCount() * (consonantDataType.getStateCount()-1), "value", "1.0");

		ComplexSubstitutionModel consonantModel = new ComplexSubstitutionModel();
		consonantModel.initByName("rates", consonantRates, "frequencies", consonantFrequencies);

		SiteModel consonantSitemodel = new SiteModel();
		consonantSitemodel.initByName("gammaCategoryCount", 1, "substModel", consonantModel, "shape", "1.0",
				"proportionInvariant", "0.0");

		String [] taxa = tree.getTaxaNames();
		StringBuilder [] binseqs = new StringBuilder[taxa.length];
		for (int i = 0; i < binseqs.length; i++) {
			binseqs[i] = new StringBuilder();
			// ascertainment correction column
			binseqs[i].append('0');
		}
		
		StringBuilder [] seqs = new StringBuilder[taxa.length];
		for (int i = 0; i < seqs.length; i++) {
			seqs[i] = new StringBuilder();
		}

		int target = sequenceLengthInput.get();
		int length = 0;
		int progress = 0;
		int cognateSite = 0;
		while (length < target) {
			int [] pattern = cognateData.getPattern(cognateData.getPatternIndex(cognateSite));
			if (!isAllZero(pattern)) {
				String word = "word" + cognateSite;
				words += word + ",";
				int start = length + 1;
				int end = start +  wordLengths[cognateSite];
				if (end > target) {
					end = target;
				}
				filters += "<data id='" + word + "' spec='FilteredAlignment' filter='" + start + "-" + end + "' data='@data'/>\n";

				
				// randomly pick number of vowels by flipping coin for every site
				// ensure there is at least one vowel and one consonant by 
				// assigning first site to a consonant and last site to a vowel
				int n = wordLengths[cognateSite];
				if (length + n > target) {
					n = target - length;
				}
				int vowelCount = 1;
				for (int i = 1; i < n; i++) {
					if (Randomizer.nextBoolean()) {
						vowelCount++;
					}
				}
				int consonantCount = n - vowelCount;
				
				SequenceSimulator vowelSim = new SequenceSimulator();
				vowelSim.initByName("data", vowelData, "tree", tree, "sequencelength", vowelCount, "outputFileName",
						"gammaShapeSequence.xml", "siteModel", vowelSitemodel, "branchRateModel", clockmodel);
				data = vowelSim.simulate();
				for (int i = 0; i < seqs.length; i++) {
					
					String seq = data.sequenceInput.get().get(i).dataInput.get();
					if (pattern[i] == 1 || pattern[1] == 3) {
						seqs[i].append(seq);
						binseqs[i].append("1");
					} else {
						// mask out sequence if pattern[i] == "0"
						for (int j = 0; j < seq.length(); j++) {
							seqs[i].append(".");
						}
						binseqs[i].append("0");
					}
				}
				
				SequenceSimulator sim = new SequenceSimulator();
				sim.initByName("data", consonantData, "tree", tree, "sequencelength", consonantCount, "outputFileName",
						"gammaShapeSequence.xml", "siteModel", consonantSitemodel, "branchRateModel", clockmodel);
				data = sim.simulate();		
				for (int i = 0; i < seqs.length; i++) {
					String seq = data.sequenceInput.get().get(i).dataInput.get();
					if (pattern[i] == 1 || pattern[1] == 3) {
						seqs[i].append(seq);
						binseqs[i].append("1");
					} else {
						// mask out sequence if pattern[i] == "0"
						for (int j = 0; j < seq.length(); j++) {
							seqs[i].append(".");
						}
						binseqs[i].append("0");
					}
				}

				length += wordLengths[cognateSite];
				
				while (progress < length) {
					progress++;
					if (progress % 10 == 0) {
						if (progress % 100 == 0) {
							System.err.print('|');
						} else {
							System.err.print('.');
						}
					}
				}
			}
			cognateSite++;
		}
		
		dataType.setID("phoneme");
		data = createAlignment(dataType, taxa, seqs);

		Binary binary = new Binary();
		binary.setID("binary");
		binaryData = createAlignment(binary, taxa, binseqs);
		return data;
	}
	

	private boolean isAllZero(int[] pattern) {
		for (int d : pattern) {
			if (d == 1) {
				return false;
			}
		}
		return true;
	}

	private int[] generateWordLengths(Alignment cognateData) {
		int [] wordLengths = new int[sequenceLengthInput.get()];
		
		PoissonDistribution poisson = new PoissonDistributionImpl(3);
		for (int i = 0; i < wordLengths.length; i++) {
			try {
				wordLengths[i] = 2 + poisson.inverseCumulativeProbability(Randomizer.nextDouble());
			} catch (MathException e) {
				e.printStackTrace();
			}
		}
		return wordLengths;
	}
	

	private Alignment generateCognateAllignment(Tree tree, int k) {
		UserDataType pdDataType;
		pdDataType = new UserDataType();
		pdDataType.initByName(
				"states", 4,
				"codelength", 1,
				"codeMap","A = 0, 1 = 1, B = 2, 0 = 0 2, ? = 0 1 2, - = 0 1 2, C = 0 1 2, D = 3");

		// set up model to draw samples from
		Alignment data = emptyAlignment(tree, pdDataType, "?");
		
		RealParameter freqs = new RealParameter("0.95 0.03 0.02 0.00");
		Frequencies f = new Frequencies();
		f.initByName("frequencies", freqs);

		BirthDeathModel pd = new BirthDeathModel();
		pd.initByName("frequencies", f, "deathprob", (PDCognateDeathRate == null) ? "0.05" : PDCognateDeathRate[k]+"");
		
		StrictClockModel clockmodel = new StrictClockModel();
		clockmodel.initByName("clock.rate", "1.0");
		
//		RealParameter freqs = new RealParameter("0.25 0.25 0.25 0.25");
//		Frequencies f = new Frequencies();
//		f.initByName("frequencies", freqs);
//
//		BinaryCovarion covarion = new BinaryCovarion();
//		covarion.initByName("alpha", "0.01", "switchRate", "0.25", 
//				"vfrequencies", "0.5 0.5", 
//				"hfrequencies", "0.5 0.5",
//				"frequencies", f);
//
//		StrictClockModel clockmodel = new StrictClockModel();
//		clockmodel.initByName("clock.rate", "1.0");

		RealParameter p = new RealParameter("0.0");
		SiteModel sitemodel = new SiteModel();
		sitemodel.initByName("gammaCategoryCount", useGammaInput.get()?4:1, "substModel", pd, "shape", useGammaInput.get()?shapes[k]+"":"1.0",
				"proportionInvariant", p);
		SequenceSimulator sim = new SequenceSimulator();
		sim.initByName("data", data, "tree", tree, "sequencelength", sequenceLengthInput.get(), "outputFileName",
				"gammaShapeSequence.xml", "siteModel", sitemodel, "branchRateModel", clockmodel);

		data = sim.simulate();
		data.userDataTypeInput.setValue(pdDataType, data);
		data.initAndValidate();

		return data;
	}
	

	private Alignment emptyAlignment(Tree tree, DataType dataType, String dummyState) {
		String[] taxa = tree.getTaxaNames();
		List<Sequence> seqs = new ArrayList<>();
		for (int j = 0; j < taxa.length; j++) {
			Sequence A = new Sequence();
			A.initByName("taxon", taxa[j], "value", dummyState);
			seqs.add(A);
		}

		Alignment data = new Alignment();
		if (dataType instanceof UserDataType) {
			data.initByName("sequence", seqs, "userDataType", dataType);
		} else {
			data.initByName("sequence", seqs, "dataType", dataType.getTypeDescription());
		}
		return data;
	}

	private Alignment createAlignment(DataType dataType, String[] taxa, StringBuilder[] sequences) {
		List<Sequence> seqs = new ArrayList<>();
		for (int j = 0; j < taxa.length; j++) {
			Sequence A = new Sequence();
			A.setID("seq_"+ ((BEASTInterface)dataType).getID()+ (j < 10?"_0":"_") + j);
			taxa[j] += "                       ".substring(taxa[j].length());
			A.initByName("taxon", taxa[j], "value", sequences[j].toString());
			seqs.add(A);
		}

		Alignment data = new Alignment();
		data.setID(((BEASTInterface)dataType).getID() + "data");
		if (dataType instanceof UserDataType) {
			data.initByName("sequence", seqs, "userDataType", dataType);
		} else {
			data.initByName("sequence", seqs, "dataType", dataType.getTypeDescription());
		}
		return data;
	}

	
	public static void main(String[] args) throws Exception {
		new Application(new PhonemeSimulator(), "PhonemeSimulator", args);
	}
}
