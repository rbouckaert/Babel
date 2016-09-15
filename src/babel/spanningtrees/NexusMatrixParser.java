package babel.spanningtrees;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Jakob Runge
 * The NexusMatrixParser parses the matrix from a `characters` block in a Nexus file.
 */
public class NexusMatrixParser {
	private static final String blockName = "characters";
	private static final String nexusComment = "[ \t]*\\[.*\\][ \t]*";
	private static final String matrixMarker = "[ \t]*matrix[ \t]*";
	// matrixLine matches a String like "  'foobar'  1001???11".
	private static final Pattern matrixLine = Pattern.compile("[ \t]*'([^']+)'[ \t]*([01\\?]*)[ \t]*");

	// languageStatusCodes maps language names to the status lines of the matrix.
	public Map<String, String> languageStatusCodes = new HashMap<>();

	public static NexusMatrixParser parseNexus(NexusParser nexus) {
		NexusMatrixParser parser = new NexusMatrixParser();
		/*
		 * The matrix will be parsed in several stages:
		 * 1: Consider only the wanted block.
		 * 2: Ignore all comment lines.
		 * 3: Ignore all lines until the matrix directive.
		 * 4: Parse matrix lines to produce a map from language names to status codes.
		 */
		if (nexus.hasBlock(NexusMatrixParser.blockName)) {
			boolean foundMatrix = false;
			for (String line : nexus.getBlock(NexusMatrixParser.blockName)) {
				if (line.matches(NexusMatrixParser.nexusComment)) {
					continue; // Ignore comments
				}
				if (!foundMatrix) { // Ignore until matrix
					if (line.matches(NexusMatrixParser.matrixMarker)) {
						foundMatrix = true;
					}
				} else {
					Matcher matcher = NexusMatrixParser.matrixLine.matcher(line);
					if(matcher.matches()){
						parser.languageStatusCodes.put(matcher.group(1), matcher.group(2));
					}
				}
			}
		}
		return parser;
	}
	
	public static void main(String[] args) {
		String testFile = "./examples/x/2016-09-13_CoBL-IE_Lgs101_Mgs172_Current_Jena200_BEAUti.nex";
		try {
			NexusParser nexus = NexusParser.parseFile(testFile);
			NexusMatrixParser matrix = NexusMatrixParser.parseNexus(nexus);
			System.out.println("Matrix parsed. Found lines for:");
			for (String name : matrix.languageStatusCodes.keySet()) {
				System.out.println("\t" + name);
			}
		} catch (FileNotFoundException e) {
			System.err.println("NexusMatrixParser.main could not read the test file.");
			e.printStackTrace();
		}
	}
}
