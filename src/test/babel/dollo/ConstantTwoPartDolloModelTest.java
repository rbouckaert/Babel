package test.babel.dollo;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import babel.evolution.datatype.MutationDeathType;
import babel.evolution.likelihood.ALSTreeLikelihood;
import babel.evolution.likelihood.AnyTipObservationProcess;
import babel.evolution.substitutionmodel.ComplexMutationDeathModel;
import beast.base.inference.parameter.RealParameter;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.evolution.branchratemodel.StrictClockModel;
import beast.base.evolution.sitemodel.SiteModel;
import beast.base.evolution.substitutionmodel.Frequencies;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;

@RunWith(Parameterized.class)
public class ConstantTwoPartDolloModelTest {
	@Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { { 0, 0, 0.0, 1.0 }, { 0, 1, 0.0, 0.0 }, { 1, 0, 0.0, 0.0 },
				{ 1, 1, 0.0, 0.0 }, { 0, 0, 1.0, 0.0 }, { 0, 1, 1.0, 0.0 }, { 1, 0, 1.0, 0.0 }, { 1, 1, 1.0, 1.0 } });
	}

	private int[] observations;
	private Double expectedLikelihood;
	protected AnyTipObservationProcess dollo;
	protected ALSTreeLikelihood als;

	public ConstantTwoPartDolloModelTest(Integer observation1, Integer observation2, Double aliveInEquilibrium,
			Double likelihood) {
		// no beagle please
		System.setProperty("java.only", "true");

		Tree tree = new TreeParser("(A:1,B:1):1");
		observations = new int[] { observation1, observation2 };
		Sequence s1 = new Sequence("A", String.valueOf(observations[0]));
		Sequence s2 = new Sequence("B", String.valueOf(observations[1]));
		Alignment alignment = new Alignment();
		MutationDeathType dtype = new MutationDeathType();
		dtype.initByName("extantCode", "1");
		alignment.initByName("sequence", Arrays.asList(new Sequence[] { s1, s2 }), "userDataType", dtype);

		RealParameter zero = new RealParameter(new Double[] { 1e-11 });
		RealParameter one = new RealParameter(new Double[] { 1.0 });

		als = new ALSTreeLikelihood();
		dollo = new AnyTipObservationProcess();
		SiteModel sites = new SiteModel();
		ComplexMutationDeathModel subst = new ComplexMutationDeathModel();
		// NOTE: The encoding of the basic MutationDeatType is "1"→0 and
		// "0"→1, and the frequencies are noted in ENCODING order, not in
		// CHARACTER order!
		Frequencies freq = new Frequencies();
		freq.initByName("frequencies", aliveInEquilibrium + " " + (1.0 - aliveInEquilibrium));
		subst.initByName("frequencies", freq, "deathprob", zero);
		sites.initByName("shape", "1.0", "substModel", subst);
		dollo.initByName("tree", tree, "data", alignment, "siteModel", sites, "branchRateModel", new StrictClockModel(),
				"mu", zero, "integrateGainRate", true);
		als.initByName("tree", tree, "data", alignment, "siteModel", sites, "branchRateModel", new StrictClockModel(),
				"observationprocess", dollo);

		expectedLikelihood = likelihood;
	}

	@Test
	public void testTrivialNodePartials() {		
		for (int v = 0; v < 2; ++v) {
			for (int i = 0; i < observations.length; ++i) {
				double[] fPartials = new double[2];
				als.getNodePartials(i, fPartials);
				// NOTE: The encoding of the basic MutationDeatType is "1"→0 and
				// "0"→1, and the frequency partials are noted in ENCODING
				// order, not in CHARACTER order!
				assertEquals((observations[i] == v) ? 1.0 : 0.0, fPartials[1 - v], 1e-7);
			}
		}
	}

	@Test
	public void testCalculateLogP() {
		assertEquals(expectedLikelihood, Math.exp(als.calculateLogP()), 1e-8);
	}
	
	
}
