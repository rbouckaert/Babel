package babel.spanningtrees;

/**
 * @author Jakob Runge
 * An instance of this class corresponds to a single entry from the `charstatelabels` block of a nexus file.
 * Entries have forms like these:
 * 1: `3512 yellow_cognate_4910`
 * 2: `1 ant_group`
 * 3: `67 back_lexeme_45349`
 * Such entries would be represented as:
 * 1: {index: 3512, meaning: "yellow", type: Charstatelabel.typeCognate, labelId: 4910}
 * 2: {index: 1, meaning: "ant", type: Charstatelabel.typeGroup, labelId: -1}
 * 3: {index: 67, meaning: "back", type: Charstatelabel.typeLexeme, labelId: 45349} 
 * */
public class Charstatelabel {
	public static final int typeCognate = 0;
	public static final int typeGroup = 1;
	public static final int typeLexeme = 2;
	
	public int index = -1;
	public String meaning = "";
	public int type = -1;
	public int labelId = -1;
}
