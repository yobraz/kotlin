class A(val bn: B?)
class B(val cn: C?)
class C(val s: String)

fun test(an: A?, z: String) = an?.bn?.cn?.s ?: z