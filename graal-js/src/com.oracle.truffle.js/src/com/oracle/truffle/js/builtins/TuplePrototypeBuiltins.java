/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.builtins;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.js.nodes.access.JSHasPropertyNode;
import com.oracle.truffle.js.nodes.access.ReadElementNode;
import com.oracle.truffle.js.nodes.array.JSArrayFirstElementIndexNode;
import com.oracle.truffle.js.nodes.array.JSArrayLastElementIndexNode;
import com.oracle.truffle.js.nodes.array.JSArrayNextElementIndexNode;
import com.oracle.truffle.js.nodes.array.JSGetLengthNode;
import com.oracle.truffle.js.nodes.cast.JSToBooleanNode;
import com.oracle.truffle.js.nodes.cast.JSToIndexNode;
import com.oracle.truffle.js.nodes.cast.JSToIntegerAsLongNode;
import com.oracle.truffle.js.nodes.cast.JSToObjectNode;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.nodes.function.JSFunctionCallNode;
import com.oracle.truffle.js.nodes.tuples.JSIsConcatSpreadableNode;
import com.oracle.truffle.js.nodes.unary.IsCallableNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSArguments;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Tuple;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.JSArray;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSTuple;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.SimpleArrayList;

import java.util.Arrays;
import java.util.Comparator;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import static com.oracle.truffle.api.CompilerDirectives.transferToInterpreterAndInvalidate;
import static com.oracle.truffle.js.builtins.ArrayPrototypeBuiltinsFactory.JSArrayEveryNodeGen;
import static com.oracle.truffle.js.builtins.ArrayPrototypeBuiltinsFactory.JSArrayFindIndexNodeGen;
import static com.oracle.truffle.js.builtins.ArrayPrototypeBuiltinsFactory.JSArrayFindNodeGen;
import static com.oracle.truffle.js.builtins.ArrayPrototypeBuiltinsFactory.JSArrayForEachNodeGen;
import static com.oracle.truffle.js.builtins.ArrayPrototypeBuiltinsFactory.JSArrayIncludesNodeGen;
import static com.oracle.truffle.js.builtins.ArrayPrototypeBuiltinsFactory.JSArrayIndexOfNodeGen;
import static com.oracle.truffle.js.builtins.ArrayPrototypeBuiltinsFactory.JSArrayJoinNodeGen;
import static com.oracle.truffle.js.builtins.ArrayPrototypeBuiltinsFactory.JSArrayReduceNodeGen;
import static com.oracle.truffle.js.builtins.ArrayPrototypeBuiltinsFactory.JSArraySomeNodeGen;
import static com.oracle.truffle.js.builtins.ArrayPrototypeBuiltinsFactory.JSArrayToLocaleStringNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleConcatNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleFilterNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleFlatMapNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleFlatNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleIteratorNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleLengthGetterNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleMapNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTuplePoppedNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTuplePushedNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleReversedNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleShiftedNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleSliceNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleSortedNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleSplicedNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleToStringNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleUnshiftedNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleValueOfNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleWithNodeGen;

/**
 * Contains builtins for Tuple.prototype.
 */
public final class TuplePrototypeBuiltins extends JSBuiltinsContainer.SwitchEnum<TuplePrototypeBuiltins.TuplePrototype> {

    public static final JSBuiltinsContainer BUILTINS = new TuplePrototypeBuiltins();

    protected TuplePrototypeBuiltins() {
        super(JSTuple.PROTOTYPE_NAME, TuplePrototype.class);
    }

    public enum TuplePrototype implements BuiltinEnum<TuplePrototype> {
        length(0),
        valueOf(0),
        popped(0),
        pushed(1),
        reversed(0),
        shifted(0),
        slice(2),
        sorted(1),
        spliced(3),
        concat(1),
        includes(1),
        indexOf(1),
        join(1),
        lastIndexOf(1),
        entries(0),
        every(1),
        filter(1),
        find(1),
        findIndex(1),
        flat(0),
        flatMap(1),
        forEach(1),
        keys(1),
        map(1),
        reduce(1),
        reduceRight(1),
        some(1),
        unshifted(1),
        toLocaleString(0),
        toString(0),
        values(0),
        with(2);

