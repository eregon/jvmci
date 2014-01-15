/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.ptx;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.HotSpotReplacementsImpl.GraphProducer;
import com.oracle.graal.nodes.*;

/**
 * Utility for creating a graph for a method where said graph implements the transition to a PTX
 * compiled version of the method. In some ways, this is similar to the wrapper generated by HotSpot
 * for a native method.
 */
public class PTXGraphProducer implements GraphProducer {
    private final HotSpotBackend hostBackend;
    private final PTXHotSpotBackend ptxBackend;

    public PTXGraphProducer(HotSpotBackend hostBackend, PTXHotSpotBackend ptxBackend) {
        this.hostBackend = hostBackend;
        this.ptxBackend = ptxBackend;
    }

    /**
     * Gets a graph for calling the PTX binary of a given method. The PTX binary is created as a
     * side effect of this call.
     */
    public StructuredGraph getGraphFor(ResolvedJavaMethod method) {
        if (canOffloadToGPU(method)) {
            InstalledCode installedCode = ptxBackend.installKernel(method, ptxBackend.compileKernel(method, true));
            return new PTXLaunchKernelGraphKit(method, installedCode.getStart(), hostBackend.getProviders()).getGraph();
        }
        return null;
    }

    protected boolean canOffloadToGPU(ResolvedJavaMethod method) {
        return method.getName().contains("lambda$main$") & method.isSynthetic();
    }
}
