// ALLOW_KOTLIN_PACKAGE
// JSPECIFY_STATE: strict

// FILE: module1/module-info.java
// MODULE_NAME: module1
module module1 {
    exports test;
}

// FILE: module1/test/Test1.java
package test;

public class Test1 {
    public void foo(Integer x) {}
}

// FILE: module2/module-info.java
// MODULE_NAME: module2
import org.jspecify.nullness.NullMarked;

@NullMarked
module module2 {
    requires java9_annotations;
    exports test;
}

// FILE: module2/test/Test2.java
package test;

public class Test2 {
    public void foo(Integer x) {}
}

// FILE: main.kt
import test.Test1
import test.Test2

fun main(x: Test1, y: Test2) {
    x.foo(null)
    y.foo(<!NULL_FOR_NONNULL_TYPE!>null<!>)
}