package beast.evolution.datatype;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.evolution.datatype.DataType;

/**
 * Package: MutationDeathType
 * Description:
 * Time: 1:09:40 PM
 */
@Description("Data type for mutation death models including Multi-State Stochastic Dollo")
public class MutationDeathType extends DataType.Base {
	public Input<String> deathCharInput = new Input<String>("deathChar","character representing death state (default 0)","0"); 
	public Input<DataType.Base> dataTypeInput = new Input<DataType.Base>("dataType","base datatype, extended by death char");
	public Input<String> extantCodeInput = new Input<String>("extantCode","character representing live state if no existing datatype is extended",Validate.XOR, dataTypeInput); 
	
    protected static String DESCRIPTION = "MutationDeathType";
    
    public static int DEATHSTATE = 0;

    
    @Override
    public void initAndValidate() {
    	char deathCode = deathCharInput.get().charAt(0);
    	if (extantCodeInput.get() != null) {
    		char extantCode = extantCodeInput.get().charAt(0);
    		
    		int [][] x = {
    				{0},  // 0
    				{1},  // 1
    				{0,1}, // -
    				{0,1}, // ?
    				};
    		stateCount = 2;
    		mapCodeToStateSet = x;
    		codeLength = 1;
    		codeMap = "" + extantCode + deathCode + GAP_CHAR + MISSING_CHAR;
    		DEATHSTATE = 1;
    	} else {
    		DataType.Base dataType = dataTypeInput.get();
    		stateCount = dataType.getStateCount() + 1;
    		mapCodeToStateSet = new int[dataType.mapCodeToStateSet.length + 1][];
    		System.arraycopy(dataType.mapCodeToStateSet, 0, mapCodeToStateSet, 0, dataType.mapCodeToStateSet.length);
    		mapCodeToStateSet[stateCount - 1] = new int[] {deathCode};
    		codeLength = 1;
    		codeMap = "" + dataType.codeMap + deathCode;
    		DEATHSTATE = stateCount - 1;
    	}
    }


	@Override
	public String getTypeDescription() {
		return "MutationDeathType";
	}

}
