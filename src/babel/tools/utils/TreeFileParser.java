/*

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
/*
 * TreeFileParser.java
 * Copyright Remco Bouckaert r.bouckaert@auckland.ac.nz (C) 2011 
*/
package babel.tools.utils;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import beast.base.core.Log;
import beast.base.evolution.alignment.Taxon;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;


public class TreeFileParser {
	/**
	 * default tree branch length, used when that info is not in the Newick tree
	 **/
	final static float DEFAULT_LENGTH = 0.001f;

	int m_nOffset = 0;
	/** labels of leafs **/
	Vector<String> m_sLabels;
	TaxonSet taxonset;

	/** position information for the leafs (if available) **/
	Vector<Float> m_fLongitude;
	Vector<Float> m_fLatitude;
	/** extreme values for position information **/
	float m_fMaxLong, m_fMaxLat, m_fMinLong, m_fMinLat;
	/** nr of labels in dataset **/
	int m_nNrOfLabels;
	/** burn in = nr of trees ignored at the start of tree file, can be set by command line option **/
	int m_nBurnIn = 0;
	boolean m_bBurnInIsPercentage = true;
	/** for memory saving, set to true **/
	boolean m_bSurpressMetadata = true;
	/** if there is no translate block. This solves issues where the taxa labels are numbers e.g. in generated tree data **/
	boolean m_bIsLabelledNewick = false;
	/** flag to indicate that single child nodes are allowed **/
	boolean m_bAllowSingleChild = false;
		
	
    int burninCount = -1;
    int totalTrees = 0, currentTree = 0;
    boolean isNexus = true;
    String fileName;

	public TreeFileParser(String sFileName, int burnInPercentage) throws IOException {
		m_sLabels = new Vector<>();
		m_fLongitude = new Vector<>();
		m_fLatitude = new Vector<>();
		m_nBurnIn = burnInPercentage;
		m_fMinLat = 90; m_fMinLong = 180;
		m_fMaxLat = -90; m_fMaxLong = -180;
		this.m_bAllowSingleChild = false;
		this.fileName = sFileName;
	} // c'tor

	public Tree [] parseFile() throws Exception {
		if (totalTrees < 0) {
			countTrees(fileName, m_nBurnIn);
		}
		
		Node [] roots = parseFile2(fileName);
		// trees have "height" set to length of branches
		// these need to be converted to heights.
		for (Node root : roots) {
			double h = getMaxHeight(root);
			normalise(root, h);
		}
		
		Tree [] trees = new Tree[roots.length];
		for (int i = 0; i < trees.length; i++) {
			trees[i] = new Tree(roots[i]);
			trees[i].m_taxonset.setValue(taxonset, trees[i]);
		}
		return trees;
	}
	
	protected void normalise(Node node, double h) {
		double len = node.getHeight();
		node.setHeight(h - len);
		for (Node child : node.getChildren()) {
			normalise(child, h - len);
		}
		
	}

	protected double getMaxHeight(Node node) {
		if (node.isLeaf()) {
			return node.getHeight();
		} else {
			double h1 = getMaxHeight(node.getLeft());
			double h2 = getMaxHeight(node.getRight());
			return node.getHeight() + Math.max(h1, h2);
		}
	}

