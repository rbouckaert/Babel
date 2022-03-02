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
				"states", 29, 
				"codelength", 2, 
				"codeMap", "-.=0,..=1,_.=2,A.=3,Aː=4,B.=5,E.=6,Eː=7,F.=8,G.=9,H.=10,I.=11,Iː=12,K.=13,L.=14,M.=15,N.=16,O.=17,Oː=18,P.=19,R.=20,S.=21,T.=22,U.=23,Uː=24,V.=25,W.=26,Ŋ.=27,ʔ.=28");
		
		Alignment data = emptyAlignment(tree, dataType);
		StrictClockModel clockmodel = new StrictClockModel();

		int target = sequenceLengthInput.get();
		int length = 0;
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

				SiteModel sitemodel = new SiteModel();
				sitemodel.initByName("gammaCategoryCount", 1, "substModel", phonemeModel, "shape", "1.0",
						"proportionInvariant", "0.0");
				SequenceSimulator sim = new beast.app.seqgen.SequenceSimulator();
				sim.initByName("data", data, "tree", tree, "sequencelength", sequenceLengthInput.get(), "outputFileName",
						"gammaShapeSequence.xml", "siteModel", sitemodel, "branchRateModel", clockmodel);
				data = sim.simulate();		

			
			
			}
		}
		return null;
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
		// set up model to draw samples from
		UserDataType dataType = new UserDataType();
		dataType.initByName(
				"states", 4,
				"codelength", 1,
				"codeMap","A = 0, 1 = 1, B = 2, 0 = 0 2, ? = 0 1 2, - = 0 1 2, C = 0 1 2, D = 3");
		Alignment data = emptyAlignment(tree, dataType);
		
		RealParameter freqs = new RealParameter("0.95 0.03 0.02 0.00");
		Frequencies f = new Frequencies();
		f.initByName("frequencies", freqs);

		BirthDeathModel pd = new BirthDeathModel();
		pd.initByName("frequencies", freqs, "deathprob", "0.05");
		

		StrictClockModel clockmodel = new StrictClockModel();

		RealParameter p = new RealParameter("0.0");
		SiteModel sitemodel = new SiteModel();
		sitemodel.initByName("gammaCategoryCount", 1, "substModel", pd, "shape", "1.0",
				"proportionInvariant", p);
		SequenceSimulator sim = new beast.app.seqgen.SequenceSimulator();
		sim.initByName("data", data, "tree", tree, "sequencelength", sequenceLengthInput.get(), "outputFileName",
				"gammaShapeSequence.xml", "siteModel", sitemodel, "branchRateModel", clockmodel);
		data = sim.simulate();		
		return data;
	}
	

	private Alignment emptyAlignment(Tree tree, DataType dataType) {
		String[] taxa = tree.getTaxaNames();
		List<Sequence> seqs = new ArrayList<>();
		for (int j = 0; j < taxa.length; j++) {
			Sequence A = new Sequence();
			A.initByName("taxon", taxa[j], "value", "?");
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
