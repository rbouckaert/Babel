package babel.evolution.likelihood;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.parameter.IntegerParameter;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.datatype.TwoStateCovarion;
import beast.base.evolution.likelihood.TreeLikelihood;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;

@Description("TreeLikelihood that assumes only a single site is reconstructed as 1 at the root")
public class MultiSiteTreeLikelihood extends TreeLikelihood {
	final public Input<IntegerParameter> rootStateInput = new Input<>("rootState", "if specified, the site that is considered to be 1");
	

	
	private double [] siteFreqs;
	private Alignment data;
	private int stateCount;
	boolean isCovarion = false;
	
	IntegerParameter rootState;
	
    protected double[] m_fStoredRootPartials;

	@Override
	public void initAndValidate() {
		rootState = rootStateInput.get();
		boolean useJava = System.getProperty("java.only") != null && System.getProperty("java.only").equals("true");
        System.setProperty("java.only", "true");
		super.initAndValidate();
        System.setProperty("java.only", useJava+"");
		
		data = dataInput.get();
		stateCount = data.getMaxStateCount();
		if (dataInput.get().getDataType() instanceof TwoStateCovarion) {
			stateCount = 4;
			isCovarion = true;
		}

		// empirical frequencies
		siteFreqs = new double[data.getSiteCount()];
		int n = 0;
		for (int i = 0; i < data.getSiteCount(); i++) {
			int k = data.getPatternIndex(i);
			int [] pattern = data.getPattern(k);
			for  (int j = 0; j < pattern.length; j++) {
				if (pattern[j] == 1) {
					siteFreqs[i]++;
					n++;
				}
			}			
		}
		for (int i = 0; i < siteFreqs.length; i++) {
			siteFreqs[i] /= n;
		}
		
	}
	
	@Override
	public double calculateLogP() {
		// TODO: there is an store/restore bug somewhere
		// make it go away (for now) by recalculating everything every time
		for (Node node : treeInput.get().getNodesAsArray()) {
			node.makeDirty(Tree.IS_FILTHY);
		}
		
		logP = super.calculateLogP();
		if (siteFreqs.length == 1 || 
				(data.isAscertained && siteFreqs.length == 2)) {
			return logP;
		}
		int patternCount = data.getPatternCount();
		double [] p0 = new double[patternCount];
		double [] p1 = new double[patternCount];

		double [] frequencies = substitutionModel.getFrequencies();
		
        int v = 0;
        for (int k = 0; k < patternCount; k++) {
            for (int i = 0; i < stateCount; i+=2) {
                p0[k] += frequencies[i] * m_fRootPartials[v];
                v++;
                p1[k] += frequencies[i+1] * m_fRootPartials[v];
                v++;
            }
        }
        
        if (rootState != null) {
    		logP = 0;
    		int w = 0;
    		for (int i = 0; i < data.getSiteCount(); i++) {
    			int j = data.getPatternIndex(i);
    			if (data.getPatternWeight(j) > 0) { // this happens for ascertained sites
    				if (j == rootState.getValue()) {
    					logP += Math.log(p1[j]);
    				} else {
    					logP += Math.log(p0[j]);
    				}
    				w++;
    			}
    		}

    		if (data.isAscertained) {
    	        final double ascertainmentCorrection = dataInput.get().getAscertainmentCorrection(patternLogLikelihoods);
    			logP -= ascertainmentCorrection * w;
    		}
    		
    		return logP;
        }
        
        
		double P0 = 0;
		for (int i = 0; i < data.getPatternCount(); i++) {
			P0 += Math.log(p0[i]) * data.getPatternWeight(i);
		}
		
		double [] s = new double[siteFreqs.length];
		for (int i = 0; i < siteFreqs.length; i++) {
			int k = data.getPatternIndex(i);
			double f = siteFreqs[i];
			s[i] = Math.log(p1[k] * f) + P0 - Math.log(p0[k] * (1-f));
		}
		double max = s[0];
		for (double d : s) {
			if ( d > max) {
				max = d;
			}
		}
		double sum = 0;
		for (double d : s) {
			sum += Math.exp(d - max);
		}
		logP = max + Math.log(sum);
		
		if (data.isAscertained) {
	        final double ascertainmentCorrection = dataInput.get().getAscertainmentCorrection(patternLogLikelihoods);
			logP -= ascertainmentCorrection /* * siteFreqs.length*/;
		}
		
		return logP;
	}
	
	@Override
	public void store() {
		if (m_fStoredRootPartials == null) {
			m_fStoredRootPartials = new double[m_fRootPartials.length];
		}
		System.arraycopy(m_fRootPartials, 0, m_fStoredRootPartials, 0, m_fRootPartials.length);
		
		super.store();
	}
	
	@Override
	public void restore() {
		double [] tmp = m_fStoredRootPartials;
		m_fStoredRootPartials = m_fRootPartials;
		m_fRootPartials = tmp;
		
		super.restore();
	}
	
	
	@Override
	protected boolean requiresRecalculation() {
		super.requiresRecalculation();
//		if (rootState.isDirtyCalculation()) {
//			super.requiresRecalculation();
//			return true;
//		}
		return true;
	}
}
