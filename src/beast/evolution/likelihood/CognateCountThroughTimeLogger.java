package beast.evolution.likelihood;

import java.io.PrintStream;
import java.util.Arrays;

import beast.core.Description;
import beast.core.Input;
import beast.core.Loggable;
import beast.evolution.alignment.Alignment;
import beast.evolution.datatype.TwoStateCovarion;
import beast.evolution.tree.Node;
import beast.evolution.tree.TreeInterface;


@Description("Logs internal states sampled from the distribution at the MRCA of a set of taxa."
		+ "Only the most likely state and its probability is logged")
public class CognateCountThroughTimeLogger extends TreeLikelihood implements Loggable {
	public Input<Double> maxHeightInput = new Input<>("maxHeight","maximum height of the interval to record");
	public Input<Integer> intervalCountInput = new Input<>("intervalCount","number of intervals to record");
	
	
	double [][] marginals;
	double [] MAPprob;
	
	int intervalCount;
	double maxHeight;
	double intervalSize;
	double [] intervalBoundaries;
			
    @Override
	public void initAndValidate() {
    	intervalCount = intervalCountInput.get();
    	maxHeight = maxHeightInput.get();
    	intervalSize = maxHeight / intervalCount;
    	intervalBoundaries = new double[intervalCount + 1];
    	for (int i = 1; i < intervalCount + 1; i++) {
    		intervalBoundaries[i] = intervalBoundaries[i-1] + intervalSize;
    	}

    	
		// ensure we do not use BEAGLE
        boolean forceJava = Boolean.valueOf(System.getProperty("java.only"));
        System.setProperty("java.only", "true");
		super.initAndValidate();
        System.setProperty("java.only", "" + forceJava);
        
        Alignment data = dataInput.get();        
        
		TreeInterface tree = treeInput.get();
        MAPprob = new double[tree.getNodeCount()];
        for (int i = 0; i < tree.getLeafNodeCount(); i++) {
            for (int j = 0; j < data.getSiteCount(); j++) {
            	if (data.getPattern(data.getPatternIndex(j))[i] == 1) {
            		MAPprob[i] = 1;
            	}
            }
        }
        
        int stateCount = dataInput.get().getMaxStateCount();
        marginals = new double[tree.getNodeCount()][];
        for (int i = tree.getLeafNodeCount(); i < tree.getNodeCount(); i++) {
        	marginals[i] = new double[data.getSiteCount() * stateCount];
        }
	}
	
    @Override
    public void init(PrintStream out) {
    	((Loggable)treeInput.get()).init(out);
    	for (int i = 0; i < intervalCount; i++) {
    		out.append(getID()+"." +i+"\t");
    	}
    }
    
	@Override
	public void log(long nSample, PrintStream out) {
		
		try {
			// force fresh recalculation of likelihood at this stage
			Arrays.fill(m_branchLengths, 0);
			calculateLogP();
			
			// calculate marginals
			TreeInterface tree = treeInput.get();
			calculateMarginals(tree.getRoot());
			
			// calc MAP assignment for each node
			// note this is not the MAP assignment for the set of nodes, but for each individual node only
			Alignment data = dataInput.get();
			int stateCount = data.getMaxStateCount();
			boolean isCovarion = false;
			if (dataInput.get().getDataType() instanceof TwoStateCovarion) {
				stateCount = 4;
				isCovarion = true;
			}
			for (int i = tree.getLeafNodeCount(); i < tree.getNodeCount(); i++) {
				double maxProb = 0;
				double [] marginal = marginals[i];
				for (int k = 0; k < data.getSiteCount(); k++) {
					int j = data.getPatternIndex(k);
					double p;
					if (isCovarion) {
						p = marginal[j*stateCount + 1] + marginal[j*stateCount + 3];
					} else {
						p = marginal[j*stateCount + stateCount - 1];
					}
					maxProb += p;
				}
				MAPprob[i] = maxProb;
			}
			
			
			double [] cognateCount = new double[intervalCount];
			
			// assign metadata to nodes
			Node [] node = tree.getNodesAsArray();
			for (int i = 0; i < tree.getNodeCount() - 1; i++) {
				double h = node[i].getHeight();
				double hp = node[i].getParent().getHeight();
				double p = MAPprob[i];
				int k = (int)(intervalCount * h/maxHeight);
				int end = (int)(intervalCount * hp/maxHeight);
				while (k < end) {
					cognateCount[k] += p * (intervalBoundaries[k] - h) / intervalSize;
					h = intervalBoundaries[k];
					k++;
				}
				cognateCount[k] += p * (hp - h) / intervalSize;
			}

	    	for (int i = 0; i < intervalCount; i++) {
	    		out.append(cognateCount[i] + "\t");
	    	}

	        
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/** traverse to the root
	 * then, sample root values, and propagate back to the MRCA
	 * along the path that goes between root and MRCA
	 * @return sample
	 */
	private void calculateMarginals(Node node) {
        int patternCount = dataInput.get().getPatternCount();
        int stateCount = dataInput.get().getMaxStateCount();
		double [] m = marginals[node.getNr()];

		if (node.isRoot()) {
			if (beagle != null) {
				throw new RuntimeException("BEAGLE is not supported yet");
				// m_fRootPartials = beagle.m_fRootPartials;
			}
			
            final double[] frequencies = substitutionModel.getFrequencies();
            for (int i = 0; i < patternCount; i++) {
            	for (int j = 0; j < stateCount; j++) {
            		m[i*stateCount + j] = frequencies[j] * m_fRootPartials[i*stateCount + j];
            	}
            	// normalise
            	double sum = 0;
            	for (int j = 0; j < stateCount; j++) {
            		sum += m[i*stateCount + j];
            	}
            	for (int j = 0; j < stateCount; j++) {
            		m[i*stateCount + j] /= sum;
            	}
            }

		} else {
			
			double [] partials = new double[dataInput.get().getPatternCount() * stateCount * m_siteModel.getCategoryCount()];
			
			if (m_siteModel.getCategoryCount() != 1) {
				throw new RuntimeException("Gamma rate heterogeneity or proportion invariant is not supported yet");
			}
			if (beagle != null) {
				throw new RuntimeException("BEAGLE is not supported yet");
				// beagle.beagle.getPartials(arg0, arg1, arg2);
        		// getTransitionMatrix(nodeNum, probabilities);
			} else {
				likelihoodCore.getNodeMatrix(node.getNr(), 0, probabilities);
			}

			if (!node.isLeaf()) { 
				likelihoodCore.getNodePartials(node.getNr(), partials);

				Arrays.fill(m, 0);
				double [] parm = marginals[node.getParent().getNr()];
				// sample using transition matrix and parent states
	            for (int j = 0; j < patternCount; j++) {
	                int childIndex = dataInput.get().getPatternIndex(j) * stateCount;
	
	                for (int i = 0; i < stateCount; i++) {
	                	// iterate over parent values
	                	double u = 0;
		            	for (int k = 0; k < stateCount; k++) {
			                int parentIndex = k * stateCount;
			                u += probabilities[parentIndex + i] * parm[j * stateCount + k];
		            	}
		            	m[j * stateCount + i] = partials[childIndex + i] * u;
		            	// normalise
		            	double sum = 0;
		            	for (int k = 0; k < stateCount; k++) {
		            		sum += m[j*stateCount + k];
		            	}
		            	for (int k = 0; k < stateCount; k++) {
		            		m[j*stateCount + k] /= sum;
		            	}

	                }

	            }
            }
		}
		for (Node child : node.getChildren()) {
			calculateMarginals(child);
		}
	}

	
}
