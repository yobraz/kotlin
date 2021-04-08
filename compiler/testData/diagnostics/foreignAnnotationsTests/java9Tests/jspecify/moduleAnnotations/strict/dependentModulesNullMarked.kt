// ALLOW_KOTLIN_PACKAGE
// JSPECIFY_STATE: strict

// FILE: module1/module-info.java
// MODULE_NAME: module1
module module1 {
}

// FILE: module2/module-info.java
// MODULE_NAME: module2
import org.jspecify.nullness.NullMarked;

@NullMarked
module module2 {
    requires java9_annotations;
    requires module1;
    exports test2;
}

// FILE: module2/test2/Test.java
package test2;

public class Test {
    public void foo(Integer x) {}
}

// FILE: main.kt
fun main(y: test2.Test) {
    y.foo(<!NULL_FOR_NONNULL_TYPE!>null<!>)
}