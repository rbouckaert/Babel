package beast.evolution.likelihood;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import beast.core.Description;
import beast.core.Distribution;
import beast.core.Input;
import beast.core.State;
import beast.evolution.alignment.Alignment;
import beast.core.Input.Validate;

@Description("Mixture model of tree likelihoods")
public class TreeMixtureModel extends Distribution {
	final public Input<List<TreeLikelihood>> likelihoodsInput = new Input<>("distribution", 
			"likelihoods to be mixed", new ArrayList<>(), Validate.REQUIRED);
	final public Input<String> weightsInput = new Input<>("weights", "comma separated list of weightsm one for each likelihood. "
			+ "Weights should sum to 1. "
			+ "If not specified, equal weights are assumed");

	double [] weights;
	List<TreeLikelihood> likelihoods;
	Alignment data;
	
	@Override
	public void initAndValidate() {
		likelihoods = likelihoodsInput.get();
		if (likelihoods.size() < 2) {
			throw new IllegalArgumentException("At least 2 likelihoods expected in the mixture. Add more likelihoods");
		}
		weights = new double[likelihoods.size()];
		Arrays.fill(weights, 1.0 / weights.length);
		if (weightsInput.get() != null) {
			String [] str = weightsInput.get().split(",");
			if (str.length != weights.length) {
				throw new IllegalArgumentException("Number of weights (" + str.length +") does not equal number of likelihoods ("+weights.length+")");
			}
		}
		
		// sanity check: all likelihoods must be for the same alignment
		data = likelihoods.get(0).dataInput.get();
		for (int i = 1; i < weights.length; i++) {
			if (likelihoods.get(i).dataInput.get() != data) {
				throw new IllegalArgumentException("all likelihoods must be for the same alignment");
			}
		}
		// sanity check
		for (int i = 0; i < weights.length; i++) {
			if (likelihoods.get(i).useAscertainedSitePatterns) {
				throw new IllegalArgumentException("TreeMixtureModel does not work with ascertained alignments");
			}
		}
	}
	
	@Override
	public double calculateLogP() {
        double [][] patternLogLikelihoods = new double[weights.length][];        
        for (int i = 0; i < weights.length; i++) {
        	TreeLikelihood likelihood = likelihoods.get(i);        	
        	likelihood.calculateLogP();
        	patternLogLikelihoods[i] = likelihood.getPatternLogLikelihoods();
        }
        
        logP = 0;
        for (int i = 0; i < data.getPatternCount(); i++) {
        	double max = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < weights.length; j++) {
            	max = Math.max(max, patternLogLikelihoods[j][i]);
            }
            double c = 0;
            for (int j = 0; j < weights.length; j++) {
            	c += weights[j] * Math.exp(patternLogLikelihoods[j][i] - max);
            }

            logP += (Math.log(c) + max) * data.getPatternWeight(i);
        }
        return logP;
	}
	
	@Override
	public List<String> getArguments() {return null;}

	@Override
	public List<String> getConditions() {return null;}

	@Override
	public void sample(State state, Random random) {}
	
	@Override
	protected boolean requiresRecalculation() {
        return true;
	}
}