	private Node [] parseFile2(String sFile) throws Exception {
		Vector<Node> trees = new Vector<Node>();
		m_nOffset = 0;
		
		File file = new File(sFile);
		long nFileSize = file.length();
		
		// parse Newick tree file
		BufferedReader fin = new BufferedReader(new FileReader(sFile));
		String sStr = fin.readLine();
		nFileSize -= sStr.length();
		// grab translate block
		while (fin.ready() && sStr.toLowerCase().indexOf("translate") < 0) {
			sStr = fin.readLine();
			nFileSize -= sStr.length();
		}
		m_bIsLabelledNewick = false;
		m_nNrOfLabels = m_sLabels.size();
		boolean bAddLabels = (m_nNrOfLabels == 0);
		if (sStr.toLowerCase().indexOf("translate") < 0) {
			m_bIsLabelledNewick = true;
			// could not find translate block, assume it is a list of Newick trees instead of Nexus file
			fin.close();
			fin = new BufferedReader(new FileReader(sFile));

			int nBurnIn = m_nBurnIn;
			if (m_bBurnInIsPercentage) {
				nFileSize = file.length();
				nBurnIn = (int) (m_nBurnIn * nFileSize/ 100);
			}
			
			while (fin.ready() && m_nNrOfLabels == 0) {
				nFileSize = file.length();
				sStr = fin.readLine();
				if (m_bBurnInIsPercentage) {
					nBurnIn -= sStr.length();
				} else {
					nBurnIn--;
				}
				if (sStr.length() > 2 && sStr.indexOf("(") >= 0) {
					String sStr2 = sStr;
					sStr2 = sStr2.substring(sStr2.indexOf("("));
					while (sStr2.indexOf('[') >= 0) {
						int i0 = sStr2.indexOf('[');
						int i1 = sStr2.indexOf(']');
						sStr2 = sStr2.substring(0, i0) + sStr2.substring(i1 + 1);
					}
					sStr2 = sStr2.replaceAll("[;\\(\\),]"," ");
					sStr2 = sStr2.replaceAll(":\\s*[0-9\\.Ee-]+"," ");
					String [] sLabels = sStr2.split("\\s+");
					if (bAddLabels) {
						m_nNrOfLabels = 0;
						for (int i = 0; i < sLabels.length; i++) {
							if (sLabels[i].length() > 0) {
								m_sLabels.add(sLabels[i]);
								m_nNrOfLabels++;
							}
						}
					}
					if (nBurnIn < 0) {
						Node tree = parseNewick(sStr);
						tree.sort();
						tree.labelInternalNodes(m_nNrOfLabels);
						trees.add(tree);
					}
//					sNewickTrees.add(sStr);
				}
			}
			while (fin.ready()) {
				sStr = fin.readLine();
				if (sStr.length() > 2 && sStr.indexOf("(") >= 0) {
					Node tree = parseNewick(sStr);
					tree.sort();
					tree.labelInternalNodes(m_nNrOfLabels);
					trees.add(tree);
					if (trees.size() % 100 ==0) {if (m_nNrOfLabels>=100||trees.size() % 1000 ==0) {System.err.print(trees.size() + " ");}}
//					sNewickTrees.add(sStr);
				}
			}
		} else {
			// read tree set from file, and store in individual strings
			sStr = fin.readLine();
			nFileSize -= sStr.length(); 
			//m_nNrOfLabels = 0;
			boolean bLastLabel = false;
			while (fin.ready() && !bLastLabel) {
				if (sStr.indexOf(";") >= 0) {
					sStr = sStr.replace(';',' ');
					sStr = sStr.trim();
					if (sStr.isEmpty()) {
						break;
					}
					bLastLabel = true;
				}
				sStr = sStr.replaceAll(",", "");
				sStr = sStr.replaceAll("^\\s+", "");
				//String[] sStrs = sStr.split("\\s+");
				
	        	// find first whitespace character in taxaTranslation
	        	int k = 0;
	        	while (k < sStr.length() && !Character.isWhitespace(sStr.charAt(k))) {
	        		k++;
	        	}
	        	String sLabel = null;
	        	if (k > 0) {
					sLabel = sStr.substring(k).trim();
					char s = sLabel.charAt(0);
					char e = sLabel.charAt(sLabel.length() - 1);
					if ((s == '\"' && e == '\"') || (s == '\'' && e == '\'')) {
						sLabel = sLabel.substring(1, sLabel.length() - 1);
					}
	        	}
				int iLabel = Integer.parseInt(sStr.substring(0, k));
				//String sLabel = sStrs[1];
				if (m_sLabels.size() < iLabel) {
					//m_sLabels.add("__dummy__");
					m_nOffset = 1;
				}
				// check if there is geographic info in the name
				if (sLabel.contains("(")) {
					int iStr = sLabel.indexOf('(');
					int iStr2 = sLabel.indexOf('x', iStr);
					if (iStr2 >= 0) {
						int iStr3 = sLabel.indexOf(')', iStr2);
						if (iStr3 >= 0) {
							float fLat = Float.parseFloat(sLabel.substring(iStr+1, iStr2));// + 180;
							float fLong = Float.parseFloat(sLabel.substring(iStr2+1, iStr3));// + 360)%360;
							if (fLat!=0 || fLong!=0) {
								m_fMinLat = Math.min(m_fMinLat, fLat);
								m_fMaxLat = Math.max(m_fMaxLat, fLat);
								m_fMinLong = Math.min(m_fMinLong, fLong);
								m_fMaxLong = Math.max(m_fMaxLong, fLong);
							}
							while (m_fLatitude.size() < m_sLabels.size()) {
								m_fLatitude.add(0f);
								m_fLongitude.add(0f);
							}
							m_fLatitude.add(fLat);
							m_fLongitude.add(fLong);
						}
					}
					sLabel = sLabel.substring(0, sLabel.indexOf("("));
				}
				if (bAddLabels) {
					m_sLabels.add(sLabel);
					m_nNrOfLabels++;
				}
				if (!bLastLabel) {
					sStr = fin.readLine();
					nFileSize -= sStr.length(); 
				}
			}
			
			// read trees
			int nBurnIn = m_nBurnIn;
			if (m_bBurnInIsPercentage) {
				nBurnIn = (int) (m_nBurnIn * nFileSize/ 100);
			}
			
			//int k = 0;
			while (fin.ready()) {
				sStr = fin.readLine();
				if (m_bBurnInIsPercentage) {
					nBurnIn -= sStr.length();
				}
				sStr = sStr.trim();
				if (sStr.length() > 5) {
					String sTree = sStr.substring(0,5);
					if (sTree.toLowerCase().startsWith("tree ")) {
						//k++;
						if (nBurnIn <= 0) {
							int i = sStr.indexOf('(');
							if (i > 0) {
								sStr = sStr.substring(i);
							}
//						if (m_bSurpressMetadata) {
//							while (sStr.indexOf('[') >= 0) {
//								int i0 = sStr.indexOf('[');
//								int i1 = sStr.indexOf(']');
//								sStr = sStr.substring(0, i0) + sStr.substring(i1 + 1);
//							}
//						}
							Node tree = parseNewick(sStr);
							//System.err.println(k + " " + tree);
							tree.sort();
							tree.labelInternalNodes(m_nNrOfLabels);
							trees.add(tree);
							if (trees.size() % 100 ==0) {if (m_nNrOfLabels>=100||trees.size() % 1000 ==0) {System.err.print(trees.size() + " ");}}
							//sNewickTrees.add(sStr);
						} else {
							if (!m_bBurnInIsPercentage) {
								nBurnIn--;
							}
						}
					}
				}
			}
			fin.close();
			if (nBurnIn > 0) {
				System.err.println("WARNING: Burn-in too large, resetting burn-in to default");
				m_sLabels.clear();
				if (m_bBurnInIsPercentage) {					
					m_nBurnIn = 10;
				} else {
					m_nBurnIn = 0;
				}
				return parseFile2(sFile);
			}
		}
		
		
		System.err.println();
		System.err.println("Geo: " +m_fMinLong + "x" + m_fMinLat + " " + m_fMaxLong + "x" + m_fMaxLat);
		return trees.toArray(new Node[1]);
	} // parseFile


	
	/** Try to map sStr into an index. First, assume it is a number.
	 * If that does not work, look in list of labels to see whether it is there.
	 */
	private int getLabelIndex(String sStr) throws Exception {
		if (!m_bIsLabelledNewick) {
			try {
				return Integer.parseInt(sStr) - m_nOffset;
			} catch (Exception e) {
			}
		}
		for (int i = 0; i < m_nNrOfLabels; i++) {
			if (sStr.equals(m_sLabels.elementAt(i))) {
				return i;
			}
		}
		// sStr may have (double) qoutes missing
		for (int i = 0; i < m_nNrOfLabels; i++) {
			String sLabel = m_sLabels.elementAt(i);
			if (sLabel.startsWith("'") && sLabel.endsWith("'") ||
					sLabel.startsWith("\"") && sLabel.endsWith("\"")) {
				sLabel = sLabel.substring(1, sLabel.length()-1);
				if (sStr.equals(sLabel)) {
					return i;
				}
			}
		}
		// sStr may have extra (double) qoutes
		if (sStr.startsWith("'") && sStr.endsWith("'") ||
				sStr.startsWith("\"") && sStr.endsWith("\"")) {
			sStr = sStr.substring(1, sStr.length()-1);
			return getLabelIndex(sStr);
		}
		throw new Exception("Label '" + sStr + "' in Newick tree could not be identified");
	}
	

