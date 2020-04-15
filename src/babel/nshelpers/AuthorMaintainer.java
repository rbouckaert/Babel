package babel.nshelpers;

import org.json.JSONException;
import org.json.JSONObject;

import beast.core.BEASTObject;
import beast.core.Input;

public class AuthorMaintainer  extends BEASTObject {

	
	final public Input<String> nameInput = new Input<>("person", "The name of the person", Input.Validate.REQUIRED);
	final public Input<String> websiteInput = new Input<>("website", "Their website/email", Input.Validate.REQUIRED);
	
	
	@Override
	public void initAndValidate() {
		
	}
	
	
	
	/***
	 * 
	 * @return A JSON string of this maintainer
	 * @throws JSONException
	 */
	public String getJSON() {

		
		JSONObject json = new JSONObject();
		try {
			json.put("name", nameInput.get());
			json.put("website", websiteInput.get());
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		return json.toString();
		
		
	}
	

}
