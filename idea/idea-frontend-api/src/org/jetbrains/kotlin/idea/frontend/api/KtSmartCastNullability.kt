/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api

/**
 * The nullability of an expression, based on smart cast types derived from data-flow analysis facts. Example:
 * ```
 *   fun foo(s: String?) {
 *     s  // KtSmartCastNullability of `s` here is UNKNOWN
 *     if (s != null) {
 *       s  // KtSmartCastNullability.NOT_NULL
 *     } else {
 *       s  // KtSmartCastNullability.NULL
 *     }
 *     s!!
 *     // KtSmartCastNullability of `s` from this point is NOT_NULL
 *   }
 * ```
 * Note that the nullability returned here is from "stable" smart cast types. The
 * [spec](https://kotlinlang.org/spec/type-inference.html#smart-cast-sink-stability) provides an explanation on smart cast stability.
 */
enum class KtSmartCastNullability(val canBeNull: Boolean, val canBeNonNull: Boolean) {
    NULL(true, false),
    NOT_NULL(false, true),
    UNKNOWN(true, true);
}