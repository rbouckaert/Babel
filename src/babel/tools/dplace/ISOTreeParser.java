package babel.tools.dplace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import beast.core.BEASTObject;
import beast.evolution.tree.Node;

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
		Node org = current;
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
		Node org = current;
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

	public static void main(String[] args) {
		ISOTreeParser parser = new ISOTreeParser();
		//Node n = parser.parse("((xmr),((knw)ctm))");
		Node n = parser.parseGlottoIsoOnly("('Alavan [aval1237]':1,'Alto Navarro Meridional [alto1237]':1,'Alto Navarro Septentrional [alto1238]':1,'Basque/ Souletin [basq1250]':1,'Biscayan [bisc1236]':1,'Guipuzcoan [guip1235]':1,('Eastern Low Navarrese [east1470]':1,'Labourdin [labo1236]':1,'Western Low Navarrese [west1508]':1)'Navarro-Labourdin Basque [basq1249][bqe]':1,'Roncalese [ronc1236]':1)'Basque [basq1248][eus]-l-':1;");
		System.out.println(n.toNewick());

	}
}
