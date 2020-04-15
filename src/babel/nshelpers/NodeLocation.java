package babel.nshelpers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.Normalizer;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.util.Log;
import beast.evolution.tree.Node;

public class NodeLocation extends BEASTObject {

	
	final public Input<String> placeInput = new Input<>("place", "The name of the location separated by | delimiters eg. Queenstown|New Zealand|Oceania", Input.Validate.REQUIRED);
	final public Input<String> patternInput = new Input<>("pattern", "order of looking up locations separated by a | delimiter, "
			+ "eg. City|Country|Continent refers. Important: search from small to large ie. do not use Country|City.", Input.Validate.REQUIRED);
	final public Input<String> latitudeInput = new Input<>("latitude", "The latitude of this place or ? if unknown", Input.Validate.REQUIRED);
	final public Input<String> longitudeInput = new Input<>("longitude", "The longitude of this place or ? if unknown", Input.Validate.REQUIRED);
	
	
	boolean hasCoords;
	String[] placeBits, patternBits;
	double latitude, longitude;
	String query;
	
	String demeCat, demeName;
	
	
	public NodeLocation() {
		
	}
	
	
	/**
	 * If parsing from a pattern and a node
	 * @param pattern - a query building pattern eg. City|Country|Continent
	 * @param node - a tree node. must be annotated with the pattern elements eg. City, Country, and Continent
	 */
	public NodeLocation(String pattern, Node node) {
		
		// Validate patterns
		this.patternBits = pattern.split("[|]");
		this.placeBits = new String[patternBits.length];
		this.demeCat = patternBits[0];
		this.latitude = 0;
		this.longitude = 0;
		this.setQuery(node);
		hasCoords = false;
		
	}
	

	
	
	@Override
	public void initAndValidate() {
		
		
		// Validate patterns
		this.placeBits = placeInput.get().split("[|]");
		this.patternBits = patternInput.get().split("[|]");
		
		if (placeBits.length != patternBits.length || placeBits.length == 0) {
			throw new IllegalArgumentException("Please ensure that 'place' and 'pattern' follow the same format, eg. place='Queenstown|New Zealand|Oceania'"
					+ "and pattern='City|Country|Continent' (using | delimiters).");
		}
		
		// Tidy the location names 
		for (int i = 0; i < this.placeBits.length; i ++) {
			this.placeBits[i] = tidyLocationName(this.placeBits[i]);
		}
		
		
		this.demeCat = patternBits[0];
		this.demeName = placeBits[0];
		
		
		if (latitudeInput.get().equals("?") || longitudeInput.get().equals("?")) hasCoords = false;
		else hasCoords = true;
		
		this.latitude = latitudeInput.get().equals("?") ? 0 : Double.parseDouble(latitudeInput.get());
		this.longitude = longitudeInput.get().equals("?") ? 0 : Double.parseDouble(longitudeInput.get());
		
		
		// Initialise the query
		this.query = "";
		for (int i = 0; i < this.patternBits.length; i ++) {
			String place = this.placeBits[i];
			this.query += place + " ";
		}
		
		// Replace all spaces etc. with _
		this.query = this.query.trim();
		this.query = this.query.replaceAll("( |[.]|-)", "_");
		
		
		
		
		
	}
	
	
	/**
	 * The search query. Assumes that the location has already been specified
	 * @return query
	 */
	public String getQuery() {
		return this.query;
	}
	
	
	/**
	 * Calculates the GeoCode query of this node, conditional on 'pattern'
	 * Eg. if the pattern is City|Country
	 * It may return Queenstown_New_Zealand
	 * @param node
	 */
	public void setQuery(Node node) {
		
		// Build query string
		this.query = "";
		for (int i = 0; i < this.patternBits.length; i ++) {
			
			
			
			String pattern = this.patternBits[i];
			
			Object place_obj = node.getMetaData(pattern);
			//System.out.println("Searching for " + pattern + " found " + place_obj) ;
			if (place_obj == null) continue;
			
			// Tidy the string
			String place = tidyLocationName(place_obj.toString());
			
			this.query += place + " ";
			this.placeBits[i] = place;
			
			
		}
		
		// Replace all spaces etc. with _
		this.demeName = placeBits[0];
		this.query = this.query.trim();
		this.query = this.query.replaceAll("( |[.]|-)", "_");
		

	}
	
	
	/**
	 * Searches for the latitude and longitude using meta data of the node using the query
	 * Involves searching using the https://geocode.xyz/ API and may take some time
	 * https://geocode.xyz/ places restrictions on number of uses 
	 * 
	 * Avoid using this function if you already know the coords
	 * 
	 */
	public void search() {
		
		if (this.hasCoords) return;
		
		this.latitude = 0;
		this.longitude = 0;

		URL coords = null;
    	try {
    		
    		
			 coords = new URL("https://geocode.xyz/" + this.query + "?json=1");
			
			 Scanner sc = new Scanner(coords.openStream());
		    
		     StringBuffer sb = new StringBuffer();
		     while(sc.hasNext()) {
		        sb.append(sc.next());
		       
		     }
		     JSONObject json = new JSONObject(sb.toString());
		     this.latitude = Double.parseDouble(json.get("latt").toString());
		     this.longitude = Double.parseDouble(json.get("longt").toString());
		     
		     Log.warning("Estimating the latitude and longitude of " + this.query + " as (" + this.latitude + "," + this.longitude + "). "
		     		+ "Please verify if this is correct.");
		     
		     this.hasCoords = true;
		     
		     // Sleep in case there's a subsequent query. Want to avoid overloading the website.
		     Thread.sleep(300);

		     
	
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			Log.warning("Failed to find " + coords.toString());
			//e.printStackTrace();
		} catch (JSONException e) {
			Log.warning("Failed to extract the json latitude/longitude from " + coords.toString());
			//e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		 

		
	}


	/**
	 * Returns the latitude. Assumes that the latitude has already been set 
	 * (either through xml input or through setCoords(node) )
	 * @return latitude
	 */
	public double getLat() {
		if (!this.hasCoords) search();
		return this.latitude;
	}
	
	
	/**
	 * Returns the longitude. Assumes that the latitude has already been set 
	 * (either through xml input or through setCoords(node) )
	 * @return longitude
	 */
	public double getLong() {
		if (!this.hasCoords) search();
		return this.longitude;
	}
	
	
	/**
	 * @return whether or not the latitude/longitude of this object has been successfully computed 
	 */
	public boolean hasCoords() {
		return this.hasCoords;
	}


	public String getXML() {
		if (!this.hasCoords) search();
		String xml = "";
		xml += "spec=\"babel.nshelpers.NodeLocation\" "; 
		xml += "place=\"" + String.join("|", this.placeBits) + "\" ";
		xml += "pattern=\"" + String.join("|", this.patternBits) + "\" ";
		xml += "latitude=\"" + (this.hasCoords ? this.latitude : "?") + "\" ";
		xml += "longitude=\"" + (this.hasCoords ? this.longitude : "?") + "\" ";
		
		return xml;
	}
	
	
	/***
	 * Replaces all non ASCII characters with something that looks similar
	 * @param str
	 * @return
	 */
	private String tidyLocationName(String str) {
		String resultString = Normalizer.normalize(str, Normalizer.Form.NFD);
		resultString = resultString.replaceAll("[^\\x00-\\x7F]", "");
		return resultString;
	}
	
	
	
	public String getDemeCat() {
		return this.demeCat;
	}
	
	public String getDemeName() {
		return this.demeName;
	}
	
	
	
	
	
	
	
	
}
