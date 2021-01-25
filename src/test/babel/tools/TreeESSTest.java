package test.babel.tools;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

public class TreeESSTest {
	final static int N = 256;
	
	private static Double[] getTrace(int p, int period) {
		Double [] trace = new Double[N];
		for (int i= 0; i< N; i++){
			trace[i] = (double)(((i/p)%period+1)/period);
		}
		return trace;
	}
	
	public static void main(String[] args) throws IOException {
		PrintStream out = System.out;
		out = new PrintStream(new File("/dev/null"));
	
		
		PrintStream seq =new PrintStream(new File("/tmp/seqs"));
		seq.print("Sample ");
		for (int i = 0; i < N; i++) seq.print(i+"\t");
		seq.println();
		for (int p = 1; p < 20; p++) {
			out.print(p + "\t");
			for (int period = 2; period < 10; period++) {
				Double[] trace = getTrace(p, period);
seq.println("seq" + p + "-" + period + " " + Arrays.toString(trace));				
				double ESS = beast.core.util.ESS.calcESS(trace, 1);
				out.print(ESS + "\t");
			}
			out.println();
		}
		seq.close();
		if (out != System.out) {
			out.close();
		}
	}


}
