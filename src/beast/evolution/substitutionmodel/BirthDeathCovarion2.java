package beast.evolution.substitutionmodel;

import java.io.PrintStream;

import beast.core.Citation;
import beast.core.Description;
import beast.core.Input;
import beast.core.Loggable;
import beast.core.Input.Validate;
import beast.core.parameter.RealParameter;
import beast.evolution.datatype.DataType;
import beast.evolution.datatype.TwoStateCovarionPlus;

@Description("Covarion model for Binary data with 3 rates")
@Citation(value="R.Bouckaert, M. Robbeets. Pseudo Dollo models for the evolution of binary characters along a tree. BIORXIV/2017/207571", DOI="https://doi.org/10.1101/207571")
public class BirthDeathCovarion2 extends ComplexSubstitutionModel implements Loggable {
    public Input<RealParameter> switchRateInput = new Input<RealParameter>("switchRate", "the rate of flipping between slow and fast modes", Validate.REQUIRED);
    public Input<RealParameter> vfrequenciesInput = new Input<RealParameter>("vfrequencies", "the frequencies of the visible states", Validate.REQUIRED);
    public Input<RealParameter> hfrequenciesInput = new Input<RealParameter>("hfrequencies", "the frequencies of the hidden rates");

    public Input<RealParameter> delParameter = new Input<RealParameter>("deathprob", "rate of death, used to calculate death probability", Validate.REQUIRED);
    public Input<RealParameter> originLengthInput = new Input<RealParameter>("originLength", "length of the branch at the origin");

    private RealParameter birthDeathRate;
    private RealParameter switchRate;
    private RealParameter frequencies;
    private RealParameter hiddenFrequencies;
    private RealParameter originLength;

    protected double[][] unnormalizedQ;
    protected double[][] storedUnnormalizedQ;
    int stateCount;

    public BirthDeathCovarion2() {
        ratesInput.setRule(Validate.OPTIONAL);
        frequenciesInput.setRule(Validate.FORBIDDEN);
    }

    @Override
    public void initAndValidate() {
    	birthDeathRate = delParameter.get();
        switchRate = switchRateInput.get();
        frequencies = vfrequenciesInput.get();
        hiddenFrequencies = hfrequenciesInput.get();
        originLength = originLengthInput.get();

        
    	if (switchRate.getDimension() != 2) {
    		throw new IllegalArgumentException("switchRate should have dimension 2");
    	}
        if (birthDeathRate.getDimension() != 2) {
            throw new IllegalArgumentException("alpha should have dimension 2");
        }
        if (frequencies.getDimension() != 3) {
            throw new IllegalArgumentException("frequencies should have dimension 3");
        }
    	if (hfrequenciesInput.get() == null) {
    		throw new IllegalArgumentException("hiddenFrequenciesshould should be specified");
    	}
        if (hiddenFrequencies.getDimension() != 2) {
            throw new IllegalArgumentException("hiddenFrequenciesshould have dimension 2");
        }
        
        
        nrOfStates = 5;
        unnormalizedQ = new double[nrOfStates][nrOfStates];
        storedUnnormalizedQ = new double[nrOfStates][nrOfStates];

        updateMatrix = true;
        try {
			eigenSystem = createEigenSystem();
		} catch (SecurityException /*| ClassNotFoundException | InstantiationException | IllegalAccessException*/ | IllegalArgumentException
				/*| InvocationTargetException*/ e) {
			throw new IllegalArgumentException(e);
		}
        rateMatrix = new double[nrOfStates][nrOfStates];
        relativeRates = new double[nrOfStates * (nrOfStates - 1)];
        storedRelativeRates = new double[nrOfStates * (nrOfStates - 1)];
    }


    @Override
    public boolean canHandleDataType(DataType dataType) {
    	return dataType.getStateCount() == 5;
        // return dataType.getClass().equals(U.class);
    }


    @Override
    protected void setupRelativeRates() {
    }

    
//    @Override
//    public void getTransitionProbabilities(Node node, double fStartTime, double fEndTime, double fRate, double[] matrix) {
//    	super.getTransitionProbabilities(node, fStartTime, fEndTime, fRate, matrix);
//    	
//    	for (int i = 0; i < 5; i++) { // sanity check
//    		double sum = 0;
//    		for (int j = 0; j < 5; j++) {
//    			sum += matrix[i*5+j];
//    		}
//    		if (Math.abs(sum - 1.0) > 1e-10) {
//    			int h = 3;
//    			h++;
//    		}
//    	}
//    }

