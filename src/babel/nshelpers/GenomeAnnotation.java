package babel.nshelpers;

import org.json.JSONException;
import org.json.JSONObject;

import beast.core.BEASTObject;
import beast.core.Input;

public class GenomeAnnotation extends BEASTObject {
	
	final public Input<String> nameInput = new Input<>("title", "The name of the annotation", Input.Validate.REQUIRED);
	final public Input<String> strandInput = new Input<>("strand", "The sense of the strand (+ or -)", "+");
	final public Input<String> typeInput = new Input<>("type", "The type of annotation eg. CDS", "CDS");
	final public Input<String> seqIDInput = new Input<>("seqid", "The reference");
	
	final public Input<Integer> startInput = new Input<>("start", "The start position of the annotation", Input.Validate.REQUIRED);
	final public Input<Integer> endInput = new Input<>("end", "The end position of the annotation", Input.Validate.REQUIRED);

	@Override
	public void initAndValidate() {
		
	}
	
	
	/***
	 * 
	 * @return A JSON string of this genome annotation
	 * @throws JSONException
	 */
	public String getJSON() {

		
		JSONObject json = new JSONObject();
		try {
			json.put("strand", strandInput.get());
			json.put("type", typeInput.get());
			json.put("start", startInput.get());
			json.put("end", endInput.get());
			if (seqIDInput.get() != null) json.put("seqid", seqIDInput.get());
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		
		return "\"" + nameInput.get() + "\":" + json.toString();
		
		
	}
	
	
	

}
