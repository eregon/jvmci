/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.nodes.extended;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code SwitchNode} class is the base of both lookup and table switches.
 */
public abstract class SwitchNode extends ControlSplitNode {

    @Successor protected final NodeSuccessorList<BeginNode> successors;
    protected double[] successorProbabilities;
    @Input private ValueNode value;
    private double[] keyProbabilities;
    private int[] keySuccessors;

    /**
     * Constructs a new Switch.
     * 
     * @param value the instruction that provides the value to be switched over
     * @param successors the list of successors of this switch
     */
    public SwitchNode(ValueNode value, BeginNode[] successors, double[] successorProbabilities, int[] keySuccessors, double[] keyProbabilities) {
        super(StampFactory.forVoid());
        this.successorProbabilities = successorProbabilities;
        assert keySuccessors.length == keyProbabilities.length;
        this.successors = new NodeSuccessorList<>(this, successors);
        this.value = value;
        this.keySuccessors = keySuccessors;
        this.keyProbabilities = keyProbabilities;
    }

    @Override
    public double probability(BeginNode successor) {
        double sum = 0;
        for (int i = 0; i < successors.size(); i++) {
            if (successors.get(i) == successor) {
                sum += successorProbabilities[i];
            }
        }
        return sum;
    }

    public ValueNode value() {
        return value;
    }

    /**
     * The number of distinct keys in this switch.
     */
    public abstract int keyCount();

    /**
     * The key at the specified position, encoded in a Constant.
     */
    public abstract Constant keyAt(int i);

    /**
     * Returns the index of the successor belonging to the key at the specified index.
     */
    public int keySuccessorIndex(int i) {
        return keySuccessors[i];
    }

    /**
     * Returns the successor for the key at the given index.
     */
    public BeginNode keySuccessor(int i) {
        return successors.get(keySuccessors[i]);
    }

    /**
     * Returns the probability of the key at the given index.
     */
    public double keyProbability(int i) {
        return keyProbabilities[i];
    }

    /**
     * Returns the index of the default (fall through) successor of this switch.
     */
    public int defaultSuccessorIndex() {
        return keySuccessors[keySuccessors.length - 1];
    }

    public BeginNode blockSuccessor(int i) {
        return successors.get(i);
    }

    public void setBlockSuccessor(int i, BeginNode s) {
        successors.set(i, s);
    }

    public int blockSuccessorCount() {
        return successors.count();
    }

    /**
     * Gets the successor corresponding to the default (fall through) case.
     * 
     * @return the default successor
     */
    public BeginNode defaultSuccessor() {
        if (defaultSuccessorIndex() == -1) {
            throw new GraalInternalError("unexpected");
        }
        return defaultSuccessorIndex() == -1 ? null : successors.get(defaultSuccessorIndex());
    }

    /**
     * Helper function that sums up the probabilities of all keys that lead to a specific successor.
     * 
     * @return an array of size successorCount with the accumulated probability for each successor.
     */
    public static double[] successorProbabilites(int successorCount, int[] keySuccessors, double[] keyProbabilities) {
        double[] probability = new double[successorCount];
        for (int i = 0; i < keySuccessors.length; i++) {
            probability[keySuccessors[i]] += keyProbabilities[i];
        }
        return probability;
    }

    @Override
    public SwitchNode clone(Graph into) {
        SwitchNode newSwitch = (SwitchNode) super.clone(into);
        newSwitch.successorProbabilities = Arrays.copyOf(successorProbabilities, successorProbabilities.length);
        newSwitch.keyProbabilities = Arrays.copyOf(keyProbabilities, keyProbabilities.length);
        newSwitch.keySuccessors = Arrays.copyOf(keySuccessors, keySuccessors.length);
        return newSwitch;
    }
}
