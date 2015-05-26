/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.builtins;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;

/**
 * Returns a string representation of the current stack. This includes the {@link CallTarget}s and
 * the contents of the {@link Frame}. Note that this is implemented as a slow path by passing
 * {@code true} to {@link FrameInstance#getFrame(FrameAccess, boolean)}.
 */
@NodeInfo(shortName = "stacktrace")
public abstract class SLStackTraceBuiltin extends SLBuiltinNode {

    public SLStackTraceBuiltin() {
        super(new NullSourceSection("SL builtin", "stacktrace"));
    }

    @Specialization
    public String trace() {
        return createStackTrace();
    }

    @TruffleBoundary
    private static String createStackTrace() {
        StringBuilder str = new StringBuilder();

        Truffle.getRuntime().iterateFrames(frameInstance -> {
            CallTarget callTarget = frameInstance.getCallTarget();
            Frame frame = frameInstance.getFrame(FrameAccess.READ_ONLY, true);
            RootNode rn = ((RootCallTarget) callTarget).getRootNode();
            if (rn.getClass().getName().contains("SLFunctionForeignAccess")) {
                return 1;
            }
            if (str.length() > 0) {
                str.append(System.getProperty("line.separator"));
            }
            str.append("Frame: ").append(rn.toString());
            FrameDescriptor frameDescriptor = frame.getFrameDescriptor();
            frameDescriptor.getSlots().stream().forEach((s) -> {
                str.append(", ").append(s.getIdentifier()).append("=").append(frame.getValue(s));
            });
            return null;
        });
        return str.toString();
    }
}
