interface X
interface Y {
    fun o() = "O"
}
interface A : X, Y
interface B : Y, X
interface I {
    fun k() = "K"
}
open class O()
class C : O(), I
class D : O(), I
fun getAorB(): A | B = object : B {}
fun getCOrD(): C | D = D()
fun getO(y: Y) = y.o()
fun getK(i: I) = i.k()

fun box() = getO(getAorB()) + getK(getCOrD())

