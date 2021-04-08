package babel.tools;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;

import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.evolution.alignment.Alignment;
import beast.core.Input.Validate;
import beast.core.util.Log;
import beast.util.ClusterTree;
import beast.util.NexusParser;
import beast.util.ClusterTree.Type;

@Description("Creates UPGMA, NJ or other cluster tree from an alignment and save to newick format")
public class CreateClusterTree extends Runnable {
	final public Input<File> nexusInput = new Input<>("in", "NEXUS file containing an alignment", Validate.REQUIRED);
    final public Input<Type> clusterTypeInput = new Input<>("clusterType", "type of clustering algorithm used for generating initial beast.tree. " +
            "Should be one of " + Arrays.toString(Type.values()) + " (default " + Type.upgma + ")", Type.average, Type.values());
	final public Input<OutFile> outputInput = new Input<>("out", "output file with newick tree, or standard output if not specified",
			new OutFile("[[none]]"));

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		NexusParser parser = new NexusParser();
		parser.parseFile(nexusInput.get());
		Alignment alignment = parser.m_alignment;

        ClusterTree tree = new ClusterTree();
        tree.initByName(
                "clusterType", clusterTypeInput.get(),
                "taxa", alignment);

		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}
		out.println(tree.getRoot().toNewick());

		Log.info("Done");        
	}

	public static void main(String[] args) throws Exception {
		new Application(new CreateClusterTree(), "Create Cluster Tree", args);
	}
}
