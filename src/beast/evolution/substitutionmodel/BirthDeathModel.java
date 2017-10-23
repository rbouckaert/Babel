package beast.evolution.substitutionmodel;

import beast.core.Citation;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.parameter.RealParameter;
import beast.evolution.datatype.DataType;
import beast.evolution.likelihood.TreeLikelihood;
import beast.evolution.sitemodel.SiteModel;
import beast.evolution.tree.Node;

@Description("Pseudo Dollo substitution model")
@Citation(value="R.Bouckaert, M. Robbeets. Pseudo Dollo models for the evolution of binary characters along a tree. BIORXIV/2017/207571", DOI="https://doi.org/10.1101/207571")
public class BirthDeathModel extends GeneralSubstitutionModel {
	//public Input<RealParameter> birthRateInput = new Input<RealParameter>("birthrate","rate at which cognates are created", Validate.REQUIRED);
    public Input<RealParameter> delParameter = new Input<RealParameter>("deathprob", "rate of death, used to calculate death probability", Validate.REQUIRED);

	
    public BirthDeathModel() {
    	ratesInput.setRule(Validate.OPTIONAL);
    }
    

    @Override
    public void initAndValidate() {
        frequencies = frequenciesInput.get();
        double[] freqs = getFrequencies();
        nrOfStates = freqs.length;
        rateMatrix = new double[(nrOfStates)][(nrOfStates)];
        
        eigenSystem = new DefaultEigenSystem(nrOfStates);
    }
//	@Override
//	public void getTransitionProbabilities(Node node, double fStartTime, double fEndTime, double fRate, double[] matrix) {
//		double t = (fStartTime - fEndTime) * fRate;
//		//{{e^(-a t), e^(a t), 1}, {1, e^(-b t), e^(b t)}, {1, 1, e^t}}
//		double a = 1.0;//birthRateInput.get().getValue();
//		double b = delParameter.get().getValue();
//		matrix[0] = Math.exp(-a*t);
//		matrix[1] = (1.0 - Math.exp(-a*t)) *  Math.exp(-b*t);
//		matrix[2] = (1.0 - Math.exp(-a*t)) * (1.0 - Math.exp(-b*t));// -  b/(a-b) * Math.exp(-a*t);
//		matrix[3] = 0.0;
//		matrix[4] = Math.exp(-b*t);
//		matrix[5] = 1.0 - Math.exp(-b*t);
//		matrix[6] = 0;
//		matrix[7] = 0;
//		matrix[8] = Math.exp(-t);
//
////		matrix[0] = Math.exp(-a*t);
////		matrix[1] = (1.0 - Math.exp(-a*t)) *  Math.exp(-b*t);
////		matrix[2] = (1.0 - Math.exp(-a*t)) * (1.0 - Math.exp(-b*t));// -  b/(a-b) * Math.exp(-a*t);
////		matrix[3] = 0.0;
////		
////		matrix[4] = 0.0;
////		matrix[5] = Math.exp(-b*t);
////		matrix[6] = 1.0 - Math.exp(-b*t);
////		matrix[7] = 0;
////		
////		matrix[8] = 0;
////		matrix[9] = 0;
////		matrix[10] = Math.exp(-t);
////		matrix[11] = 0;
////
////		matrix[12] = 0;
////		matrix[13] = 0;
////		matrix[14] = 0;
////		matrix[15] = 1.0;
//	
//	}
	
