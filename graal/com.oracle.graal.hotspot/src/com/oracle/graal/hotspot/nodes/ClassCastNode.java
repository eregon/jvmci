/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.replacements.nodes.*;

/**
 * {@link MacroNode Macro node} for {@link Class#cast(Object)}.
 * 
 * @see ClassSubstitutions#cast(Class, Object)
 */
public class ClassCastNode extends MacroNode implements Canonicalizable {

    public ClassCastNode(Invoke invoke) {
        super(invoke);
    }

    private ValueNode getJavaClass() {
        return arguments.get(0);
    }

    private ValueNode getObject() {
        return arguments.get(1);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ValueNode javaClass = getJavaClass();
        if (javaClass.isConstant()) {
            ValueNode object = getObject();
            Class c = (Class) HotSpotObjectConstant.asObject(javaClass.asConstant());
            if (c != null && !c.isPrimitive()) {
                HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) HotSpotResolvedObjectType.fromClass(c);
                CheckCastNode checkcast = graph().add(new CheckCastNode(type, object, null, false));
                return checkcast;
            }
        }
        return this;
    }
}
