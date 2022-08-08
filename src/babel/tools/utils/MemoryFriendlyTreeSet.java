package babel.tools.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;

public class MemoryFriendlyTreeSet extends TreeFileParser {
//	Tree [] trees;
//	int current = 0;
	int lineNr;
    //public Map<String, String> translationMap = null;
    //public List<String> taxa;

    // label count origin for NEXUS trees
    int origin = -1;

    BufferedReader fin;

    public MemoryFriendlyTreeSet(String inputFileName, int burninPercentage) throws IOException  {
    	super(inputFileName, burninPercentage);
		countTrees(inputFileName, burninPercentage);
        fin = new BufferedReader(new FileReader(inputFileName));
	}


	@Override
	public void reset() throws FileNotFoundException  {
		currentTree = 0;
        fin = new BufferedReader(new FileReader(new File(fileName)));
        lineNr = 0;
        try {
            if (isNexus) {
                while (currentTree < burninCount && fin.ready()) {
        			String str = nextLine();
                    if (str == null) {
                        return;
                    }
                    if (str.trim().toLowerCase().startsWith("tree ")) {
                    	currentTree++;
                    }
                }
            } else {
                while (fin.ready() && currentTree < burninCount) {
                    final String str = nextLine();
                    if (str == null) {
                        return;
                    }
                    if (str.trim().length() > 2 && !str.trim().startsWith("#")) {
                    	currentTree++;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Around line " + lineNr + "\n" + e.getMessage());
        }
    } // parseFile

    /**
     * read next line from Nexus file that is not a comment and not empty 
     * @throws IOException *
     */
    String nextLine() throws IOException  {
        String str = readLine();
        if (str == null) {
            return null;
        }
        if (str.matches("^\\s*\\[.*")) {
            final int start = str.indexOf('[');
            int end = str.indexOf(']', start);
            while (end < 0) {
                str += readLine();
                end = str.indexOf(']', start);
            }
            str = str.substring(0, start) + str.substring(end + 1);
            if (str.matches("^\\s*$")) {
                return nextLine();
            }
        }
        if (str.matches("^\\s*$")) {
            return nextLine();
        }
        return str;
    }

    /**
     * read line from nexus file *
     */
    String readLine() throws IOException {
        if (!fin.ready()) {
            return null;
        }
        lineNr++;
        return fin.readLine();
    }
    
	@Override
	public boolean hasNext() {
		return currentTree < totalTrees;
	}
	
	@Override
	public Tree next() throws IOException {
		String str = nextLine();
		if (str == null) {
			return null;
		}
		Node root = null;
		if (isNexus) {
            if (str.trim().toLowerCase().startsWith("tree ")) {
				root = parseNewick(str);
            }
		} else {
			if (str.trim().length() > 2 && !str.trim().startsWith("#")) {
				root = parseNewick(str);
			}
		}
		if (root != null) {
			double h = getMaxHeight(root);
			normalise(root, h);
			Tree tree = new Tree(root);
			tree.m_taxonset.setValue(taxonset, tree);
			currentTree++;
			return tree;
		}

		return next();
	} 
} // class MemoryFriendlyTreeSet