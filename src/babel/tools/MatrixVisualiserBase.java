package babel.tools;


import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;

import beast.core.Description;
import beast.core.Runnable;
import beast.core.util.Log;

@Description(value="Visualises rate matrix as a graph with nodes on a circle", isInheritable=false)
/** Do not use directly. Use MatrixVisualiser tool (or any other derived class), 
 * which has Application support **/
public class MatrixVisualiserBase extends Runnable {

	String [] colour = new String[]{
			"f7eb00",
			"f02c25",
			"175bad",
			"672e94",
			"008c43",
			"e27db3",
			"f57c00",
			"a31c1b",

			"2c25f0",
			"5bad17",
			"2e9467",
			"8c4320",
			"7db3e2",
			"7c20f5",
			"1c1ba3",

			"25f02c",
			"ad175b",
			"94672e",
			"43208c",
			"b3e27d",
			"20f57c",
			"1ba31c",
			
			"f0252c",
			"17ad5b",
			"67942e",
			"20438c",
			"e2b37d",
			"f5007c",
			"a31b1c"
			
	};


	public double [][] getMatrix() {
		return new double[][] {
			{0.0, 1.0, 2.0},
		    {0.5, 0.0, 1.5},
		    {0.5, 1.5, 0.0}
		};
//		return = new double[][] {
//			{0.0, 1.0, 2.0,1.0,3.0},
//			{0.5, 0.0, 1.5, 0.1, 0.1},
//			{0.5, 1.5, 0.0, 0.1, 0.1},
//			{0.5, 1.5, 1.5, 0.0, 0.1},
//			{0.5, 3.0, 1.5, 0.1, 0.0}
//		};
//		return matrix4x4 = new double[][] {
//			{0.0, 1.0, 2.0,1.0},
//	        {0.5, 0.0, 1.5, 0.1},
//	        {0.5, 1.5, 0.0, 0.1},
//	        {0.5, 1.5, 1.5, 0.0}
//		};

	}
	public String[] getLabels(double[][] rates) {
		String [] labels = new String[rates.length];
		for (int i = 0; i < labels.length; i++) {
			labels[i] = "state " + i;
		}
		return labels;		
	}

	
	@Override
	public void initAndValidate() {
	}

	
	@Override
	public void run() throws Exception {

		double[][] matrix = getMatrix();
		String[] labels = getLabels(matrix);
		String svg = getSVG(matrix, labels);
		
		try {
			File tmpFile0 = new File(getFileName());
			Log.warning("Writing to file " + tmpFile0.getPath());
			FileWriter outfile = new FileWriter(tmpFile0);
			outfile.write(svg);
			outfile.close();

			File tmpFile1 = tmpFile0.getName().toLowerCase().endsWith("svg") ?
					new File(tmpFile0.getPath().replaceAll("svg", "tsv")) :
						new File(tmpFile0.getPath()+".tsv");						
			Log.warning("Writing to file " + tmpFile1.getPath());
			outfile = new FileWriter(tmpFile1);
			outfile.write(matrixToString(labels, matrix));
			outfile.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.err.println("Done");
	}
	
	private String matrixToString(String[] labels, double[][] matrix) {
		StringBuilder b = new StringBuilder();
		for (String label : labels) {
			b.append(label).append('\t');
		}
		b.append('\n');
		for (int i = 0; i < labels.length; i++) {
			for (int j = 0; j < labels.length; j++) {
				b.append(matrix[i][j]).append('\t');
			}
			b.append('\n');
		}
		return b.toString();
	}
	
	public String getFileName() {		
		return "/tmp/matrix.svg";
	}
	
	String getSVG(double [][] matrix, String [] label) {
		return getSVG(matrix, label, -1);		
	}
	
	String getSVG(double [][] matrix, String [] label, double scale) {
		int w = 500;
		int h = 500;
		int n = matrix.length;
		if (n > colour.length) {
			String [] tmp = new String[n];
			for (int i = 0; i < n; i++) {
				tmp[i] = colour[i % colour.length];
			}
			colour = tmp;
		}
		if (label.length != n) {
			throw new IllegalArgumentException("number of labels should be same as size of matrix (" + n + ")");
		}
		
		// normalise
		double max = Double.NEGATIVE_INFINITY;
		double min = Double.POSITIVE_INFINITY;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (i != j) {
					max = Math.max(max, matrix[i][j]);
					min = Math.min(min, matrix[i][j]);
				}
			}
		}

		if (scale <= 0) {
			scale = 10/max;
		}

