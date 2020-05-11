package babel.tools;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.imageio.ImageIO;

import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;

import beast.app.beauti.BeautiDoc;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.math.distributions.ParametricDistribution;

@Description("Creates time series plot for value + 95%HPD for one or more time series")
public class TimeSeriesPlot extends Runnable {
	final public Input<File> dataFileInput = new Input<>("in","tsv file containing data in rows, "
			+ "first column is time (in days) "
			+ "second column mean, third and fourth the 95%HPD values ");
	final public Input<OutFile> outputInput = new Input<>("out", "png or pdf output file.",
			new OutFile("[[none]]"));

	String [] labels;
	double [][] data;
	double max;
    int m_nTicks;

    
    final static int[] NR_OF_TICKS = new int[]{5, 10, 8, 6, 8, 10, 6, 7, 8, 9, 10};

    final static int WIDTH = 1200;
    final static int HEIGHT = 1200;
    
    final static Color [] colour = new Color[]{Color.blue, Color.red, Color.green};
    // the length in pixels of a tick
    private static final int TICK_LENGTH = 5;

    // the right margin
    private static final int RIGHT_MARGIN = 20;

    // the margin to the left of y-labels
    private static final int MARGIN_LEFT_OF_Y_LABELS = 5;

    // the top margin
    private static final int TOP_MARGIN = 10;

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		loadData();
		max = maxData();
		
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			if (outputInput.get().getPath().toLowerCase().endsWith("png")) {
				drawPNG(outputInput.get().getPath());
			} else if (outputInput.get().getPath().toLowerCase().endsWith("pdf")) {
				drawPDF(outputInput.get().getPath());
			} else {
				throw new IllegalArgumentException("Unrecognised extension of output file: should be png or pdf");
			}
		}
		Log.warning("Done");
	}
	
	private double maxData() {
		double max = Double.NEGATIVE_INFINITY;
		for (int i = 1; i < data.length; i++) {
			for (double d : data[i]) {
				max = Math.max(d, max);
			}
		}
		return max;
	}
	
	public void draw(String path, double [][] data) throws IOException, DocumentException {
		this.data = data;
		if (data.length % 3 != 1) {
			throw new IllegalArgumentException("Expected the number of columns to be 1 + 3n, but found " + data.length);
		}
		max = maxData();
		if (path.toLowerCase().endsWith("png")) {
			drawPNG(path);
		} else if (path.endsWith("pdf")) {
			drawPDF(path);
		} else {
			throw new IllegalArgumentException("Unrecognised extension of output file: should be png or pdf");
		}
	}
	

	
	private void drawPDF(String path) throws IOException, DocumentException {
		com.itextpdf.text.Document doc = new com.itextpdf.text.Document();
		Log.warning("Writing to file " + path);
		PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(path));
		doc.setPageSize(new com.itextpdf.text.Rectangle(WIDTH, HEIGHT));
		doc.open();
		PdfContentByte cb = writer.getDirectContent();
		Graphics2D g = new PdfGraphics2D(cb, WIDTH, HEIGHT);
		 
		g.setPaintMode();
		initDrawing(g);
		drawGraph(null, 0, g);
		
		g.dispose();
		doc.close();
	}
	
	private void drawPNG(String path) throws IOException {
		BufferedImage bi;
		Graphics2D g;
		bi = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		g = (Graphics2D) bi.getGraphics();
		initDrawing(g);
		drawGraph(null, 0, g);
		
		Log.warning("Writing to file " + path);
		ImageIO.write(bi, "png", new File(path));
	}
	
	private void initDrawing(Graphics2D g) {
		g.setColor(Color.black);
		g.setFont(new Font("Arial",Font.PLAIN, 50));
//		g.drawString("Time", 120, 1170);

		AffineTransform orig = g.getTransform();
		g.rotate(-Math.PI/2);
//		g.drawString("R", -880, 40);
		g.setTransform(orig);

		g.setColor(Color.red);
		g.setComposite(AlphaComposite.SrcOver.derive(0.25f));	
	}

	void loadData() {
		try {
			Log.warning("Loading " + dataFileInput.get().getPath());
			String [] strs = BeautiDoc.load(dataFileInput.get()).split("\n");
			labels = strs[0].split("\t");
			int n = labels.length;
			if (n % 3 != 1) {
				throw new IllegalArgumentException("Expected the number of columns to be 1 + 3n, but found " + n);
			}
			int m = strs.length-1;
			data = new double[n][m];
			for (int i = 0; i < m; i++) {
				String [] strs2 = strs[i+1].split("\t");
				for (int j = 0; j < n; j++) {
					data[j][i] = Double.parseDouble(strs2[j]);
				}
			}
			
 		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalArgumentException(e);
		}
	}

    final int F = 10;

	
    private void drawGraph(ParametricDistribution distr, int labelOffset, Graphics2D g) {
        final int width = WIDTH;//(int) g.getDeviceConfiguration().getBounds().getWidth();
        final int height = HEIGHT;//(int) g.getDeviceConfiguration().getBounds().getHeight();

        double minValue = 0;
        double maxValue = max;
        try {
            minValue = data[0][0];
            maxValue = data[0][data[0].length - 1];
        } catch (Exception e) {
            // use defaults
        }
        double xRange = maxValue - minValue;
        // adjust yMax so that the ticks come out right
        double x0 = minValue;
        int k = 0;
        double f = xRange;
        double f2 = x0;
        while (f > F) {
            f /= F;
            f2 /= F;
            k++;
        }
        while (f < 1 && f > 0) {
            f *= F;
            f2 *= F;
            k--;
        }
        f = Math.ceil(f);
        f2 = Math.floor(f2);
//		final int NR_OF_TICKS_X = NR_OF_TICKS[(int) f];
        for (int i = 0; i < k; i++) {
            f *= F;
            f2 *= F;
        }
        for (int i = k; i < 0; i++) {
            f /= F;
            f2 /= F;
        }
        //double adjXRange = f;

        xRange = xRange + minValue - f2;
        xRange = adjust(xRange);
        final int NR_OF_TICKS_X = m_nTicks;

        minValue = f2; //xRange = adjXRange;

        int points = data[0].length;
        int[] xPoints = new int[points];
        int[] yPoints = new int[points];
        int[] xPoints2 = new int[points * 2];
        int[] boundsPoints = new int[points * 2];

        double yMax = (int) (max - 1e-200) + 1;
        // yMax = adjust(yMax);
        int NR_OF_TICKS_Y = (int) yMax;
        while (NR_OF_TICKS_Y > 20) {
        	NR_OF_TICKS_Y /= 2;
        }

        // draw ticks on edge
        Font font = g.getFont();
        Font smallFont = new Font(font.getName(), font.getStyle(), font.getSize() * 2/4);
        g.setFont(smallFont);

        // collect the ylabels and the maximum label width in small font
        String[] ylabels = new String[NR_OF_TICKS_Y+1];
        int maxLabelWidth = 0;
        FontMetrics sfm = g.getFontMetrics(smallFont);
        for (int i = 0; i <= NR_OF_TICKS_Y; i++) {
            ylabels[i] = format(yMax * i / NR_OF_TICKS_Y);
            int stringWidth = sfm.stringWidth(ylabels[i]);
            if (stringWidth > maxLabelWidth) maxLabelWidth = stringWidth;
        }

        // collect the xlabels
        String[] xlabels = new String[NR_OF_TICKS_X+1];
        for (int i = 0; i <= NR_OF_TICKS_X; i++) {
            xlabels[i] = format(minValue + xRange * i / NR_OF_TICKS_X);
        }
        int maxLabelHeight = sfm.getMaxAscent()+sfm.getMaxDescent();

        int leftMargin = maxLabelWidth + TICK_LENGTH + 1 + MARGIN_LEFT_OF_Y_LABELS;
        int bottomMargin = maxLabelHeight + TICK_LENGTH + 1;

        int graphWidth = width - leftMargin - RIGHT_MARGIN;
        int graphHeight = height - TOP_MARGIN - bottomMargin - labelOffset;

        // DRAW GRAPH PAPER
        g.setColor(Color.WHITE);
        g.fillRect(leftMargin, TOP_MARGIN, graphWidth, graphHeight);
        g.setColor(Color.BLACK);
        g.drawRect(leftMargin, TOP_MARGIN, graphWidth, graphHeight);

		g.setStroke(new BasicStroke(2.0f));
        for (int i = 0; i <= NR_OF_TICKS_Y; i++) {
            int y = TOP_MARGIN + graphHeight - i * graphHeight / NR_OF_TICKS_Y;
            g.drawLine(leftMargin, y, width - RIGHT_MARGIN, y);
        }

        
        for (int m = 0; m + 1 < data.length; m += 3) {
	        for (int i = 0; i < points; i++) {
	            xPoints[i] = leftMargin + graphWidth * i / points;
	            yPoints[i] = 1 + (int) (TOP_MARGIN + graphHeight - graphHeight * data[1 + m][i] / yMax);
	
	            xPoints2[i] = xPoints[i];
	            boundsPoints[i] = 1 + (int) (TOP_MARGIN + graphHeight - graphHeight * data[2 + m][i] / yMax);
	            xPoints2[2*points - i - 1] = xPoints[i];
	            boundsPoints[2*points - i - 1] = 1 + (int) (TOP_MARGIN + graphHeight - graphHeight * data[3 + m][i] / yMax);
	        }
	        
			g.setColor(colour[(m / 3) % colour.length]);
			g.setComposite(AlphaComposite.SrcOver.derive(0.25f));	
	        g.fillPolygon(xPoints2, boundsPoints, points * 2);

			g.setComposite(AlphaComposite.SrcOver.derive(1f));
			g.setStroke(new BasicStroke(2.0f));
	        g.drawPolygon(xPoints, yPoints, points);
	        
	        if (labels != null) {
	        	g.drawString(labels[m+1].replaceAll("_", " "), WIDTH - 200, TOP_MARGIN + sfm.getHeight() * (1+m/3));
	        }
        }	        
        

		g.setColor(Color.black);
		for (int i = 0; i <= NR_OF_TICKS_X; i++) {
            int x = leftMargin + i * graphWidth / NR_OF_TICKS_X;
            g.drawLine(x, TOP_MARGIN + graphHeight, x, TOP_MARGIN + graphHeight + TICK_LENGTH);
            g.drawString(xlabels[i], x-sfm.stringWidth(xlabels[i])/2, TOP_MARGIN + graphHeight + TICK_LENGTH + 1 + sfm.getMaxAscent());
        }

        // draw the y labels and ticks
        for (int i = 0; i <= NR_OF_TICKS_Y; i++) {
            int y = TOP_MARGIN + graphHeight - i * graphHeight / NR_OF_TICKS_Y;
            g.drawLine(leftMargin - TICK_LENGTH, y, leftMargin, y);
            g.drawString(ylabels[i], leftMargin - TICK_LENGTH - 1 - sfm.stringWidth(ylabels[i]), y + sfm.getHeight()/4);
        }

        
        int fontHeight = font.getSize() * 10 / 12;
        g.setFont(new Font(font.getName(), font.getStyle(), fontHeight));

    }
    
    private String format(double value) {
        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);
        pw.printf("%.3g", value);
        if (value != 0.0 && Math.abs(value) / 1000 < 1e-320) { // 2e-6 = 2 * AbstractContinuousDistribution.solverAbsoluteAccuracy
        	pw.printf("*");
        }
        pw.flush();
        return writer.toString();
    }
    
    private double adjust(double yMax) {
        // adjust yMax so that the ticks come out right
        int k = 0;
        double y = yMax;
        while (y > F) {
            y /= F;
            k++;
        }
        while (y < 1 && y > 0) {
            y *= F;
            k--;
        }
        y = Math.ceil(y);
        m_nTicks = NR_OF_TICKS[(int) y];
        for (int i = 0; i < k; i++) {
            y *= F;
        }
        for (int i = k; i < 0; i++) {
            y /= F;
        }
        return y;
    }
    
    public static void main(String[] args) throws Exception {
		new Application(new TimeSeriesPlot(), "Time series plot", args);
	}
}
