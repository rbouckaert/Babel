package beast.evolution.substitutionmodel;

import beast.core.Citation;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.evolution.datatype.Binary;
import beast.evolution.datatype.DataType;
import beast.evolution.datatype.StandardData;
import beast.evolution.tree.Node;

import java.util.Arrays;

/**
 * @author Luke Maurits
 */
@Description("Ordinal subtitution model with equal relative rates.")
public class Ordinal extends NStatesNoRatesSubstitutionModel {

    @Override
    protected void setupRelativeRates() {
	int i;
	for(i=0; i<nrOfStates * (nrOfStates -1); i++) {
		relativeRates[i] = 0;
	}
	// Low edge case
	relativeRates[0] = 1.0;
	// Standard cases
	for(i=1; i<nrOfStates-1; i++) {
		relativeRates[nrOfStates*i-1] = 1.0;
		relativeRates[nrOfStates*i] = 1.0;
	}
	// High edge case
	relativeRates[nrOfStates*(nrOfStates-1)-1] = 1.0;
    }

}
