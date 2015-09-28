package babel.spanningtrees;

import java.util.ArrayList;
import java.util.List;

public class Cognate {
	int GlossID;
	int MultistateCode;
	List<String> languages = new ArrayList<String>();
	List<String> word = new ArrayList<String>();
	List<Integer> edges = new ArrayList<Integer>(); // encoded as (0,1) (0,3) entries in languages list
}
