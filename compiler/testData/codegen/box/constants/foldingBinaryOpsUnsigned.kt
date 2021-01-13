// WITH_RUNTIME
// IGNORE_BACKEND: WASM

val a = "INT " + 0x8fffffffU
val b = "BYTE " + 0x8ffU
val c = "LONG " + 0xffff_ffff_ffffU

fun box(): String {
    if (a != "INT 2415919103") {
        return "FAIL 0: $a"
    }
    if (b != "BYTE 2303") {
        return "FAIL 1: $b"
    }
    if (c != "LONG 281474976710655") {
        return "FAIL 1: $c"
    }
    return "OK"
}
