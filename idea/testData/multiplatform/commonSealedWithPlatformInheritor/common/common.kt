// ISSUE: KT-45848

sealed class <!LINE_MARKER("descr='Is subclassed by Derived PlatfromDerived'")!>Base<!>

class Derived : Base()

fun test_1(b: Base) = when (b) {
    is Derived -> 1
}
