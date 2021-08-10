// FIR_IDENTICAL
// !DIAGNOSTICS: -EXPERIMENTAL_IS_NOT_ENABLED
// WITH_RUNTIME

import kotlin.experimental.ExperimentalTypeInference

class Foo<K>

@OptIn(ExperimentalTypeInference::class)
fun <K> buildFoo(@BuilderInference builderAction: Foo<K>.() -> Unit): Foo<K> = Foo()

fun <L> Foo<L>.bar() {}

fun <K> id(x: K) = x

fun main() {
    val x = <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>buildFoo<!> {
        val y = id(::bar)
    }
}