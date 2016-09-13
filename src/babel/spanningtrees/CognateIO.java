package babel.spanningtrees;

import java.awt.Color;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class CognateIO {
	//final static public String KML_FILE = "locations.kml";
	final static public String KML_FILE = "/home/remco/data/beast/aboriginal/pny10/pny.kml";
	final static public String BG_FILE = "bg(-41.47,110.72)x(-6.94,156.57).png";
	final static public String NEXUSFILE = "filtered.nex";
	//final static public String DATAFILE = "cognates7recoded.dat";
	//public static final int NTAX = 194;
	//public static final int NGLOSSIDS = 205;
	final static public String DATAFILE = "/home/remco/data/beast/aboriginal/pny10/pny10.tab";
	public static final int NTAX = 299;
	public static int NGLOSSIDS = 184;
	static Map<String,String> l2l = new HashMap<String, String>();
	
	// if FILL_IN_MISSING_DATA = true, mark those less than THRESHOLD distance as missing, 
	// and keep the remainder at zero
	final static boolean FILL_IN_MISSING_DATA = true;

	// if there are equal or less than MINIMUM_COGNATE_SIZE
	// in a cognate, do not output cognate to NEXUSFILE
	final static int MINIMUM_COGNATE_SIZE = 1;
	
	//final static double MISSING_DATA_THRESHOLD = CognateData.THRESHOLD;
	final static double MISSING_DATA_THRESHOLD = 1000;

	
	static double COGNATE_SPLIT_THRESHOLD = 750.0;
	
	
	public static List<Entry> readCognates(File file) throws Exception {
		List<Entry> entries = new ArrayList<Entry>();
		BufferedReader fin = new BufferedReader(new FileReader(file));
		String sStr = null;
		// eat up header
		sStr = fin.readLine();
		while (fin.ready()) {
			sStr = fin.readLine();
			String [] strs = sStr.split("\t");
			if (strs.length != 1) {
			Entry entry = new Entry();
				entry.GlossID = Integer.parseInt(strs[0]);
				entry.Gloss = strs[1];
				entry.Subgroup = strs[2];
				entry.Language = strs[3];
				l2l.put(entry.Language.replaceAll("[-_ '`’\\\\]", ""), entry.Language);
				entry.Language = entry.Language.replaceAll("[-_ '`’\\\\]", "");
				entry.Word = strs[4];
				entry.MultistateCode = Integer.parseInt(strs[5]);
				entries.add(entry);
			}
		}
		fin.close();
		return entries;
	}
	
	public static void writeCognates(File file, List<Entry> entries) throws Exception {
        PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)));
        out.println("GlossID	Gloss	Subgroup	Language	Word	MultistateCode");
        for (Entry entry : entries) {
        	out.println(entry.GlossID+ "\t" +entry.Gloss+ "\t" +entry.Subgroup+ "\t" +entry.Language+ "\t" +entry.Word+ "\t" +entry.MultistateCode);
        }
        out.close();
	}
	
	
	public static void writeCognatesToNexus(File file, Map<Integer, Map<Integer,Cognate>> cognateGlossMap, Map<String,Location> locations) throws Exception {
		List<String> languages = new ArrayList<String>();
		Map<Integer,Cognate> cognateMap = cognateGlossMap.get(1);
    	for (Cognate cognate : cognateMap.values()) {
    		for (String language : cognate.languages) {
    			languages.add(language);
    		}
    	}		
		
        Map<String,Integer> mapLanguageToSequence = new HashMap<String, Integer>();
        for (int i = 0; i < languages.size(); i++) {
        	mapLanguageToSequence.put(languages.get(i), i);
        }
        ArrayList<ArrayList<Integer>> sequences = new ArrayList<>(languages.size());
        for (int i = 0; i < languages.size(); i++) {
        	sequences.set(i, new ArrayList<>());
        }
        
        int [] count = new int [250];
        for (Map<Integer,Cognate> cognatemap : cognateGlossMap.values()) {
        	Set<String> missing = new HashSet<String>();
        	for (Cognate cognate : cognatemap.values()) {
        		if (cognate.MultistateCode == 0) {
        			missing.addAll(cognate.languages);
        			break;
        		}
        	}
        	for (Cognate cognate : cognatemap.values()) {

        		if (cognate.MultistateCode > 0 && cognate.languages.size() > MINIMUM_COGNATE_SIZE) {
            		List<Location> cognateLocations = new ArrayList<Location>();
            		for (String language : cognate.languages) {
            			cognateLocations.add(locations.get(language));
            		}

            		count[cognate.languages.size()]++;
            		if (cognate.languages.size() > 50) {
            			System.err.println(cognate.languages.size() + " " + cognate.GlossID + " " + cognate.MultistateCode);
            		}
            		
        			int [] code = new int[sequences.size()];
        			// mark those less than THRESHOLD distance as missing, 
        			// and keep the remainder at zero
        			if (FILL_IN_MISSING_DATA) {
	        			for (String language : missing) {
	        				Location loc1 = locations.get(language);
	        				if (loc1 != null) {
		        				double minDist = Double.MAX_VALUE;
		        				for (Location loc2 : cognateLocations) {
		        					double dist = CognateData.distance(loc1, loc2);
		        					minDist = Math.min(minDist, dist);
		        				}
		        				if (minDist < MISSING_DATA_THRESHOLD) {
		        					code[mapLanguageToSequence.get(language)] = -1;
		        				}
	        				}
	        			}
        			} else {
	        			for (String language : missing) {
        					code[mapLanguageToSequence.get(language)] = -1;
	        			}
        			}
        			for (String language : cognate.languages) {
        				code[mapLanguageToSequence.get(language)] = 1;
        			}
        	        for (int i = 0; i < languages.size(); i++) {
        	        	sequences.get(i).add(code[i]);
        	        }
        		}
        	}
        }
        
        for (int i = 0; i < 100; i++) {
        	System.err.println(i + " " + count[i]);
        }

        PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)));
        out.println("#NEXUS");
        out.println("Begin data;");
        out.println("Dimensions ntax=" + languages.size() +" nchar=" + sequences.get(0).size() +";");
        out.println("Format datatype=binary symbols=\"01\" gap=-;");
        out.println("Matrix");
        for (int i = 0; i < languages.size(); i++) {
        	out.print("\"" + l2l.get(languages.get(i)).replace(" ", "") + "\" ");
        	for (int j = 0; j < sequences.get(0).size(); j++) {
        		switch (sequences.get(i).get(j)) {
        		case -1 :
        			out.print('-');
        			break;
        		case -2 :
        			out.print('2');
        			break;
        		case 0 :
        			out.print('0');
        			break;
        		case 1 :
        			out.print('1');
        			break;
        		default:
        			out.print(sequences.get(i).get(j));
        			break;
        		}
			}
        	out.println();
        }
        out.println("End;");
        out.close();
	}

	
	/** grabs placemarks out of kml files **/
	static public Map<String,Location> loadKMLFile(String sFileName) {
		Random rand = new Random(10);
		
		System.err.println("Loading " + sFileName);
		Map<String,Location> map = new HashMap<String, Location>();
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setValidating(false);
			org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(new File(sFileName));
			doc.normalize();
			
			// grab styles out of the KML file
			HashMap<String,Integer> mapStyleToColor = new HashMap<String, Integer>();
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
						//} else if (sName.equals("description")) {
							//sDescription = oChild.getTextContent();
						} else if (sName.equals("Polygon") || sName.equals("Point") || sName.equals("LineString")) {
							XPath xpath = XPathFactory.newInstance().newXPath();
							String expression = ".//coordinates";
							Node oCoords = (Node) xpath.evaluate(expression, oChild, XPathConstants.NODE);
							String sCoord = oCoords.getTextContent();
							String [] sCoords = sCoord.split("\\s+");
							for (int i = 0; i < sCoords.length; i++) {
								String sStr = sCoords[i];
								String [] sStrs = sStr.split(",");
								if (sStrs.length > 1) {
									//Point point = new Point();
									try {
									nX.add(Double.parseDouble(sStrs[0]));// * Parser.MAX_LATITUDE_INT_UNITS / 360)); 
									nY.add(Double.parseDouble(sStrs[1]));///180f) * Parser.MAX_LONGITUDE_INT_UNITS));
									} catch (NumberFormatException e) {
										System.err.println("Problem with " + sPlacemarkName + " " + e.getMessage());
									}
								}
							}
						}
					}
				}
				if (nX.size() > 0) {
					Location poly = new Location();
					poly.latitude = nY.get(0);
					poly.longitude = nX.get(0);
					color = Math.abs(color);
					long r = color%256;
					long g = (color/256) % 256;
					long b = color/(256*256)% 256;
					poly.color = new Color((int)r, (int)g, (int)b);
					map.put(sPlacemarkName, poly);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return map;
	} // loadKMLFile

	
	public static void main(String[] args) throws Exception {
		List<Entry> entries = CognateIO.readCognates(new File(CognateIO.DATAFILE));

		int missingCount = 0;
		for (Entry entry : entries) {
			if (entry.MultistateCode == 0) {
				missingCount ++;
			}
		}
		System.err.println(entries.size() + " " + missingCount);
		//TCognateIO.writeCognates(new File("x.dat"), entries);
	}

}
