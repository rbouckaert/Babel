package babel.tools.dplace;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.math.MathException;
import org.xml.sax.SAXException;

import beast.app.beauti.BeautiDoc;
import beast.core.BEASTInterface;
import beast.core.BEASTObject;
import beast.core.MCMC;
import beast.evolution.alignment.Taxon;
import beast.evolution.alignment.TaxonSet;
import beast.evolution.tree.Node;
import beast.math.distributions.MRCAPrior;
import beast.math.distributions.ParametricDistribution;
import beast.util.Randomizer;
import beast.util.XMLParser;
import beast.util.XMLParserException;

public class ISOTreeParser extends BEASTObject {
	final static int QUOT = "'".charAt(0);
	
	enum MODE {iso, glotto};
	MODE mode = MODE.iso;
	String MATCH = ".*\\[...\\].*";
	String MATCH2 = ".*\\[(...)\\].*";

	public void setMode(MODE mode) {
		this.mode = mode;
		if (mode == MODE.iso) {
			MATCH = ".*\\[...\\].*";
			MATCH2 = ".*\\[(...)\\].*";
		}
		if (mode == MODE.glotto) {
			MATCH = ".*\\[........\\].*";
			MATCH2 = ".*\\[(........)\\].*";
		}
	}
	static final boolean [] isLabelChar = new boolean[256];
	{
		Arrays.fill(isLabelChar, true);
		isLabelChar['('] = false;
		isLabelChar[')'] = false;
		isLabelChar[','] = false;
		isLabelChar[';'] = false;
	}
	
	@Override
	public void initAndValidate() {
	}
	
	public Node parse(String s) {
		int i = 0;
		Node current = new Node();
		//Node org = current;
		Node parent = null;
		while (i < s.length()) {
			char c = s.charAt(i);
			switch (c) {
			case '(':
			{
				Node node = new Node();
				node.setParent(current);
				current.addChild(node);
				parent = current;
				current = node;
				i++;
				// Log.warning("push");
				break;
			}
			case ',':
			{
				Node node = new Node();
				node.setParent(parent);
				parent.addChild(node);
				current = node;
				i++;
				break;
			}
			case ')':
				current = parent;
				parent = parent.getParent();
				if (i < s.length() - 1 && isLabelChar[s.charAt(i+1)]) {
					int end = i + 1;
					while (isLabelChar[s.charAt(end)]) {
						end++;
					}
					String label = s.substring(i + 1, end);
					current.setID(label);
					i = end;
				} else {
					i++;
				}
				// Log.warning("pop");
				break;
			case ';':
			case '\n':
				if (!s.substring(i+1).matches("^\\s*$")) {
					throw new IllegalArgumentException("Unexpected ';' at position " + i);
				}
				i++;
				break;
			default:
				int end = i;
				while (isLabelChar[s.charAt(end)]) {
					end++;
				}
				String label = s.substring(i, end);
				current.setID(label);
				i = end;	
			}
			
		}
		
		return current;
	}

	
	public Node parseGlottoIsoOnly(String s) {
		s = s.replaceAll(":1", "");
		Set<String> usedISOs = new HashSet<>();
		
		int i = 0;
		Node current = new Node();
		//Node org = current;
		Node parent = null;
		while (i < s.length()) {
			int j = i;
			char c = s.charAt(i);
			switch (c) {
			case '(':
			{
				Node node = new Node();
				node.setParent(current);
				current.addChild(node);
				parent = current;
				current = node;
				i++;
				break;
			}
			case ',':
			{
				Node node = new Node();
				node.setParent(parent);
				parent.addChild(node);
				current = node;
				i++;
				break;
			}
			case ')':
				current = parent;
				parent = parent.getParent();
				if (i < s.length() - 1 && s.charAt(i+1) == QUOT) {
					int end = i + 2;
					while (s.charAt(end) != QUOT) {
						end++;
					}
					String label = s.substring(i, end);
					if (label.matches(MATCH)) {
						label = label.replaceAll(MATCH2, "$1");
						if (!usedISOs.contains(label)) {
							current.setID(label);
							usedISOs.add(label);
						}
					}
					i = end + 1;
				} else {
					i++;
				}
				break;
			case ';':
				if (!s.substring(i+1).matches("^\\s*$")) {
					throw new IllegalArgumentException("Unexpected ';' at position " + i);
				}
				i++;
				break;
			default:
				int end = i + 1;
				while (!(s.charAt(end) == QUOT)) {
					end++;
				}
				String label = s.substring(i, end);
				if (label.matches(MATCH)) {
					label = label.replaceAll(MATCH2, "$1");
					current.setID(label);
					usedISOs.add(label);
				}
				i = end + 1;	
			}
			if (i == j) {
				throw new IllegalArgumentException("Unexpected character [" + c + "] at position " + i);
			}
			
		}
		
		current = removeDeadLeafs(current);
		return current;
	}
	
