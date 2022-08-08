/*
 * AbstractObservationProcess.java
 *
 * Copyright (C) 2002-2012 Alexei Drummond,
 * Andrew Rambaut, Marc Suchard and Alexander V. Alekseyenko
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * BEAST is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package babel.evolution.likelihood;



import java.util.Set;

import babel.evolution.datatype.MutationDeathType;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Input.Validate;
import beast.base.inference.CalculationNode;
import beast.base.inference.parameter.RealParameter;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.AscertainedAlignment;
import beast.base.evolution.branchratemodel.BranchRateModel;
import beast.base.evolution.branchratemodel.StrictClockModel;
import beast.base.evolution.likelihood.LikelihoodCore;
import beast.base.evolution.sitemodel.SiteModel;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.util.GammaFunction;


@Description("Abstract Observation Process defines how the integration of gain events is done along the tree."+
        "Specific instances should define how the data is collected."+
        "Alekseyenko, AV., Lee, CJ., Suchard, MA. Wagner and Dollo: a stochastic duet" +
        "by composing two parsimonious solos. Systematic Biology 2008 57(5): 772 - 784; doi:" +
        "10.1080/10635150802434394. PMID: 18853363")
public class AbstractObservationProcess extends CalculationNode {
    //    Input<String> Name = new Input<String>("Name", "description here");
    public Input<Tree> treeModelInput = new Input<>("tree", "description here", Validate.REQUIRED);
    public Input<Alignment> patternsInput = new Input<>("data", "description here", Validate.REQUIRED);
    public Input<SiteModel> siteModelInput = new Input<>("siteModel", "description here", Validate.REQUIRED);
    public Input<BranchRateModel> branchRateModeInput = new Input<>("branchRateModel", "description here");
    public Input<RealParameter> muInput = new Input<>("mu", "description here", Validate.REQUIRED);
    public Input<RealParameter> lamInput = new Input<>("lam", "description here");
    public Input<Boolean> integrateGainRateInputInput = new Input<>("integrateGainRate", "description here", false);


	@Override
	public void initAndValidate() {
		// TODO Auto-generated method stub
		
	}

//    dr.evomodel.MSSD.AbstractObservationProcess abstractobservationprocess;
//
//    double getNodeSurvivalProbability(int index, double averageRate) {
//        return abstractobservationprocess.getNodeSurvivalProbability(index, averageRate);
//    }
//
//    double nodePatternLikelihood(double[] arg0, PartialsProvider arg1) {
//        return abstractobservationprocess.nodePatternLikelihood(arg0, arg1);
//    }
//
//    double getAverageRate() {
//        return abstractobservationprocess.getAverageRate();
//    }
//
//    double getLogTreeWeight() {
//        return abstractobservationprocess.getLogTreeWeight();
//    }
//
//    double calculateLogTreeWeight() {
//        return abstractobservationprocess.calculateLogTreeWeight();
//    }
//
//    @Override
//    public void store() {
//        abstractobservationprocess.store();
//    }
//
//    @Override
//    public void restore() {
//        abstractobservationprocess.restore();
//    }
//
//    @Override
//    protected boolean requiresRecalculation() {
//        return abstractobservationprocess.requiresRecalculation();
//    }
//
//    void setIntegrateGainRate(boolean integrateGainRate) {
//        abstractobservationprocess.setIntegrateGainRate(integrateGainRate);
//    }

    protected boolean[] nodePatternInclusion;
    protected boolean[] storedNodePatternInclusion;
    protected double[] cumLike;
    protected double[] nodePartials;
    protected double[] nodeLikelihoods;
    protected int nodeCount;
    protected int patternCount;
    protected int stateCount;
    protected Tree treeModel;
    protected Alignment patterns;
    protected int[] patternWeights;
    protected RealParameter mu;
    protected RealParameter lam;

    // update control variables
    protected boolean weightKnown;
    protected double logTreeWeight;
    protected double storedLogTreeWeight;
    private double gammaNorm;
    private double totalPatterns;
    protected MutationDeathType dataType;
    protected int deathState;
    protected SiteModel siteModel;
    private double logN;
    protected boolean nodePatternInclusionKnown = false;
    BranchRateModel branchRateModel;

    public void init(String Name, Tree treeModel, Alignment patterns, SiteModel siteModel,
                                      BranchRateModel branchRateModel, RealParameter mu, RealParameter lam,
                                      boolean integrateGainRate) {
        //super(Name);
        this.treeModel = treeModel;
        this.patterns = patterns;
        this.mu = mu;
        this.lam = lam;
        this.siteModel = siteModel;
        if (branchRateModel != null) {
            this.branchRateModel = branchRateModel;
        } else {
            this.branchRateModel = new StrictClockModel();
        }
        //addModel(treeModel);
        //addModel(siteModel);
        //addModel(this.branchRateModel);
        //addVariable(mu);
        //addVariable(lam);

        nodeCount = treeModel.getNodeCount();
        stateCount = patterns.getDataType().getStateCount();
        this.patterns = patterns;
        patternCount = patterns.getPatternCount();
        patternWeights = patterns.getWeights();
        totalPatterns = 0;
        for (int i = 0; i < patternCount; ++i) {
            totalPatterns += patternWeights[i];
        }
        logN = Math.log(totalPatterns);

        gammaNorm = -GammaFunction.lnGamma(totalPatterns + 1);

        dataType = (MutationDeathType) patterns.getDataType();
        this.deathState = MutationDeathType.DEATHSTATE;
        setNodePatternInclusion();
        cumLike = new double[patternCount];
        nodeLikelihoods = new double[patternCount];
        weightKnown = false;
        
        this.integrateGainRate = integrateGainRate;
    }

    public RealParameter getMuParameter() {
        return mu;
    }

    public RealParameter getLamParameter() {
        return lam;
    }

    private double calculateSiteLogLikelihood(int site, double[] partials, double[] frequencies) {
        int v = site * stateCount;
        double sum = 0.0;
        for (int i = 0; i < stateCount; i++) {
            sum += frequencies[i] * partials[v + i];
        }
        return Math.log(sum);
    }


    private void calculateNodePatternLikelihood(int nodeIndex,
                                                      double[] freqs,
                                                      LikelihoodCore likelihoodCore,
                                                      double averageRate,
                                                      double[] cumLike) {
                    // get partials for node nodeIndex
            likelihoodCore.getNodePartials(nodeIndex, nodePartials); // MAS
            /*
                multiply the partials by equilibrium probs
                    this part could be optimized by first summing
                    and then multiplying by equilibrium probs
            */
            double prob = Math.log(getNodeSurvivalProbability(nodeIndex, averageRate));

            for (int j = 0; j < patternCount; ++j) {
                if (nodePatternInclusion[nodeIndex * patternCount + j]) {
                    cumLike[j] += Math.exp(calculateSiteLogLikelihood(j, nodePartials, freqs) + prob);
                }
            }
    }

    private double accumulateCorrectedLikelihoods(double[] cumLike, double ascertainmentCorrection,
                                                  double[] patterWeights) {
        double logL = 0;
        for (int j = 0; j < patternCount; ++j) {
            logL += Math.log(cumLike[j] / ascertainmentCorrection) * patternWeights[j];
        }
        return logL;
    }

    public final double nodePatternLikelihood(double[] freqs, PartialsProvider likelihoodCore) {
        int i, j;
        double logL = gammaNorm;

        double birthRate = lam.getValue(0);
        double logProb;
        if (!nodePatternInclusionKnown)
            setNodePatternInclusion();
        if (nodePartials == null) {
            nodePartials = new double[patternCount * stateCount];
        }

        double averageRate = getAverageRate();

        for (j = 0; j < patternCount; ++j) cumLike[j] = 0;

        for (i = 0; i < nodeCount; ++i) {
            // get partials for node i
            likelihoodCore.getNodePartials(i, nodePartials);
            /*
                multiply the partials by equilibrium probs
                    this part could be optimized by first summing
                    and then multiplying by equilibrium probs
            */
//            likelihoodCore.calculateLogLikelihoods(nodePartials, freqs, nodeLikelihoods);   // MAS Removed
            logProb = Math.log(getNodeSurvivalProbability(i, averageRate));

            for (j = 0; j < patternCount; ++j) {
                if (nodePatternInclusion[i * patternCount + j]) {
//                    cumLike[j] += Math.exp(nodeLikelihoods[j] + logProb);  // MAS Replaced with line below
                    cumLike[j] += Math.exp(calculateSiteLogLikelihood(j, nodePartials, freqs)
                                    + logProb);
                }
            }
        }

        double ascertainmentCorrection = getAscertainmentCorrection(cumLike);
//        System.err.println("AscertainmentCorrection: "+ascertainmentCorrection);

        for (j = 0; j < patternCount; ++j) {
            logL += Math.log(cumLike[j] / ascertainmentCorrection) * patternWeights[j];
        }

        double deathRate = mu.getValue(0);

        double logTreeWeight = getLogTreeWeight();

        if (integrateGainRate) {
            logL -= gammaNorm + logN + Math.log(-logTreeWeight * deathRate / birthRate) * totalPatterns;
        } else {
            logL += logTreeWeight + Math.log(birthRate / deathRate) * totalPatterns;
        }
        return logL;
    }

    protected double getAscertainmentCorrection(double[] patternProbs) {
        // This function probably belongs better to the AscertainedSitePatterns
        double excludeProb = 0, includeProb = 0, returnProb = 1.0;
        if (this.patterns instanceof AscertainedAlignment) {
//            int[] includeIndices = ((AscertainedAlignment) patterns).getIncludePatternIndices();
            Set<Integer> excludeIndices = ((AscertainedAlignment) patterns).getExcludedPatternIndices();
//            for (int i = 0; i < ((AscertainedAlignment) patterns).getIncludePatternCount(); i++) {
//                int index = includeIndices[i];
//                includeProb += patternProbs[index];
//            }
            for (int index : excludeIndices) {
                excludeProb += patternProbs[index];
            }
            if (includeProb == 0.0) {
                returnProb -= excludeProb;
            } else if (excludeProb == 0.0) {
                returnProb = includeProb;
            } else {
                returnProb = includeProb - excludeProb;
            }
        }

        return returnProb;
    }

    final public double getLogTreeWeight() {
        if (!weightKnown) {
            logTreeWeight = calculateLogTreeWeight();
            weightKnown = true;
        }

        return logTreeWeight;
    }

    // should override this method
    public double calculateLogTreeWeight() {return Double.NEGATIVE_INFINITY;}

    // should override this method
    void setNodePatternInclusion() {}

    final public double getAverageRate() {
        if (!averageRateKnown) {
            double avgRate = 0.0;
            double proportions[] = siteModel.getCategoryProportions(null);
            for (int i = 0; i < siteModel.getCategoryCount(); ++i) {
                avgRate += proportions[i] * siteModel.getRateForCategory(i, null);
            }
            averageRate = avgRate;
            averageRateKnown = true;
        }
        return averageRate;
    }

    public double getNodeSurvivalProbability(int index, double averageRate) {
        Node node = treeModel.getNode(index);
        Node parent = node.getParent();

        if (parent == null) return 1.0;

        final double deathRate = mu.getValue(0) * averageRate; //getAverageRate();
        final double branchRate = branchRateModel.getRateForBranch(node);
        // Get the operational time of the branch
        final double branchTime = branchRate * node.getLength();
        return 1.0 - Math.exp(-deathRate * branchTime);
    }


    @Override
	public boolean requiresRecalculation() {
    	if (mu.somethingIsDirty()) {
      	  averageRateKnown = false;
    	  weightKnown = false;
    	  nodePatternInclusionKnown = false;
    	}
    	if (siteModel.isDirtyCalculation()) {
    	  averageRateKnown = false;
      }
      if (treeModel.somethingIsDirty()) {
    	  weightKnown = false;
    	  nodePatternInclusionKnown = false;
      }
    	return true;
    }
