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
public class AncestralStateLogger2 extends TreeLikelihood implements Loggable {
	public Input<String> valueInput = new Input<>("value", "space delimited set of labels, one for each site in the alignment. Used as site label in the tree metadata.");
	
	double [][] marginals;
	String [] MAPsite;
	double [] MAPprob;
	String [] siteLabels;
	
    @Override
	public void initAndValidate() {
		// ensure we do not use BEAGLE
        boolean forceJava = Boolean.valueOf(System.getProperty("java.only"));
        System.setProperty("java.only", "true");
		super.initAndValidate();
        System.setProperty("java.only", "" + forceJava);
        
        siteLabels = valueInput.get().trim().split("\\s+");
        
        int totalWeight = 0;
        Alignment data = dataInput.get();
        for (int weight : data.getWeights()) {
            totalWeight += weight;
        }
        if (siteLabels.length != data.getSiteCount()) {
        	siteLabels = new String[data.getSiteCount()];
        	for (int i = 0; i < siteLabels.length; i++) {
        		siteLabels[i] = "s" + i;
        	}
        }
        
        
		TreeInterface tree = treeInput.get();
        MAPsite = new String[tree.getNodeCount()];
        MAPprob = new double[tree.getNodeCount()];
        for (int i = 0; i < tree.getLeafNodeCount(); i++) {
        	MAPsite[i] = "?";
        }
        for (int i = 0; i < tree.getLeafNodeCount(); i++) {
            for (int j = 0; j < data.getSiteCount(); j++) {
            	if (data.getPattern(data.getPatternIndex(j))[i] == 1) {
                	MAPsite[i] = siteLabels[j];
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
    }
    
	@Override
	public void log(int nSample, PrintStream out) {
		
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
				int iMax = -1;
				double [] marginal = marginals[i];
				for (int k = 0; k < data.getSiteCount(); k++) {
					int j = data.getPatternIndex(k);
					double p;
					if (isCovarion) {
						p = marginal[j*stateCount + 1] + marginal[j*stateCount + 3];
					} else {
						p = marginal[j*stateCount + stateCount - 1];
					}
					if (p > maxProb) {
						if (data.getWeights()[j] > 0) { // ignore ascertained columns
							maxProb = p;
							iMax = j;
						}
					}
				}
				MAPprob[i] = maxProb;
				MAPsite[i] = siteLabels[iMax];
			}
			
			// assign metadata to nodes
			Node [] node = tree.getNodesAsArray();
			for (int i = 0; i < tree.getNodeCount(); i++) {
				node[i].metaDataString = "site='"+ MAPsite[i]+"'," +
						"prob=" + MAPprob[i];
			}			
			
            // generate output
	        out.print("tree STATE_" + nSample + " = ");
	    	String newick = tree.getRoot().toSortedNewick(new int[1], true);
	        out.print(newick);
	        out.print(";");

			
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
