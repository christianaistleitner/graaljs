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
import com.oracle.truffle.js.nodes.cast.JSToIntegerAsLongNode;
import com.oracle.truffle.js.nodes.cast.JSToObjectNode;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.nodes.tuples.IsConcatSpreadableNode;
import com.oracle.truffle.js.nodes.unary.IsCallableNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Tuple;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.JSArray;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSProxy;
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
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleIteratorNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTuplePoppedNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTuplePushedNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleReversedNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleShiftedNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleSliceNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleSortedNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleSplicedNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleToStringNodeGen;
import static com.oracle.truffle.js.builtins.TuplePrototypeBuiltinsFactory.JSTupleValueOfNodeGen;

/**
 * Contains builtins for Tuple.prototype.
 */
public final class TuplePrototypeBuiltins extends JSBuiltinsContainer.SwitchEnum<TuplePrototypeBuiltins.TuplePrototype> {

    public static final JSBuiltinsContainer BUILTINS = new TuplePrototypeBuiltins();

    protected TuplePrototypeBuiltins() {
        super(JSTuple.PROTOTYPE_NAME, TuplePrototype.class);
    }

    public enum TuplePrototype implements BuiltinEnum<TuplePrototype> {
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
        // TODO: entries(1),
        every(1),
        // TODO: filter(1),
        find(1),
        findIndex(1),
        // TODO: flat(1),
        // TODO: flatMap(1),
        forEach(1),
        // TODO: keys(1),
        // TODO: map(1),
        reduce(1),
        reduceRight(1),
        some(1),
        // TODO: unshifted(1),
        toLocaleString(0),
        toString(0),
        values(0);
        // TODO: with(1);

        private final int length;

        TuplePrototype(int length) {
            this.length = length;
        }

        @Override
        public int getLength() {
            return length;
        }
    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, TuplePrototype builtinEnum) {
        switch (builtinEnum) {
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
            case every:
                return JSArrayEveryNodeGen.create(context, builtin, false, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case find:
                return JSArrayFindNodeGen.create(context, builtin, false, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case findIndex:
                return JSArrayFindIndexNodeGen.create(context, builtin, false, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case forEach:
                return JSArrayForEachNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case reduce:
                return JSArrayReduceNodeGen.create(context, builtin, false, true, args().withThis().fixedArgs(1).varArgs().createArgumentNodes(context));
            case reduceRight:
                return JSArrayReduceNodeGen.create(context, builtin, false, false, args().withThis().fixedArgs(1).varArgs().createArgumentNodes(context));
            case some:
                return JSArraySomeNodeGen.create(context, builtin, false, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case toLocaleString:
                return JSArrayToLocaleStringNodeGen.create(context, builtin, false, args().withThis().createArgumentNodes(context));
            case toString:
                return JSTupleToStringNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case values:
                return JSTupleIteratorNodeGen.create(context, builtin, JSRuntime.ITERATION_KIND_VALUE, args().withThis().createArgumentNodes(context));
        }
        return null;
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
        protected void toStringNoTuple(Object thisObj) {
            throw Errors.createTypeError("Tuple.prototype.toString requires that 'this' be a Tuple");
        }
    }

    public abstract static class JSTupleIteratorNode extends JSBuiltinNode {
        @Child private ArrayPrototypeBuiltins.CreateArrayIteratorNode createArrayIteratorNode;

        public JSTupleIteratorNode(JSContext context, JSBuiltin builtin, int iterationKind) {
            super(context, builtin);
            this.createArrayIteratorNode = ArrayPrototypeBuiltins.CreateArrayIteratorNode.create(context, iterationKind);
        }

        @Specialization(guards = "isJSObject(thisObj)")
        protected DynamicObject doJSObject(VirtualFrame frame, DynamicObject thisObj) {
            return createArrayIteratorNode.execute(frame, thisObj);
        }

        @Specialization(guards = "!isJSObject(thisObj)")
        protected DynamicObject doNotJSObject(
                VirtualFrame frame,
                Object thisObj,
                @Cached("createToObject(getContext())") JSToObjectNode toObjectNode
        ) {
            return createArrayIteratorNode.execute(frame, toObjectNode.execute(thisObj));
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
            return Tuple.create(Arrays.copyOf(values, values.length-1));
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
            Object[] values = new Object[tuple.getArraySizeInt()];
            if(tuple.getArraySize() == 0) {
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

        public BasicTupleOperation(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
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

        @Child private IsConcatSpreadableNode isConcatSpreadableNode;
        @Child private JSHasPropertyNode hasPropertyNode;
        @Child private ReadElementNode readElementNode;
        @Child private JSGetLengthNode getLengthNode;
        @Child private JSArrayFirstElementIndexNode firstElementIndexNode;
        @Child private JSArrayLastElementIndexNode lastElementIndexNode;
        @Child private JSArrayNextElementIndexNode nextElementIndexNode;

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

        private void concatElement(Object el, SimpleArrayList<Object> list) {
            if (isConcatSpreadable(el)) {
                long len = getLength(el);
                if (len > 0) {
                    concatSpreadable(el, len, list);
                }
            } else {
                if (!JSRuntime.isJSPrimitive(el)) {
                    throw Errors.createTypeError("Tuples cannot contain non-primitive values");
                }
                list.add(el, growProfile);
            }
        }

        private boolean isConcatSpreadable(Object object) {
            if (isConcatSpreadableNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isConcatSpreadableNode = insert(IsConcatSpreadableNode.create(getContext()));
            }
            return isConcatSpreadableNode.execute(object);
        }

        private void concatSpreadable(Object el, long len, SimpleArrayList<Object> list) {
            if (JSRuntime.isTuple(el)) {
                Tuple tuple = (Tuple) el;
                for (long k = 0; k < tuple.getArraySize(); k++) {
                    list.add(tuple.getElement(k), growProfile);
                }
            } else if (JSProxy.isJSProxy(el) || !JSDynamicObject.isJSDynamicObject(el)) {
                // strictly to the standard implementation; traps could expose optimizations!
                for (long k = 0; k < len; k++) {
                    if (hasProperty(el, k)) {
                        list.add(get(el, k), growProfile);
                    }
                }
            } else if (len == 1) {
                // fastpath for 1-element entries
                if (hasProperty(el, 0)) {
                    list.add(get(el, 0), growProfile);
                }
            } else {
                long k = firstElementIndex((DynamicObject) el, len);
                long lastI = lastElementIndex((DynamicObject) el, len);
                for (; k <= lastI; k = nextElementIndex(el, k, len)) {
                    list.add(get(el, k), growProfile);
                }
            }
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

        private long firstElementIndex(DynamicObject target, long length) {
            if (firstElementIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                firstElementIndexNode = insert(JSArrayFirstElementIndexNode.create(getContext()));
            }
            return firstElementIndexNode.executeLong(target, length);
        }

        private long lastElementIndex(DynamicObject target, long length) {
            if (lastElementIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lastElementIndexNode = insert(JSArrayLastElementIndexNode.create(getContext()));
            }
            return lastElementIndexNode.executeLong(target, length);
        }

        private long nextElementIndex(Object target, long currentIndex, long length) {
            if (nextElementIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nextElementIndexNode = insert(JSArrayNextElementIndexNode.create(getContext()));
            }
            return nextElementIndexNode.executeLong(target, currentIndex, length);
        }

        private long getLength(Object obj) {
            if (getLengthNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getLengthNode = insert(JSGetLengthNode.create(getContext()));
            }
            return getLengthNode.executeLong(obj);
        }
    }
}