        private final int len;

        TuplePrototype(int length) {
            this.len = length;
        }

        @Override
        public int getLength() {
            return len;
        }

        @Override
        public boolean isGetter() {
            return this == length;
        }
    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, TuplePrototype builtinEnum) {
        switch (builtinEnum) {
            case length:
                return JSTupleLengthGetterNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
            case valueOf:
                return JSTupleValueOfNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
            case popped:
                return JSTuplePoppedNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
            case pushed:
                return JSTuplePushedNodeGen.create(context, builtin, args().withThis().varArgs().createArgumentNodes(context));
            case reversed:
                return JSTupleReversedNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
            case shifted:
                return JSTupleShiftedNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
            case slice:
                return JSTupleSliceNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case sorted:
                return JSTupleSortedNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case spliced:
                return JSTupleSplicedNodeGen.create(context, builtin, args().withThis().varArgs().createArgumentNodes(context));
            case concat:
                return JSTupleConcatNodeGen.create(context, builtin, args().withThis().varArgs().createArgumentNodes(context));
            case includes:
                return JSArrayIncludesNodeGen.create(context, builtin, false, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case indexOf:
                return JSArrayIndexOfNodeGen.create(context, builtin, false, true, args().withThis().varArgs().createArgumentNodes(context));
            case join:
                return JSArrayJoinNodeGen.create(context, builtin, false, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case lastIndexOf:
                return JSArrayIndexOfNodeGen.create(context, builtin, false, false, args().withThis().varArgs().createArgumentNodes(context));
            case entries:
                return JSTupleIteratorNodeGen.create(context, builtin, JSRuntime.ITERATION_KIND_KEY_PLUS_VALUE, args().withThis().createArgumentNodes(context));
            case every:
                return JSArrayEveryNodeGen.create(context, builtin, false, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case filter:
                return JSTupleFilterNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case find:
                return JSArrayFindNodeGen.create(context, builtin, false, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case findIndex:
                return JSArrayFindIndexNodeGen.create(context, builtin, false, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case flat:
                return JSTupleFlatNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case flatMap:
                return JSTupleFlatMapNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case forEach:
                return JSArrayForEachNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case keys:
                return JSTupleIteratorNodeGen.create(context, builtin, JSRuntime.ITERATION_KIND_KEY, args().withThis().createArgumentNodes(context));
            case map:
                return JSTupleMapNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case reduce:
                return JSArrayReduceNodeGen.create(context, builtin, false, true, args().withThis().fixedArgs(1).varArgs().createArgumentNodes(context));
            case reduceRight:
                return JSArrayReduceNodeGen.create(context, builtin, false, false, args().withThis().fixedArgs(1).varArgs().createArgumentNodes(context));
            case some:
                return JSArraySomeNodeGen.create(context, builtin, false, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case unshifted:
                return JSTupleUnshiftedNodeGen.create(context, builtin, args().withThis().varArgs().createArgumentNodes(context));
            case toLocaleString:
                return JSArrayToLocaleStringNodeGen.create(context, builtin, false, args().withThis().createArgumentNodes(context));
            case toString:
                return JSTupleToStringNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case values:
                return JSTupleIteratorNodeGen.create(context, builtin, JSRuntime.ITERATION_KIND_VALUE, args().withThis().createArgumentNodes(context));
            case with:
                return JSTupleWithNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
        }
        return null;
    }

    public abstract static class JSTupleLengthGetterNode extends JSBuiltinNode {

        public JSTupleLengthGetterNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected long doTuple(Tuple thisObj) {
            return thisObj.getArraySize();
        }

        @Specialization(guards = {"isJSTuple(thisObj)"})
        protected long doJSTuple(DynamicObject thisObj) {
            return JSTuple.valueOf(thisObj).getArraySize();
        }

        @Fallback
        protected long doOther(Object thisObj) {
            throw Errors.createTypeErrorIncompatibleReceiver(thisObj);
        }
    }

    public abstract static class JSTupleToStringNode extends JSBuiltinNode {

        public JSTupleToStringNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected String toString(Tuple thisObj) {
            return thisObj.toString();
        }

        @Specialization(guards = {"isJSTuple(thisObj)"})
        protected String toString(DynamicObject thisObj) {
            return JSTuple.valueOf(thisObj).toString();
        }

        @Fallback
        protected void toStringNoTuple(@SuppressWarnings("unused") Object thisObj) {
            throw Errors.createTypeError("Tuple.prototype.toString requires that 'this' be a Tuple");
        }
    }

    public abstract static class JSTupleIteratorNode extends BasicTupleOperation {

        @Child private ArrayPrototypeBuiltins.CreateArrayIteratorNode createArrayIteratorNode;

        public JSTupleIteratorNode(JSContext context, JSBuiltin builtin, int iterationKind) {
            super(context, builtin);
            this.createArrayIteratorNode = ArrayPrototypeBuiltins.CreateArrayIteratorNode.create(context, iterationKind);
        }

        @Specialization
        protected DynamicObject doObject(VirtualFrame frame, Object thisObj,
                                         @Cached("createToObject(getContext())") JSToObjectNode toObjectNode) {
            Tuple tuple = thisTupleValue(thisObj);
            Object obj = toObjectNode.execute(tuple);
            return createArrayIteratorNode.execute(frame, obj);
        }
    }

    public abstract static class JSTupleValueOfNode extends JSBuiltinNode {

        public JSTupleValueOfNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Tuple doTuple(Tuple thisObj) {
            return thisObj;
        }

        @Specialization(guards = "isJSTuple(thisObj)")
        protected Tuple doJSTuple(DynamicObject thisObj) {
            return JSTuple.valueOf(thisObj);
        }

        @Fallback
        protected void fallback(@SuppressWarnings("unused") Object thisObj) {
            throw Errors.createTypeError("Tuple.prototype.valueOf requires that 'this' be a Tuple");
        }
    }

    public abstract static class JSTuplePoppedNode extends JSBuiltinNode {

        public JSTuplePoppedNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Tuple doTuple(Tuple thisObj) {
            return getPoppedTuple(thisObj);
        }

        @Specialization(guards = "isJSTuple(thisObj)")
        protected Tuple doJSTuple(DynamicObject thisObj) {
            Tuple tuple = JSTuple.valueOf(thisObj);
            return getPoppedTuple(tuple);
        }

        private Tuple getPoppedTuple(Tuple tuple) {
            if (tuple.getArraySize() <= 1) {
                return Tuple.EMPTY_TUPLE;
            }
            Object[] values = tuple.getElements();
            return Tuple.create(Arrays.copyOf(values, values.length - 1));
        }

        @Fallback
        protected void fallback(@SuppressWarnings("unused") Object thisObj) {
            throw Errors.createTypeError("Tuple.prototype.popped requires that 'this' be a Tuple");
        }
    }

    public abstract static class JSTuplePushedNode extends BasicTupleOperation {
        public JSTuplePushedNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Tuple doTuple(Tuple thisObj, Object[] args) {
            return getPushedTuple(thisObj, args);
        }

        @Specialization(guards = "isJSTuple(thisObj)")
        protected Tuple doJSTuple(DynamicObject thisObj, Object[] args) {
            Tuple tuple = JSTuple.valueOf(thisObj);
            return getPushedTuple(tuple, args);
        }

        private Tuple getPushedTuple(Tuple tuple, Object[] args) {
            long targetSize = tuple.getArraySize() + args.length;
            checkSize(targetSize);

            Object[] values = Arrays.copyOf(tuple.getElements(), (int) (targetSize));
            for (int i = 0; i < args.length; i++) {
                Object value = args[i];
                if (!JSRuntime.isJSPrimitive(value)) {
                    throw Errors.createTypeError("Tuples cannot contain non-primitive values");
                }
                values[tuple.getArraySizeInt() + i] = value;
            }
            return Tuple.create(values);
        }

        @Fallback
        protected void fallback(@SuppressWarnings("unused") Object thisObj, @SuppressWarnings("unused") Object args) {
            throw Errors.createTypeError("Tuple.prototype.popped requires that 'this' be a Tuple");
        }
    }

    public abstract static class JSTupleReversedNode extends JSBuiltinNode {

        public JSTupleReversedNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Tuple doTuple(Tuple thisObj) {
            return getReversedTuple(thisObj);
        }

        @Specialization(guards = "isJSTuple(thisObj)")
        protected Tuple doJSTuple(DynamicObject thisObj) {
            Tuple tuple = JSTuple.valueOf(thisObj);
            return getReversedTuple(tuple);
        }

        private Tuple getReversedTuple(Tuple tuple) {
            Object[] values = new Object[tuple.getArraySizeInt()];
            for (int i = 0; i < values.length; i++) {
                values[i] = tuple.getElement(values.length - i - 1);
            }
            return Tuple.create(values);
        }

        @Fallback
        protected void fallback(@SuppressWarnings("unused") Object thisObj) {
            throw Errors.createTypeError("Tuple.prototype.reversed requires that 'this' be a Tuple");
        }
    }

    public abstract static class JSTupleShiftedNode extends JSBuiltinNode {

        public JSTupleShiftedNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Tuple doTuple(Tuple thisObj) {
            return getShiftedTuple(thisObj);
        }

        @Specialization(guards = "isJSTuple(thisObj)")
        protected Tuple doJSTuple(DynamicObject thisObj) {
            Tuple tuple = JSTuple.valueOf(thisObj);
            return getShiftedTuple(tuple);
        }

        private Tuple getShiftedTuple(Tuple tuple) {
            if (tuple.getArraySize() == 0) {
                return tuple;
            }
            return Tuple.create(Arrays.copyOfRange(tuple.getElements(), 1, tuple.getArraySizeInt()));
        }

        @Fallback
        protected void fallback(@SuppressWarnings("unused") Object thisObj) {
            throw Errors.createTypeError("Tuple.prototype.shifted requires that 'this' be a Tuple");
        }
    }

    public abstract static class BasicTupleOperation extends JSBuiltinNode {

        private final BranchProfile errorProfile = BranchProfile.create();

        public BasicTupleOperation(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        protected Tuple thisTupleValue(Object value) {
            if (value instanceof Tuple) {
                return (Tuple) value;
            }
            if (JSTuple.isJSTuple(value)) {
                return JSTuple.valueOf((DynamicObject) value);
            }
            errorProfile.enter();
            throw Errors.createTypeError("'this' must be a Tuple");
        }

        protected Tuple toTupleValue(Object obj) {
            if (obj instanceof Tuple) {
                return (Tuple) obj;
            }
            if (JSTuple.isJSTuple(obj)) {
                return JSTuple.valueOf((JSDynamicObject) obj);
            }
            throw Errors.createTypeError("'this' must be a Tuple");
        }

        protected void checkSize(long size) {
            if (size > JSRuntime.MAX_SAFE_INTEGER) {
                throw Errors.createTypeError("length too big");
            }
        }
    }

    public abstract static class JSTupleSliceNode extends BasicTupleOperation {

        public JSTupleSliceNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Tuple slice(Object thisObj, Object begin, Object end,
                              @Cached("create()") JSToIntegerAsLongNode toIntegerAsLong) {
            Tuple tuple = toTupleValue(thisObj);
            long size = tuple.getArraySize();

            long startPos = toIntegerAsLong.executeLong(begin);
            long endPos = end == Undefined.instance ? size : toIntegerAsLong.executeLong(end);

            startPos = startPos < 0 ? Math.max(size + startPos, 0) : Math.min(startPos, size);
            endPos = endPos < 0 ? Math.max(size + endPos, 0) : Math.min(endPos, size);

            if (startPos >= endPos) {
                return Tuple.EMPTY_TUPLE;
            }
            return Tuple.create(Arrays.copyOfRange(tuple.getElements(), (int) startPos, (int) endPos));
        }
    }

    public abstract static class JSTupleSortedNode extends BasicTupleOperation {

        @Child private IsCallableNode isCallableNode;

        public JSTupleSortedNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object sorted(Object thisObj, Object comparefn) {
            checkCompareFunction(comparefn);
            Tuple tuple = toTupleValue(thisObj);
            long size = tuple.getArraySize();

            if (size < 2) {
                // nothing to do
                return thisObj;
            }

            Object[] values = tuple.getElements();
            sortIntl(getComparator(comparefn), values);

            return Tuple.create(values);
        }

        private void checkCompareFunction(Object compare) {
            if (!(compare == Undefined.instance || isCallable(compare))) {
                throw Errors.createTypeError("The comparison function must be either a function or undefined");
            }
        }

        protected final boolean isCallable(Object callback) {
            if (isCallableNode == null) {
                transferToInterpreterAndInvalidate();
                isCallableNode = insert(IsCallableNode.create());
            }
            return isCallableNode.executeBoolean(callback);
        }

        private Comparator<Object> getComparator(Object comparefn) {
            if (comparefn == Undefined.instance) {
                return JSArray.DEFAULT_JSARRAY_COMPARATOR;
            } else {
                assert isCallable(comparefn);
                return new SortComparator(comparefn);
            }
        }

        private class SortComparator implements Comparator<Object> {
            private final Object compFnObj;
            private final boolean isFunction;

            SortComparator(Object compFnObj) {
                this.compFnObj = compFnObj;
                this.isFunction = JSFunction.isJSFunction(compFnObj);
            }

            @Override
            public int compare(Object arg0, Object arg1) {
                if (arg0 == Undefined.instance) {
                    if (arg1 == Undefined.instance) {
                        return 0;
                    }
                    return 1;
                } else if (arg1 == Undefined.instance) {
                    return -1;
                }
                Object retObj;
                if (isFunction) {
                    retObj = JSFunction.call((DynamicObject) compFnObj, Undefined.instance, new Object[]{arg0, arg1});
                } else {
                    retObj = JSRuntime.call(compFnObj, Undefined.instance, new Object[]{arg0, arg1});
                }
                int res = convertResult(retObj);
                return res;
            }

            private int convertResult(Object retObj) {
                if (retObj instanceof Integer) {
                    return (int) retObj;
                } else {
                    double d = JSRuntime.toDouble(retObj);
                    if (d < 0) {
                        return -1;
                    } else if (d > 0) {
                        return 1;
                    } else {
                        // +/-0 or NaN
                        return 0;
                    }
                }
            }
        }

        @TruffleBoundary
        private static void sortIntl(Comparator<Object> comparator, Object[] array) {
            try {
                Arrays.sort(array, comparator);
            } catch (IllegalArgumentException e) {
                // Collections.sort throws IllegalArgumentException when
                // Comparison method violates its general contract

                // See ECMA spec 15.4.4.11 Array.prototype.sort (comparefn).
                // If "comparefn" is not undefined and is not a consistent
                // comparison function for the elements of this array, the
                // behaviour of sort is implementation-defined.
            }
        }
    }

    public abstract static class JSTupleSplicedNode extends BasicTupleOperation {

        public JSTupleSplicedNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Tuple spliced(Object thisObj, Object[] args,
                                @Cached("create()") JSToIntegerAsLongNode toIntegerAsLong) {
            Object start = JSRuntime.getArgOrUndefined(args, 0);
            Object deleteCount = JSRuntime.getArgOrUndefined(args, 1);

            Tuple tuple = toTupleValue(thisObj);
            long size = tuple.getArraySize();

            long startPos = toIntegerAsLong.executeLong(start);
            startPos = startPos < 0 ? Math.max(size + startPos, 0) : Math.min(startPos, size);

            long insertCount, delCount;
            if (args.length == 0) {
                insertCount = 0;
                delCount = 0;
            } else if (args.length == 1) {
                insertCount = 0;
                delCount = size - startPos;
            } else {
                insertCount = args.length - 2;
                delCount = toIntegerAsLong.executeLong(deleteCount);
                delCount = Math.min(Math.max(delCount, 0), size - startPos);
            }

            checkSize(size + insertCount - delCount);
            Object[] values = new Object[(int) (size + insertCount - delCount)];

            int k = 0;
            while (k < startPos) {
                values[k] = tuple.getElement(k);
                k++;
            }
            for (int i = 2; i < args.length; i++) {
                if (!JSRuntime.isJSPrimitive(args[i])) {
                    throw Errors.createTypeError("Tuples cannot contain non-primitive values");
                }
                values[k] = args[i];
                k++;
            }
            for (int i = (int) (startPos + delCount); i < size; i++) {
                values[k] = tuple.getElement(i);
                k++;
            }

            return Tuple.create(values);
        }
    }

    public abstract static class JSTupleConcatNode extends BasicTupleOperation {

        private final BranchProfile growProfile = BranchProfile.create();

        @Child private JSIsConcatSpreadableNode isConcatSpreadableNode;
        @Child private JSToObjectNode toObjectNode;
        @Child private JSHasPropertyNode hasPropertyNode;
        @Child private ReadElementNode readElementNode;
        @Child private JSGetLengthNode getLengthNode;

        public JSTupleConcatNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Tuple concat(Object thisObj, Object[] args) {
            Tuple tuple = toTupleValue(thisObj);
            SimpleArrayList<Object> list = new SimpleArrayList<>(1 + JSConfig.SpreadArgumentPlaceholderCount);
            concatElement(tuple, list);
            for (Object arg : args) {
                concatElement(arg, list);
            }
            return Tuple.create(list.toArray());
        }

        private void concatElement(Object e, SimpleArrayList<Object> list) {
            if (isConcatSpreadable(e)) {

                // TODO: re-evaluate, check proposal for changes
                // The ToObject(e) call is not according to current proposal spec, but it wouldn't work otherwise.
                e = toObject(e);

                long len = getLengthOfArrayLike(e);
                for (long k = 0; k < len; k++) {
                    if (hasProperty(e, k)) {
                        Object subElement = get(e, k);
                        if (JSRuntime.isObject(subElement)) {
                            throw Errors.createTypeError("Tuples cannot contain non-primitive values");
                        }
                        list.add(subElement, growProfile);
                    }
                }
            } else {
                if (JSRuntime.isObject(e)) {
                    throw Errors.createTypeError("Tuples cannot contain non-primitive values");
                }
                list.add(e, growProfile);
            }
        }

        private boolean isConcatSpreadable(Object object) {
            if (isConcatSpreadableNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isConcatSpreadableNode = insert(JSIsConcatSpreadableNode.create(getContext()));
            }
            return isConcatSpreadableNode.execute(object);
        }

        private Object toObject(Object object) {
            if (toObjectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toObjectNode = insert(JSToObjectNode.createToObjectNoCheck(getContext()));
            }
            return toObjectNode.execute(object);
        }

        private long getLengthOfArrayLike(Object obj) {
            if (getLengthNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getLengthNode = insert(JSGetLengthNode.create(getContext()));
            }
            return getLengthNode.executeLong(obj);
        }

        private boolean hasProperty(Object obj, long idx) {
            if (hasPropertyNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hasPropertyNode = insert(JSHasPropertyNode.create());
            }
            return hasPropertyNode.executeBoolean(obj, idx);
        }

        private Object get(Object target, long index) {
            if (readElementNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readElementNode = insert(ReadElementNode.create(getContext()));
            }
            if (JSRuntime.longIsRepresentableAsInt(index)) {
                return readElementNode.executeWithTargetAndIndex(target, (int) index);
            } else {
                return readElementNode.executeWithTargetAndIndex(target, (double) index);
            }
        }
    }

    public abstract static class JSTupleFilterNode extends BasicTupleOperation {

        private final BranchProfile growProfile = BranchProfile.create();

        public JSTupleFilterNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Tuple filter(Object thisObj, Object callbackfn, Object thisArg,
                               @Cached IsCallableNode isCallableNode,
                               @Cached JSToBooleanNode toBooleanNode,
                               @Cached("createCall()") JSFunctionCallNode callNode) {
            Tuple tuple = thisTupleValue(thisObj);
            if (!isCallableNode.executeBoolean(callbackfn)) {
                throw Errors.createTypeErrorCallableExpected();
            }
            SimpleArrayList<Object> list = SimpleArrayList.create(tuple.getArraySize());
            for (long k = 0; k < tuple.getArraySize(); k++) {
                Object value = tuple.getElement(k);
                boolean selected = toBooleanNode.executeBoolean(
                        callNode.executeCall(JSArguments.create(thisArg, callbackfn, value, k, tuple))
                );
                if (selected) {
                    list.add(value, growProfile);
                }
            }
            return Tuple.create(list.toArray());
        }
    }

    public abstract static class TupleFlattenOperation extends BasicTupleOperation {

        private final BranchProfile errorProfile = BranchProfile.create();

        @Child
        private JSFunctionCallNode callNode;

        public TupleFlattenOperation(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        protected Object call(Object[] arguments) {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(JSFunctionCallNode.createCall());
            }
            return callNode.executeCall(arguments);
        }

        protected void flattenIntoTuple(SimpleArrayList<Object> target, BranchProfile growProfile, Tuple source, long depth, Object mapperFunction, Object thisArg) {
            for (int i = 0; i < source.getArraySize(); i++) {
                Object element = source.getElement(i);
                if (mapperFunction != null) {
                    element = call(JSArguments.create(thisArg, mapperFunction, element, i, source));
                    if (JSRuntime.isObject(element)) {
                        errorProfile.enter();
                        throw Errors.createTypeError("Tuples cannot contain non-primitive values");
                    }
                }
                if (depth > 0 && element instanceof Tuple) {
                    flattenIntoTuple(target, growProfile, (Tuple) element, depth - 1, mapperFunction, thisArg);
                } else {
                    target.add(element, growProfile);
                }
            }
        }
    }

    public abstract static class JSTupleFlatNode extends TupleFlattenOperation {

        private final BranchProfile growProfile = BranchProfile.create();

        @Child private JSToIntegerAsLongNode toIntegerNode = JSToIntegerAsLongNode.create();

        public JSTupleFlatNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Tuple flat(Object thisObj, Object depth) {
            Tuple tuple = thisTupleValue(thisObj);
            long depthNum = 1;
            if (depth != Undefined.instance) {
                depthNum = toIntegerNode.executeLong(depth);
            }
            SimpleArrayList<Object> list = SimpleArrayList.create(tuple.getArraySize());
            flattenIntoTuple(list, growProfile, tuple, depthNum, null, null);
            return Tuple.create(list.toArray());
        }
    }

    public abstract static class JSTupleFlatMapNode extends TupleFlattenOperation {

        private final BranchProfile growProfile = BranchProfile.create();

        @Child private IsCallableNode isCallableNode = IsCallableNode.create();

        public JSTupleFlatMapNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Tuple flatMap(Object thisObj, Object mapperFunction, Object thisArg) {
            Tuple tuple = thisTupleValue(thisObj);
            if (!isCallableNode.executeBoolean(mapperFunction)) {
                throw Errors.createTypeErrorCallableExpected();
            }
            SimpleArrayList<Object> list = SimpleArrayList.create(tuple.getArraySize());
            flattenIntoTuple(list, growProfile, tuple, 1, mapperFunction, thisArg);
            return Tuple.create(list.toArray());
        }
    }

    public abstract static class JSTupleMapNode extends BasicTupleOperation {

        private final BranchProfile growProfile = BranchProfile.create();
        private final BranchProfile errorProfile = BranchProfile.create();

        @Child private IsCallableNode isCallableNode = IsCallableNode.create();
        @Child private JSFunctionCallNode callNode;

        public JSTupleMapNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Tuple map(Object thisObj, Object mapperFunction, Object thisArg) {
            Tuple tuple = thisTupleValue(thisObj);
            if (!isCallableNode.executeBoolean(mapperFunction)) {
                throw Errors.createTypeErrorCallableExpected();
            }
            SimpleArrayList<Object> list = SimpleArrayList.create(tuple.getArraySize());
            for (long k = 0; k < tuple.getArraySize(); k++) {
                Object value = tuple.getElement(k);
                value = call(JSArguments.create(thisArg, mapperFunction, value, k, tuple));
                if (JSRuntime.isObject(value)) {
                    errorProfile.enter();
                    throw Errors.createTypeError("Tuples cannot contain non-primitive values");
                }
                list.add(value, growProfile);
            }
            return Tuple.create(list.toArray());
        }

        protected Object call(Object[] arguments) {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(JSFunctionCallNode.createCall());
            }
            return callNode.executeCall(arguments);
        }
    }

    public abstract static class JSTupleUnshiftedNode extends BasicTupleOperation {

        private final BranchProfile growProfile = BranchProfile.create();
        private final BranchProfile errorProfile = BranchProfile.create();

        public JSTupleUnshiftedNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Tuple unshifted(Object thisObj, Object[] args) {
            Tuple tuple = thisTupleValue(thisObj);
            SimpleArrayList<Object> list = SimpleArrayList.create(tuple.getArraySize());
            for (Object arg : args) {
                if (JSRuntime.isObject(arg)) {
                    errorProfile.enter();
                    throw Errors.createTypeError("Tuples cannot contain non-primitive values");
                }
                list.add(arg, growProfile);
            }
            for (long k = 0; k < tuple.getArraySize(); k++) {
                Object value = tuple.getElement(k);
                if (JSRuntime.isObject(value)) {
                    errorProfile.enter();
                    throw Errors.createTypeError("Tuples cannot contain non-primitive values");
                }
                list.add(value, growProfile);
            }
            return Tuple.create(list.toArray());
        }
    }

    public abstract static class JSTupleWithNode extends BasicTupleOperation {

        private final BranchProfile rangeErrorProfile = BranchProfile.create();
        private final BranchProfile primitiveErrorProfile = BranchProfile.create();

        @Child private JSToIndexNode toIndexNode = JSToIndexNode.create();

        public JSTupleWithNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Tuple with(Object thisObj, Object index, Object value) {
            Tuple tuple = thisTupleValue(thisObj);
            Object[] list = tuple.getElements();
            long i = toIndexNode.executeLong(index);
            if (i >= list.length) {
                rangeErrorProfile.enter();
                throw Errors.createRangeError("Index out of range");

            }
            if (JSRuntime.isObject(value)) {
                primitiveErrorProfile.enter();
                throw Errors.createTypeError("Tuples cannot contain non-primitive values");
            }
            list[(int) i] = value;
            return Tuple.create(list);
        }
    }
}
