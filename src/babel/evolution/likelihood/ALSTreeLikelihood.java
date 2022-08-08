/*
 * ALSTreeLikelihood.java
 *
 * Copyright (C) 2002-2012 Alexei Drummond,
 * Andrew Rambaut, Marc Suchard and Alexander V. Alekseyenko
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * BEAST is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package babel.evolution.likelihood;


import beagle.Beagle;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.evolution.likelihood.TreeLikelihood;
import beast.base.evolution.sitemodel.SiteModel;


@Description("Treelikelihood for running the Multi-State Stochastic Dollo process")
public class ALSTreeLikelihood extends TreeLikelihood implements PartialsProvider {
    public Input<AbstractObservationProcess> opInput = new Input<AbstractObservationProcess>("observationprocess", "description here");

    protected AbstractObservationProcess observationProcess;

    @Override
    public void initAndValidate() {
        observationProcess = opInput.get();
        // ensure TreeLikelihood initialises the partials for tips
        m_useAmbiguities.setValue(true, this);
        super.initAndValidate();
    }

    @Override
    public double calculateLogP() {
        // Calculate the partial likelihoods
        super.calculateLogP();
        // get the frequency model
        double[] freqs = ((SiteModel.Base) siteModelInput.get()).substModelInput.get().getFrequencies();
        // let the observationProcess handle the rest
        logP = observationProcess.nodePatternLikelihood(freqs, this);
        return logP;
    }

    
	@Override
	public void getNodePartials(int iNode, double[] fPartials) {
		if (beagle != null) {;
			beagle.getBeagle().getPartials(beagle.getPartialBufferHelper().getOffsetIndex(iNode), Beagle.NONE, fPartials);
		} else {
			likelihoodCore.getNodePartials(iNode, fPartials);
		}
	}
}
