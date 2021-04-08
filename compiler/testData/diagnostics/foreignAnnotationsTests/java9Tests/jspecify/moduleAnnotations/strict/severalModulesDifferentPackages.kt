// ALLOW_KOTLIN_PACKAGE
// JSPECIFY_STATE: strict

// FILE: module1/module-info.java
// MODULE_NAME: module1
import org.jspecify.nullness.NullMarked;

@NullMarked
module module1 {
    requires java9_annotations;
    exports test1;
}

// FILE: module1/test1/Test.java
package test1;

public class Test {
    public void foo(Integer x) {}
}

// FILE: module2/module-info.java
// MODULE_NAME: module2
module module2 {
    exports test2;
}

// FILE: module2/test2/Test.java
package test2;

public class Test {
    public void foo(Integer x) {}
}

// FILE: main.kt
fun main(x: test1.Test, y: test2.Test) {
    x.foo(<!NULL_FOR_NONNULL_TYPE!>null<!>)
    y.foo(null)
}