	 double height(Node node) {
		 return node.getHeight();
//		 if (node.isLeaf()) {
//			 return node.m_fLength;
//		 } else {
//			 return node.m_fLength + Math.max(height(node.m_left), height(node.m_right));
//		 }
	 }
	 
	 char [] m_chars;
	 int m_iTokenStart;
	 int m_iTokenEnd;
	 final static int COMMA = 1;
	 final static int BRACE_OPEN = 3;
	 final static int BRACE_CLOSE = 4;
	 final static int COLON = 5;
	 final static int SEMI_COLON = 8;
	 final static int META_DATA = 6;
	 final static int TEXT = 7;
	 final static int UNKNOWN = 0;
	 
	 int nextToken() {
		 m_iTokenStart = m_iTokenEnd;
		 while (m_iTokenEnd < m_chars.length) {
			 // skip spaces
			 while (m_iTokenEnd < m_chars.length && (m_chars[m_iTokenEnd] == ' ' || m_chars[m_iTokenEnd] == '\t')) {
				 m_iTokenStart++;
				 m_iTokenEnd++;
			 }
			 if (m_chars[m_iTokenEnd] == '(') {
				 m_iTokenEnd++;
				 return BRACE_OPEN;
			 }
			 if (m_chars[m_iTokenEnd] == ':') {
				 m_iTokenEnd++;
				 return COLON;
			 }
			 if (m_chars[m_iTokenEnd] == ';') {
				 m_iTokenEnd++;
				 return SEMI_COLON;
			 }
			 if (m_chars[m_iTokenEnd] == ')') {
				 m_iTokenEnd++;
				 return BRACE_CLOSE;
			 }
			 if (m_chars[m_iTokenEnd] == ',') {
				 m_iTokenEnd++;
				 return COMMA;
			 }
			 if (m_chars[m_iTokenEnd] == '[') {
				 m_iTokenEnd++;
				 while (m_iTokenEnd < m_chars.length && m_chars[m_iTokenEnd-1] != ']') {
					 m_iTokenEnd++;
				 }
				 return META_DATA;
			 }
			 while (m_iTokenEnd < m_chars.length && (m_chars[m_iTokenEnd] != ' ' && m_chars[m_iTokenEnd] != '\t'
				 && m_chars[m_iTokenEnd] != '('  && m_chars[m_iTokenEnd] != ')'  && m_chars[m_iTokenEnd] != '['
					 && m_chars[m_iTokenEnd] != ':'&& m_chars[m_iTokenEnd] != ','&& m_chars[m_iTokenEnd] != ';')) {
				 m_iTokenEnd++;
			 }
			 return TEXT;
		 }
		 return UNKNOWN;
	 }

