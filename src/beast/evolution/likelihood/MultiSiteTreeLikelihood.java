package beast.evolution.likelihood;

import beast.evolution.alignment.Alignment;
import beast.evolution.datatype.TwoStateCovarion;

public class MultiSiteTreeLikelihood extends TreeLikelihood {
	

	
	private double [] siteFreqs;
	private Alignment data;
	private int stateCount;
	boolean isCovarion = false;
	
	@Override
	public void initAndValidate() {
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

}
