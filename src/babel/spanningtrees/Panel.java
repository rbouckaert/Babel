package babel.spanningtrees;


import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;

public class Panel extends JPanel implements KeyListener {
	private static final long serialVersionUID = 1L;

	int mode = DRAW_GLOSS;
	final static int DRAW_ALL_GLOSS = 0;
	final static int DRAW_GLOSS = 1;
	
	BufferedImage m_bgImage;
	double [] m_fBGImageBox = new double[4];
	/** extreme values for position information **/
	public float m_fMaxLong, m_fMaxLat, m_fMinLong, m_fMinLat;
	
	LocationParser locations;
	CognateData data;
	
	int [][] edgecount;
	List<String> languages;
	
	int meaningClassID = 1;
	
	public Panel(String [] args) {
		parseArgs(args);
		addKeyListener(this);
	}
	
	private void parseArgs(String[] args) {
		CognateIO.COGNATE_SPLIT_THRESHOLD = 3000;
		CognateIO.COGNATE_SPLIT_THRESHOLD = 6000;
		CognateIO.COGNATE_SPLIT_THRESHOLD = 7000;
		CognateIO.NGLOSSIDS = 207;

    	int i = 0;
    	while (i < args.length) {
    		String arg = args[i];
    		switch (arg) {
    		case "-maxdist":
    			if (i+1 >= args.length) {
    				log("-maxdist argument requires another argument");
    				printUsageAndExit();
    			}
    			CognateIO.COGNATE_SPLIT_THRESHOLD = Integer.parseInt(args[i+1]);
    			i += 2;
    			break;
    		case "-words":
    			if (i+1 >= args.length) {
    				log("-words argument requires another argument");
    				printUsageAndExit();
    			}
    			CognateIO.NGLOSSIDS = Integer.parseInt(args[i+1]);
    			i += 2;
    			break;
    		case "-kml":
    			if (i+1 >= args.length) {
    				log("-kml argument requires another argument");
    				printUsageAndExit();
    			}
    			KML_FILE = args[i+1];
    			i += 2;
    			break;
    		case "-bg":
    			if (i+1 >= args.length) {
    				log("-bg argument requires another argument");
    				printUsageAndExit();
    			}
    			BG_FILE = args[i+1];
    			i += 2;
    			break;
    		case "-nex":
    			if (i+1 >= args.length) {
    				log("-nex argument requires another argument");
    				printUsageAndExit();
    			}
    			NEXUS_FILE = args[i+1];
    			i += 2;
    			break;
    		case "-cognates":
    			if (i+1 >= args.length) {
    				log("-cognates argument requires another argument");
    				printUsageAndExit();
    			}
    			COGNATE_FILE = args[i+1];
    			i += 2;
    			break;
    		case "-h":
    		case "-help":
    		case "--help":
    			printUsageAndExit();
    			break;
    		default:
				log("unrecognised command " + arg);
				printUsageAndExit();
    		}
    	}
		
	}

	void log(String m) {
		System.err.println(m);
	}
	
	private void printUsageAndExit() {
		System.out.println("java babel.spanningtree.Panel [options]");
		System.out.println("Draws spanning trees of congates");
		System.out.println("-maxdist <number> maximum allowed distance of branches. Any branch over will result in spanning trees being broken up and message being logged.");
		System.out.println("-words <number> number of meaning classes (default 207)");
		System.out.println("-kml <file> kml file with point locations for each of the languages");
		System.out.println("-bg <file> image file with world map in Mercator projection");
		System.out.println("-nex <file> specify nexus file with binary data");
		System.out.println("-cognates <file> specify cognate file with labels for each column in the nexus file");
		System.out.println("-h, -help print this message");
		System.exit(0);
	}

	void loadBGImage(String sFileName) throws Exception {
		m_bgImage = ImageIO.read(new File(sFileName));
		try {
			Pattern pattern = Pattern
					.compile(".*\\(([0-9\\.Ee-]+),([0-9\\.Ee-]+)\\)x\\(([0-9\\.Ee-]+),([0-9\\.Ee-]+)\\).*");
			Matcher matcher = pattern.matcher(sFileName);
			matcher.find();
			m_fBGImageBox[1] = Float.parseFloat(matcher.group(1));
			m_fBGImageBox[0] = Float.parseFloat(matcher.group(2));
			m_fBGImageBox[3] = Float.parseFloat(matcher.group(3));
			m_fBGImageBox[2] = Float.parseFloat(matcher.group(4));
		} catch (Exception e) {
			final double[] fBGImageBox = { -180, -90, 180, 90 };
			m_fBGImageBox = fBGImageBox;
		}
	} // loadBGImage
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		
		
