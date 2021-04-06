// WITH_FOREIGN_ANNOTATIONS
// FULL_JDK_9

// FILE: module-info.java
// INCLUDE_JAVA_AS_BINARY
import org.jspecify.nullness.NullMarked;

@NullMarked
module sandbox {
    requires org.jspecify;
    requires kotlin.stdlib;
}

// FILE: Test.java
public class Test {
    void foo(Integer x) {}
}

// FILE: main.kt
fun main(x: Test) {
    x.foo(1)
}