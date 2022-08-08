/*
 * SingleTipObservationProcess.java
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

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.parameter.RealParameter;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Taxon;
import beast.base.evolution.branchratemodel.BranchRateModel;
import beast.base.evolution.sitemodel.SiteModel;
import beast.base.evolution.tree.Tree;

/**
 * Package: SingleTimeObservationProcess
 * Description:
 * <p/>
 * <p/>
 * Created by
 *
 * @author Alexander V. Alekseyenko (alexander.alekseyenko@gmail.com)
 *         Date: Jan 13, 2012
 *         Time: 11:32:28 AM
 */
@Description("Observation process for Multi-State Stochastic Dollo model. Defines a data collection process where the traits must be present in a specific tip node.")
public class SingleTipObservationProcess extends AnyTipObservationProcess{

    public Input<Taxon> theTip = new Input<Taxon>("taxon", "A taxon in which the traits must be present", Input.Validate.REQUIRED);

    //dr.evomodel.MSSD.AnyTipObservationProcess singletipobservationprocess;

    @Override
	public void initAndValidate() {
        init(treeModelInput.get(),
                patternsInput.get(),
                siteModelInput.get(),
                branchRateModeInput.get(),
                muInput.get(),
                (lamInput.get() == null ? new RealParameter("1.0") : lamInput.get()),
                theTip.get(),
                integrateGainRateInputInput.get());
        //anytipobservationprocess = singletipobservationprocess;
    }

//    public double calculateLogTreeWeight() {
//        return singletipobservationprocess.calculateLogTreeWeight();
//    }
    protected Taxon sourceTaxon;

    public void init(Tree treeModel, Alignment patterns, SiteModel siteModel,
                                       BranchRateModel branchRateModel, RealParameter mu, RealParameter lam, Taxon sourceTaxon,
                                       boolean integrateGainRate) {
        init("SingleTipObservationProcess", treeModel, patterns, siteModel, branchRateModel, mu, lam, integrateGainRate);
        this.sourceTaxon = sourceTaxon;
    }

    @Override
	public double calculateLogTreeWeight() {
        return -lam.getValue(0) / (getAverageRate() * mu.getValue(0));
    }
}