		int nW2 = m_bgImage.getWidth();
		int nH2 = m_bgImage.getHeight();

		int x0 = (int)(nW2 * (m_fMinLong- m_fBGImageBox[0])/(m_fBGImageBox[2] - m_fBGImageBox[0]));
		int x1 = (int)(nW2 * (m_fMaxLong- m_fBGImageBox[0])/(m_fBGImageBox[2] - m_fBGImageBox[0]));
		int y0 = (int)(nH2 * (m_fMaxLat- m_fBGImageBox[3])/(m_fBGImageBox[1] - m_fBGImageBox[3]));
		int y1 = (int)(nH2 * (m_fMinLat- m_fBGImageBox[3])/(m_fBGImageBox[1] - m_fBGImageBox[3]));

		double m_fScaleX = 10;
		double m_fScaleY = 10;
		int nW = getWidth();
		int nH = getHeight();
		m_fScaleX = (nW + 0.0f) / (m_fMaxLong - m_fMinLong);
		m_fScaleY = (nH + 0.0f) / (m_fMaxLat - m_fMinLat);

		
		g.drawImage(m_bgImage,
				0, 0, getWidth(), getHeight(),
				x0, 
				y0, 
				x1,
				y1, 
				null);
		if (m_fMaxLong > 180) {
			x0 = (int)(nW2 * (-360+m_fMinLong- m_fBGImageBox[0])/(m_fBGImageBox[2] - m_fBGImageBox[0]));
			x1 = (int)(nW2 * (-360+m_fMaxLong- m_fBGImageBox[0])/(m_fBGImageBox[2] - m_fBGImageBox[0]));
			g.drawImage(m_bgImage,
					0, 0, getWidth(), getHeight(),
					x0, 
					y0, 
					x1,
					y1, 
					null);			
		}
		
		g2.setColor(Color.red);
		g2.setFont(new Font(Font.DIALOG, Font.BOLD, 20));
		if (meaningClassID <= CognateIO.NGLOSSIDS) {
			g2.drawString(meaningClassID + ": " + data.getMeaningClassName(meaningClassID), 10, 400);
		}
		System.err.println(meaningClassID + ": " + data.getMeaningClassName(meaningClassID));

