package babel.spanningtrees;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Jakob Runge
 * This class aims to provide a simple parser for Nexus files.
 * That is it shall take some file or string and parse it so that the following tasks can be accomplished:
 * 1.: Get the names of blocks defined in the Nexus file.
 * 2.: Get the lines of a block by the blocks name.
 * */
public class NexusBlockParser {
	
	//Marker regexes to find begin/end of a nexus block:
	private static final Pattern beginMarker = Pattern.compile("begin (.+);?"); 
	private static final String endMarker = "end;";
	
	private HashMap<String, String[]> blocks = new HashMap<>();

	public static NexusBlockParser parseFile(File file) throws FileNotFoundException{
		BufferedReader input = new BufferedReader(new FileReader(file));
		NexusBlockParser parser = NexusBlockParser.parseLines(input.lines());
		try {
			input.close();
		} catch (IOException e) {
			System.err.println("Problem closing input in NexusParser.parseFile().");
		}
		return parser;
	}
	
	/**
	 * Proxy for parseFile(File f)
	 * @throws FileNotFoundException 
	 * */
	public static NexusBlockParser parseFile(String fileName) throws FileNotFoundException{
		return NexusBlockParser.parseFile(new File(fileName));
	}
	
	public static NexusBlockParser parseLines(Stream<String> lines){
		
		NexusBlockParser parser = new NexusBlockParser();
		String blockName = null; // Only set if inside a block
		ArrayList<String> currentBlock = new ArrayList<>();
		for(String line : lines.collect(Collectors.toList())){
			if(blockName == null){
				//Searching for next block to start.
				Matcher matcher = NexusBlockParser.beginMarker.matcher(line);
				if(matcher.matches()){
					blockName = matcher.group(1);
					if(blockName.length() > 0 && blockName.charAt(blockName.length() - 1) == ';'){
						blockName = blockName.substring(0, blockName.length() - 1);
					}
				}
				//else this line is ignored.
			}else if(line.matches(NexusBlockParser.endMarker)){
				//End of a block.
				parser.blocks.put(blockName, currentBlock.toArray(new String[currentBlock.size()]));
				blockName = null;
				currentBlock.clear();
			}else{
				//Inside a block, so adding the line:
				currentBlock.add(line);
			}
		}
		return parser;
	}
	
	public boolean hasBlock(String name){
		return this.blocks.containsKey(name);
	}
	
	public Set<String> getBlockNames(){
		return this.blocks.keySet();
	}
	
	public String[] getBlock(String name){
		return this.blocks.get(name);
	}
	
	/**
	 * This method will work as a small and simple test of the NexusBlockParser class.
	 * It shall:
	 * 1.: Open and parse a Nexus file
	 * 2.: List the names of blocks (and lines) found in the Nexus file
	 * */
	public static void main(String[] args) {
		String testFile = "./examples/x/2016-09-13_CoBL-IE_Lgs101_Mgs172_Current_Jena200_BEAUti.nex";
		try {
			NexusBlockParser parser = NexusBlockParser.parseFile(testFile);
			System.out.println("Nexus file parsed. Blocks are:");
			for(String blockName : parser.getBlockNames()){
				System.out.println(String.format("\t%s: %s lines.", blockName, parser.getBlock(blockName).length));
			}
		} catch (FileNotFoundException e) {
			System.err.println("NexusParser.main could not read the test file.");
			e.printStackTrace();
		}
	}

}
