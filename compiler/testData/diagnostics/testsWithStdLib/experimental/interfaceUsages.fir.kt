// !USE_EXPERIMENTAL: kotlin.RequiresOptIn
// FILE: api.kt

package api

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
annotation class InternalMarker

interface Foo {
    @InternalMarker // Declaration that efficiently forbids implementation of this interface
    val doNotImplementThisInterface: Unit
}

@InternalMarker
interface Bar {
    fun bar()
}

// FILE: usageFoo.kt

package usage

import api.*

class Usage : Foo {
    override val doNotImplementThisInterface: Unit
        get() = Unit
}

class DelegatedUsage(foo: Foo): Foo by foo

// FILE: usageBar.kt

package usage.bar

import api.*

class Usage : <!EXPERIMENTAL_API_USAGE!>Bar<!> {
    override fun bar() {}
}

class DelegatedUsage(bar: <!EXPERIMENTAL_API_USAGE!>Bar<!>): <!EXPERIMENTAL_API_USAGE, EXPERIMENTAL_API_USAGE!>Bar<!> by bar
