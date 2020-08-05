package babel.evolution.substitutionmodel;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.evolution.substitutionmodel.GeneralSubstitutionModel;

/**
 * @author Luke Maurits
 */
@Description("A simple subclass of GeneralSubstitutionModel which does not require a rates input and does require a number of states input.")
public class NStatesNoRatesSubstitutionModel extends GeneralSubstitutionModel {

    // Number of states input is required
    public Input<Integer> nrOfStatesInput = new Input<Integer>("stateNumber", "the number of character states", Validate.REQUIRED);

    public NStatesNoRatesSubstitutionModel() {
    	// Rates input is *not* required
        ratesInput.setRule(Validate.OPTIONAL);
    }

    @Override
    // Minimally changed from parent implementation:
    // Derive nrOfStates from the appropriate input, not freqs.
    // Ensure frequencies has correct length.
    // Do not check dimension of rates parameter.
    public void initAndValidate() {
        nrOfStates = nrOfStatesInput.get();
        frequencies = frequenciesInput.get();
        if(frequencies.getFreqs().length != nrOfStates) {
            throw new RuntimeException("number of stationary frequencies does not match number of states.");
        }
	updateMatrix = true;
	try {
		eigenSystem = createEigenSystem();
	} catch (RuntimeException e) {
            throw e;
	} catch (Exception e) {
	    throw new RuntimeException(e.getMessage());
        }
	rateMatrix = new double[nrOfStates][nrOfStates];
	relativeRates = new double[nrOfStates * (nrOfStates - 1)];
	storedRelativeRates = new double[nrOfStates * (nrOfStates - 1)];
    }
}
