// JSPECIFY_STATE: warn
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
    take(<!NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS!>Test2().foo()<!>)
}
