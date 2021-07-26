// FILE: a/Test.kt

package a

private val thing = mutableListOf("x", "y")
    public get(): List<String>

// FILE: b/Fest.kt

package b

private val thing = 4
    public get(): Number

// FILE: c/Main.kt

package c

import a.thing
import b.thing

fun main() {
    println(thing.size)
    println(thing + 1)
    println(thing)
    println(thing.toString())
}

<!REDECLARATION!>val testA = 10<!>
<!REDECLARATION!>val testA = "test"<!>

fun test() {
    println(testA)
}

fun fest() {
    val festA = 10
    val festA = "test"
    println(festA)
}
