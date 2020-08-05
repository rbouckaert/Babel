package babel.evolution.likelihood;


import java.io.PrintStream;

import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Function;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Loggable;
import beast.evolution.likelihood.TreeLikelihood;
import beast.evolution.sitemodel.SiteModel;

@Description("Gets probability of the last state at the root of a tree")
public class LastStateRootProb extends BEASTObject implements Function, Loggable {
	final public Input<TreeLikelihood> likelihoodInput = new Input<>("likelihood", "likelihood to get the root probability from", Validate.REQUIRED);

	TreeLikelihood likelihood;
	@Override
	public void initAndValidate() {
		likelihood = likelihoodInput.get();
	}

	@Override
	public int getDimension() {
		return 1;
	}

	@Override
	public double getArrayValue() {
		likelihood.calculateLogP();
		double [] partials = likelihood.getRootPartials();
		final double[] frequencies = likelihood.getSubstitutionModel().getFrequencies();
		double [] p = new double[frequencies.length];
		for (int i = 0; i < p.length; i++) {
			p[i] = partials[i] * frequencies[i];
		}
		double sum = 0;
		for (double d : p) {
			sum += d;
		}
		
		
		double lastP = p[p.length-1] / sum;
		return lastP;
	}

	@Override
	public double getArrayValue(int dim) {
		return getArrayValue();
	}

	@Override
	public void init(PrintStream out) {
		SiteModel sm = (SiteModel) likelihood.siteModelInput.get();
		int stateCount = sm.getSubstitutionModel().getFrequencies().length;
		out.append(getID() + "_" + stateCount + "\t");
		
	}

	@Override
	public void log(long sample, PrintStream out) {
		out.append(getArrayValue()+"\t");
		
	}

	@Override
	public void close(PrintStream out) {
		// nothing to do
	}

}
