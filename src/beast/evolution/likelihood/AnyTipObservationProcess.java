/*
 * AnyTipObservationProcess.java
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

package beast.evolution.likelihood;

import beast.core.Description;
import beast.core.parameter.RealParameter;
import beast.evolution.alignment.Alignment;
import beast.evolution.branchratemodel.BranchRateModel;
import beast.evolution.sitemodel.SiteModel;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;


//import beast.core.*;
//import dr.evolution.alignment.PatternList;
//import dr.evomodel.branchratemodel.BranchRateModel;
//import dr.evomodel.sitemodel.SiteModel;
//import dr.evomodel.tree.TreeModel;
//import dr.inference.model.Parameter;


@Description("Observation process for Multi-State Stochastic Dollo model. Defines a data collection process where the traits must be present in at least one tip node.")
public class AnyTipObservationProcess extends AbstractObservationProcess {


    //dr.evomodel.MSSD.AnyTipObservationProcess anytipobservationprocess;


    @Override
    public void initAndValidate() {
        init(
                "AnyTip",
                treeModelInput.get(),
                patternsInput.get(),
                siteModelInput.get(),
                branchRateModeInput.get(),
                muInput.get(),
                (lamInput.get() == null ? new RealParameter("1.0") : lamInput.get()),
                integrateGainRateInputInput.get());
        //abstractobservationprocess = anytipobservationprocess;
    }


//    double calculateLogTreeWeight() {
//        return anytipobservationprocess.calculateLogTreeWeight();
//    }
//
//    void setTipNodePatternInclusion() {
//        anytipobservationprocess.setTipNodePatternInclusion();
//    }
//
//    void setNodePatternInclusion() {
//        anytipobservationprocess.setNodePatternInclusion();
//    }

    
    protected double[] u0;
    protected double[] p;

    @Override
	public void init(String modelName, Tree treeModel, Alignment patterns, SiteModel siteModel,
                                    BranchRateModel branchRateModel, RealParameter mu, RealParameter lam, boolean integrateGainRate) {
        super.init(modelName, treeModel, patterns, siteModel, branchRateModel, mu, lam, integrateGainRate);
    }

    @Override
	public double calculateLogTreeWeight() {
        int L = treeModel.getNodeCount();
        if (u0 == null || p == null) {
            u0 = new double[L];    // probability that the trait at node i survives to no leaf
            p = new double[L];     // probability of survival on the branch ancestral to i
        }
        int i, j, childNumber;
        Node node;
        double logWeight = 0.0;

        double averageRate = getAverageRate();

        for (i = 0; i < L; ++i) {
            p[i] = 1.0 - getNodeSurvivalProbability(i, averageRate);
        }

        /*Tree.Utils.*/postOrderTraversalList(treeModel, postOrderNodeList);

        for (int postOrderIndex = 0; postOrderIndex < nodeCount; postOrderIndex++) {

            i = postOrderNodeList[postOrderIndex];

            if (i < treeModel.getLeafNodeCount()) { // Is tip
                u0[i] = 0.0;
                logWeight += 1.0 - p[i];
            } else { // Is internal node or root
                u0[i] = 1.0;
                node = treeModel.getNode(i);
                for (j = 0; j < node.getChildCount(); ++j) {                   
                    childNumber = node.getChild(j).getNr();
                    u0[i] *= 1.0 - p[childNumber] * (1.0 - u0[childNumber]);
                }
                logWeight += (1.0 - u0[i]) * (1.0 - p[i]);
            }
        }

        return -logWeight * lam.getValue(0) / (getAverageRate() * mu.getValue(0));
    }


    private void postOrderTraversalList(Tree tree, int[] postOrderList) {
        int idx = nodeCount - 1;
        int cidx = nodeCount - 1;

        postOrderList[idx] = tree.getRoot().getNr();

        while (cidx > 0) {
            Node cNode = tree.getNode(postOrderList[idx]);
            for(int i = 0; i < cNode.getChildCount(); ++i) {
                cidx -= 1;
                postOrderList[cidx] = cNode.getChild(i).getNr();
            }
            idx -= 1;
        }
	}

	public void setTipNodePatternInclusion() { // These values never change
        for (int i = 0; i < treeModel.getLeafNodeCount(); i++) {
            Node node = treeModel.getNode(i);

            for (int patternIndex = 0; patternIndex < patternCount; patternIndex++) {
                extantInTipsBelow[i * patternCount + patternIndex] = 1;
                int taxonIndex = patterns.getTaxonIndex(node.getID());
                int patternItem = patterns.getPattern(taxonIndex, patternIndex);
                int[] states = dataType.getStatesForCode(patternItem);
                for (int state : states) {
                    if (state == deathState) {
                        extantInTipsBelow[i * patternCount + patternIndex] = 0;
                    }
                }
                extantInTips[patternIndex] += extantInTipsBelow[i * patternCount + patternIndex];

            }
        }

        for (int i = 0; i < treeModel.getNodeCount(); i++) {
            for (int patternIndex = 0; patternIndex < patternCount; patternIndex++) {
                nodePatternInclusion[i * patternCount + patternIndex] =
                        (extantInTipsBelow[i * patternCount +patternIndex] >= extantInTips[patternIndex]);
            }
        }
    }

    @Override
	public void setNodePatternInclusion() {

        if (postOrderNodeList == null) {
            postOrderNodeList = new int[nodeCount];         
        }
        
        if (nodePatternInclusion == null) {
            nodePatternInclusion = new boolean[nodeCount * patternCount];
            storedNodePatternInclusion = new boolean[nodeCount * patternCount];
        }

        if (extantInTips == null) {
            extantInTips = new int[patternCount];
            extantInTipsBelow = new int[nodeCount * patternCount];
            setTipNodePatternInclusion();
        }

        // Determine post-order traversal
        /*Tree.Utils.*/postOrderTraversalList(treeModel, postOrderNodeList);

        // Do post-order traversal
        // Do post-order traversal
        for (int postOrderIndex = 0; postOrderIndex < nodeCount; postOrderIndex++) {
            Node node = treeModel.getNode(postOrderNodeList[postOrderIndex]);
            final int nChildren = node.getChildCount();
            if (nChildren > 0) {
                final int nodeNumber = node.getNr();
                for (int patternIndex = 0; patternIndex < patternCount; patternIndex++) {
                    extantInTipsBelow[nodeNumber * patternCount + patternIndex] = 0;
                    for (int j = 0; j < nChildren; j++) {
                        final int childIndex = node.getChild(j).getNr();
                        extantInTipsBelow[nodeNumber * patternCount + patternIndex] +=
                                extantInTipsBelow[childIndex * patternCount + patternIndex];
                    }
                }
            }
        }

        for (int i = treeModel.getLeafNodeCount(); i < treeModel.getNodeCount(); ++i) {
            for (int patternIndex = 0; patternIndex < patternCount; patternIndex++) {
                nodePatternInclusion[i * patternCount + patternIndex] =
                        (extantInTipsBelow[i * patternCount + patternIndex] >= extantInTips[patternIndex]);
            }
        }
        
        nodePatternInclusionKnown = true;
    }

    private int[] extantInTips;
    private int[] extantInTipsBelow; // Easier to store/restore (later) if 1D array

    private int[] postOrderNodeList;

}