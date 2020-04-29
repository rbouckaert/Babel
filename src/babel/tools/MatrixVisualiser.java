package babel.tools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import beast.core.Description;
import beast.core.Runnable;

@Description("visualises rate matrix as a graph with nodes on a circle")
public abstract class MatrixVisualiser extends Runnable {

	String [] colour = new String[]{
			"f7eb00",
			"f02c25",
			"175bad",
			"672e94",
			"008c43",
			"e27db3",
			"f57c00",
			"a31c1b"};


	public abstract double [][] getRates();
	public abstract String[] getLabels(double[][] rates);

	
	@Override
	public void initAndValidate() {
	}

	
	@Override
	public void run() throws Exception {

		double[][] rates = getRates();
		String[] labels = getLabels(rates);
		String svg = getSVG(rates, labels);
		
		try {
			File tmpFile0 = new File("/tmp/matrix.svg");
			FileWriter outfile = new FileWriter(tmpFile0);
			outfile.write(svg);
			outfile.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.err.println("Done");
	}
	
	String getSVG(double [][] rates, String [] label) {
		int w = 500;
		int h = 500;
		int n = rates.length;
		
		// normalise
		double max = rates[0][0];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				max = Math.max(max, rates[i][j]);
			}
		}
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				rates[i][j] /= max;
			}
		}

		double[] x = new double[n];
		double[] y = new double[n];
		for (int i = 0; i < n; i++) {
			x[i] = 10+(w-40) / 2.0 * (1.0 + Math.cos(i * Math.PI * 2.0 / n));
			y[i] = 10+(h-40) / 2.0 * (1.0 + Math.sin(i * Math.PI * 2.0 / n));
		}

		String svg = "<svg width='" + w*2 + "' height='" + h
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
				
				if (i != j) {
					double width = 10 * rates[i][j];
					if (width < 0.25) {
						width = 0.25;
					}
					svg += "  <path marker-end='url(#head"+i+")' stroke-width='" + width
							+ "' fill='none' stroke='#" + colour[i] + "' d='M" + start + " Q" + middle + " " + end + "'></path>  \n";
				}
			}
		}
		for (int i = 0; i < n; i++) {
			svg += "   <circle cx='" + x[i] + "' cy='" + y[i] + "' r='20' stroke='#" + colour[i]
					+ "' stroke-width='4' fill='url(#grad"+i+")' />\n";
		}
		for (int i = 0; i < n; i++) {
			svg += "	<text x='"+w+"' y='" + (i * 30 + 30) + "' font-family='Verdana' font-size='35' fill='#" + colour[i]
					+ "'>" + label[i] + "</text>\n";
		}
		svg += "</svg> \n";
		return svg;
	}
		
	public static void main(String[] args) throws Exception {
		MatrixVisualiser s = new MatrixVisualiser() {
			private double [][] rates5x5 = new double[][] {
				{0.0, 1.0, 2.0,1.0,3.0},
				{0.5, 0.0, 1.5, 0.1, 0.1},
				{0.5, 1.5, 0.0, 0.1, 0.1},
				{0.5, 1.5, 1.5, 0.0, 0.1},
				{0.5, 3.0, 1.5, 0.1, 0.0}
			};
			private double [][] rates4x4 = new double[][] {
				{0.0, 1.0, 2.0,1.0},
		        {0.5, 0.0, 1.5, 0.1},
		        {0.5, 1.5, 0.0, 0.1},
		        {0.5, 1.5, 1.5, 0.0}
			};
			private double [][] rates3x3 = new double[][] {
				{0.0, 1.0, 2.0},
			    {0.5, 0.0, 1.5},
			    {0.5, 1.5, 0.0}
			};
			
			public double [][] getRates() {
				return rates3x3;
			}
			
			public String[] getLabels(double[][] rates) {
				String [] labels = new String[rates.length];
				for (int i = 0; i < labels.length; i++) {
					labels[i] = "label" + i;
				}
				return labels;
			}
		};
		s.run();
	}

}
