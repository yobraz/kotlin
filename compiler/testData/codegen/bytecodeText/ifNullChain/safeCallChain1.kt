class A(val b: B)
class B(val c: C)
class C(val s: String)

fun test(an: A?) = an?.b?.c?.s

// JVM_IR_TEMPLATES
// 1 IFNULL
// 1 ACONST_NULL
// 1 DUP
// 1 POP
// 1 GOTO
