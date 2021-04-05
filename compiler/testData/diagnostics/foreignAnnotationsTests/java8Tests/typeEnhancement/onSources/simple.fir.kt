// WITH_FOREIGN_ANNOTATIONS
// FILE: A.java

import org.checkerframework.checker.nullness.qual.*;
import java.util.*;

class A {
    List<@NonNull String> foo() { return null; }
}
