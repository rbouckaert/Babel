package babel.spanningtrees;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Jakob Runge This class aims to parse a `locations` block from a
 *         NexusParser. To do this it makes use of the NexusParser class.
 */
public class LocationParser {

	private static final String blockName = "locations";
	private static final Pattern locationMarker = Pattern.compile(" *([^ ]+) = ([^ ]+) (.+);");
	private HashMap<String, Location> locations = new HashMap<>();

	public static LocationParser parseNexus(NexusParser nexus) {
		LocationParser parser = new LocationParser();
		if (nexus.hasBlock(LocationParser.blockName)) {
			for (String line : nexus.getBlock(LocationParser.blockName)) {
				Matcher matcher = LocationParser.locationMarker.matcher(line);
				if (matcher.matches()) {
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

	/**
	 * This method makes it possible to parse locations from a KML file. It is
	 * mostly a copy of the former CognateIO.loadKmlFile.
	 */
	public static LocationParser parseKMLFile(String sFileName) {
		Random rand = new Random(10);

		System.err.println("Loading " + sFileName);
		LocationParser parser = new LocationParser();
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setValidating(false);
			org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(new File(sFileName));
			doc.normalize();

			// grab styles out of the KML file
			HashMap<String, Integer> mapStyleToColor = new HashMap<String, Integer>();
			NodeList oStyles = doc.getElementsByTagName("Style");
			for (int iNode = 0; iNode < oStyles.getLength(); iNode++) {
				Node oStyle = oStyles.item(iNode);
				String sID = oStyle.getAttributes().getNamedItem("id").getTextContent();
				XPath xpath = XPathFactory.newInstance().newXPath();
				String expression = ".//IconStyle/color";
				Node oColor = (Node) xpath.evaluate(expression, oStyles.item(iNode), XPathConstants.NODE);
				if (oColor != null) {
					String sColor = oColor.getTextContent();
					sColor = sColor.substring(2);
					Integer nColor = Integer.parseInt(sColor, 16);

					nColor = 0x0000FF + rand.nextInt(0xFFFF) * 0xFF;
					mapStyleToColor.put(sID, nColor);
				}
			}

			// grab polygon info from placemarks
			NodeList oPlacemarks = doc.getElementsByTagName("Placemark");
			for (int iNode = 0; iNode < oPlacemarks.getLength(); iNode++) {
				String sPlacemarkName = "";
				Vector<Double> nX = new Vector<Double>();
				Vector<Double> nY = new Vector<Double>();
				Node node = oPlacemarks.item(iNode);
				NodeList oChildren = node.getChildNodes();
				long color = 0x808080;
				for (int iChild = 0; iChild < oChildren.getLength(); iChild++) {
					Node oChild = oChildren.item(iChild);
					if (oChild.getNodeType() == Node.ELEMENT_NODE) {
						String sName = oChild.getNodeName();
						if (sName.equals("name")) {
							sPlacemarkName = oChild.getTextContent();
						} else if (sName.equals("Style")) {
							String expression = ".//PolyStyle/color";
							XPath xpath = XPathFactory.newInstance().newXPath();
							Node oColor = (Node) xpath.evaluate(expression, oStyles.item(iNode), XPathConstants.NODE);
							if (oColor != null) {
								String sColor = oColor.getTextContent();
								sColor = sColor.substring(2);
								color = Integer.parseInt(sColor, 16);
							}
						} else if (sName.equals("styleUrl")) {
							String sID = oChild.getTextContent();
							sID = sID.substring(2);
							if (mapStyleToColor.containsKey(sID)) {
								color = mapStyleToColor.get(sID);
							}
						} else if (sName.equals("Polygon") || sName.equals("Point") || sName.equals("LineString")) {
							XPath xpath = XPathFactory.newInstance().newXPath();
							String expression = ".//coordinates";
							Node oCoords = (Node) xpath.evaluate(expression, oChild, XPathConstants.NODE);
							String sCoord = oCoords.getTextContent();
							String[] sCoords = sCoord.split("\\s+");
							for (int i = 0; i < sCoords.length; i++) {
								String sStr = sCoords[i];
								String[] sStrs = sStr.split(",");
								if (sStrs.length > 1) {
									// Point point = new Point();
									try {
										nX.add(Double.parseDouble(sStrs[0]));// *
																				// Parser.MAX_LATITUDE_INT_UNITS
																				// /
																				// 360));
										nY.add(Double.parseDouble(sStrs[1]));/// 180f)
																				/// *
																				/// Parser.MAX_LONGITUDE_INT_UNITS));
									} catch (NumberFormatException e) {
										System.err.println("Problem with " + sPlacemarkName + " " + e.getMessage());
									}
								}
							}
						}
					}
				}
				if (nX.size() > 0 && nY.size() > 0) {
					Location poly = new Location();
					poly.latitude = nY.get(0);
					poly.longitude = nX.get(0);
					color = Math.abs(color);
					long r = color % 256;
					long g = (color / 256) % 256;
					long b = color / (256 * 256) % 256;
					poly.color = new Color((int) r, (int) g, (int) b);
					parser.locations.put(sPlacemarkName, poly);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return parser;
	}

	public boolean hasLocation(String name) {
		return this.locations.containsKey(name);
	}

	public Location getLocation(String name) {
		return this.locations.get(name);
	}

	public Set<String> getLocationNames() {
		return this.locations.keySet();
	}

	public Collection<Location> getLocations() {
		return this.locations.values();
	}

	public static void main(String[] args) {
		String testFile = "./examples/x/2016-09-13_CoBL-IE_Lgs101_Mgs172_Current_Jena200_BEAUti.nex";
		try {
			NexusParser nexus = NexusParser.parseFile(testFile);
			LocationParser locations = LocationParser.parseNexus(nexus);
			System.out.println("Locations parsed. Found locations for:");
			for (String name : locations.getLocationNames()) {
				System.out.println("\t" + name);
			}
		} catch (FileNotFoundException e) {
			System.err.println("LocationParser.main could not read the test file.");
			e.printStackTrace();
		}
	}

}
