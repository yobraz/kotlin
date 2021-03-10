@org.jspecify.nullness.DefaultNonNull
public class A {
    public void foo(String x) {}

    @org.jspecify.nullness.Nullable
    public String bar() { return null; }
}