//    protected void handleModelChangedEvent(Model model, Object object, int index) {
//        if (model == siteModel) {
//            averageRateKnown = false;
//        }
//        if (model == treeModel || model == siteModel || model == branchRateModel) {
//            weightKnown = false;
//        }
//        if (model == treeModel) {
//            if (object instanceof TreeModel.TreeChangedEvent) {
//                if (((TreeModel.TreeChangedEvent) object).isTreeChanged()) {
//                    nodePatternInclusionKnown = false;
//                }
//            }
//        }
//    }
//
//    protected final void handleVariableChangedEvent(Variable variable, int index, Parameter.ChangeType type) {
//        if (variable == mu || variable == lam) {
//            weightKnown = false;
//        } else {
//            System.err.println("AbstractObservationProcess: Got unexpected parameter changed event. (Parameter = " + variable + ")");
//        }
//    }
//
    @Override
	public void store() {
//        storedAverageRate = averageRate;
        storedLogTreeWeight = logTreeWeight;
        System.arraycopy(nodePatternInclusion, 0, storedNodePatternInclusion, 0, storedNodePatternInclusion.length);
    }

    @Override
	public void restore() {
//        averageRate = storedAverageRate;
        averageRateKnown = false;
        logTreeWeight = storedLogTreeWeight;
        boolean[] tmp = storedNodePatternInclusion;
        storedNodePatternInclusion = nodePatternInclusion;
        nodePatternInclusion = tmp;
    }

    protected void acceptState() {
    }

    public void setIntegrateGainRate(boolean integrateGainRate) {
        this.integrateGainRate = integrateGainRate;
    }

    private boolean integrateGainRate = false;

    private double averageRate;
    private boolean averageRateKnown = false;

}