// !USE_EXPERIMENTAL: kotlin.RequiresOptIn

@RequiresOptIn
annotation class MyInternal
abstract class BaseClass @MyInternal constructor()
class Subclass @MyInternal constructor(): <!EXPERIMENTAL_API_USAGE_ERROR!>BaseClass<!>()
