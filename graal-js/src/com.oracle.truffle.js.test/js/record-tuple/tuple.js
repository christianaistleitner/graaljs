/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */

/**
 * Test for checking basic tuple functionality.
 *
 * @option ecmascript-version=2022
 */

load('../assert.js');

let a, b, c, d, e, f;

/*
 * Test 1:
 * valid tuple literals
 */
const t1a = #[];
const t1b = #[1, 2];
const t1c = #[1, 2, #[3]];
// TODO: const t1d = #[1, 2, #{ a: 3 }];
const t1e = #[...t1b, 3];

/*
 * Test 2:
 * invalid tuple literals
 */
assertThrows(function() {
    eval("const x = #[,]"); // SyntaxError, holes are disallowed by syntax
}, SyntaxError);

/*
 * Test 3:
 * a confusing case
 */
let x, y = #[x = 0];
assertSame(0, x);
assertSame(#[0], y);

/*
 * Test 4:
 * typeof
 */
const t4 = 2;
assertSame("tuple", typeof #[t4]);
assertSame("tuple", typeof #[]);
assertSame("tuple", typeof #[#[]]);
assertSame("tuple", typeof #[1, 2]);

/*
 * Test 5:
 * Tuple.prototype.toString
 */
assertSame([].toString(), #[].toString());
assertSame([1].toString(), #[1].toString());
assertSame([1, 2, 3].toString(), #[1, 2, 3].toString());
assertSame([1, 2, [3]].toString(), #[1, 2, #[3]].toString());

/*
 * Test 6:
 * constructor
 */
assertThrows(function() {
    eval("const x = new Tuple()"); // TypeError: Tuple is not a constructor
}, TypeError);
assertThrows(function() {
    eval("class Test extends Tuple {} const x = new Test()"); // TypeError: Tuple is not a constructor
}, TypeError);

/*
 * Test 7:
 * Tuple function
 */
assertSame(Tuple().toString(), #[].toString());
assertSame(Tuple(1, 2).toString(), #[1, 2].toString());
assertSame(Tuple(1, #[2]).toString(), #[1, #[2]].toString());
// TODO: remove .toString() calls above / remove above
assertSame(Tuple(), #[]);
assertSame(Tuple(1, 2), #[1, 2]);
assertSame(Tuple(1, Tuple(2)), #[1, #[2]]);

/*
 * Test 8:
 * non-primitive values
 */
assertThrows(function() {
    eval("const x = #[1, {}]"); // TypeError: Tuples cannot contain non-primitive values
}, TypeError);
assertThrows(function() {
    eval("const x = Tuple(1, {})"); // TypeError: Tuples cannot contain non-primitive values
}, TypeError);

/*
 * Test 9:
 * Equality - test cases taken from https://github.com/tc39/proposal-record-tuple
 */
assertTrue(#[1] === #[1]);
assertTrue(#[1, 2] === #[1, 2]);
// TODO: assertTrue(Object(#[1, 2]) !== Object(#[1, 2]));

assertTrue(#[-0] === #[+0]);
// TODO: assertTrue(#[NaN] === #[NaN]);

assertTrue(#[-0] == #[+0]);
// TODO: assertTrue(#[NaN] == #[NaN]);
assertTrue(#[1] != #["1"]);

// TODO: assertTrue(!Object.is(#[-0], #[+0]));
// TODO: assertTrue(Object.is(#[NaN], #[NaN]));

// Map keys are compared with the SameValueZero algorithm
// TODO: assertTrue(new Map().set(#[1], true).get(#[1]));
// TODO: assertTrue(new Map().set(#[-0], true).get(#[0]));

/*
 * Test 10:
 * Tuple.isTuple
 */
assertTrue(Tuple.isTuple(#[1]));
assertTrue(Tuple.isTuple(Tuple(1)));
assertFalse(Tuple.isTuple(1));
assertFalse(Tuple.isTuple("1"));
assertFalse(Tuple.isTuple(BigInt(1)));

/*
 * Test 11:
 * Type Conversion
 */
a = #[42];
// 3.1.1 ToBoolean
assertSame(true, !!a); // JSToBigIntNode
if (a) { // JSToBooleanUnaryNode
    // ok
} else {
    fail("#[42] should be true")
}
// 3.1.2 ToNumber
assertThrows(function() {
    eval("a + 1"); // JSToNumberNode
}, TypeError);
assertThrows(function() {
    eval("Math.abs(a)"); // JSToDoubleNode
}, TypeError);
assertThrows(function() {
    eval("parseInt(\"1\", a)"); // JSToInt32Node
}, TypeError);
assertThrows(function() {
    eval("'ABC'.codePointAt(a)"); // JSToIntegerAsIntNode
}, TypeError);
assertThrows(function() {
    eval("[1].at(a)"); // JSToIntegerAsLongNode
}, TypeError);
assertThrows(function() {
    eval("'ABC'.split('', a);"); // JSToUInt32Node
}, TypeError);
// 3.1.3 ToBigInt
assertThrows(function() {
    eval("BigInt.asIntN(64, a)");
}, TypeError);
// 3.1.4 ToString
assertSame("42", a + ""); // JSToStringNode
assertSame([10] < [2], #[10] < #[2]); // JSToStringOrNumberNode
assertSame([1] < [1], #[1] < #[1]);
assertSame([1] <= 1, #[1] <= 1);
// 3.1.5 ToObject
// haven't found a test case yet

/*
 * Test 12:
 * Destructuring
 */
[a, ...b] = #[1, 2, 3];
assertSame(1, a);
assertSame(true, Array.isArray(b));
assertSame("2,3", b.toString());
assertSame(#[2, 3], #[...b]);

/*
 * Test 13:
 * Spreading
 */
a = #[1, 2];
b = [1, 2];
assertSame(#[1, 2, 3], #[...a, 3]);
assertSame(#[1, 2, 3], #[...b, 3]);

/*
 * Test 14:
 * Spreading
 */
a = #[1, 2];
b = [1, 2];
assertSame(#[1, 2, 3], #[...a, 3]);
assertSame(#[1, 2, 3], #[...b, 3]);

/*
 * Test 15:
 * Access (using index)
 */
a = #[1, "2", BigInt(42)];
b = [1, "2", BigInt(42)];
for (let i = -1; i < 5; i++) {
    assertSame(b[i], a[i]);
}