	 protected Node parseNewick(String sStr) { // throws Exception {
		 try {
		if (sStr == null || sStr.length() == 0) {
			return null;
		}
		
		m_chars = sStr.toCharArray();
		m_iTokenStart = sStr.indexOf('(');
		if (m_iTokenStart < 0) {
			return null;
		}
		m_iTokenEnd = m_iTokenStart;
		Vector<Node> stack = new Vector<Node>();
		Vector<Boolean> isFirstChild =  new Vector<Boolean>();
		stack.add(new Node());
		isFirstChild.add(true);
		stack.lastElement().setHeight(0);
		boolean bIsLabel = true;
		while (m_iTokenEnd < m_chars.length) {
			switch (nextToken()) {
			case BRACE_OPEN:
			{
				Node node2 = new Node();
				node2.setHeight(0);
				stack.add(node2);
				isFirstChild.add(true);
				bIsLabel = true;
			}
				break;
			case BRACE_CLOSE:
			{
				if (isFirstChild.lastElement()) {
					if (m_bAllowSingleChild) {
						// process single child nodes
						Node left = stack.lastElement();
						stack.remove(stack.size()-1);
						isFirstChild.remove(isFirstChild.size()-1);
						Node dummyparent = new Node();
						dummyparent.setHeight(0);
						dummyparent.setLeft(left);
						left.setParent(dummyparent);
						//dummyparent.m_right = null;
						Node parent = stack.lastElement();
						parent.setLeft(left);
						left.setParent(parent);
						break;
					} else {
						// don't know how to process single child nodes
						throw new Exception("Node with single child found.");
					}
				}
				// process multi(i.e. more than 2)-child nodes by pairwise merging.
				while (isFirstChild.elementAt(isFirstChild.size()-2) == false) {
					Node right = stack.lastElement();
					stack.remove(stack.size()-1);
					isFirstChild.remove(isFirstChild.size()-1);
					Node left = stack.lastElement();
					stack.remove(stack.size()-1);
					isFirstChild.remove(isFirstChild.size()-1);
					Node dummyparent = new Node();
					dummyparent.setHeight(0);
					dummyparent.setLeft(left);
					left.setParent(dummyparent);
					dummyparent.setRight(right);
					right.setParent(dummyparent);
					stack.add(dummyparent);
					isFirstChild.add(false);
				}
				// last two nodes on stack merged into single parent node 
				Node right = stack.lastElement();
				stack.remove(stack.size()-1);
				isFirstChild.remove(isFirstChild.size()-1);
				Node left = stack.lastElement();
				stack.remove(stack.size()-1);
				isFirstChild.remove(isFirstChild.size()-1);
				Node parent = stack.lastElement();
				parent.setLeft(left);
				left.setParent(parent);
				parent.setRight(right);
				right.setParent(parent);
			}
				break;
			case COMMA:
			{
				Node node2 = new Node();
				node2.setHeight(0);
				stack.add(node2);
				isFirstChild.add(false);
				bIsLabel = true;
			}
				break;
			case COLON:
				bIsLabel = false;
				break;
			case TEXT:
				if (bIsLabel) {
					String sLabel = sStr.substring(m_iTokenStart, m_iTokenEnd);
					stack.lastElement().setNr(getLabelIndex(sLabel));
					stack.lastElement().setID(m_sLabels.get(getLabelIndex(sLabel)));
				} else {
					String sLength = sStr.substring(m_iTokenStart, m_iTokenEnd);
					stack.lastElement().setHeight(Float.parseFloat(sLength)); 
				}
				break;
			case META_DATA:
				if (stack.lastElement().metaDataString == null) {
					stack.lastElement().metaDataString = sStr.substring(m_iTokenStart+1, m_iTokenEnd-1);
				} else {
					stack.lastElement().metaDataString = stack.lastElement().metaDataString + ("," +sStr.substring(m_iTokenStart+1, m_iTokenEnd-1));
				}
				break;
			case SEMI_COLON:
				//System.err.println(stack.lastElement().toString());
				return stack.lastElement();
			default:
				throw new Exception("parseNewick: unknown token");	
			}
		}
		return stack.lastElement();
		 } catch (Exception e) {
			 e.printStackTrace();
			 Log.warning(e.getMessage() + ": " + sStr.substring(Math.max(0, m_iTokenStart-100), m_iTokenStart) + " >>>" + sStr.substring(m_iTokenStart, m_iTokenEnd) + " <<< ...");
			 throw new RuntimeException(e.getMessage() + ": " + sStr.substring(Math.max(0, m_iTokenStart-100), m_iTokenStart) + " >>>" + sStr.substring(m_iTokenStart, m_iTokenEnd) + " <<< ..."); 
		 }
		//return node;
	 }

	 
     /** determine number of trees in the file,
 	 * and number of trees to skip as burnin
 	 * @throws IOException
 	 * @throws FileNotFoundException **/
 	 void countTrees(String inputFileName, int burninPercentage) throws IOException  {
         BufferedReader fin = new BufferedReader(new FileReader(new File(inputFileName)));
         if (!fin.ready()) {
        	 fin.close();
         	 throw new IOException("File appears empty");
         }
     	 String str = fin.readLine();
         if (!str.toUpperCase().trim().startsWith("#NEXUS")) {
        	 if (!str.contains("(")) {
        		 // try next line
        		 str = fin.readLine();
                 if (!str.toUpperCase().trim().startsWith("#NEXUS")) {
                  	// the file contains a list of Newick trees instead of a list in Nexus format
                  	isNexus = false;
                  	if (str.trim().length() > 0) {
                  		totalTrees = 2;
                  		collectTaxaNames(str);
                  	}                	 
                 }
        	 } else {
               	isNexus = false;
               	if (str.trim().length() > 0) {
               		totalTrees = 1;
               		collectTaxaNames(str);
               	}                	 
        	 }
         }
         while (fin.ready()) {
         	 str = fin.readLine();
             if (isNexus) {
                 if (str.toLowerCase().contains("translate")) {
                     parseTranslateBlock(fin);
                 } else if (str.trim().toLowerCase().startsWith("tree ")) {
                 	totalTrees++;
                 }
             } else if (str.trim().length() > 0) {
         		totalTrees++;
             }
         }
         fin.close();

         burninCount = Math.max(0, (burninPercentage * totalTrees)/100);

         Log.warning("Processing " + (totalTrees - burninCount) + " trees from file" +
                 (burninPercentage > 0 ? " after ignoring first " + burninPercentage + "% = " + burninCount + " trees." : "."));
		}
 	
