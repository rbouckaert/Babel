package babel.tools.dplace;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import babel.tools.dplace.ISOTreeParser.MODE;
import beastfx.app.inputeditor.BeautiDoc;
import beast.base.core.Description;
import beast.base.core.Log;
import beast.base.evolution.tree.Node;

@Description("Extract Glottolog trees, one labelled with ISO codes, and one labelled with glotto code")
public class ExtractGlottologTrees {
	final static String TREE_FILE = "glotto/tree-glottolog-newick.txt";
	
	public static void main(String[] args) throws IOException {
		
		ExtractGlottologTrees x = new ExtractGlottologTrees();
		x.extractISOTree();
		x.extractGlottoTree(MODE.glotto);
		x.extractGlottoTree(MODE.iso);
		
	}

	Set<String> usedISOs;
	
	private void extractGlottoTree(MODE mode) throws IOException {
		String src0 = BeautiDoc.load(TREE_FILE);
		usedISOs = new LinkedHashSet<>();
		
		StringBuilder buf = new StringBuilder();
		buf.append('(');
		for (String src : src0.split("\n")) {
			ISOTreeParser parser = new ISOTreeParser();
			parser.setMode(mode);
			//Node n = parser.parse("((xmr),((knw)ctm))");
			Node node = parser.parseGlottoIsoOnly(src);
			if (node != null) {
				StringBuilder b = new StringBuilder();
				TreeConstraintProvider.toNewick(node, b, true);
				String ISOs = b.toString();
				
				//Log.warning(ISOs);
				buf.append(ISOs);
				buf.append(',');
			}
		}
		buf.deleteCharAt(buf.length() - 1);
		buf.append(')');
		String ISOs = buf.toString();
		Log.info(ISOs);
		
		
//		ISOTreeParser parser = new ISOTreeParser();
//		Node root = parser.parse(ISOs);
//		buf = new StringBuilder();
//		TreeConstraintProvider.toNewick(root, buf, true);
//		Log.info(buf.toString());
	}

	private String toISOTree(Node node) {
		if (node.isLeaf()) {
			if (node.getMetaData("iso") instanceof String) {
				String iso = (String) node.getMetaData("iso");
				if (iso.equals("bqe")) {
					int h = 3;
					h++;
				}
				usedISOs.add(iso);
				return iso;
			} else {
				return null;
			}
		} else {
			StringBuilder buf = new StringBuilder();
			buf.append('(');
			for (Node child : node.getChildren()) {
				String isoTree = toISOTree(child);
				if (isoTree != null) {
					buf.append(isoTree);
				}
				buf.append(',');
			}
			buf.deleteCharAt(buf.length() - 1);
			String str = TreeConstraintProvider.cleanUpNewick(buf.toString());
			str = str.replaceAll(",$", "");
			buf = new StringBuilder();
			buf.append(str);
			
			if (node.getMetaData("iso") instanceof String) {
				String iso = (String) node.getMetaData("iso");
				if (iso.equals("bqe")) {
					int h = 3;
					h++;
				}
				if (!usedISOs.contains(iso)) {
					usedISOs.add(iso);
					if (buf.length() == 4) {
						buf.insert(0, '(');
						buf.append(')');
						buf.append(iso);
					} else {
						buf.append(iso);
					}
				}
			}
			buf.append(')');
			return buf.toString();
		}
	}

	private void extractISOTree() {
		
	}

}
