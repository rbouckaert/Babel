package babel.tools;


import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import beast.app.treeannotator.TreeAnnotator;
import beast.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.evolution.alignment.TaxonSet;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.util.FrequencySet;
import beast.util.Randomizer;

@Description("Match clades from two tree sets and print support for both sets so "
		+ "they can be plotted in an X-Y plot")
public class CladeSetComparator extends Runnable {
	final public Input<List<TreeFile>> srcInput = new Input<>("tree","2 or more source tree (set or MCC tree) files", new ArrayList<>());
	final public Input<TreeFile> src1Input = new Input<>("tree1","source tree (set or MCC tree) file");
	final public Input<TreeFile> src2Input = new Input<>("tree2","source tree (set or MCC tree) file");
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));
	final public Input<OutFile> svgOutputInput = new Input<>("svg", "svg output file. if not specified, no SVG output is produced.",
			new OutFile("[[none]]"));
	final public Input<OutFile> pngOutputInput = new Input<>("png", "png output file. if not specified, no PNG output is produced.",
			new OutFile("[[none]]"));
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);

	double n;
	
	final String header = "<svg version=\"1.2\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" class=\"graph\" aria-labelledby=\"title\" role=\"img\" height=\"1200\">\n" + 
			"<g class=\"grid x-grid\" id=\"xGrid\">\n" + 
			"  <line x1=\"90\" x2=\"90\" y1=\"10\" y2=\"1010\" style=\"stroke:#000;stroke-width:2\"></line>\n" + 
			"</g>\n" + 
			"<g class=\"grid y-grid\" id=\"yGrid\">\n" + 
			"  <line x1=\"90\" x2=\"1090\" y1=\"1010\" y2=\"1010\" style=\"stroke:#000;stroke-width:2\"></line>\n" + 
			"</g>\n" + 
			"<line x1=\"90\" x2=\"1090\" y1=\"1010\" y2=\"10\" style=\"stroke:#000;stroke-width:2\"></line>\n" + 
			"<line x1=\"90\" x2=\"840\" y1=\"760\" y2=\"10\" style=\"stroke:#00f;stroke-width:1\"></line>\n" + 
			"<line x1=\"340\" x2=\"1090\" y1=\"1010\" y2=\"260\" style=\"stroke:#00f;stroke-width:1\"></line>\n" + 
			"  <g class=\"labels x-labels\">\n" + 
			"  <text x=\"90\" y=\"1030\">0.0</text>\n" + 
			"  <text x=\"290\" y=\"1030\">0.2</text>\n" + 
			"  <text x=\"490\" y=\"1030\">0.4</text>\n" + 
			"  <text x=\"690\" y=\"1030\">0.6</text>\n" + 
			"  <text x=\"890\" y=\"1030\">0.8</text>\n" + 
			"  <text x=\"1090\" y=\"1030\">1.0</text>\n" + 
			"  <text x=\"520\" y=\"1040\" class=\"label-title\">file1</text>\n" + 
			"</g>\n" + 
			"<g class=\"labels y-labels\">\n" + 
			"  <text x=\"60\" y=\"15\">1.0</text>\n" + 
			"  <text x=\"60\" y=\"215\">0.8</text>\n" + 
			"  <text x=\"60\" y=\"415\">0.6</text>\n" + 
			"  <text x=\"60\" y=\"615\">0.4</text>\n" + 
			"  <text x=\"60\" y=\"815\">0.2</text>\n" + 
			"  <text x=\"60\" y=\"1015\">0.0</text>\n" + 
			"  <text x=\"40\" y=\"540\" class=\"label-title\" transform=\"rotate(90,40,540)\">file2</text>\n" + 
			"</g>\n" + 
			"<g class=\"data\" data-setname=\"Our first data set\">\n"; 