    @Override
    protected void setupRateMatrix() {
        setupUnnormalizedQMatrix();

        for (int i = 0; i < nrOfStates; i++) {
            for (int j = 0; j < nrOfStates; j++) {
                rateMatrix[i][j] = unnormalizedQ[i][j];
            }
        }
        // bring in frequencies
//        for (int i = 0; i < m_nStates; i++) {
//            for (int j = i + 1; j < m_nStates; j++) {
//            	m_rateMatrix[i][j] *= fFreqs[j];
//            	m_rateMatrix[j][i] *= fFreqs[i];
//            }
//        }
        // set up diagonal
        for (int i = 0; i < nrOfStates; i++) {
            double fSum = 0.0;
            for (int j = 0; j < nrOfStates; j++) {
                if (i != j)
                    fSum += rateMatrix[i][j];
            }
            rateMatrix[i][i] = -fSum;
        }
        // normalise rate matrix to one expected substitution per unit time
        //normalize(rateMatrix, getFrequencies());
    } // setupRateMatrix

    @Override
    public double[] getFrequencies() {
    	if (nrOfStates == 6) {
	        double[] fFreqs = new double[6];
	        fFreqs[0] = frequencies.getValue(0) * hiddenFrequencies.getValue(0);
	        fFreqs[1] = frequencies.getValue(1) * hiddenFrequencies.getValue(0);
	        fFreqs[2] = frequencies.getValue(2) * hiddenFrequencies.getValue(0);
	        fFreqs[3] = frequencies.getValue(0) * hiddenFrequencies.getValue(1);
	        fFreqs[4] = frequencies.getValue(1) * hiddenFrequencies.getValue(1);
	        fFreqs[5] = frequencies.getValue(2) * hiddenFrequencies.getValue(1);
	        return fFreqs;
    	} else {
	        double [] matrix = new double[nrOfStates * nrOfStates];
    		getTransitionProbabilities(null, originLength.getValue(), 0, 1.0, matrix);
	        double[] fFreqs = new double[5];
	        for (int i = 0; i < 5; i++) {
	        	fFreqs[i] = (matrix[i] + matrix[15 + i])/2.0;
	        }
	        fFreqs[2] = 0;
    		
//	        fFreqs[0] = 1e-10;
//	        fFreqs[1] = 1e-10;
//	        fFreqs[2] = 1.0 - 4e-10;
//	        fFreqs[3] = 1e-10;
//	        fFreqs[4] = 1e-10;
    		
//	        double[] fFreqs = new double[5];
//	        fFreqs[0] = frequencies.getValue(0) * hiddenFrequencies.getValue(0);
//	        fFreqs[1] = frequencies.getValue(1) * hiddenFrequencies.getValue(0);
//	        fFreqs[2] = frequencies.getValue(2);
//	        fFreqs[3] = frequencies.getValue(0) * hiddenFrequencies.getValue(1);
//	        fFreqs[4] = frequencies.getValue(1) * hiddenFrequencies.getValue(1);
	        return fFreqs;
    	}
    }


