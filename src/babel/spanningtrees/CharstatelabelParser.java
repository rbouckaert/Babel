package babel.spanningtrees;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Jakob Runge
 * This class aims to parse the `charstatelabels` block in a nexus file.
 */
public class CharstatelabelParser {

	private static final String blockName = "charstatelabels";
	private static final Pattern cognateMarker = Pattern.compile(" *(\\d+) ([^_]+)_cognate_([^_]+)");
	private static final Pattern lexemeMarker = Pattern.compile(" *(\\d+) ([^_]+)_lexeme_([^_]+)");
	private static final Pattern groupMarker = Pattern.compile(" *(\\d+) ([^_]+)_group");

	public List<Charstatelabel> labels = new ArrayList<>();
	public Map<String, List<Charstatelabel>> meaningLabelMap = new HashMap<>();
	public Map<String, List<Charstatelabel>> meaningCognateMap = new HashMap<>();

	protected void putLabel(Map<String, List<Charstatelabel>> map, Charstatelabel label){
		List<Charstatelabel> labelList = map.get(label.meaning);
		if(labelList == null){
			labelList = new ArrayList<>();
			map.put(label.meaning, labelList);
		}
		labelList.add(label);
	}
	
	protected void addLabel(Charstatelabel label) {
		this.labels.add(label);
		//Add label to meaningLabelMap:
		putLabel(this.meaningLabelMap, label);
		//Add label to meaningCognateMap:
		if(label.type == Charstatelabel.typeCognate){
			putLabel(this.meaningCognateMap, label);
		}
	}

	public static CharstatelabelParser parseNexus(NexusBlockParser nexus) {
		CharstatelabelParser parser = new CharstatelabelParser();
		if (nexus.hasBlock(CharstatelabelParser.blockName)) {
			for (String line : nexus.getBlock(CharstatelabelParser.blockName)) {
				Charstatelabel label = new Charstatelabel();
				// Dissecting the entry:
				Matcher cognateMatcher = CharstatelabelParser.cognateMarker.matcher(line);
				Matcher lexemeMatcher = CharstatelabelParser.lexemeMarker.matcher(line);
				Matcher groupMatcher = CharstatelabelParser.groupMarker.matcher(line);
				if (cognateMatcher.matches()) {
					label.index = Integer.parseInt(cognateMatcher.group(1));
					label.meaning = cognateMatcher.group(2);
					label.type = Charstatelabel.typeCognate;
					label.labelId = Integer.parseInt(cognateMatcher.group(3));
				} else if (lexemeMatcher.matches()) {
					label.index = Integer.parseInt(lexemeMatcher.group(1));
					label.meaning = lexemeMatcher.group(2);
					label.type = Charstatelabel.typeLexeme;
					label.labelId = Integer.parseInt(lexemeMatcher.group(3));
				} else if (groupMatcher.matches()) {
					label.index = Integer.parseInt(groupMatcher.group(1));
					label.meaning = groupMatcher.group(2);
					label.type = Charstatelabel.typeGroup;
				} else {
					continue; // Skipping lines we can't identify
				}
				// Adding label:
				parser.addLabel(label);
			}
		}
		return parser;
	}

	public static void main(String[] args) {
		String testFile = "./examples/x/2016-09-13_CoBL-IE_Lgs101_Mgs172_Current_Jena200_BEAUti.nex";
		try {
			NexusBlockParser nexus = NexusBlockParser.parseFile(testFile);
			CharstatelabelParser charstatelabels = CharstatelabelParser.parseNexus(nexus);
			//Print prove of work:
			for(String meaning : charstatelabels.meaningCognateMap.keySet()){
				System.out.println("Meaning "+meaning+":");
				for(Charstatelabel label : charstatelabels.meaningCognateMap.get(meaning)){
					System.out.println("  "+label.labelId);
				}
			}
			// FIXME IMPLEMENT
		} catch (FileNotFoundException e) {
			System.err.println("LocationParser.main could not read the test file.");
			e.printStackTrace();
		}
	}

}