		double[] x = new double[n];
		double[] y = new double[n];
		for (int i = 0; i < n; i++) {
			x[i] = 10+(w-40) / 2.0 * (1.0 + Math.cos(i * Math.PI * 2.0 / n));
			y[i] = 10+(h-40) / 2.0 * (1.0 + Math.sin(i * Math.PI * 2.0 / n));
		}

        final DecimalFormat formatter = new DecimalFormat("#.##");
		String svg = "<svg width='" + w*2 + "' height='" + h+50
				+ "'  xmlns:xlink='http://www.w3.org/1999/xlink' xmlns='http://www.w3.org/2000/svg'>\n"
				+ " <defs>\n";
		for (int i = 0; i < n; i++) {
			svg += "    <radialGradient id='grad"+i+"' cx='50%' cy='50%' r='50%' fx='50%' fy='50%'>\n"
				+ "      <stop offset='0%' style='stop-color:rgb(255,255,255);\n"
				+ "      stop-opacity:0'></stop>\n"
				+ "      <stop offset='100%' style='stop-color:#"+colour[i]+";stop-opacity:1'></stop>\n"
				+ "    </radialGradient>\n" + "\n";
			svg += "    <marker id='head"+i+"' viewBox='0 0 20 20' refX='20' refY='10' markerUnits='strokeWidth' markerWidth='8' markerHeight='6' orient='auto'>\n"
			+ "        <path d='M 0 0 L 20 10 L 0 20 z' fill='#"+colour[i]+"'/>\n" 
			+ "    </marker>\n";
		}
		svg += " </defs>    \n";
		svg += "<g transform='translate("+ w/2+",50)'>\n";
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				double len = Math.sqrt((x[i]-x[j])*(x[i]-x[j])+(y[i]-y[j])*(y[i]-y[j]));
				double a = 20/len;
				double b = 20 /len;
				double c= 40/len;
				String start = (x[i] - a * (x[i] - x[j])) + "," + 
							   (y[i] - a * (y[i] - y[j]));
				
				double midx = (x[i] + x[j]) / 2; 
				double midy = (y[i] + y[j]) / 2;
				String middle = (midx + c * (y[i] - y[j])) + "," + 
						        (midy - c * (x[i] - x[j])); 
				String end = (x[j] + b * (x[i] - x[j])) + "," + 
						     (y[j] + b * (y[i] - y[j]));
				
				if (i != j && matrix[i][j] != 0) {
					double width = scale * matrix[i][j];
					if (width < 0.25) {
//						width = 0.25;
					}
					svg += "  <path marker-end='url(#head"+i+")' stroke-width='" + width
							+ "' fill='none' "
//							+ "stroke='url(#grad"+i+")' "
							+ "stroke='#"+colour[i]+"' "
							+ "d='M" + start + " Q" + middle + " " + end + "'></path>  \n";
					svg +=  "<text x='"+(midx + 0.8*c * (y[i] - y[j]) - 10)+"' "
							+ "y='"+(midy - 0.8*c * (x[i] - x[j]))+"' font-family='Verdana' font-size='10' fill='#" + colour[i] + "'>" + formatter.format(matrix[i][j])  + "</text>\n";
				}
			}
		}
		for (int i = 0; i < n; i++) {
			svg += "   <circle cx='" + x[i] + "' cy='" + y[i] + "' r='20' stroke='#" + colour[i]
					+ "' stroke-width='4' fill='url(#grad"+i+")' />\n";
		}
		
		AffineTransform affinetransform = new AffineTransform();     
		FontRenderContext frc = new FontRenderContext(affinetransform,true,true);     
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2.0 / n;
			Font font = new Font("Verdana", Font.PLAIN, 28);
			double textWidth = 30 + font.getStringBounds(label[i], frc).getWidth();
			double x1 = a <= Math.PI/2.0 || a >= 1.5*Math.PI ? x[i] + 30 : x[i]-textWidth;
			double y1 = y[i]+10;
			svg += "	<text x='"+x1+"' y='" + y1 + "' font-family='Verdana' font-size='28' fill='#" + colour[i]
					+ "'>" + label[i].replaceAll("_", " ") + "</text>\n";
		}
		svg += "</g>\n";
		//svg += "	<text x='5' y='15' font-family='Verdana' font-size='10' fill='#000'>max (biggest arrow): " + formatter.format(max)  + "</text>\n";
		//svg += "	<text x='5' y='30' font-family='Verdana' font-size='10' fill='#000'>min (thinnest arrow): " + formatter.format(min)  + "</text>\n";

		svg += "</svg> \n";
		return svg;
	}

}