    protected void setupUnnormalizedQMatrix() {

    	double b = birthDeathRate.getValue(0);
    	double d = birthDeathRate.getValue(1);
        double s1 = switchRate.getValue(0);
        double s2 = switchRate.getValue(1);
        
        double slowFactor = 0.00;
//        double f0 = hiddenFrequencies.getValue(0);
//        double f1 = hiddenFrequencies.getValue(1);
        double p0 = frequencies.getValue(0);
        double p1 = frequencies.getValue(1);
        double p2 = frequencies.getValue(2);
//
//        
//        
//        assert Math.abs(1.0 - f0 - f1) < 1e-8;
//        assert Math.abs(1.0 - p0 - p1 - p2) < 1e-8;

    	if (nrOfStates == 6) {
	        unnormalizedQ[0][1] = b;// * p1/p1;
	        unnormalizedQ[0][2] = 0.0;
	        unnormalizedQ[0][3] = s1;
	        unnormalizedQ[0][4] = 0.0;
	        unnormalizedQ[0][5] = 0.0;
	
	        unnormalizedQ[1][0] = 0.0;
	
	        unnormalizedQ[1][2] = d;//*p0;
	        unnormalizedQ[1][3] = 0.0;
	        unnormalizedQ[1][4] = s2;
	        unnormalizedQ[1][5] = 0.0;
	        
	        
	        unnormalizedQ[2][0] = 0.0;
	        unnormalizedQ[2][1] = 0.0;
	
	        unnormalizedQ[2][3] = 0.0;
	        unnormalizedQ[2][4] = 0.0;
	        unnormalizedQ[2][5] = s1;
	
	        
	        unnormalizedQ[3][0] = s1;
	        unnormalizedQ[3][1] = 0.0;
	        unnormalizedQ[3][2] = 0.0;
	
	        unnormalizedQ[3][4] = b * slowFactor;
	        unnormalizedQ[3][5] = 0.0;
	
	        
	        unnormalizedQ[4][0] = 0.0;
	        unnormalizedQ[4][1] = s2;
	        unnormalizedQ[4][2] = 0.0;
	        unnormalizedQ[4][3] = 0.0;
	
	        unnormalizedQ[4][5] = d * slowFactor;
	
	        unnormalizedQ[5][0] = 0.0;
	        unnormalizedQ[5][1] = 0.0;
	        unnormalizedQ[5][2] = s1;
	        unnormalizedQ[5][3] = 0.0;
	        unnormalizedQ[5][4] = 0.0;
    	}

    	if (nrOfStates == 5) {
	    	unnormalizedQ[0][1] = b;// * p1/p1;
	        unnormalizedQ[0][2] = 0.0;
	        unnormalizedQ[0][3] = s1;
	        unnormalizedQ[0][4] = 0.0;
	
	        unnormalizedQ[1][0] = 0.0;
	
	        unnormalizedQ[1][2] = d;// * p0;
	        unnormalizedQ[1][3] = 0.0;
	        unnormalizedQ[1][4] = s2;
	        
	        
	        unnormalizedQ[2][0] = 0.0;
	        unnormalizedQ[2][1] = 0.0;
	
	        unnormalizedQ[2][3] = 0.0;
	        unnormalizedQ[2][4] = 0.0;
	
	        
	        unnormalizedQ[3][0] = s1;
	        unnormalizedQ[3][1] = 0.0;
	        unnormalizedQ[3][2] = 0.0;
	
	        unnormalizedQ[3][4] = b * slowFactor;
	
	        
	        unnormalizedQ[4][0] = 0.0;
	        unnormalizedQ[4][1] = s2;
	        unnormalizedQ[4][2] = d * slowFactor;
	        unnormalizedQ[4][3] = 0.0;
    	}    	
    }

    /**
     * Normalize rate matrix to one expected substitution per unit time
     *
     * @param matrix the matrix to normalize to one expected substitution
     * @param pi     the equilibrium distribution of states
     */
    private void normalize(double[][] matrix, double[] pi) {

        double subst = 0.0;
        int dimension = pi.length;

        for (int i = 0; i < dimension; i++) {
            subst += -matrix[i][i] * pi[i];
        }

        // normalize, including switches
        for (int i = 0; i < dimension; i++) {
            for (int j = 0; j < dimension; j++) {
                matrix[i][j] = matrix[i][j] / subst;
            }
        }

// TODO: work out normalisation
//        double switchingProportion = 0.0;
//        switchingProportion += matrix[0][2] * pi[2];
//        switchingProportion += matrix[2][0] * pi[0];
//        switchingProportion += matrix[1][3] * pi[3];
//        switchingProportion += matrix[3][1] * pi[1];
//        //System.out.println("switchingProportion=" + switchingProportion);
//
//        // normalize, removing switches
//        for (int i = 0; i < dimension; i++) {
//            for (int j = 0; j < dimension; j++) {
//                matrix[i][j] = matrix[i][j] / (1.0 - switchingProportion);
//            }
//        }
    }

	@Override
	public void init(PrintStream out) {
		out.print("freq1\tfreq2\tfreq3\tfreq4\tfreq5\tsumfreqs\trateb\trated\trates1a\trates1b\trates2a\trates2b\t");
	}

	@Override
	public void log(long nSample, PrintStream out) {
		double [] freqs = getFrequencies();
		double sum = 0;
		for (int i = 0; i < freqs.length; i++) {
			out.print(freqs[i]+"\t");
			sum += freqs[i];
		}
		out.print(sum+"\t");
		
		setupRateMatrix();
		out.print(rateMatrix[0][1] + "\t");
		out.print(rateMatrix[1][2] + "\t");
		out.print(rateMatrix[0][3] + "\t");
		out.print(rateMatrix[3][0] + "\t");
		out.print(rateMatrix[1][4] + "\t");
		out.print(rateMatrix[4][1] + "\t");
	}

	@Override
	public void close(PrintStream out) {
		// nothing TO DO 
		
	}


}
