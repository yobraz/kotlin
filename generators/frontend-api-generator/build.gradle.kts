plugins {
    kotlin("jvm")
    id("jps-compatible")
}

sourceSets {
    "main" { java.srcDirs("main") }
    "test" { projectDefault() }
}

dependencies {
    api(kotlinStdlib("jdk8"))

    testApi(projectTests(":generators:test-generator"))
    testApi(projectTests(":compiler:tests-common"))
    testApi(projectTests(":compiler:tests-spec"))
    testApi(projectTests(":idea-frontend-fir:idea-fir-low-level-api"))
    testApi(projectTests(":idea-frontend-fir"))
    testApi(intellijCoreDep()) { includeJars("intellij-core", "guava", rootProject = rootProject) }
    testApiJUnit5()
}

val generateFrontendApiTests by generator("org.jetbrains.kotlin.generators.tests.frontend.api.GenerateTestsKt")
