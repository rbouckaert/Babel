package babel.tools;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.PoissonDistribution;
import org.apache.commons.math.distribution.PoissonDistributionImpl;

import babel.evolution.substitutionmodel.BirthDeathModel;
import babel.tools.utils.MemoryFriendlyTreeSet;
import beast.app.seqgen.SequenceSimulator;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.Input.Validate;
import beast.core.parameter.RealParameter;
import beast.core.util.Log;
import beast.evolution.alignment.Alignment;
import beast.evolution.alignment.Sequence;
import beast.evolution.branchratemodel.StrictClockModel;
import beast.evolution.datatype.DataType;
import beast.evolution.datatype.UserDataType;
import beast.evolution.sitemodel.SiteModel;
import beast.evolution.substitutionmodel.ComplexSubstitutionModel;
import beast.evolution.substitutionmodel.Frequencies;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;
import beast.util.XMLProducer;

@Description("Simulate phoneme alignemnts on a tree. "
		+ "1: generate cognates on tree by pseudo Dollo model. "
		+ "2: generate word lengths + vowel or consonant sites. "
		+ "3: generate vowel or consonant alignments. ")
public class PhonemeSimulator extends Runnable {
	final public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<Integer> sequenceLengthInput = new Input<>("sequencelength", "nr of phonemes to generate (default 1000).", 1000);
	final public Input<OutFile> outputInput = new Input<>("out","output file. Print to stdout if not specified");

	
	private String words = "";
	private String filters = "";

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {		

		// get trees
		MemoryFriendlyTreeSet srcTreeSet = new MemoryFriendlyTreeSet(treesInput.get().getPath(), 0);
		srcTreeSet.reset();
		Tree tree = srcTreeSet.next();
		
//		1: generate cognates on tree by pseudo Dollo model.
		Alignment cognateData = generateCognateAllignment(tree);
		
//		2: generate word lengths + vowel or consonant sites.
		int [] wordLengths = generateWordLengths(cognateData);
		
//		3: generate vowel or consonant alignments.
		Alignment phonemeData = generatePhonemeData(tree, cognateData, wordLengths);
		
		// output results
        PrintStream out = System.out;
        if (outputInput.get() != null) {
			Log.warning("Writing to file " + outputInput.get().getPath());
        	out = new PrintStream(outputInput.get());
        }
        
        XMLProducer producer = new XMLProducer();
        out.println("{\"sequences\":\"");
        String xml = producer.toRawXML(phonemeData);
        out.println(xml);
        out.println("\",");
        
        out.println("\"words\":\"" + words + "\",");
        out.println("\"filters\":\"");
        out.println(filters);
        out.println("\"\n}");

        if (outputInput.get() != null) {
        	out.close();
        }
        Log.warning.println("Done.");
	}

	
	
	private Alignment generatePhonemeData(Tree tree, Alignment cognateData, int[] wordLengths) {
		UserDataType dataType = new UserDataType();
		dataType.initByName(
				"states", 28, 
				"codelength", 2, 
				"codeMap", "-.=0,Ŋ.=27,_.=2,A.=3,Aː=4,B.=5,E.=6,Eː=7,F.=8,G.=9,H.=10,I.=11,Iː=12,K.=13,L.=14,M.=15,N.=16,O.=17,Oː=18,P.=19,R.=20,S.=21,T.=22,U.=23,Uː=24,V.=25,W.=26,ʔ.=1,..=0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27");
		UserDataType vowelDataType = new UserDataType();
		vowelDataType.initByName(
				"states", 12, 
				"codelength", 2, 
				"codeMap", "-.=0,Uː=1,_.=2,A.=3,Aː=4,E.=5,Eː=6,I.=7,Iː=8,O.=9,Oː=10,U.=11,..=0 1 2 3 4 5 6 7 8 9 10 11");
		UserDataType consonantDataType = new UserDataType();
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
				filters += "<data id=\"" + word + "\" spec='FilteredAlignment' filter='" + start + "-" + end + "' data='@data'/>\n";

				
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
				
				SequenceSimulator vowelSim = new beast.app.seqgen.SequenceSimulator();
				vowelSim.initByName("data", vowelData, "tree", tree, "sequencelength", vowelCount, "outputFileName",
						"gammaShapeSequence.xml", "siteModel", vowelSitemodel, "branchRateModel", clockmodel);
				data = vowelSim.simulate();
				for (int i = 0; i < seqs.length; i++) {
					String seq = data.sequenceInput.get().get(i).dataInput.get();
					seqs[i].append(seq);
				}
				
				SequenceSimulator sim = new beast.app.seqgen.SequenceSimulator();
				sim.initByName("data", consonantData, "tree", tree, "sequencelength", consonantCount, "outputFileName",
						"gammaShapeSequence.xml", "siteModel", consonantSitemodel, "branchRateModel", clockmodel);
				data = sim.simulate();		
				for (int i = 0; i < seqs.length; i++) {
					String seq = data.sequenceInput.get().get(i).dataInput.get();
					seqs[i].append(seq);
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
		
		data = createAlignment(dataType, taxa, seqs);
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
	

	private Alignment generateCognateAllignment(Tree tree) {
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
		pd.initByName("frequencies", f, "deathprob", "0.05");
		

		StrictClockModel clockmodel = new StrictClockModel();

		RealParameter p = new RealParameter("0.0");
		SiteModel sitemodel = new SiteModel();
		sitemodel.initByName("gammaCategoryCount", 1, "substModel", pd, "shape", "1.0",
				"proportionInvariant", p);
		SequenceSimulator sim = new beast.app.seqgen.SequenceSimulator();
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
		data.initByName("sequence", seqs, "userDataType", dataType);
		return data;
	}

	private Alignment createAlignment(UserDataType dataType, String[] taxa, StringBuilder[] sequences) {
		List<Sequence> seqs = new ArrayList<>();
		for (int j = 0; j < taxa.length; j++) {
			Sequence A = new Sequence();
			A.initByName("taxon", taxa[j], "value", sequences[j].toString());
			seqs.add(A);
		}

		Alignment data = new Alignment();
		data.initByName("sequence", seqs, "userDataType", dataType);
		return data;
	}

	
	public static void main(String[] args) throws Exception {
		new Application(new PhonemeSimulator(), "PhonemeSimulator", args);
	}
}
