/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.sparc;

import static com.oracle.graal.api.code.ValueUtil.isRegister;
import static com.oracle.graal.api.code.ValueUtil.isStackSlot;
import static com.oracle.graal.lir.sparc.SPARCArithmetic.DADD;
import static com.oracle.graal.lir.sparc.SPARCArithmetic.FADD;
import static com.oracle.graal.lir.sparc.SPARCArithmetic.IADD;
import static com.oracle.graal.lir.sparc.SPARCArithmetic.LADD;

import com.oracle.graal.api.code.CallingConvention;
import com.oracle.graal.api.code.CodeCacheProvider;
import com.oracle.graal.api.code.DeoptimizationAction;
import com.oracle.graal.api.code.ForeignCallLinkage;
import com.oracle.graal.api.code.StackSlot;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.api.meta.AllocatableValue;
import com.oracle.graal.api.meta.Constant;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.compiler.gen.LIRGenerator;
import com.oracle.graal.compiler.target.LIRGenLowerable;
import com.oracle.graal.graph.GraalInternalError;
import com.oracle.graal.lir.FrameMap;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.sparc.SPARCMove.MoveFromRegOp;
import com.oracle.graal.lir.sparc.SPARCMove.MoveToRegOp;
import com.oracle.graal.lir.sparc.SPARCArithmetic.Op2Stack;
import com.oracle.graal.nodes.BreakpointNode;
import com.oracle.graal.nodes.DeoptimizingNode;
import com.oracle.graal.nodes.DirectCallTargetNode;
import com.oracle.graal.nodes.IndirectCallTargetNode;
import com.oracle.graal.nodes.InfopointNode;
import com.oracle.graal.nodes.SafepointNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.Condition;
import com.oracle.graal.nodes.calc.ConvertNode.Op;
import com.oracle.graal.nodes.java.CompareAndSwapNode;

/**
 * This class implements the SPARC specific portion of the LIR generator.
 */
public class SPARCLIRGenerator extends LIRGenerator {

    public static class SPARCSpillMoveFactory implements LIR.SpillMoveFactory {

        @Override
        public LIRInstruction createMove(AllocatableValue result, Value input) {
            throw new InternalError("NYI");
        }
    }

    public SPARCLIRGenerator(StructuredGraph graph, CodeCacheProvider runtime, TargetDescription target, FrameMap frameMap, CallingConvention cc, LIR lir) {
        super(graph, runtime, target, frameMap, cc, lir);
        lir.spillMoveFactory = new SPARCSpillMoveFactory();
    }

    @Override
    protected void emitNode(ValueNode node) {
        if (node instanceof LIRGenLowerable) {
            ((LIRGenLowerable) node).generate(this);
        } else {
            super.emitNode(node);
        }
    }

    @Override
    public Variable emitMove(Value input) {
        Variable result = newVariable(input.getKind());
        emitMove(result, input);
        return result;
    }

    @Override
    public void emitMove(AllocatableValue dst, Value src) {
        if (isRegister(src) || isStackSlot(dst)) {
            append(new MoveFromRegOp(dst, src));
        } else {
            append(new MoveToRegOp(dst, src));
        }
    }

    @Override
    protected boolean peephole(ValueNode valueNode) {
        // SPARC: Auto-generated method stub
        return false;
    }

    @Override
    protected void emitReturn(Value input) {
        // SPARC: Auto-generated method stub

    }

    @Override
    public void emitJump(LabelRef label) {
        // SPARC: Auto-generated method stub

    }

    @Override
    public void emitCompareBranch(Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef label) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef label, boolean negated) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, boolean negated, LabelRef label) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public Variable emitConditionalMove(Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        // SPARC: Auto-generated method stub
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        // SPARC: Auto-generated method stub
    }

    @Override
    protected void emitForeignCall(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        // SPARC: Auto-generated method stub
    }

    @Override
    protected void emitSequentialSwitch(Constant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget, Value key) {
        // SPARC: Auto-generated method stub
    }

    @Override
    protected void emitSwitchRanges(int[] lowKeys, int[] highKeys, LabelRef[] targets, LabelRef defaultTarget, Value key) {
        // SPARC: Auto-generated method stub
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void emitBitCount(Variable result, Value operand) {
        // SPARC: Auto-generated method stub

    }

    @Override
    public void emitBitScanForward(Variable result, Value operand) {
        // SPARC: Auto-generated method stub

    }

    @Override
    public void emitBitScanReverse(Variable result, Value operand) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void emitMathAbs(Variable result, Variable input) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void emitMathSqrt(Variable result, Variable input) {
        // SPARC: Auto-generated method stub

    }

    @Override
    public void emitMathLog(Variable result, Variable input, boolean base10) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void emitMathCos(Variable result, Variable input) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void emitMathSin(Variable result, Variable input) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void emitMathTan(Variable result, Variable input) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void emitByteSwap(Variable result, Value operand) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public boolean canInlineConstant(Constant c) {
        // SPARC: Auto-generated method stub
        return false;
    }

    @Override
    public boolean canStoreConstant(Constant c) {
        // SPARC: Auto-generated method stub
        return false;
    }

    @Override
    public Value emitAddress(Value base, long displacement, Value index, int scale) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Value emitLoad(Kind kind, Value address, DeoptimizingNode canTrap) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public void emitStore(Kind kind, Value address, Value input, DeoptimizingNode canTrap) {
        // SPARC: Auto-generated method stub

    }

    @Override
    public Value emitAddress(StackSlot address) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Value emitNegate(Value input) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Value emitAdd(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Stack(IADD, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Stack(LADD, result, a, loadNonConst(b)));
                break;
            case Float:
                append(new Op2Stack(FADD, result, a, loadNonConst(b)));
                break;
            case Double:
                append(new Op2Stack(DADD, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + a.getKind() + " prim: " + a.getKind().isPrimitive());
        }
        return result;
    }

    @Override
    public Value emitSub(Value a, Value b) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Value emitMul(Value a, Value b) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Value emitDiv(Value a, Value b, DeoptimizingNode deopting) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Value emitRem(Value a, Value b, DeoptimizingNode deopting) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Value emitUDiv(Value a, Value b, DeoptimizingNode deopting) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Value emitURem(Value a, Value b, DeoptimizingNode deopting) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Value emitAnd(Value a, Value b) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Value emitOr(Value a, Value b) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Value emitXor(Value a, Value b) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Value emitShl(Value a, Value b) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Value emitShr(Value a, Value b) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Value emitUShr(Value a, Value b) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public Value emitConvert(Op opcode, Value inputVal) {
        // SPARC: Auto-generated method stub
        return null;
    }

    @Override
    public void emitMembar(int barriers) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void emitDeoptimize(DeoptimizationAction action, DeoptimizingNode deopting) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void visitCompareAndSwap(CompareAndSwapNode i) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void visitBreakpointNode(BreakpointNode i) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void emitUnwind(Value operand) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void emitNullCheck(ValueNode v, DeoptimizingNode deopting) {
        // SPARC: Auto-generated method stub
    }

    @Override
    public void visitInfopointNode(InfopointNode i) {
        // SPARC: Auto-generated method stub
    }
}
