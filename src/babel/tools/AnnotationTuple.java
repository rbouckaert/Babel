package babel.tools;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.util.Log;

public class AnnotationTuple extends BEASTObject {
	
	enum Type {
		categorical,
		continuous,
		location,
		date
	}
	
	final public Input<Integer> indexInput = new Input<>("index", "0-counting position within the sequence name (where the name is split using delimiter)", Validate.REQUIRED);
	final public Input<String> delimiterInput = new Input<>("split", "Character to split the sequence names with", Validate.REQUIRED);
	final public Input<String> nameInput = new Input<>("label", "Name of the annotation to appear in the tree", Validate.REQUIRED);
	final public Input<String> typeInput = new Input<>("type", "Type of variable", "categorical");
	final public Input<String> dateFormatInput = new Input<>("dateFormat", "Format of date (if applicable)", "yyyy-M-dd");
	
	
	int index;
	String delimiter;
	String name;
	Type type;
	
	
	public AnnotationTuple() {
		
	}

	@Override
	public void initAndValidate() {
		this.index = indexInput.get();
		this.delimiter = delimiterInput.get();
		this.name = nameInput.get();
		this.type = Type.valueOf(typeInput.get());
		
		
		
	}

	
	
	/***
	 * Splits the sequence name using 'delimiter' and inserts the 'index'th position into a JSON
	 * @param sequenceName
	 * @return
	 */
	public String toJSON(String sequenceName) {
		
		if (sequenceName == null || sequenceName == "") return "";
		String val = this.getValue(sequenceName);
		return name + ":'" + val + "'";

	}
	
	
	/**
	 * Gets the 'index'th value of this string when split over 'delimiter'
	 * @param sequenceName
	 * @return
	 */
	public String getValue(String sequenceName) {
		String[] vals = sequenceName.split("[" + delimiter + "]");
		if (index >= vals.length) {
			throw new ArrayIndexOutOfBoundsException(sequenceName + " does not have an " + index + "th split using delimiter " + delimiter + "!");
		}
		String val = vals[index];
		
		
		// Remove all non ASCII characters
		//val = val.replaceAll("[^\\x00-\\x7F]", "");
		
		
		// Convert date to number
		if (this.type == Type.date) {
			return "" + convertDateToDouble(val);
		}
		
		// Remove all - or .
		val = val.replaceAll("([-]|[.])", "_");
		return val;
	}

	public String getName() {
		return this.name;
	}
	
	public String getType() {
		return this.type.toString();
	}
	
	// Get the type except simplify as continuous/categorical
	public String getTypeSimple() {
		if (this.type == Type.date) return Type.continuous.toString();
		if (this.type == Type.location) return Type.categorical.toString();
		return this.type.toString();
	}


	public boolean isLocation() {
		return this.type == Type.location;
	}
	

	
	public boolean isDate() {
		return this.type == Type.date;
	}
	

    /**
     * See if we can convert the date to a double value *
     * Borrowed from package beast.evolution.tree.TraitSet
     */
    protected double convertDateToDouble(String str) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormatInput.get());
        LocalDate date = LocalDate.parse(str, formatter);

        Log.warning.println("Using format '" + dateFormatInput.get() + "' to parse '" + str +
                "' as: " + (date.getYear() + (date.getDayOfYear()-1.0) / (date.isLeapYear() ? 366.0 : 365.0)));

        double year = date.getYear() + (date.getDayOfYear()-1.0) / (date.isLeapYear() ? 366.0 : 365.0);

        return year;
        
    }



	
	
}
