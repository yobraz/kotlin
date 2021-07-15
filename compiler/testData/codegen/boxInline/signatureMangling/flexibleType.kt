// TARGET_BACKEND: JVM_IR
// WITH_RUNTIME
// FULL_JDK
// FILE: 1.kt
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Files
import java.nio.file.FileSystems
import java.nio.file.Path

inline fun callReadAttributes() {
    val p = FileSystems.getDefault().getPath("/")
    val attributes = Files.readAttributes(p, "*")
}

// FILE: 2.kt
fun box(): String {
    callReadAttributes()
    return "OK"
}