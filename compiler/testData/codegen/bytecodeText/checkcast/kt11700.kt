// TARGET_BACKEND: JVM_IR

// FILE: j/JBase.java
package j;
interface JBase {
    void foo();
}

// FILE: j/JSub.java
package j;
public interface JSub extends JBase{}

// FILE: j/JBaseImpl.java
package j;
public class JBaseImpl implements JBase {
    @Override
    public void foo() {}
}

// FILE: j/JSubImpl.java
package j;
public class JSubImpl implements JSub {
    @Override
    public void foo() {}
}

// FILE: j/JOperation.java
package j;
public class JOperation {
    public void forJBase(JBase jb) {
        jb.foo();
    }
}

// FILE: main.kt
package k

import j.JBaseImpl
import j.JOperation
import j.JSub
import j.JSubImpl

fun test() {
    JOperation().forJBase(JBaseImpl())
}

fun <T : JBaseImpl> testGeneric(t: T) {
    JOperation().forJBase(t)
}

fun <T> testMultipleBounds(t: T) where T : Any, T : JSub {
    JOperation().forJBase(t)
}

// 0 CHECKCAST j/JBase
