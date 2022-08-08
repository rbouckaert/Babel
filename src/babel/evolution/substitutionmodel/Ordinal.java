package babel.evolution.substitutionmodel;

import beast.base.core.Citation;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Input.Validate;
import beast.base.evolution.datatype.Binary;
import beast.base.evolution.datatype.DataType;
import beast.base.evolution.datatype.StandardData;
import beast.base.evolution.tree.Node;

import java.util.Arrays;

/**
 * @author Luke Maurits
 */
@Description("Ordinal subtitution model with equal relative rates.")
public class Ordinal extends NStatesNoRatesSubstitutionModel {

    @Override
    public void setupRelativeRates() {
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
