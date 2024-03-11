package babel.tools;

import java.io.File;
import java.io.IOException;
import java.util.List;

import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Input.Validate;
import beast.base.core.Log;
import beastfx.app.tools.LogAnalyser;

@Description("Create SVG files to visualise rates for a discrete rate analysis")
public class DTARatesVisualiser extends MatrixVisualiserBase {
	final public Input<File> inFile = new Input<>("in", "trace file containing substitution matrix rates", new File("[[none]]"));
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trees to used as burn-in (and will be ignored)", 10);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("/tmp/matrix.svg"));
	final public Input<String> labelInput = new Input<>("labels","comma separated list of location labels", Validate.REQUIRED);
	final public Input<String> prefixInput = new Input<>("prefix","prefix of rate matrix entry, e.g., geoSubstModelLogger, "
			+ "which picks up entries geoSubstModelLogger.relGeoRate_XXX_YYY for labels XXX and YYY from labels input. "
			+ "Attempt is made to identify them automatically if not specified.");
	final public Input<String> separatorInput = new Input<>("separator","label separator in trace entry names", "_");


	String [] labels;
	String separator;
	
	public DTARatesVisualiser() {
		arrowThresholdInput.defaultValue = 0.0;
		rateThresholdInput.defaultValue = 0.0;
	}
	
	@Override
	public double [][] getMatrix() {
		try {
			Log.warning("Processing " + inFile.get().getPath());
			LogAnalyser tracelog = new LogAnalyser(inFile.get().getPath(), burnInPercentageInput.get(), true, false);
			labels = labelInput.get().split(",");
			int n = labels.length;
			
			String prefix = getPrefix(tracelog);
			double [][] rates = new double[n][n];
			List<String> traceLabels = tracelog.getLabels();
			for (int i = 0; i < n; i++) {
				for (int  j = 0; j < n; j++) {
					if (i != j) {
						String label = getLabelx(traceLabels, prefix, labels[i], labels[j]);
						Double [] trace = tracelog.getTrace(label);
						double mean = mean(trace);
						rates[i][j] = mean;
					}
				}
			}
			return rates;
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	private String getLabelx(List<String> traceLabels, String prefix, String string1, String string2) {
		separator = separatorInput.get();
		String label = prefix + string1 + separator + string2;
		for (int i = 0; i < traceLabels.size(); i++) {
			if (traceLabels.get(i).equals(label)) {
				return label;
			}
		}
		
		// it may be a symmetric matrix, try to flip labels
		label = prefix + string2 + separator + string1;
		for (int i = 0; i < traceLabels.size(); i++) {
			if (traceLabels.get(i).equals(label)) {
				return label;
			}
		}

		// no luck, give up
		throw new IllegalArgumentException("Could not find entry for " + label);
	}

	private String getPrefix(LogAnalyser tracelog) {
		if (prefixInput.get() != null) {
			return prefixInput.get();
			// return prefixInput.get() + ".relGeoRate_";
		}
		String prefix = null;
		for (String label : tracelog.getLabels()) {
			if (label.contains(".relGeoRate_")) {
				prefix = label.substring(0, label.indexOf(".relGeoRate_") + 12);
				break;
			}
		}
		if (prefix == null) {
			throw new IllegalArgumentException("trace file does not appear to contain rate matrix (items with '.relGeoRate_' in them");
		}
		return prefix;		
	}

	private double mean(Double[] trace) {
		double sum = 0;
		for (double d : trace) {
			sum += d;
		}
		return sum / trace.length;
	}

	@Override
	public String[] getLabels(double[][] rates) {
		return labelInput.get().split(",");
	}

	
	@Override
	public String getFileName() {
		return outputInput.get().getPath();
	}

	
	public static void main(String[] args) throws Exception {
		new Application(new DTARatesVisualiser(), "DTARatesVisualiser", args);
	}

}
