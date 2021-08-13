// MODULE: m1
// FILE: m1.kt

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class Property(
    val description: String = "",
    val ignore: Boolean = false,

    val externalName: String = ""
)

// MODULE: m2(m1)
// FILE: m2.kt

open class LocatableRunConfigurationOptions {
    @Property(ignore = true)
    var isNameGenerated = false
}

// MODULE: m3(m2)
// FILE: m3.kt

class JvmConfigurationOptions : LocatableRunConfigurationOptions()

fun box(): String {
    JvmConfigurationOptions()
    return "OK"
}