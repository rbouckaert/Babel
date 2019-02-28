package beast.evolution.substitutionmodel;

import beast.core.Citation;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.parameter.RealParameter;
import beast.evolution.datatype.Binary;
import beast.evolution.datatype.DataType;
import beast.evolution.datatype.StandardData;
import beast.evolution.tree.Node;

import java.util.Arrays;

/**
 * @author Luke Maurits
 */
@Description("Ordinal subtitution model with equal relative rates and a special \"out of system\" state.")
public class NestedOrdinal extends NStatesNoRatesSubstitutionModel {

    @Override
    protected void setupRelativeRates() {
	int i;
	for(i=0; i<nrOfStates * (nrOfStates -1); i++) {
		relativeRates[i] = 0;
	}
	// Top row - "getting out of jail"
	// All "within system" states equally likely
	for(i=0; i<nrOfStates-1; i++) {
		relativeRates[i] = 1.0;
	}
	// Leftmost colum - "getting into jail"
	for(i=1; i<nrOfStates; i++) {
		relativeRates[i*(nrOfStates-1)] = 1.0;
	}
	// Low edge case
	relativeRates[nrOfStates] = 1.0;
	// Standard cases
	for(i=2; i<nrOfStates-1; i++) {
		relativeRates[nrOfStates*i-1] = 1.0;
		relativeRates[nrOfStates*i] = 1.0;
	}
	// High edge case
	relativeRates[nrOfStates*(nrOfStates-1)-1] = 1.0;
    }

    @Override
    protected void setupRateMatrix() {
        double[] freqs = frequencies.getFreqs().clone();

	// Populate rateMatrix from relativeRate
        for (int i = 0; i < nrOfStates; i++) {
            rateMatrix[i][i] = 0;
            for (int j = 0; j < i; j++) {
                rateMatrix[i][j] = relativeRates[i * (nrOfStates - 1) + j];
            }
            for (int j = i + 1; j < nrOfStates; j++) {
                rateMatrix[i][j] = relativeRates[i * (nrOfStates - 1) + j - 1];
            }
        }
        // bring in frequencies
        for (int i = 0; i < nrOfStates; i++) {
            for (int j = i + 1; j < nrOfStates; j++) {
                rateMatrix[i][j] *= freqs[j];
                rateMatrix[j][i] *= freqs[i];
            }
        }
        // set up diagonal
        for (int i = 0; i < nrOfStates; i++) {
            double sum = 0.0;
            for (int j = 0; j < nrOfStates; j++) {
                if (i != j)
                    sum += rateMatrix[i][j];
            }
            rateMatrix[i][i] = -sum;
        }
        double subst = 0.0;
        for (int i = 0; i < nrOfStates; i++)
            subst += -rateMatrix[i][i] * freqs[i];

        for (int i = 0; i < nrOfStates; i++) {
            for (int j = 0; j < nrOfStates; j++) {
                rateMatrix[i][j] = rateMatrix[i][j] / subst;
            }
        }
    } // setupRateMatrix
}
