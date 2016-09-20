package babel.spanningtrees;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Jakob Runge
 * This class aims to parse the `charstatelabels` block in a nexus file.
 */
public class CharstatelabelParser {

	private static final String blockName = "charstatelabels";
	private static final Pattern cognateMarker = Pattern.compile(" *(\\d+) ([^_]+)_cognate_([^_]+)");
	private static final Pattern lexemeMarker = Pattern.compile(" *(\\d+) ([^_]+)_lexeme_([^_]+)");
	private static final Pattern groupMarker = Pattern.compile(" *(\\d+) (.+)_group");
	private static final Pattern cognateFileMarker = Pattern.compile(" *(\\d+) (.+)_(\\d+)");

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

	protected Optional<Charstatelabel> parseCharstateLabel(String line){
		Charstatelabel label = new Charstatelabel();
		Matcher cognateMatcher = CharstatelabelParser.cognateMarker.matcher(line);
		Matcher lexemeMatcher = CharstatelabelParser.lexemeMarker.matcher(line);
		Matcher groupMatcher = CharstatelabelParser.groupMarker.matcher(line);
		Matcher cognateFileMatcher = CharstatelabelParser.cognateFileMarker.matcher(line);
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
		} else if(cognateFileMatcher.matches()){
			label.index = Integer.parseInt(cognateFileMatcher.group(1));
			label.meaning = cognateFileMatcher.group(2);
			label.type = Charstatelabel.typeCognate;
			label.labelId = Integer.parseInt(cognateFileMatcher.group(3));
		} else {
			return Optional.empty();
		}
		return Optional.of(label);
	}

	public static CharstatelabelParser parseNexus(NexusBlockParser nexus) {
		CharstatelabelParser parser = new CharstatelabelParser();
		if (nexus.hasBlock(CharstatelabelParser.blockName)) {
			for (String line : nexus.getBlock(CharstatelabelParser.blockName)) {
				parser.parseCharstateLabel(line).ifPresent(label -> parser.addLabel(label));
			}
		}
		return parser;
	}

	public static CharstatelabelParser parseCognateFile(File file) throws IOException{
		CharstatelabelParser parser = new CharstatelabelParser();
		BufferedReader fdata = new BufferedReader(new FileReader(file));
		for(String line : fdata.lines().collect(Collectors.toList())){
			//Some lines end with a ',', which we don't need:
			if(line.charAt(line.length() - 1) == ','){
				line = line.substring(0, line.length() - 1);
			}
			parser.parseCharstateLabel(line).ifPresent(label -> parser.addLabel(label));
		}
		fdata.close();
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
		} catch (FileNotFoundException e) {
			System.err.println("LocationParser.main could not read the test file.");
			e.printStackTrace();
		}
	}

}
