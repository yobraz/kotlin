pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }

    plugins {
        val kotlin_version: String by settings
        kotlin("multiplatform.kpm").version(kotlin_version)
    }
}

rootProject.name = "kpmTransientPluginOptions"