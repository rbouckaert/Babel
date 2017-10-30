package babel.tools;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

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
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);

	double n;
	
	final String header = "<svg version=\"1.2\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" class=\"graph\" aria-labelledby=\"title\" role=\"img\">\n" + 
			"<g class=\"grid x-grid\" id=\"xGrid\">\n" + 
			"  <line x1=\"90\" x2=\"90\" y1=\"10\" y2=\"1010\"></line>\n" + 
			"</g>\n" + 
			"<g class=\"grid y-grid\" id=\"yGrid\">\n" + 
			"  <line x1=\"90\" x2=\"1090\" y1=\"1010\" y2=\"1010\"></line>\n" + 
			"</g>\n" + 
			"  <g class=\"labels x-labels\">\n" + 
			"  <text x=\"100\" y=\"1020\">0.0</text>\n" + 
			"  <text x=\"300\" y=\"1020\">0.2</text>\n" + 
			"  <text x=\"500\" y=\"1020\">0.4</text>\n" + 
			"  <text x=\"700\" y=\"1020\">0.6</text>\n" + 
			"  <text x=\"900\" y=\"1020\">0.8</text>\n" + 
			"  <text x=\"1100\" y=\"1020\">1.0</text>\n" + 
			"  <text x=\"500\" y=\"1030\" class=\"label-title\">file1</text>\n" + 
			"</g>\n" + 
			"<g class=\"labels y-labels\">\n" + 
			"  <text x=\"80\" y=\"15\">1.0</text>\n" + 
			"  <text x=\"80\" y=\"215\">0.8</text>\n" + 
			"  <text x=\"80\" y=\"415\">0.6</text>\n" + 
			"  <text x=\"80\" y=\"615\">0.4</text>\n" + 
			"  <text x=\"80\" y=\"815\">0.2</text>\n" + 
			"  <text x=\"80\" y=\"1015\">0.0</text>\n" + 
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

		CladeSet cladeSet1 = getCladeSet(src1Input.get().getPath());
		double n1 = n;

		CladeSet cladeSet2 = getCladeSet(src2Input.get().getPath());		
		double n2 = n;
		
		// create map of clades to support values in set1
		Map<String, Double> cladeMap = new LinkedHashMap<>();
		for (int i = 0; i < cladeSet1.getCladeCount(); i++) {
			String clade = cladeSet1.getClade(i);
			int support = cladeSet1.getFrequency(i);
			cladeMap.put(clade, support/ n1);
		}
		
		// process clades in set2
		for (int i = 0; i < cladeSet2.getCladeCount(); i++) {			
			String clade = cladeSet2.getClade(i);
			int support = cladeSet2.getFrequency(i);
			if (cladeMap.containsKey(clade)) {
				// clade is also in set1
				output(out, svg, clade,cladeMap.get(clade),support/n2);
				cladeMap.remove(clade);
			} else {
				// clade is not in set1
				output(out, svg, clade, 0.0, support/n2);
			}
		}		
		
		// process left-overs of clades in set1 that are not in set2 
		for (String clade : cladeMap.keySet()) {
			output(out, svg, clade, cladeMap.get(clade), 0.0);
		}

		if (svg != null) {
			svg.println(footer);
		}
		Log.info.println("Done");
	}

	private void output(PrintStream out, PrintStream svg, String clade, Double support1, double support2) {
		out.println(clade.replaceAll(" ", "") + " " + support1 + " " + support2);
		if ((support1 < 0.1 && support2 > 0.9) ||
			(support2 < 0.1 && support1 > 0.9)) {
			Log.warning("Problem clade: " + clade.replaceAll(" ", "") + " " + support1 + " " + support2);
		}
		
		if (svg != null) {
			svg.println("  <circle style=\"opacity:0.25;fill:#a00000\" cx=\""+ (90 +1000* support1 + Randomizer.nextInt(10) - 5) + 
					"\" cy=\""+ (10 + 1000 - 1000 * support2 + Randomizer.nextInt(10) - 5) +"\" "
							+ "data-value=\"7.2\" r=\"" + (support1 + support2) * 10 + "\"></circle>");
		}
	}

	private CladeSet getCladeSet(String path) throws IOException {
		MemoryFriendlyTreeSet srcTreeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(path, burnInPercentageInput.get());
		srcTreeSet.reset();
		Tree tree = srcTreeSet.next();
		CladeSet cladeSet1 = new CladeSet(tree);
		n = 1;
		while (srcTreeSet.hasNext()) {
			tree = srcTreeSet.next();
			cladeSet1.add(tree);
			n++;
		}
		return cladeSet1;
	}

	public static void main(String[] args) throws Exception {
		new Application(new CladeSetComparator(), "Clade Set Comparator", args);

	}

}
