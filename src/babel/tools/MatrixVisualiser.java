package babel.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import beastfx.app.inputeditor.BeautiDoc;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beast.base.core.Description;
import beast.base.core.Input;

@Description("Visualises rate matrix as a graph with nodes on a circle")
public class MatrixVisualiser extends MatrixVisualiserBase {
	public Input<File> inFile = new Input<>("in","tsv file containing matrix. First line contain labels, next lines the matrix (numbers only). "
			+ "Use # at start of line for comments, empy lines are ignored", new File("[[none]]"));
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("/tmp/matrix.svg"));

	String [] labels;

	public MatrixVisualiser() {
	}
	
	public double [][] getMatrix() {
		try {
			String str = BeautiDoc.load(inFile.get());
			List<String> strs  = new ArrayList<>();
			for (String str2 : str.split("\n")) {
				if (str2.trim().length() != 0 && !str2.startsWith("#")) {
					strs.add(str2);							
				}
			}
			if (strs.size() == 0) {
				throw new IllegalArgumentException("Could not find any info in the input file");
			}
			labels = strs.get(0).split("\t");
			int n = labels.length;
			double [][] matrix = new double[n][n];
			if (strs.size() != n+1) {
				throw new IllegalArgumentException("Number of labels ("+ n + ") does not match number of lines in matrix ("+(strs.size()-1)+")");
			}
			for (int i = 0; i < n; i++) {
				String [] strs2 = strs.get(i+1).split("\t");
				if (strs2.length != n) {
					throw new IllegalArgumentException("At row " + (i+1) + " the number of entries in matrix ("+strs2.length+") does not match number number of labels ("+ n + ")");
				}
				for (int j = 0; j < n; j++) {
					matrix[i][j] = Double.parseDouble(strs2[j]);
				}
			}
			return matrix;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public String[] getLabels(double[][] rates) {
		return labels;
	}
	
	@Override
	public String getFileName() {
		return outputInput.get().getPath();
	}
	
	public static void main(String[] args) throws Exception {
		new Application(new MatrixVisualiser(), "Matrix visualiser", args);
	}
}