	/*
	Q = S.J.S^(-1)
			where
			Q = (-a | a  | 0
			      0 | -b | b
			      0 | 0  | 0)
			S = (1 | 1 | a/(a-b)
			     1 | 0 | 1
			     1 | 0 | 0)
			J = (0 | 0  | 0
			     0 | -a | 0
			     0 | 0  | -b)
			S^(-1) = (0 | 0        | 1
			          1 | -a/(a-b) | -b/(a-b)
			          0 | 1        | -1)
			          
    P(t) = exp(Qt) = S.exp(Jt).S^(-1) = S.{{1,0,0},{0,exp(-at),0},{0,0,exp(-bt)}}.S^(-1)
    = ( 1 | exp(-at) | exp(-bt) 
        1 | 0        | 0
        1 | exp(-at) | 0         ) . S^(-1)
    = ( exp(-at) |  -a/(a-b) exp(-at) - exp(-bt) | 1 -a/(a-b) exp(-at) -b/(a-b) exp(-bt)  
        0        | 0                             | 1
        exp(-at) | -a/(a-b)exp(-at)              | 1 + a/(a-b) exp(-at) b/(a-b)
    
    
    
    
    = ( 1        | 1 | 1/(a-b)
        exp(-at) | 0 | exp(-at)      . S^(-1)
        exp(-bt) | 0 | 0        )
    =( exp(-bt) | 0 | 0
       1 -a/(a-b) exp(-at) + b/(a-b)exp(-bt) | 1 | 1/(a-b) + exp(-at)
        exp(-at)-exp(-bt) | 0 | exp(-at)
        
			          
			*/

	@Override
	public EigenDecomposition getEigenDecomposition(Node node) {
        synchronized (this) {
            if (updateMatrix) {
            	double [][] rateMatrix = getRateMatrix();

                eigenDecomposition = eigenSystem.decomposeMatrix(rateMatrix);
                updateMatrix = false;
            }
        }
        return eigenDecomposition;
    }
    
	protected double[][] getRateMatrix() {
		if (delParameter.get().getValue() > 1.0) {
			throw new IllegalArgumentException("death rate should not exceed birthrate (1.0)");
		}
		switch (nrOfStates) {
		case 3:
		{
			double [][] rateMatrix = new double[3][3];
			rateMatrix[0][0] = -1;//- birthRateInput.get().getValue();
			rateMatrix[0][1] = 1; //birthRateInput.get().getValue();
			rateMatrix[0][2] = 0;
			rateMatrix[1][0] = 0;
			rateMatrix[1][1] = - delParameter.get().getValue();
			rateMatrix[1][2] = delParameter.get().getValue();
			rateMatrix[2][0] = 0;
			rateMatrix[2][1] = 0;
			rateMatrix[2][2] = 0; 
			return rateMatrix;
		}
		case 4:
		{
			double [][] rateMatrix = new double[4][4];
			rateMatrix[0][0] = -1.0;//- birthRateInput.get().getValue();
			rateMatrix[0][1] = 1.0;//birthRateInput.get().getValue();
			rateMatrix[0][2] = 0;
			rateMatrix[0][3] = 0;
			
			rateMatrix[1][0] = 0;
			rateMatrix[1][1] = - delParameter.get().getValue();
			rateMatrix[1][2] = delParameter.get().getValue();
			rateMatrix[1][3] = 0;
	
			rateMatrix[2][0] = 0;
			rateMatrix[2][1] = 0;
			rateMatrix[2][2] = 0;
			rateMatrix[2][3] = 0;
	
			rateMatrix[3][0] = 0;
			rateMatrix[3][1] = 0;
			rateMatrix[3][2] = 0;
			rateMatrix[3][3] = 0;
			return rateMatrix;
		}
		default :
			calcStateCount();
			return getRateMatrix();
		}
	}


	private void calcStateCount() {
		for (Object o : getOutputs()) {
			if (o instanceof SiteModel.Base) {
				SiteModel.Base s = (SiteModel.Base) o;
				for (Object o2 : s.getOutputs()) {
					if (o2 instanceof TreeLikelihood) {
						TreeLikelihood tl = (TreeLikelihood) o2;
						DataType dataType = tl.dataInput.get().getDataType();
						nrOfStates =- dataType.getStateCount();
					}
				}
			}
		}
	}

	@Override
	public void restore() {
		updateMatrix = true;
		super.restore();
	}
	
    @Override
    protected boolean requiresRecalculation() {
        // we only get here if delParameter or mutationRate is dirty
    	updateMatrix = true;
        return true;
    }
    
    
    @Override
	public boolean canHandleDataType(DataType dataType) {
		return (dataType.getStateCount() == 3 || dataType.getStateCount() == 4);
	}


}
