class A(val bn: B?)
class B(val cn: C?)
class C(val s: String)

fun test(an: A?) = an?.bn?.cn?.s

// JVM_IR_TEMPLATES
// 3 DUP
// 3 IFNULL
// 1 POP
// 1 ACONST_NULL
// 1 GOTO
