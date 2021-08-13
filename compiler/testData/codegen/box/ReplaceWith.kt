// WITH_RUNTIME
// MODULE: m1
// FILE: m1.kt
interface StateStorageManager {
    fun expandMacro(collapsedPath: String): String

    @Deprecated(level = DeprecationLevel.ERROR, message = "Use expandMacro(collapsedPath)", replaceWith = ReplaceWith("expandMacro(collapsedPath)"))
    fun expandMacros(collapsedPath: String): String = expandMacro(collapsedPath)
}

// MODULE: m2(m1)
// FILE: m2.kt

abstract class StateStorageManagerImpl : StateStorageManager {
}

class ApplicationStorageManager : StateStorageManagerImpl() {
    override fun expandMacro(collapsedPath: String): String {
        return collapsedPath
    }
}

fun box(): String = ApplicationStorageManager().expandMacro("OK")
