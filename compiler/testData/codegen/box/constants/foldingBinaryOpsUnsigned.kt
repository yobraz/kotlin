
val a = "CONCAT " + 0x8fffffffU

fun box(): String {
    if (a != "CONCAT 2415919103") {
        return "FAIL 0: $a"
    }
    return "OK"
}
