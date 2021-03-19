/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// Auto-generated file. DO NOT EDIT!

package kotlin.collections

/** An iterator over a sequence of values of type `UByte`. */
internal abstract class UByteIterator : Iterator<UByte> {
    final override fun next() = nextUByte()

    /** Returns the next value in the sequence without boxing. */
    public abstract fun nextUByte(): UByte
}

/** An iterator over a sequence of values of type `UShort`. */
internal abstract class UShortIterator : Iterator<UShort> {
    final override fun next() = nextUShort()

    /** Returns the next value in the sequence without boxing. */
    public abstract fun nextUShort(): UShort
}

/** An iterator over a sequence of values of type `UInt`. */
internal abstract class UIntIterator : Iterator<UInt> {
    final override fun next() = nextUInt()

    /** Returns the next value in the sequence without boxing. */
    public abstract fun nextUInt(): UInt
}

/** An iterator over a sequence of values of type `ULong`. */
internal abstract class ULongIterator : Iterator<ULong> {
    final override fun next() = nextULong()

    /** Returns the next value in the sequence without boxing. */
    public abstract fun nextULong(): ULong
}

