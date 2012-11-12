/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * A node that changes the type of its input, usually narrowing it.
 * For example, a PI node refines the type of a receiver during
 * type-guarded inlining to be the type tested by the guard.
 */
public class PiNode extends FloatingNode implements LIRLowerable, Virtualizable {

    @Input private ValueNode object;
    @Input(notDataflow = true) private final FixedNode anchor;

    public ValueNode object() {
        return object;
    }

    public FixedNode anchor() {
        return anchor;
    }

    public PiNode(ValueNode object, FixedNode anchor, Stamp stamp) {
        super(stamp);
        this.object = object;
        this.anchor = anchor;
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
        generator.setResult(this, generator.operand(object));
    }

    @Override
    public boolean inferStamp() {
        if (object().objectStamp().alwaysNull() && objectStamp().nonNull()) {
            // a null value flowing into a nonNull PiNode can happen should be guarded by a type/isNull guard, but the
            // compiler might see this situation before the branch is deleted
            return false;
        }
        return updateStamp(stamp().join(object().stamp()));
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        VirtualObjectNode virtual = tool.getVirtualState(object());
        if (virtual != null) {
            tool.replaceWithVirtual(virtual);
        }
    }
}