		for (String language: locations.getLocationNames()) {
			Location loc = locations.getLocation(language);
			g.setColor(loc.color);
			g.setColor(new Color(0x5050a0));
			int gx = (int) ((loc.longitude - m_fMinLong) * m_fScaleX);
			int gy = (int) ((m_fMaxLat - loc.latitude) * m_fScaleY);
			g2.setStroke(new BasicStroke(3.0f));
			g.drawOval(gx - 3, gy - 3, 5, 5);
			g.drawOval(gx - 5, gy - 5, 10, 10);
			g.drawOval(gx - 7, gy - 7, 14, 14);
			
			g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
			//g.drawString(language, gx+10, gy);
		}

		
		g.setColor(Color.black);

//final int JITTER = 12;
int JITTER = 8;
switch (mode) {
case DRAW_ALL_GLOSS: 
	JITTER = 12;
		((Graphics2D) g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
		g2.setStroke(new BasicStroke(3.0f));
		for (meaningClassID = 1; meaningClassID < CognateIO.NGLOSSIDS; meaningClassID++) {
			plot(g2, JITTER, m_fScaleX, m_fScaleY);
		}
		break;
case DRAW_GLOSS:
	g2.setStroke(new BasicStroke(5.0f));
	((Graphics2D) g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
	plot(g2, JITTER, m_fScaleX, m_fScaleY);
	break;
		
}


//}

//		g.setColor(Color.black);
//		((Graphics2D) g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
//		for (int i = 0; i < edgecount.length; i++) {
//			for (int j = i; j < edgecount.length; j++) {
//				if (edgecount[i][j] > 10) {
//					Location loc0 = locations.get(languages.get(i));
//					Location loc1 = locations.get(languages.get(j));
//					x0 = (int) ((loc0.longitude - m_fMinLong) * m_fScaleX);
//					y0 = (int) ((m_fMaxLat - loc0.latitude) * m_fScaleY);
//					x1 = (int) ((loc1.longitude - m_fMinLong) * m_fScaleX);
//					y1 = (int) ((m_fMaxLat - loc1.latitude) * m_fScaleY);
//					g.drawString(edgecount[i][j] + "", (x0+x1)/2, (y0+y1)/2);
//				}
//				
//			}
//		}
	}

	void plot(Graphics2D g2, int JITTER, double m_fScaleX, double m_fScaleY) {
		Map<Integer,Cognate> map = data.getCognates(meaningClassID);
		if (map != null) {
			
			g2.setFont(new Font(Font.DIALOG, Font.BOLD, 14));
			for (Cognate c : map.values()) {
				Random rand = new Random();//c.MultistateCode+2);
				Color color = new Color(Color.HSBtoRGB(
						((float)c.MultistateCode/map.size()) * ((float)c.MultistateCode/map.size()) * rand.nextFloat(), 
						0.5f + rand.nextFloat()/2.0f, 
						0.90f)); 
				color = new Color(rand.nextInt(0xFFFF) * 0xFF | 0x80);
				g2.setColor(color);
				for (int i = 0; i < c.languages.size(); i++) {
					if (c.MultistateCode > 0) {
// FIXME revisit this code to make it useful or remove
//						Location loc = locations.get(c.languages.get(i));
//						int x0 = (int) ((loc.longitude - m_fMinLong) * m_fScaleX);
//						int y0 = (int) ((m_fMaxLat - loc.latitude) * m_fScaleY);
//						String word =  c.word.get(i) + ":" + c.MultistateCode;
//						g2.drawString(word, x0, y0);
					}
				}
				
				
				List<Integer> edges = c.edges;
				for (int i = 0; i < edges.size(); i += 2) {
					int p0 = edges.get(i);
					int p1 = edges.get(i + 1);
					Location loc0 = locations.getLocation(c.languages.get(p0));
					Location loc1 = locations.getLocation(c.languages.get(p1));
					int x0 = (int) ((loc0.longitude - m_fMinLong) * m_fScaleX);
					int y0 = (int) ((m_fMaxLat - loc0.latitude) * m_fScaleY);
					int x1 = (int) ((loc1.longitude - m_fMinLong) * m_fScaleX);
					int y1 = (int) ((m_fMaxLat - loc1.latitude) * m_fScaleY);
					x0 += rand.nextInt(JITTER) - JITTER/2;
					x1 += rand.nextInt(JITTER) - JITTER/2;
					y0 += rand.nextInt(JITTER) - JITTER/2;
					y1 += rand.nextInt(JITTER) - JITTER/2;
					g2.drawLine(x0, y0, x1, y1);
//FIXME revisit this piece of code to remove or keep
//					double dist = CognateData.distance(loc0, loc1);
//					g.drawString((dist + "     ").substring(0,6) , (x0 + x1)/2, (y0+y1)/2);
				}
			}
		}
	}

	
	static public String KML_FILE = "/home/remco/data/beast/ie/saskyY3/geo/ie.kml";
	static public String BG_FILE = "/home/remco/data/map/IEsmall.png";
	//static public String NEXUSFILE = "filtered.nex";
	//final static public String DATAFILE = "cognates7recoded.dat";
	//public static final int NTAX = 194;
	//public static final int NGLOSSIDS = 205;
	static public String NEXUS_FILE = "/home/remco/data/beast/ie/saskyY3/geo/IELex.nex";
	static public String COGNATE_FILE = "/home/remco/data/beast/ie/saskyY3/geo/cognates.dat";

	
	void loadLocations(LocationParser locations) {
		this.locations = locations;

		m_fMinLat = 90;
		m_fMinLong = 180;
		m_fMaxLat = -90;
		m_fMaxLong = -180;
		for (Location loc: locations.getLocations()) {
			m_fMinLat = Math.min(m_fMinLat, (float) loc.latitude);
			m_fMaxLat = Math.max(m_fMaxLat, (float) loc.latitude);
			m_fMinLong = Math.min(m_fMinLong, (float) loc.longitude);
			m_fMaxLong = Math.max(m_fMaxLong, (float) loc.longitude);
		}
		float fOffset = 10f;
		m_fMaxLong = m_fMaxLong + fOffset;
		m_fMaxLat = m_fMaxLat + fOffset;
		m_fMinLong = m_fMinLong - fOffset;
		m_fMinLat = m_fMinLat - fOffset;
	}

	void loadData(final String nexusFile, final String cognateFile) throws Exception {
		data = new CognateData() {
			@Override
			void loadCognateData(String fileName) throws Exception {
				System.err.println("Loading " + fileName);
				mapGlossIDtoMeaningClassName = new HashMap<Integer, String>();
				cognateGlossMap = new HashMap<Integer, Map<Integer,Cognate>>();

				List<Entry> entries = readCognates();
				
				for (Entry entry : entries) {
					mapGlossIDtoMeaningClassName.put(entry.GlossID, entry.Gloss);
					if (cognateGlossMap.containsKey(entry.GlossID)) {
						Map<Integer, Cognate> cognateMap = cognateGlossMap.get(entry.GlossID);
						if (cognateMap.containsKey(entry.MultistateCode)) {
							Cognate cognate = cognateMap.get(entry.MultistateCode);
							cognate.languages.add(entry.Language);
							cognate.word.add(entry.Word);
						} else {
							Cognate cognate = new Cognate();
							cognate.GlossID = entry.GlossID;
							cognate.MultistateCode = entry.MultistateCode;
							cognate.languages.add(entry.Language);
							cognate.word.add(entry.Word);
							cognateMap.put(entry.MultistateCode, cognate);
						}				
					} else {
						Map<Integer, Cognate> cognateMap = new HashMap<Integer, Cognate>();
						Cognate cognate = new Cognate();
						cognate.GlossID = entry.GlossID;
						cognate.MultistateCode = entry.MultistateCode;
						cognate.languages.add(entry.Language);
						cognate.word.add(entry.Word);
						cognateMap.put(entry.MultistateCode, cognate);
						cognateGlossMap.put(entry.GlossID, cognateMap);
					}
				}
			}
			
			public List<Entry> readCognates() throws Exception {
				String str = null;
				List<String> mapPositionToCognate = new ArrayList<>();
				List<String> mapPositionToGloss = new ArrayList<>();
				List<Integer> mapPositionToGlossID = new ArrayList<>();
				List<Integer> mapPositionToState = new ArrayList<>();
				File file = new File(cognateFile);
				System.err.println("Loading " + cognateFile);
				BufferedReader fin = new BufferedReader(new FileReader(file));
				int k = 0;
				int meaningClassID = 0;
				String prev = "";
				while (fin.ready()) {
					str = fin.readLine();
					String str2 = str.replaceAll("\\s*\\d+\\s", "");
					str2 = str2.replaceAll(",", "");
					mapPositionToCognate.add(str2);
					str2 = str2.replaceAll("(.*)_.*", "$1");
					mapPositionToGloss.add(str2);

					if (str.replaceAll("[0-9\\s]", "").equals(prev)) {
						//str = str.replaceAll("\\s*\\d+\\s", "");
						//mapPositionToCognate.add(str);
						//str = str.replaceAll("_.*", "");
						//mapPositionToGloss.add(str);
						mapPositionToGlossID.add(meaningClassID);
						mapPositionToState.add(k++);
					} else if (str.matches(".*_group,")) {
						//str = str.replaceAll("\\s*\\d+\\s", "");
						//mapPositionToCognate.add(str);
						//mapPositionToGloss.add("groupcode");
						k = 0;
						meaningClassID++;
						mapPositionToGlossID.add(meaningClassID);
						mapPositionToState.add(k++);
					} else {
						k = 0;
						meaningClassID++;
						mapPositionToGlossID.add(meaningClassID);
						mapPositionToState.add(k++);
					}
					prev = str.replaceAll("[0-9\\s]", "");
				}
				fin.close();
				
				CognateIO.NGLOSSIDS = meaningClassID;
				
				List<Entry> entries = new ArrayList<Entry>();
				file = new File(nexusFile);
				fin = new BufferedReader(new FileReader(file));
				String sStr = null;
				// eat up header
				do {
					sStr = fin.readLine();
				} while (!sStr.toLowerCase().matches(".*matrix.*"));
				do {
					sStr = fin.readLine();
				} while (sStr.matches(".*\\[.*"));

				// process data
				while (fin.ready()) {
					sStr = sStr.trim();
					String [] strs = sStr.split("\\s+");
					if (strs.length != 1) {
						String lang = strs[0].replaceAll("'", "");
						String cognates = strs[1].trim();
						for (int i = 0; i < cognates.length(); i++) {
							char c = cognates.charAt(i);
							if (c == '1') {
								Entry entry = new Entry();
								entry.GlossID = mapPositionToGlossID.get(i);
								entry.Gloss = mapPositionToGloss.get(i);
								entry.Subgroup = "x";
								entry.Language = lang;
								entry.Word = mapPositionToCognate.get(i);
								entry.MultistateCode = mapPositionToState.get(i);
								entries.add(entry);
							}
						}
					}
					sStr = fin.readLine();
					if (sStr.matches(";")) {
						fin.close();
						return entries;
					}
				}
				fin.close();
				return entries;
			}

		};
		data.loadCognateData(nexusFile);
		data.calcSpanningTrees(locations);
		//CognateIO.writeCognatesToNexus(new File(CognateIO.NEXUSFILE), data.cognateGlossMap, locations);

		
		edgecount = new int[CognateIO.NTAX][CognateIO.NTAX];
		languages = new ArrayList<String>();
		Map<String,Integer> langMap = new HashMap<String, Integer>();
		
		for (meaningClassID = 1; meaningClassID < CognateIO.NGLOSSIDS; meaningClassID++) {		
			Map<Integer,Cognate> map = data.getCognates(meaningClassID);
			if (map != null) {
				for (Cognate c : map.values()) {
					List<Integer> edges = c.edges;
					for (int i = 0; i < edges.size(); i += 2) {
						String p0 = c.languages.get(edges.get(i));
						String p1 = c.languages.get(edges.get(i + 1));
						int i0 = -1, i1 = -1;
						if (!langMap.containsKey(p0)) {
							langMap.put(p0, languages.size());
							languages.add(p0);
						}
						if (!langMap.containsKey(p1)) {
							langMap.put(p1, languages.size());
							languages.add(p1);
						}
						i0 = langMap.get(p0);
						i1 = langMap.get(p1);
						edgecount[Math.min(i0, i1)][Math.max(i0, i1)]++;
					}
				}
			}
		}
		meaningClassID = 1;
	
	}

	
	@Override
	public void keyTyped(KeyEvent e) {
		if (e.getKeyChar() == 'p') {
			meaningClassID --;
			if (meaningClassID < 1) {
				meaningClassID = 1;
			}
		}
		if (e.getKeyChar() == 'n') {
			meaningClassID ++;
			if (meaningClassID > CognateIO.NGLOSSIDS) {
				meaningClassID = CognateIO.NGLOSSIDS;
			}
		}
		repaint();
		
		if (e.getKeyChar() == 'f') {
			try {
	        	com.itextpdf.text.Document doc = new com.itextpdf.text.Document();
	        	PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream("/tmp/x.pdf"));
	        	doc.setPageSize(new com.itextpdf.text.Rectangle(getWidth(), getHeight()));
	        	doc.open();
	        	PdfContentByte cb = writer.getDirectContent();

//	        	for (int i = 1; i < CognateIO.NGLOSSIDS; i++) {
//	        		Graphics2D g2d = new PdfGraphics2D(cb, getWidth(), getHeight());
//	        		GlossID = i;
//	        		paint(g2d);
//	        		g2d.dispose();
//	        		doc.newPage();
//	        		System.err.println("page " + i);
//	        	}
        		Graphics2D g2d = new PdfGraphics2D(cb, getWidth(), getHeight());
        		paint(g2d);
        		g2d.dispose();
        		g2d.dispose();
	        	
	        	doc.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}

		}
	}

