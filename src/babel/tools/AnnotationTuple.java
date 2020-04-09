package babel.tools;


import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.Input.Validate;

public class AnnotationTuple extends BEASTObject {
	
	
	final public Input<Integer> indexInput = new Input<>("index", "0-counting position within the sequence name (where the name is split using delimiter)", Validate.REQUIRED);
	final public Input<String> delimiterInput = new Input<>("split", "Character to split the sequence names with", Validate.REQUIRED);
	final public Input<String> nameInput = new Input<>("label", "Name of the annotation to appear in the tree", Validate.REQUIRED);
	
	
	
	int index;
	String delimiter;
	String name;
	
	
	public AnnotationTuple() {
		
	}

	@Override
	public void initAndValidate() {
		this.index = indexInput.get();
		this.delimiter = delimiterInput.get();
		this.name = nameInput.get();
	}

	
	
	/***
	 * Splits the sequence name using 'delimiter' and inserts the 'index'th position into a JSON
	 * @param sequenceName
	 * @return
	 */
	public String toJSON(String sequenceName) {
		
		if (sequenceName == null || sequenceName == "") return "";
		
		
		
		
		String[] vals = sequenceName.split("[" + delimiter + "]");
		if (index >= vals.length) {
			throw new ArrayIndexOutOfBoundsException(sequenceName + " does not have an " + index + "th split using delimiter " + delimiter + "!");
		}
		String val = vals[index];
		
		return name + ":'" + val + "'";
		
		
	}
	
	

	
	
	
}
