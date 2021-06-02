/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("NON_MEMBER_FUNCTION_NO_BODY")

package kotlin.js

internal fun jsEqeq(a: Any?, b: Any?): Boolean

internal fun jsNotEq(a: Any?, b: Any?): Boolean

internal fun jsEqeqeq(a: Any?, b: Any?): Boolean

internal fun jsNotEqeq(a: Any?, b: Any?): Boolean

internal fun jsGt(a: Any?, b: Any?): Boolean

internal fun jsGtEq(a: Any?, b: Any?): Boolean

internal fun jsLt(a: Any?, b: Any?): Boolean

internal fun jsLtEq(a: Any?, b: Any?): Boolean

internal fun jsNot(a: Any?): Boolean

internal fun jsUnaryPlus(a: Any?): Any?

internal fun jsUnaryMinus(a: Any?): Any?

internal fun jsPrefixInc(a: Any?): Any?

internal fun jsPostfixInc(a: Any?): Any?

internal fun jsPrefixDec(a: Any?): Any?

internal fun jsPostfixDec(a: Any?): Any?

internal fun jsPlus(a: Any?, b: Any?): Any?

internal fun jsMinus(a: Any?, b: Any?): Any?

internal fun jsMult(a: Any?, b: Any?): Any?

internal fun jsDiv(a: Any?, b: Any?): Any?

internal fun jsMod(a: Any?, b: Any?): Any?

internal fun jsPlusAssign(a: Any?, b: Any?): Any?

internal fun jsMinusAssign(a: Any?, b: Any?): Any?

internal fun jsMultAssign(a: Any?, b: Any?): Any?

internal fun jsDivAssign(a: Any?, b: Any?): Any?

internal fun jsModAssign(a: Any?, b: Any?): Any?

internal fun jsAnd(a: Any?, b: Any?): Any?

internal fun jsOr(a: Any?, b: Any?): Any?

internal fun jsBitAnd(a: Any?, b: Any?): Int

internal fun jsBitOr(a: Any?, b: Any?): Int

internal fun jsBitXor(a: Any?, b: Any?): Int

internal fun jsBitNot(a: Any?): Int

internal fun jsBitShiftR(a: Any?, b: Any?): Int

internal fun jsBitShiftRU(a: Any?, b: Any?): Int

internal fun jsBitShiftL(a: Any?, b: Any?): Int

internal fun jsInstanceOf(a: Any?, b: Any?): Boolean

internal fun jsNewTarget(a: Any?): Any?

internal fun emptyObject(a: Any?): Any?

internal fun openInitializerBox(a: Any?, b: Any?): Any?

internal fun jsArrayLength(a: Any?): Any?

internal fun jsArrayGet(a: Any?, b: Any?): Any?

internal fun jsArraySet(a: Any?, b: Any?, c: Any?): Any?

internal fun arrayLiteral(a: Any?): Any?

internal fun int8Array(a: Any?): Any?

internal fun int16Array(a: Any?): Any?

internal fun int32Array(a: Any?): Any?

internal fun float32Array(a: Any?): Any?

internal fun float64Array(a: Any?): Any?

internal fun int8ArrayOf(a: Any?): Any?

internal fun int16ArrayOf(a: Any?): Any?

internal fun int32ArrayOf(a: Any?): Any?

internal fun float32ArrayOf(a: Any?): Any?

internal fun float64ArrayOf(a: Any?): Any?

internal fun <T> Object_create(): T

internal fun <T> sharedBox_create(v: T?): dynamic

internal fun <T> sharedBox_read(box: dynamic): T?

internal fun <T> sharedBox_write(box: dynamic, nv: T?)

internal fun _undefined(): Nothing?

internal fun <T> DefaultType(): T

internal fun _jsBind_(receiver: Any?, target: Any?): Any?

internal fun <A> slice(a: A): A

internal fun _unreachable(): Nothing

@Suppress("REIFIED_TYPE_PARAMETER_NO_INLINE")
internal fun <reified T : Any> _jsClass(): JsClass<T>

/**
 * Function corresponding to JavaScript's `typeof` operator
 */
public fun jsTypeOf(value: Any?): String

// Returns true if the specified property is in the specified object or its prototype chain.
internal fun jsIn(lhs: Any?, rhs: Any): Boolean

internal fun jsDelete(e: Any?)