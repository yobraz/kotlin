// FIR_IDENTICAL
// !USE_EXPERIMENTAL: kotlin.RequiresOptIn

@RequiresOptIn
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CONSTRUCTOR)
annotation class Experimental

interface Foo {
    @Experimental val foo: Int
}

data class Bar @Experimental constructor(override val foo: Int): Foo

fun foo(bar: Bar) {
    bar.foo // no EXPERIMENTAL_API_USAGE_ERROR
}
