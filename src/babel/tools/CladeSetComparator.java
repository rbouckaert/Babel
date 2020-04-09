package babel.tools;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

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
import beast.evolution.tree.CladeSet;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;

@Description("Match clades from two tree sets and print support for both sets so "
		+ "they can be plotted in an X-Y plot")
public class CladeSetComparator extends Runnable {
	final public Input<TreeFile> src1Input = new Input<>("tree1","source tree (set) file");
	final public Input<TreeFile> src2Input = new Input<>("tree2","source tree (set) file");
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
	
	@Override
	public void run() throws Exception {
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}
		PrintStream svg = null;
		if (svgOutputInput.get() != null && !svgOutputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + svgOutputInput.get().getPath());
			svg = new PrintStream(svgOutputInput.get());
			svg.println(header.replaceAll("file1", src1Input.get().getPath()).replaceAll("file2", src2Input.get().getPath()));
		}

		Graphics2D g = null;
		BufferedImage bi = null;
		if (pngOutputInput.get() != null && !pngOutputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + pngOutputInput.get().getPath());
			bi = new BufferedImage(1200, 1200, BufferedImage.TYPE_INT_ARGB);
			g = (Graphics2D) bi.getGraphics();
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
			g.drawString(src1Input.get().getPath(), 520, 1170);

			AffineTransform orig = g.getTransform();
			g.rotate(Math.PI/2);
			g.translate(540,-40);
			g.drawString(src2Input.get().getPath(), 0, 0);
			g.setTransform(orig);

			g.setColor(Color.red);
			g.setComposite(AlphaComposite.SrcOver.derive(0.25f));
		}

		CladeSet cladeSet1 = getCladeSet(src1Input.get().getPath());
		double n1 = n;

		CladeSet cladeSet2 = getCladeSet(src2Input.get().getPath());		
		double n2 = n;
		
		// create map of clades to support values in set1
		Map<String, Double> cladeMap = new LinkedHashMap<>();
		Map<String, Double> cladeHeightMap = new LinkedHashMap<>();
		for (int i = 0; i < cladeSet1.getCladeCount(); i++) {
			String clade = cladeSet1.getClade(i);
			int support = cladeSet1.getFrequency(i);
			cladeMap.put(clade, support/ n1);
			cladeHeightMap.put(clade, cladeSet1.getMeanNodeHeight(i));
		}
		
		// process clades in set2
		double maxDiff = 0;
		for (int i = 0; i < cladeSet2.getCladeCount(); i++) {			
			String clade = cladeSet2.getClade(i);
			int support = cladeSet2.getFrequency(i);
			double h2 = cladeSet2.getMeanNodeHeight(i);
			if (cladeMap.containsKey(clade)) {
				// clade is also in set1
				double h1 = cladeHeightMap.get(clade);
				output(out, svg, clade,cladeMap.get(clade),support/n2, g, h1, h2);
				// System.out.println((h1 - h2) + " " + (100 * (h1 - h2) / h1));
				
				maxDiff = Math.max(maxDiff, Math.abs(cladeMap.get(clade) - support/n2));
				cladeMap.remove(clade);
			} else {
				// clade is not in set1
				output(out, svg, clade, 0.0, support/n2, g, 0, h2);
				maxDiff = Math.max(maxDiff, support/n2);
			}
		}		
		
		// process left-overs of clades in set1 that are not in set2 
		for (String clade : cladeMap.keySet()) {
			double h1 = cladeHeightMap.get(clade);
			output(out, svg, clade, cladeMap.get(clade), 0.0, g, h1, 0.0);
			maxDiff = Math.max(maxDiff, cladeMap.get(clade));
		}

		if (svg != null) {
			svg.println(footer);
		}
		if (bi != null) {
			ImageIO.write(bi, "png", pngOutputInput.get());
		}
		Log.info("Maximum difference in clade support: " + maxDiff);
		Log.info.println("Done");
	}

	private void output(PrintStream out, PrintStream svg, String clade, Double support1, double support2, Graphics2D g, double h1, double h2) {
		out.println(clade.replaceAll(" ", "") + " " + support1 + " " + support2);
		if ((support1 < 0.1 && support2 > 0.9) ||
			(support2 < 0.1 && support1 > 0.9)) {
			Log.warning("Problem clade: " + clade.replaceAll(" ", "") + " " + support1 + " " + support2);
		}
		
		if (Math.abs(support1 - support2) > 0.25) {
				Log.warning("Clade of interest (>25% difference): " + clade.replaceAll(" ", "") + " " + support1 + " " + support2);
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
			
		}
	}

	private CladeSet getCladeSet(String path) throws IOException {
		Log.warning("Processing " + path);
		MemoryFriendlyTreeSet srcTreeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(path, burnInPercentageInput.get());
		srcTreeSet.reset();
		Tree tree = srcTreeSet.next();
		CladeSet cladeSet1 = new CladeSet(tree);
		n = 1;
		while (srcTreeSet.hasNext()) {
			tree = srcTreeSet.next();
			cladeSet1.add(tree);
			n++;
			
			maxHeight = Math.max(maxHeight, tree.getRoot().getHeight());
		}
		return cladeSet1;
	}

	public static void main(String[] args) throws Exception {
		new Application(new CladeSetComparator(), "Clade Set Comparator", args);

	}

}
