/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.gradle

import org.junit.Test
import java.io.File

public class DirtyLookupIT : BaseGradleIT() {

    @Test //https://youtrack.jetbrains.com/issue/KT-28233
    fun testChangeTypeAlias() {
        val project = Project("simpleNewIncremental")
        project.setupWorkingDir()

        project.build(":lib:build") {
            assertSuccessful()
        }

        project.projectDir.resolve("lib/src/main/kotlin/foo/types.kt").writeText("typealias Type = Int")

        project.projectDir.resolve("lib/src/main/kotlin/foo/KotlinClassToUpdate.kt")
            .replaceText("get() = \"0\"", " get() = 0")

        project.build("build") {
            assertSuccessful()
        }
    }

    private fun File.replaceText(oldText: String, newText: String) {
        writeText(readText().replace(oldText, newText))
    }

    @Test //https://youtrack.jetbrains.com/issue/KT-40656
    fun testCompanionObjectChanges() {
        val project = Project("incrementalMultiproject")
        project.setupWorkingDir()

        project.build("build") {
            assertSuccessful()
        }

        project.projectDir.resolve("src/kotlin/test/TestClass")
            .writeText(
                "package test\n" +
                        "class TestClass {\n" +
                        "    companion object {\n" +
                        "        privateconst val text = \"some simple text...\"\n" +
                        "    }\n" +
                        "}"
            )

        project.build("build") {
            assertFailed()
        }
    }

    @Test //https://youtrack.jetbrains.com/issue/KT-25455
    fun testOverrideMethod() {
        val project = Project("simpleNewIncremental")
        project.setupWorkingDir()

        project.build("build") {
            assertSuccessful()
        }

        project.projectDir.resolve("lib/src/main/kotlin/foo/NewKotlinOpenClass")
            .writeText(
                "package foo\n" +
                        "open class NewKotlinOpenClass {}"
            )

        project.build("build") {
            assertFailed()
        }
    }

    @Test //https://youtrack.jetbrains.com/issue/KT-13677
    fun changeMemberVisibility() {
        val project = Project("simpleNewIncremental")
        project.setupWorkingDir()

        project.build("build") {
            assertSuccessful()
        }

        project.projectDir.resolve("lib/src/main/kotlin/bar/lib.kt")
            .appendText("fun useOverrideProtectedMethod(arg: foo.NewKotlinOpenClass) {\n" +
                                "    arg.methodToOverride()\n" +
                                "}")

        project.projectDir.resolve("main/src/main/kotlin/bar/main.kt")
            .appendText("fun useOverrideProtectedMethod(arg: foo.NewKotlinOpenClass) {\n" +
                                "    arg.methodToOverride()\n" +
                                "}")

        project.projectDir.resolve("lib/src/main/kotlin/foo/KotlinOpenClass.kt")
            .replaceText("open protected fun protectedMethod() {}", "open public fun protectedMethod() {}")

        project.build("build") {
            assertSuccessful()
        }

    }
}