     private void parseTranslateBlock(BufferedReader fin) throws IOException {
         String line = fin.readLine();
         final StringBuilder translateBlock = new StringBuilder();
         while (line != null && !line.trim().toLowerCase().equals(";")) {
             translateBlock.append(line.trim());
             if (!line.endsWith(",")) {
            	 translateBlock.append(',');
             }
             line = fin.readLine();
         }
         final String[] taxaTranslations = translateBlock.toString().split(",");
         m_nOffset = -1;
         for (final String taxaTranslation : taxaTranslations) {
             final String[] translation = taxaTranslation.split("[\t ]+");
             if (translation.length == 2) {
            	 m_sLabels.add(translation[1]);
            	 
            	 if (m_nOffset == -1) {
            		 m_nOffset = Integer.parseInt(translation[0].trim());
            	 }
             } else {
                 // Log.err.println("Ignoring translation:" + Arrays.toString(translation));
                 String str = translation[1];
                 int i = 2;
                 while (i < translation.length) {
                	 str += " " + translation[i];
                	 i++;
                 }
                 str = str.replaceAll("\"", "");
                 m_sLabels.add(str);
            	 if (m_nOffset == -1) {
            		 m_nOffset = Integer.parseInt(translation[0].trim());
            	 }
             }
         }
         m_nNrOfLabels = m_sLabels.size();
         createTaxonSet();
     }

 	 
 	 
