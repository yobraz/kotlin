class A(val b: B)
class B(val c: C)
class C(val s: String)

fun test(an: A?) = an?.b?.c?.s ?: "zap"

// JVM_IR_TEMPLATES
// 1 DUP
// 1 IFNULL
// 1 GOTO
// 1 POP
// 0 ACONST_NULL