	public static Node removeDeadLeafs(Node node) {
		if (node.isLeaf()) {
			if (node.getID() == null) {
				return null;
			} else {
				return node;
			}
		} else {
			List<Node> children = new ArrayList<>();
			for (Node child : node.getChildren()) {
				Node n = removeDeadLeafs(child);
				if (n != null) {
					children.add(n);
				}
			}
			node.removeAllChildren(false);
			if (children.size() == 0) {
				if (node.getID() == null) {
					return null;
				} else {
					return node;
				}
			}
			
			if (children.size() == 1) {
				if (node.getID() == null) {
					return children.get(0);
				} else {
					node.addChild(children.get(0));
					return node;
				}
			}
			
			for (Node child : children) {
				node.addChild(child);
			}
			return node;
		}
		
	}

	private void toRandomBinary(Node n) {
		if (n.isLeaf()) {
			return;
		} else {
			List<Node> children = new ArrayList<>();
			children.addAll(n.getChildren());
			for (Node c : children) {
				toRandomBinary(c);
			}
			while (children.size() > 2) {
				Node left = children.get(Randomizer.nextInt(children.size()));
				children.remove(left);
				Node right = children.get(Randomizer.nextInt(children.size()));
				children.remove(right);
	
				Node newNode = new Node();
				newNode.addChild(left);
				left.setParent(newNode);
				newNode.addChild(right);
				right.setParent(newNode);
				children.add(newNode);
			}

			n.removeAllChildren(false);
			n.addChild(children.get(0));
			n.addChild(children.get(1));
			children.get(0).setParent(n);
			children.get(1).setParent(n);
		}
	}

	private double addjustHeights(Node n, double maxHeight) {
		if (n.isLeaf()) {
			n.setHeight(0);
			return n.getHeight();
		} else {
			double h = 0;
			for (Node c : n.getChildren()) {
				h = Math.max(h, addjustHeights(c, n.getHeight() < Double.MAX_VALUE ? n.getHeight() : maxHeight));
			}
			if (n.getHeight() == Double.MAX_VALUE) {
				h += (Math.exp(-20*Randomizer.nextDouble())) * (maxHeight - h);
				n.setHeight(h);
				return h;
			}
			return n.getHeight();
		}
		
	}

	private void setConstrainedHeights(Node n, String xmlfile, String treeID) throws SAXException, IOException, ParserConfigurationException, XMLParserException, MathException {
		XMLParser parser = new XMLParser();
		String xml = BeautiDoc.load(xmlfile);
		MCMC mcmc = (MCMC) parser.parseBareFragment(xml, false);
		BEASTInterface o = mcmc.posteriorInput.get();
		BEASTInterface tree = getObjectWithID(treeID, o);
		for (BEASTInterface o2 : tree.getOutputs()) {
			if (o2 instanceof MRCAPrior) {
				MRCAPrior prior = (MRCAPrior) o2;
				ParametricDistribution distr = prior.distInput.get();
				if (distr != null && !prior.useOriginateInput.get()) {
					distr.initAndValidate();
					double h = distr.inverseCumulativeProbability(0.1 + Randomizer.nextDouble() * 0.4);
					TaxonSet taxonset = prior.taxonsetInput.get();
					Set<String> taxa = new HashSet<>();
					for (Taxon t : taxonset.taxonsetInput.get()) {
						taxa.add(t.getID());
					}
					System.out.print(prior.getID() + " " );
					Node mrca = TreeConstraintProvider.getMRCA(n, taxa);
					System.out.println();
					mrca.setHeight(h);
				}
			}
		}
	}

	private BEASTInterface getObjectWithID(String id, BEASTInterface o) {
		if (o.getID().equals(id)) {
			return o;
		}
		for (BEASTInterface o2 : o.listActiveBEASTObjects()) {
			BEASTInterface o3 = getObjectWithID(id, o2);
			if (o3 != null) {
				return o3;
			}
		}
		return null;
	}

	public static void main(String[] args) throws Exception {
		ISOTreeParser parser = new ISOTreeParser();
		//Node n = parser.parse("((xmr),((knw)ctm))");
		Node n = parser.parse(args[0]);
		parser.toRandomBinary(n);
		//parser.setConstrainedHeights(n, "/tmp/x.xml", "Tree.t:DPLACE");
		parser.addjustHeights(n, 8000);
		System.out.println(n.toNewick());

	}


}