	private void collectTaxaNames(String str) {
		int i = 0;
		while (i < str.length()) {
			char c = str.charAt(i);
			switch (c) {
			case '(':
			case ')':
			case ',':
				// ignore
				i++;
				break;
			case '[':
				// eat up meta data
				while (i < str.length() && str.charAt(i) != ']') {
					i++;
				}
				break;
			case ':':
				// eat up length
				while (i < str.length() && !(str.charAt(i) == ')'|| str.charAt(i) == ',')) {
					i++;
				}
				break;
			default:
				StringBuilder b = new StringBuilder();
				boolean done = false;
				while (i < str.length() && !done) {
					c = str.charAt(i);
					done =  c == ')' || c == ':' || c == ',' || c == '(' || c == '[';
					if (!done) {
						if (c != '\'' && c != '"') {
							b.append(c);
						}
						i++;
					} else {
						m_sLabels.add(b.toString());
					}
				}
				
			}
		}		
		m_nNrOfLabels = m_sLabels.size();
        createTaxonSet();
	}
	
	private void createTaxonSet() {
		taxonset = new TaxonSet();
		for (String id : m_sLabels) {
			Taxon taxon = new Taxon(id);
			taxonset.taxonsetInput.get().add(taxon);
		}
		taxonset.initAndValidate();
	}


	public boolean hasNext() {
		return false;
	}

	public Tree next() throws IOException {
		return null;
	}

	public void reset() throws IOException {
	}

	
	

    
} // class TreeFileParser