//			"  <circle cx=\"90\" cy=\"192\" data-value=\"7.2\" r=\"4\"></circle>\n" + 
//			"  <circle cx=\"240\" cy=\"141\" data-value=\"8.1\" r=\"4\"></circle>\n" + 
//			"  <circle cx=\"388\" cy=\"179\" data-value=\"7.7\" r=\"4\"></circle>\n" + 
//			"  <circle cx=\"531\" cy=\"200\" data-value=\"6.8\" r=\"4\"></circle>\n" + 
//			"  <circle cx=\"677\" cy=\"104\" data-value=\"6.7\" r=\"4\"></circle>\n" + 
		final String footer =	"</g>\n" + 
			"</svg>\n" + 
			"\n";
	
	
	@Override
	public void initAndValidate() {
	}

	
	double maxHeight = 0.0;
	int problemCount = 0;
	int interestCount = 0;
	int inconsistentHeightIntervals = 0;
	double meanHeightsDifference = 0;

	
	@Override
	public void run() throws Exception {
		long start = System.currentTimeMillis();
		if (srcInput.get().size()  == 0) {
			CladeSetWithHeights cladeSet1 = getCladeSet(src1Input.get().getPath());
			double n1 = n;
			CladeSetWithHeights cladeSet2 = getCladeSet(src2Input.get().getPath());
			double n2 = n;
			process(src1Input.get(), src2Input.get(), "", cladeSet1, n1, cladeSet2, n2);
			return;
		}
		
		if (srcInput.get().size()  == 1) {
			throw new IllegalArgumentException("Must specify at least 2 tree files with \'tree\' argument");
		}

		int n = srcInput.get().size();
		CladeSetWithHeights [] cladeSets = new CladeSetWithHeights[n];
		double [] count = new double[n];
		for (int i = 0; i < n; i++) {
			cladeSets[i] = getCladeSet(srcInput.get().get(i).getPath());
			count[i] = this.n;
		}		
		for (int i = 0; i < n; i++) {
			for (int j = i+1; j < n; j++) {
				process(srcInput.get().get(i), srcInput.get().get(j), n > 2 ? i+"-"+j : "", cladeSets[i], count[i], cladeSets[j], count[j]);				
			}			
		}
		
		if (pngOutputInput.get() != null && 
			!pngOutputInput.get().getName().equals("[[none]]") &&
			n > 2) {
			String str = pngOutputInput.get().getPath();
			if (str.contains(".")) {
				int k = str.lastIndexOf('.');
				str = str.substring(0, k);
			}
			str += ".html";
			PrintStream out = new PrintStream(str);
			out.println("<html>\n<body>");
			out.println("<table>");
			for (int i = 0; i < n; i++) {
				out.print("<tr>");
				for (int j = 0; j < i; j++) {
					out.print("<td>&nbsp;</td>");
				}
				for (int j = i+1; j < n; j++) {
					String png = pngOutputInput.get().getPath();
					if (png.contains(".")) {
						int k = png.lastIndexOf('.');
						png = png.substring(0, k) + (i+"-"+j) + png.substring(k);
					}
					out.println("\t<td><img src='" + png + "'/></td>");
				}			
				out.println("</tr>");
			}
			
			out.println("</table>");
			out.println("</body>\n</html>");
			out.close();			
		}
		long end = System.currentTimeMillis();
		System.err.println("All done in " + (end - start)/1000 + " seconds");
	}
	
	void process(TreeFile tree1, TreeFile tree2, String suffix, 
			CladeSetWithHeights cladeSet1, double n1,
			CladeSetWithHeights cladeSet2, double n2
			)  throws Exception {
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			String str = normalise(outputInput.get().getPath(), suffix);			
			Log.warning("Writing to file " + str);
			out = new PrintStream(str);
		}
		PrintStream svg = null;
		if (svgOutputInput.get() != null && !svgOutputInput.get().getName().equals("[[none]]")) {
			String str = normalise(svgOutputInput.get().getPath(), suffix);
			Log.warning("Writing to file " + str);
			svg = new PrintStream(str);
			svg.println(header.replaceAll("file1", tree1.getPath()).replaceAll("file2", tree2.getPath()));
		}

		Graphics2D g = null;
		BufferedImage bi = null;
		if (pngOutputInput.get() != null && !pngOutputInput.get().getName().equals("[[none]]")) {
			bi = new BufferedImage(1200, 1200, BufferedImage.TYPE_INT_ARGB);
			g = (Graphics2D) bi.getGraphics();
			initPNG(g, tree1, tree2);
		}

//		CladeSetWithHeights cladeSet1 = getCladeSet(tree1.getPath());
//		double n1 = n;

