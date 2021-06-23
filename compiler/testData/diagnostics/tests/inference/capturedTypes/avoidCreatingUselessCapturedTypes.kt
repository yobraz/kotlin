// FIR_IDENTICAL
// WITH_RUNTIME

fun <L> foo(x: () -> L): L = null as L

object Entities {
//    val map: Map<String, Int> = hashMapOf(
//        "aa" to foo { '1' }
//    )

    val map2: Map<String, () -> CharSequence> = hashMapOf(
        "&Aacute;" to { java.lang.StringBuilder() },
        "&Aacute" to { "" },
        "&zwnj;" to { "8204" })
}
