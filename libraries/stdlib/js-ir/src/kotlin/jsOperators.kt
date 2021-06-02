/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("UNUSED_PARAMETER")

package kotlin.js

internal inline fun jsDeleteProperty(obj: Any, property: Any) {
    jsDelete(obj.asDynamic()[property])
}
