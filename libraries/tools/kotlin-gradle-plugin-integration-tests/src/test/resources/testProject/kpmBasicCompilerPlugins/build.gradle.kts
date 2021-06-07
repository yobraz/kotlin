import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.*

plugins {
    kotlin("multiplatform.pm20")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("org.jetbrains.kotlin.plugin.noarg")
    id("org.jetbrains.kotlin.plugin.sam.with.receiver")
}

repositories {
    mavenCentral()
    mavenLocal()
}

allOpen {
    annotation("lib.AllOpen")
    preset("spring")
}

noArg {
    annotation("lib.NoArg")
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform.pm20") {
    configure<KotlinPm20ProjectExtension> {
        mainAndTest {
            jvm
        }
    }
}