	@Override
	public void keyPressed(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void keyReleased(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}

	public static void main(String[] args) throws Exception {
		JFrame frame = new JFrame();
		frame.setSize(1024, 728);
		Panel pane = new Panel(args);
		pane.loadLocations(LocationParser.parseKMLFile(KML_FILE));
		pane.loadData(NEXUS_FILE, COGNATE_FILE);
		pane.loadBGImage(BG_FILE);
		frame.add(pane);
		frame.addKeyListener(pane);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);	
		frame.setVisible(true);
		
		if (false)
		try {
        	com.itextpdf.text.Document doc = new com.itextpdf.text.Document();
    		String label = CognateIO.KML_FILE;
    		label = label.substring(label.lastIndexOf('/') + 1, label.lastIndexOf('.'));

        	PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream("/tmp/" + label + ".pdf"));
        	doc.setPageSize(new com.itextpdf.text.Rectangle(pane.getWidth(), pane.getHeight()));
        	doc.open();
        	PdfContentByte cb = writer.getDirectContent();

        	for (int i = 1; i < CognateIO.NGLOSSIDS; i++) {
        		Graphics2D g2d = new PdfGraphics2D(cb, pane.getWidth(), pane.getHeight());
        		pane.meaningClassID = i;
        		pane.paint(g2d);
        		g2d.dispose();
        		doc.newPage();
        		System.err.println("page " + i);
        	}
//    		Graphics2D g2d = new PdfGraphics2D(cb, pane.getWidth(), pane.getHeight());
//    		pane.paint(g2d);
//    		g2d.dispose();
//    		g2d.dispose();
        	
        	doc.close();
        	System.exit(0);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		

	}

}
