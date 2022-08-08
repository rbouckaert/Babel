
package babel.tools;


import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
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

import beastfx.app.treeannotator.TreeAnnotator;
import beastfx.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Log;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;


@Description("Match clades from two or more tree sets and print the difference in clade probability throughout the MCMC chains")
public class CladeSetOverStateComparator extends Runnable {
	final public Input<List<TreeFile>> srcInput = new Input<>("tree","2 or more source tree (set or MCC tree) files", new ArrayList<>());
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));
	
	
	
	int nfiles = 0;
	
	@Override
	public void initAndValidate() {
		
		nfiles = srcInput.get().size();
		if (nfiles < 2) throw new IllegalArgumentException("Please provide at least 2 tree files");
		
		
	}

	
	@Override
	public void run() throws Exception {
		
		
		// Print to output
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			String str = outputInput.get().getPath();			
			Log.warning("Writing to file " + str);
			out = new PrintStream(str);
		}
		
		

		
		// List of tree readers
		List<MemoryFriendlyTreeSet> treeSets = new ArrayList<MemoryFriendlyTreeSet>();
		for (TreeFile treeFile : srcInput.get()) {
			MemoryFriendlyTreeSet treeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(treeFile.getAbsolutePath(), 0);
			treeSet.reset();
			treeSets.add(treeSet);
		}
		
		
		// IO
		out.println("sample\tmax.clade.diff\tmean.clade.diff");
	
		
		// Iterate through the cumulative states
		while (hasNext(treeSets)) {
			
			
			
			// TODO: create new class like mmeory friendly tree set except it 
			
			
			
			
			
			
		}
		
	
		
	}
	
	
	/**
	 * Check that all treesets have another tree
	 * @param treeSets
	 * @return
	 */
	private boolean hasNext(List<MemoryFriendlyTreeSet> treeSets) {
		
		
		for (MemoryFriendlyTreeSet ts : treeSets) {
			if (!ts.hasNext()) return false;
		}
		return true;
		
	}
	
	
	public static void main(String[] args) throws Exception {
		new Application(new CladeSetOverStateComparator(), "Clade Set Over State Comparator", args);

	}

	
}
