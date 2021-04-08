// ALLOW_KOTLIN_PACKAGE
// JSPECIFY_STATE: strict

// FILE: sandbox/module-info.java
// MODULE_NAME: sandbox
module sandbox {
    requires java9_annotations;
    exports test;
}

// FILE: sandbox/test/package-info.java
@NullMarked
package test;

import org.jspecify.nullness.NullMarked;

// FILE: sandbox/test/Foo.java
package test;

class Foo {}

// FILE: sandbox2/module-info.java
// MODULE_NAME: sandbox2
module sandbox2 {
    requires java9_annotations;
    exports test;
}

// FILE: sandbox2/test/Test.java
package test;

public class Test {
    public void foo(Integer x) {}
}

// FILE: main.kt
import test.Test

fun main(x: Test) {
    x.foo(null)
}