// FILE: kt45853b.kt
abstract class A {
    open val a: A? get() = null
}

class B() : AX2() {
    override fun getA(): X? = super.a
}

// FILE: X.java
public interface X {
    X getA();
}

// FILE: AX.java
public abstract class AX extends A implements X {
    @Override
    public AX getA() {
        return (AX) super.getA();
    }
}

//FILE: AX2.java
public abstract class AX2 extends AX {}