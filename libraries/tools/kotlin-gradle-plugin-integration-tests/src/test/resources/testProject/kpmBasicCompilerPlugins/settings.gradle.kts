pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }

    val kotlin_version: String by settings
    plugins {
        kotlin("multiplatform.pm20").version(kotlin_version)
    }

    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.jetbrains.kotlin.plugin.allopen" -> useModule("org.jetbrains.kotlin:kotlin-allopen:$kotlin_version")
                "org.jetbrains.kotlin.plugin.noarg" -> useModule("org.jetbrains.kotlin:kotlin-noarg:$kotlin_version")
                "org.jetbrains.kotlin.plugin.sam.with.receiver" -> useModule("org.jetbrains.kotlin:kotlin-sam-with-receiver:$kotlin_version")
            }
        }
    }
}

rootProject.name = "kpmAllOpenSimple"