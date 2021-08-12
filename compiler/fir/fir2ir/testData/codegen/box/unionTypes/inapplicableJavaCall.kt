// FILE: foo.kt
open class A
class B : A()
class C : A()
class D : A()

class F{
    fun test(a: C | B) {}
}

// FILE: Caller.java

public class Caller {
    private final F f = new F();
    public void a() {
        f.test(new A());
    }
    public void b() {
        f.test(new B());
    }
    public void c() {
        f.test(new C());
    }
    public void d() {
        f.test(new D());
    }
}

// FILE: box.kt

fun box(): String {
    var result = ""
    val caller = Caller()

    try {
        caller.a()
    } catch (e: IllegalArgumentException) {
        result += "O"
    }

    caller.b()
    caller.c()

    try {
        caller.d()
    } catch (e: IllegalArgumentException) {
        result += "K"
    }

    return result
}