//		CladeSetWithHeights cladeSet2 = getCladeSet(tree2.getPath());		
//		double n2 = n;
		
		// create map of clades to support values in set1
		Map<String, Double> cladeMap = new LinkedHashMap<>();
		Map<String, Integer> cladeToIndexMap = new LinkedHashMap<>();
		Map<String, Double> cladeHeightMap = new LinkedHashMap<>();
		for (int i = 0; i < cladeSet1.getCladeCount(); i++) {
			String clade = cladeSet1.getClade(i);
			BitSet bitset = cladeSet1.get(i);
			int support = cladeSet1.getFrequency(i);
			if (cladeSet1 instanceof SummaryCladeSetWithHeights) {
				cladeMap.put(clade, ((SummaryCladeSetWithHeights)cladeSet1).posteriors.get(bitset));
			} else {
				cladeMap.put(clade, support/ n1);
			}
			cladeHeightMap.put(clade, cladeSet1.getMeanNodeHeight(i));
			cladeToIndexMap.put(clade, i);
		}
		
		// process clades in set2
		double maxDiff = 0, meanDiff=0;
		int meanDiffCount = 0;
		double [] hist = new double[40];
		problemCount = 0;
		interestCount = 0;
		meanHeightsDifference = 0;
		inconsistentHeightIntervals = 0;
		for (int i = 0; i < cladeSet2.getCladeCount(); i++) {			
			String clade = cladeSet2.getClade(i);			
			double support2; 
			if (cladeSet2 instanceof SummaryCladeSetWithHeights) {
				BitSet bitset = cladeSet2.get(i);
				support2 = ((SummaryCladeSetWithHeights)cladeSet2).posteriors.get(bitset);
			} else {
				int support = cladeSet2.getFrequency(i);
				support2 = support/n2;	
			}
			double h2 = cladeSet2.getMeanNodeHeight(i);
			if (cladeMap.containsKey(clade)) {
				// clade is also in set1
				double h1 = cladeHeightMap.get(clade);
				double [] heights1 = cladeSet1.nodeHeights.get(cladeSet1.get(cladeToIndexMap.get(clade)));
				Arrays.sort(heights1);
				double lo1 = heights1[(int)(heights1.length * 0.025)];
				double hi1 = heights1[(int)(heights1.length * 0.975)];
				
				double [] heights2 = cladeSet2.nodeHeights.get(cladeSet2.get(i));
				Arrays.sort(heights2);
				double lo2 = heights2[(int)(heights2.length * 0.025)];
				double hi2 = heights2[(int)(heights2.length * 0.975)];
				
				double support1 = cladeMap.get(clade);
				output(out, svg, clade,support1, support2, g, h1, h2, 
						lo1, lo2, hi1, hi2);
				// System.out.println((h1 - h2) + " " + (100 * (h1 - h2) / h1));
				
				maxDiff = Math.max(maxDiff, Math.abs(cladeMap.get(clade) - support2));
				if (support2 + cladeMap.get(clade) > 0.01) {
					meanDiff += Math.abs(cladeMap.get(clade) - support2);
					meanHeightsDifference += Math.abs(hi1-hi2)/(h1+h2)/2.0;
					meanDiffCount++;
					if (lo1 > hi2 || lo2 > hi1) {
						inconsistentHeightIntervals++;
					}
				}
				cladeMap.remove(clade);
				
				// record difference in 95%HPD (if support > 1% in both clade sets)
				if (support1 > 0.01 && support2 > 0.01) {
					if ((hi1-lo1) < (hi2-lo2)) {
						double w = (hist.length/2) * (hi1-lo1)/(hi2-lo2);
						if (w < 0) {
							w = 0;
						}
						hist[(int)w] += support1 + support2;
					} else {
						double w = hist.length - 1 - (hist.length/2) * (hi2-lo2)/(hi1-lo1);
						if (w >= hist.length) {
							w = hist.length - 1;
						}
						hist[(int)w] += support1 + support2;
					}
				}
			} else {
				// clade is not in set1
				output(out, svg, clade, 0.0, support2, g, 0, h2, 0, h2, 0, h2);
				maxDiff = Math.max(maxDiff, support2);
				if (support2> 0.01) {
					meanDiff += support2;
					meanDiffCount++;
				}
			}
		}
		
		// process left-overs of clades in set1 that are not in set2 
		for (String clade : cladeMap.keySet()) {
			double h1 = cladeHeightMap.get(clade);
			output(out, svg, clade, cladeMap.get(clade), 0.0, g, h1, 0.0, h1, 0, h1, 0);
			double s = cladeMap.get(clade);
			maxDiff = Math.max(maxDiff, s);
			if (s> 0.01) {
				meanDiff += s;
				meanDiffCount++;
			}
		}

        final DecimalFormat formatter = new DecimalFormat("#.##");
		if (svg != null) {
			svg.println("<text x='110' y='25'>Max difference in clade support: " + formatter.format(maxDiff * 100)+ "%</text>");
			svg.println("<text x='110' y='45'>Mean difference in clade support (when sum over 1%): " + formatter.format(meanDiff/meanDiffCount * 100)+ "%</text>");
			svg.println("<text x='110' y='65'>" + interestCount + " clades >25% difference "+ problemCount + " problematic</text>");
			svg.println("<text x='110' y='85'>" + inconsistentHeightIntervals + " inconsistent height intervals " + formatter.format(100.0*meanHeightsDifference/meanDiffCount) + " average % mean height diff");
			svg.println(footer);
		}
		if (bi != null) {
			// draw histogram of 95%HPD interval fractions
			double max = 0;
			for (double d : hist) {
				max = Math.max(max, d);
			}
			int width = 10, height = 90;
			g.setComposite(AlphaComposite.SrcOver.derive(1.0f));
			g.drawRect(100, 0, width*hist.length, height);
			for (int i = 0; i < hist.length; i++) {
				g.drawRect(100 + i * width, height-(int)(hist[i] * height / max), width, (int)(hist[i] * height / max));
			}
			
			g.setFont(new Font("Arial", Font.PLAIN, 18));
			g.setColor(Color.black);
			g.drawString("Max difference in clade support: " + formatter.format(maxDiff * 100)+ "%", 510, 25);
			g.drawString("Mean difference in clade support (when sum over 1%): " + formatter.format(meanDiff/meanDiffCount * 100)+ "%", 510, 45);
			g.drawString(interestCount + " clades >25% difference "+ problemCount + " problematic", 510, 65);
			g.drawString(inconsistentHeightIntervals + " inconsistent height intervals " + formatter.format(100.0*meanHeightsDifference/meanDiffCount) + " average % mean height diff", 510, 85);

			String str = normalise(pngOutputInput.get().getPath(), suffix);
			Log.warning("Writing to file " + str);
			ImageIO.write(bi, "png", new File(str));
		}
		Log.info("Maximum difference in clade support: " + maxDiff);
		Log.info("Mean difference in clade support (when sum over 1%): " + meanDiff/meanDiffCount);
		Log.info(interestCount + " clades >25% difference "+ problemCount + " problematic");
		Log.info(inconsistentHeightIntervals + " inconsistent height intervals " + formatter.format(100.0*meanHeightsDifference/meanDiffCount) + " average % mean height diff");
		Log.info.println("Done");
	}

	private String normalise(String str, String suffix) {
		if (str.contains(".")) {
			int k = str.lastIndexOf('.');
			str = str.substring(0, k) + suffix + str.substring(k);
		} else {
			str += suffix;
		}
		return str;
	}

	private void initPNG(Graphics2D g, TreeFile tree1, TreeFile tree2) {
		g.setColor(Color.white);
		g.fillRect(0, 0, 1200, 1200);
		
		g.setColor(Color.black);
		g.drawRect(100, 100, 1000, 1000);
		
		// diagonals
		int h = 1200;
		g.drawLine(100, h-100, 1100, h-1100);
		g.setColor(Color.blue);
		g.drawLine(100, h-300,  900, h-1100);
		g.drawLine(300, h-100, 1100, h- 900);
		
		g.drawString("0.0", 100, 1130);
		g.drawString("0.2", 300, 1130);
		g.drawString("0.4", 500, 1130);
		g.drawString("0.6", 700, 1130);
		g.drawString("0.8", 900, 1130);
		g.drawString("1.0",1100, 1130);
		
		g.drawString("0.0", 60, h-100);
		g.drawString("0.2", 60, h-300);
		g.drawString("0.4", 60, h-500);
		g.drawString("0.6", 60, h-700);
		g.drawString("0.8", 60, h-900);
		g.drawString("1.0", 60, h-1100);

		g.setColor(Color.black);
		g.setFont(new Font("Arial",Font.PLAIN, 50));
		g.drawString(tree1.getPath(), 120, 1170);

		AffineTransform orig = g.getTransform();
		g.rotate(-Math.PI/2);
		g.drawString(tree2.getPath(), -880, 40);
		g.setTransform(orig);

		g.setColor(Color.red);
		g.setComposite(AlphaComposite.SrcOver.derive(0.25f));	
	}

	private void output(PrintStream out, PrintStream svg, String clade, Double support1, double support2, Graphics2D g, double h1, double h2,
			double lo1, double lo2, double hi1, double hi2) {
		out.println(clade.replaceAll(" ", "") + " " + support1 + " " + support2);
//		if ((support1 < 0.1 && support2 > 0.9) ||
//			(support2 < 0.1 && support1 > 0.9)) {
		if (Math.abs(support1 - support2) > 0.9) {
			Log.warning("Problem clade: " + clade.replaceAll(" ", "") + " " + support1 + " " + support2);
			problemCount++;
		}
		
		if (Math.abs(support1 - support2) > 0.25) {
				Log.warning("Clade of interest (>25% difference): " + clade.replaceAll(" ", "") + " " + support1 + " " + support2);
				interestCount++;
			}

		if (svg != null) {
			svg.println("  <circle style=\"opacity:0.25;fill:#a00000\" cx=\""+ (90 +1000* support1 + Randomizer.nextInt(10) - 5) + 
					"\" cy=\""+ (10 + 1000 - 1000 * support2 + Randomizer.nextInt(10) - 5) +"\" "
							+ "data-value=\"7.2\" r=\"" + (support1 + support2) * 10 + "\"></circle>");
		}
		
		if (g != null) {
			double x = (100 + 1000 * support1 + Randomizer.nextInt(10) - 5);
			double y = (     1100 - 1000 * support2 + Randomizer.nextInt(10) - 5);
			double r = 1+(support1 + support2) * 10; 
			g.setColor(Color.red);
			g.setComposite(AlphaComposite.SrcOver.derive(0.25f));
			g.fillOval((int)(x-r/2), (int)(y-r/2), (int) r, (int) r);
			
			g.setColor(Color.blue);
			float alpha = (float)(0.1 + ((support1 + support2)/2.0)*0.9);
			g.setComposite(AlphaComposite.SrcOver.derive(alpha));
			x = 100 + 1000.0 * h1 / maxHeight;
			y = 1100 - 1000.0 * h2/ maxHeight;
			r = 3 + Math.max(support1, support2) * 13;
			g.fillOval((int)(x-r/2), (int)(y-r/2), (int) r, (int) r);
			
			
			if ((support1 + support2) > 0.1) {
				g.setComposite(AlphaComposite.SrcOver.derive(alpha * alpha));
				int x1 = (int)(100 + 1000.0 * lo1 / maxHeight);
				int y1 = (int)(1100 - 1000.0 * h2/ maxHeight);
				int x2 = (int)(100 + 1000.0 * hi1 / maxHeight);
				int y2 = (int)(1100 - 1000.0 * h2/ maxHeight);
				g.drawLine(x1, y1, x2, y2);
				x1 = (int)(100 + 1000.0 * h1 / maxHeight);
				y1 = (int)(1100 - 1000.0 * lo2/ maxHeight);
				x2 = (int)(100 + 1000.0 * h1 / maxHeight);
				y2 = (int)(1100 - 1000.0 * hi2/ maxHeight);
				g.drawLine(x1, y1, x2, y2);
			}
			
		}
	}

	private CladeSetWithHeights getCladeSet(String path) throws IOException {
		Log.warning("Processing " + path);
		MemoryFriendlyTreeSet srcTreeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(path, burnInPercentageInput.get());
		srcTreeSet.reset();
		Tree tree = srcTreeSet.next();
		CladeSetWithHeights cladeSet1 = new CladeSetWithHeights(tree);
		n = 1;
		maxHeight = Math.max(maxHeight, tree.getRoot().getHeight());

		while (srcTreeSet.hasNext()) {
			tree = srcTreeSet.next();
			cladeSet1.add(tree);
			n++;
			
			maxHeight = Math.max(maxHeight, tree.getRoot().getHeight());
		}
		
		if (n==1) {
			// might be a summary tree
			cladeSet1 = new SummaryCladeSetWithHeights(tree);
		}
		return cladeSet1;
	}

	public class SummaryCladeSetWithHeights extends CladeSetWithHeights {

		public SummaryCladeSetWithHeights(Tree tree) {
	        this(tree, tree.getTaxonset());
		}
		
	    public SummaryCladeSetWithHeights(Tree tree, TaxonSet taxonSet) {
	        this.taxonSet = taxonSet;
	        add(tree);
	    }
	    
	    void addClades(Node node, BitSet bits) {

	        if (node.isLeaf()) {
	            if (taxonSet != null) {
	                int index = taxonSet.getTaxonIndex(node.getID());
	                bits.set(index);
	            } else {
	                bits.set(node.getNr());
	            }
	        } else {

	            BitSet bits2 = new BitSet();
	            for (Node child : node.getChildren()) {
	                addClades(child, bits2);
	            }

	            add(bits2, 1);
	            Double [] heightHPD = (Double []) node.getMetaData("height_95%_HPD");
	            double lo = node.getHeight(), hi = node.getHeight();
	            if (heightHPD != null) {
		            lo = heightHPD[0];
		            hi = heightHPD[1];
	            }
	            addNodeHeight(bits2, node.getHeight(), lo, hi); // TODO ?= tree.getNodeHeight(node)
	            double posterior = 1;
	            Double posterior_ = (Double) node.getMetaData("posterior");
	            if (posterior_ != null) {
	            	posterior = posterior_;
	            }
	            posteriors.put(bits2, posterior);

	            if (bits != null) {
	                bits.or(bits2);
	            }
	        }
	    }
	    
	    void addNodeHeight(BitSet bits, double height, double lo, double hi) {
	        totalNodeHeight.put(bits, (getTotalNodeHeight(bits) + height));
	        nodeHeights.put(bits, new double[]{lo, height, hi});
	    }

	    Map<BitSet, Double> posteriors = new HashMap<>();
	}
	
	public class CladeSetWithHeights extends FrequencySet<BitSet> {
	    //
	    // Public stuff
	    //

	    public CladeSetWithHeights() {}

	    /**
	     * @param tree
	     */
	    public CladeSetWithHeights(Tree tree) {
	        this(tree, tree.getTaxonset());
	    }

	    /**
	     * @param taxonSet  a set of taxa used to label the tips
	     */
	    public CladeSetWithHeights(Tree tree, TaxonSet taxonSet) {
	        this.taxonSet = taxonSet;
	        add(tree);
	    }

	    /** get number of unique clades */
	    public int getCladeCount()
	    {
	        return size();
	    }

	    /** get clade bit set */
	    public String getClade(int index) {
	        BitSet bits = get(index);

	        StringBuffer buffer = new StringBuffer("{");
	        boolean first = true;
	        for (String taxonId : getTaxaSet(bits)) {
	            if (!first) {
	                buffer.append(", ");
	            } else {
	                first = false;
	            }
	            buffer.append(taxonId);
	        }
	        buffer.append("}");
	        return buffer.toString();
	    }

	    private SortedSet<String> getTaxaSet(BitSet bits) {

	        SortedSet<String> taxaSet = new TreeSet<>();

	        for (int i = 0; i < bits.length(); i++) {
	            if (bits.get(i)) {
	                taxaSet.add(taxonSet.asStringList().get(i)); //TODO ?= taxonList.getTaxonId(i)
	            }
	        }
	        return taxaSet;
	    }

	    /** get clade frequency */
	    int getCladeFrequency(int index)
	    {
	        return getFrequency(index);
	    }

	    /** adds all the clades in the tree */
	    public void add(Tree tree) {
	        if (taxonSet == null) {
	            taxonSet = tree.getTaxonset();
	        }

	        totalTrees += 1;

	        // Recurse over the tree and add all the clades (or increment their
	        // frequency if already present). The root clade is not added.
	        addClades(tree.getRoot(), null);
	    }

	    void addClades(Node node, BitSet bits) {

	        if (node.isLeaf()) {
	            if (taxonSet != null) {
	                int index = taxonSet.getTaxonIndex(node.getID());
	                bits.set(index);
	            } else {
	                bits.set(node.getNr());
	            }
	        } else {

	            BitSet bits2 = new BitSet();
	            for (Node child : node.getChildren()) {
	                addClades(child, bits2);
	            }

	            add(bits2, 1);
	            addNodeHeight(bits2, node.getHeight()); // TODO ?= tree.getNodeHeight(node)

	            if (bits != null) {
	                bits.or(bits2);
	            }
	        }
	    }

	    public double getMeanNodeHeight(int i) {
	        BitSet bits = get(i);

	        return getTotalNodeHeight(bits) / getFrequency(i);
	    }

	    double getTotalNodeHeight(BitSet bits) {
	        Double tnh = totalNodeHeight.get(bits);
	        if (tnh == null) return 0.0;
	        return tnh;
	    }

	    private void addNodeHeight(BitSet bits, double height) {
	        totalNodeHeight.put(bits, (getTotalNodeHeight(bits) + height));
	        if (!nodeHeights.containsKey(bits)) {
	        	nodeHeights.put(bits, new double[]{height});
	        } else {
	        	double [] heights = nodeHeights.get(bits);
	        	double [] newHeights = new double[heights.length + 1];
	        	System.arraycopy(heights, 0, newHeights, 0, heights.length);
	        	newHeights[heights.length] = height;
	        	nodeHeights.put(bits, newHeights);
	        }
	    }

	    // Generifying found that this code was buggy. Luckily it is not used anymore.

//	    /** adds all the clades in the CladeSet */
//	    public void add(CladeSet cladeSet)
//	    {
//	        for (int i = 0, n = cladeSet.getCladeCount(); i < n; i++) {
//	            add(cladeSet.getClade(i), cladeSet.getCladeFrequency(i));
//	        }
//	    }

	    private BitSet annotate(Tree tree, Node node, String freqAttrName) {
	        BitSet b = null;
	        if (node.isLeaf()) {
	            int index;
	            if (taxonSet != null) {
	                index = taxonSet.getTaxonIndex(node.getID());
	            } else {
	                index = node.getNr();
	            }
	            b = new BitSet(tree.getLeafNodeCount());
	            b.set(index);

	        } else {

	            for (Node child : node.getChildren()) {
	                BitSet b1 = annotate(tree, child, freqAttrName);
	                if( child.isRoot() ) {
	                    b = b1;
	                } else {
	                    b.or(b1);
	                }
	            }
	            final int total = getFrequency(b);
	            if( total >= 0 ) {
	                node.setMetaData(freqAttrName, total / (double)totalTrees );
	            }
	        }
	        return b;
	    }

	    /**
	     * Annotate clades of tree with posterior probability
	     * @param tree
	     * @param freqAttrName name of attribute to set per node
	     * @return sum(log(all clades probability))
	     */
	    public double annotate(Tree tree, String freqAttrName) {
	        annotate(tree, tree.getRoot(), freqAttrName);

	        double logClade = 0.0;
	        for(Node internalNode : tree.getInternalNodes()) {
	            final double f = (Double) internalNode.getMetaData(freqAttrName);
	            logClade += Math.log(f);
	        }
	        return logClade;
	    }

	    public boolean hasClade(int index, Tree tree) {
	        BitSet bits = get(index);

	        Node[] mrca = new Node[1];
	        findClade(bits, tree.getRoot(), mrca);

	        return (mrca[0] != null);
	    }

	    private int findClade(BitSet bitSet, Node node, Node[] cladeMRCA) {

	        if (node.isLeaf()) {

	            if (taxonSet != null) {
	                int index = taxonSet.getTaxonIndex(node.getID());
	                if (bitSet.get(index)) return 1;
	            } else {
	                if (bitSet.get(node.getNr())) return 1;
	            }
	            return -1;
	        } else {
	            int count = 0;
	            for (Node child : node.getChildren()) {
	                int childCount = findClade(bitSet, child, cladeMRCA);

	                if (childCount != -1 && count != -1) {
	                    count += childCount;
	                } else count = -1;
	            }

	            if (count == bitSet.cardinality()) cladeMRCA[0] = node;

	            return count;
	        }
	    }

	    //
	    // Private stuff
	    //
	    TaxonSet taxonSet = null;
	    Map<BitSet, Double> totalNodeHeight = new HashMap<>();
	    Map<BitSet, double[]> nodeHeights = new HashMap<>();
	    int totalTrees = 0;
	}

	
	public static void main(String[] args) throws Exception {
		new Application(new CladeSetComparator(), "Clade Set Comparator", args);

	}

	
}
