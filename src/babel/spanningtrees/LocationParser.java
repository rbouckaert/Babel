package babel.spanningtrees;

import java.awt.Color;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Jakob Runge
 * This class aims to parse a `locations` block from a NexusParser.
 * To do this it makes use of the NexusParser class.
 * */
public class LocationParser {

	private static final String blockName = "locations";
	private static final Pattern locationMarker = Pattern.compile(" *([^ ]+) = ([^ ]+) (.+);");
	private HashMap<String, Location> locations = new HashMap<>();
	
	public static LocationParser parseNexus(NexusParser nexus){
		LocationParser parser = new LocationParser();
		if(nexus.getBlockNames().contains(LocationParser.blockName)){
			for(String line : nexus.getBlock(LocationParser.blockName)){
				Matcher matcher = LocationParser.locationMarker.matcher(line);
				if(matcher.matches()){
					String name = matcher.group(1);
					Location location = new Location();
					location.latitude = Double.parseDouble(matcher.group(2));
					location.longitude = Double.parseDouble(matcher.group(3));
					location.color = new Color(0, 0, 255);
					parser.locations.put(name, location);
				}
			}
		}
		return parser;
	}
	
	public Location getLocation(String name){
		return this.locations.get(name);
	}
	
	public Set<String> getLocationNames(){
		return this.locations.keySet();
	}
	
	public static void main(String[] args) {
		String testFile = "./examples/x/2016-09-12_CoBL-IE_Lgs101_Mgs172_Current_Jena200_BEAUti.nex";
		try {
			NexusParser nexus = NexusParser.parseFile(testFile);
			LocationParser locations = LocationParser.parseNexus(nexus);
			System.out.println("Locations parsed. Found locations for:");
			for(String name : locations.getLocationNames()){
				System.out.println("\t"+name);
			}
		} catch (FileNotFoundException e) {
			System.err.println("LocationParser.main could not read the test file.");
			e.printStackTrace();
		}
	}

}
