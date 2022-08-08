package babel.util;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import beast.base.core.BEASTObject;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Loggable;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.likelihood.GenericTreeLikelihood;
import beast.base.evolution.likelihood.TreeLikelihood;
import beast.base.evolution.sitemodel.SiteModel;
import beast.base.evolution.substitutionmodel.JukesCantor;
import beast.base.evolution.tree.Node;

@Description("Logs site rates calcualted as average over the site rates weighted by site treelikelihood")
public class SiteRateLogger extends BEASTObject implements Loggable {
	public Input<GenericTreeLikelihood> likelihoodInput = new Input<>("likelihood", "treelikelihood for which site rates should be logged");
	public Input<Integer> fromInput = new Input<>("from", "index of first site to log. First site is 1 (not 0). If not specified, start at 1.", -1);
	public Input<Integer> toInput = new Input<>("to", "index of last site to log. If not specified, end at last site.", -1);
	public Input<String> valueInput = new Input<>("value", "space delimited set of labels, one for each site in the alignment. Used as site label in the log file.");

	int from, to;

	class MyTreeLikelihood extends TreeLikelihood {
		
		double [] partials = null;
		
		public String getSiteRate(int siteIndex) {
			Alignment data = dataInput.get();
			int paternIndex = data.getPatternIndex(siteIndex);
			
			Node root = treeInput.get().getRoot();
			int stateCount = data.getDataType().getStateCount();
			int categoryCount = m_siteModel.getCategoryCount();
			int patternCount = data.getPatternCount();
			if (partials == null) {
				partials = new double[patternCount * stateCount * categoryCount];
			}
			likelihoodCore.getNodePartials(root.getNr(), partials);
			
            final double[] frequencies = m_siteModel.getSubstitutionModel().getFrequencies();
            
			double [] rateProbs = new double[categoryCount];
            for (int l = 0; l < categoryCount; l++) {
            	double sum = 0.0;
            	for (int i = 0; i < stateCount; i++) {
            		sum += frequencies[i] * partials[l * stateCount * patternCount + paternIndex * stateCount + i];
            	}
            	rateProbs[l] = sum;
            }

			
			double rate = 0;
			double sum = 0;
			double sum2 = 0;
			final double[] proportions = m_siteModel.getCategoryProportions(null);
			double [] rates = m_siteModel.getCategoryRates(root);
            for (int l = 0; l < categoryCount; l++) {
            	rate += rateProbs[l] * proportions[l] * rates[l];
            	// average probability of rate
                sum += rateProbs[l] * proportions[l];
                // average rate
                sum2 += proportions[l] * rates[l];
            }
			rate = rate / sum;
			rate = rate / sum2;
            

			
			return "" + rate;
		}
		
	}
	
	MyTreeLikelihood likelihood;
	int siteCount;
	
	@Override
	public void initAndValidate() {
		GenericTreeLikelihood org = likelihoodInput.get();
		// ensure we do not use BEAGLE
        boolean forceJava = Boolean.valueOf(System.getProperty("java.only"));
        System.setProperty("java.only", "true");
        // create new treelikelihood for which we can obtain its internal state
		likelihood = new MyTreeLikelihood();
		likelihood.initByName(                    
				"data", org.getInput("data").get(), 
				"tree", org.getInput("tree").get(), 
				"siteModel", org.getInput("siteModel").get(),
                "branchRateModel", org.getInput("branchRateModel").get(), 
                "useAmbiguities", org.getInput("useAmbiguities").get(), 
//                "useTipLikelihoods", org.getInput("useTipLikelihoods").get(),
                "scaling", org.getInput("scaling").get().toString());
		System.setProperty("java.only", "" + forceJava);
		
		siteCount = ((Alignment)org.getInput("data").get()).getSiteCount();
		
		from = fromInput.get() - 1;
		if (from < 0) {
			from = 0;
		}
		to = toInput.get() - 1;
		if (to < 0) {
			to = siteCount;
		}
		if (to < from) {
			throw new IllegalArgumentException("\"to\" ("+to+") must be larger than \"from\" ("+from+")");
		}
		
		String values = valueInput.get();
		if (values != null && values.trim().length() > 0) {
			int labelCount = values.split("\\s+").length;
			if (labelCount != (from - to)) {
				throw new IllegalArgumentException("Expected "+(from-to)+" lables, but there are " + labelCount);
			}
		}
	}

	@Override
	public void init(PrintStream out) {
		String values = valueInput.get();
		if (values != null && values.trim().length() > 0) {
			// use values as labels
			values = values.trim().replaceAll("\\s+", "\t");
			out.append(values);
			out.append("\t");
		} else {
			for (int i = from; i < to; i++) {
				out.append((getID() == null ? "siterate" : getID()) + (i + 1));
				out.append('\t');
			}
		}
	}

	@Override
	public void log(long sample, PrintStream out) {
		// refresh all internal data structures
		likelihood.calculateLogP();
		for (int i = from; i < to; i++) {
			out.append(likelihood.getSiteRate(i));
			out.append('\t');
		}
	}

	@Override
	public void close(PrintStream out) {
		// nothing to do
	}


	public static void main(String[] args) {
		List<List<Double>> results = new ArrayList<List<Double>>();
		double [] alphas = new double[]{0.1,0.2,0.4,0.8,1.0,2.0,4.0,8.0,16.0};
		int [] cats = new int[]{1, 2, 4, 8, 16, 32};
		
		for (double alpha : alphas) {
			List<Double> r = new ArrayList<>();
			for (int categories: cats) {
				SiteModel gammaSiteModel = new SiteModel();
				gammaSiteModel.initByName("gammaCategoryCount", categories,
						"shape", alpha+"",
						"substModel", new JukesCantor(),
						"proportionInvariant", "0.4");
				double sum = 0;
				double [] weights = gammaSiteModel.getCategoryProportions(null);
				for (int i = 0; i < categories; i++) {
					sum += weights[i] * gammaSiteModel.getRateForCategory(i, null);
				}
				r.add(sum);
			}
			results.add(r);
		}
		
		PrintStream out = System.out;
		// header
		out.println("Categories\t");
		for (double alpha : alphas) {
			out.print(alpha + "\t");
		}
		out.println();
		
		//table content
		for (int i = 0; i < cats.length; i++) {
			out.print(cats[i] + "\t");
			for (Double d : results.get(i)) {
				out.print(d+"\t");
			}
			out.println();
		}
	}
}
