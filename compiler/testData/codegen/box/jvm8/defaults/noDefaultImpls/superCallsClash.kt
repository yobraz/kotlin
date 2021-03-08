// !JVM_DEFAULT_MODE: all
// TARGET_BACKEND: JVM
// JVM_TARGET: 1.8
// WITH_RUNTIME

class AndroidTargetConfigurator :
    AndroidModuleConfigurator,
    ModuleConfiguratorWithTests {

    override fun getConfiguratorSettings(): String =
        { super<AndroidModuleConfigurator>.getConfiguratorSettings() + super<ModuleConfiguratorWithTests>.getConfiguratorSettings()}()

}


interface ModuleConfiguratorWithTests : ModuleConfiguratorWithSettings {
    override fun getConfiguratorSettings() = "K"
}

interface ModuleConfiguratorWithSettings  {
    fun getConfiguratorSettings(): String = "fail"
}

fun main() {
    AndroidTargetConfigurator().getConfiguratorSettings()
}

interface AndroidModuleConfigurator :
    ModuleConfiguratorWithSettings {
    override fun getConfiguratorSettings() = "O"
}


fun box(): String {
    return AndroidTargetConfigurator().getConfiguratorSettings()
}