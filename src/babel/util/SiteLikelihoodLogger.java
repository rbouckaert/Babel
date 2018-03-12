package babel.util;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Input;
import beast.core.Loggable;
import beast.evolution.alignment.Alignment;
import beast.evolution.likelihood.GenericTreeLikelihood;
import beast.evolution.likelihood.TreeLikelihood;
import beast.evolution.sitemodel.SiteModel;
import beast.evolution.substitutionmodel.JukesCantor;

@Description("Logs site likelihoods for each site")
public class SiteLikelihoodLogger extends BEASTObject implements Loggable {
	public Input<TreeLikelihood> likelihoodInput = new Input<>("likelihood", "treelikelihood for which site likelihoods should be logged");
	public Input<Integer> fromInput = new Input<>("from", "index of first site to log. First site is 1 (not 0). If not specified, start at 1.", -1);
	public Input<Integer> toInput = new Input<>("to", "index of last site to log. If not specified, end at last site.", -1);
	public Input<String> valueInput = new Input<>("value", "space delimited set of labels, one for each site in the alignment. Used as site label in the log file.");

	int from, to;

	int siteCount;
	TreeLikelihood likelihood;
	Alignment data;

	@Override
	public void initAndValidate() {
		GenericTreeLikelihood org = likelihoodInput.get();
        likelihood = likelihoodInput.get();
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
			if (labelCount != (to-from)) {
				String str = "";
				for (String s : values.split("\\s+")) {
					str += "[" + s + "]";
				}
				throw new IllegalArgumentException("Expected "+(to-from)+" labels, but there are " + labelCount + " " + str);
			}
		}
		
		data = likelihood.dataInput.get();
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
		double [] patternLogLikelihoods = likelihood.getPatternLogLikelihoods();
		for (int i = from; i < to; i++) {
			out.append(patternLogLikelihoods[data.getPatternIndex(i)] + "");
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
