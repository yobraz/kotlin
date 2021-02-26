// JSPECIFY_STATE: strict
// !DIAGNOSTICS: -UNUSED_PARAMETER

// FILE: test/package-info.java
@DefaultNonNull
package test;

import org.jspecify.annotations.DefaultNonNull;

// FILE: test/Test2.java
package test;

import Foo;

public class Test2 {
    public Foo<String> foo() { return new Foo<>(); };
}

// FILE: main.kt
import test.Test2

class Foo<A>

fun take(x: Foo<in String?>) {}

fun main() {
    take(<!TYPE_MISMATCH!>Test2().foo()<!>)
}
