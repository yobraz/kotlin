/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.native

import org.jetbrains.kotlin.gradle.BaseGradleIT
import org.jetbrains.kotlin.gradle.GradleVersionRequired
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.DUMMY_FRAMEWORK_TASK_NAME
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.POD_BUILD_TASK_NAME
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.POD_DOWNLOAD_TASK_NAME
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.POD_GEN_TASK_NAME
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.POD_IMPORT_TASK_NAME
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.POD_INSTALL_TASK_NAME
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.POD_SETUP_BUILD_TASK_NAME
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.POD_SPEC_TASK_NAME
import org.jetbrains.kotlin.gradle.transformProjectWithPluginsDsl
import org.jetbrains.kotlin.gradle.util.modify
import org.jetbrains.kotlin.gradle.util.runProcess
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

private val String.validFrameworkName: String
    get() = replace('-', '_')
private val invalidTaskNameCharacters = "[/\\\\:<>\"?*|]".toRegex()

private val String.validTaskName
    get() = replace(invalidTaskNameCharacters, "_")


class CocoaPodsIT : BaseGradleIT() {

    override val defaultGradleVersion: GradleVersionRequired
        get() = GradleVersionRequired.FOR_MPP_SUPPORT

    // We use Kotlin DSL. Earlier Gradle versions fail at accessors codegen.
    private val gradleVersion = GradleVersionRequired.FOR_MPP_SUPPORT

    private val defaultFamily = Family.IOS

    val PODFILE_IMPORT_DIRECTIVE_PLACEHOLDER = "<import_mode_directive>"

    private val cocoapodsSingleKtPod = "native-cocoapods-single"
    private val cocoapodsMultipleKtPods = "native-cocoapods-multiple"
    private val templateProjectName = "native-cocoapods-template"
    private val groovyTemplateProjectName = "native-cocoapods-template-groovy"

    private val dummyTaskName = ":$DUMMY_FRAMEWORK_TASK_NAME"
    private val podspecTaskName = ":$POD_SPEC_TASK_NAME"
    private val podDownloadTaskName = ":$POD_DOWNLOAD_TASK_NAME"
    private val podGenTaskName = ":$POD_GEN_TASK_NAME"
    private val podBuildTaskName = ":$POD_BUILD_TASK_NAME"
    private val podSetupBuildTaskName = ":$POD_SETUP_BUILD_TASK_NAME"
    private val podImportTaskName = ":$POD_IMPORT_TASK_NAME"
    private val podInstallTaskName = ":$POD_INSTALL_TASK_NAME"
    private val cinteropTaskName = ":cinterop"

    private val defaultPodRepo = "https://github.com/AFNetworking/AFNetworking"
    private val defaultPodName = "AFNetworking"
    private val defaultLibraryPodName = "YandexMapKit"
    private val downloadUrlPodName = "podspecWithFilesExample"
    private val downloadUrlRepoName = "https://github.com/alozhkin/podspecWithFilesExample/raw/master"
    private val defaultTarget = "IOS"
    private val defaultFamilyName = defaultFamily.name
    private val defaultSDK = "iphonesimulator"
    private val defaultPodspecTaskName = podspecFullTaskName()
    private val defaultPodDownloadTaskName = podDownloadFullTaskName()
    private val defaultPodGenTaskName = podGenFullTaskName()
    private val defaultBuildTaskName = podBuildFullTaskName()
    private val defaultSetupBuildTaskName = podSetupBuildFullTaskName()
    private val defaultCinteropTaskName = cinteropTaskName + defaultPodName + defaultTarget
    private val downloadUrlTaskName = podDownloadTaskName + downloadUrlPodName.capitalize()

    private fun podDownloadFullTaskName(podName: String = defaultPodName) = podDownloadTaskName + podName.capitalize()

    private fun podGenFullTaskName(familyName: String = defaultFamilyName) = podGenTaskName + familyName.capitalize()

    private fun podspecFullTaskName(familyName: String = defaultFamilyName) = podspecTaskName + familyName.capitalize()

    private fun podSetupBuildFullTaskName(podName: String = defaultPodName, sdkName: String = defaultSDK) =
        podSetupBuildTaskName + podName.capitalize() + sdkName.capitalize()

    private fun podBuildFullTaskName(podName: String = defaultPodName, sdkName: String = defaultSDK) =
        podBuildTaskName + podName.capitalize() + sdkName.capitalize()

    private fun cinteropFullTaskName(podName: String = defaultPodName, targetName: String = defaultTarget) =
        cinteropTaskName + podName.capitalize() + targetName.capitalize()

    private lateinit var hooks: CustomHooks
    private lateinit var project: BaseGradleIT.Project

    @Before
    fun configure() {
        hooks = CustomHooks()
        project = getProjectByName(templateProjectName)
    }

    @Test
    fun testPodspecSingle() = doTestPodspec(
        cocoapodsSingleKtPod,
        mapOf("kotlin-library" to null),
        mapOf("kotlin-library" to kotlinLibraryPodspecContent())
    )

    @Test
    fun testPodspecCustomFrameworkNameSingle() = doTestPodspec(
        cocoapodsSingleKtPod,
        mapOf("kotlin-library" to "MultiplatformLibrary"),
        mapOf("kotlin-library" to kotlinLibraryPodspecContent("MultiplatformLibrary"))
    )

    @Test
    fun testXcodeUseFrameworksSingle() = doTestXcode(
        cocoapodsSingleKtPod,
        ImportMode.FRAMEWORKS,
        "ios-app", mapOf("kotlin-library" to null)
    )

    @Test
    fun testXcodeUseFrameworksWithCustomFrameworkNameSingle() = doTestXcode(
        cocoapodsSingleKtPod,
        ImportMode.FRAMEWORKS,
        "ios-app",
        mapOf("kotlin-library" to "MultiplatformLibrary")
    )

    @Test
    fun testXcodeUseModularHeadersSingle() = doTestXcode(
        cocoapodsSingleKtPod,
        ImportMode.MODULAR_HEADERS,
        "ios-app",
        mapOf("kotlin-library" to null)
    )

    @Test
    fun testXcodeUseModularHeadersWithCustomFrameworkNameSingle() = doTestXcode(
        cocoapodsSingleKtPod,
        ImportMode.MODULAR_HEADERS,
        "ios-app",
        mapOf("kotlin-library" to "MultiplatformLibrary")
    )

    @Test
    fun testPodImportSingle() = doTestImportSingle()

    @Test
    fun testPodImportMultiple() = doTestImportMultiple()

    @Test
    fun testPodspecMultiple() = doTestPodspec(
        cocoapodsMultipleKtPods,
        mapOf("kotlin-library" to null, "second-library" to null),
        mapOf("kotlin-library" to kotlinLibraryPodspecContent(), "second-library" to secondLibraryPodspecContent()),
    )

    @Test
    fun testPodspecCustomFrameworkNameMultiple() = doTestPodspec(
        cocoapodsMultipleKtPods,
        mapOf("kotlin-library" to "FirstMultiplatformLibrary", "second-library" to "SecondMultiplatformLibrary"),
        mapOf(
            "kotlin-library" to kotlinLibraryPodspecContent("FirstMultiplatformLibrary"),
            "second-library" to secondLibraryPodspecContent("SecondMultiplatformLibrary")
        )
    )

    @Test
    fun testXcodeUseFrameworksMultiple() = doTestXcode(
        cocoapodsMultipleKtPods,
        ImportMode.FRAMEWORKS,
        null,
        mapOf("kotlin-library" to null, "second-library" to null)
    )

    @Test
    fun testXcodeUseFrameworksWithCustomFrameworkNameMultiple() = doTestXcode(
        cocoapodsMultipleKtPods,
        ImportMode.FRAMEWORKS,
        null,
        mapOf("kotlin-library" to "FirstMultiplatformLibrary", "second-library" to "SecondMultiplatformLibrary")
    )

    @Test
    fun testXcodeUseModularHeadersMultiple() = doTestXcode(
        cocoapodsMultipleKtPods,
        ImportMode.MODULAR_HEADERS,
        null,
        mapOf("kotlin-library" to null, "second-library" to null)
    )

    @Test
    fun testXcodeUseModularHeadersWithCustomFrameworkNameMultiple() = doTestXcode(
        cocoapodsMultipleKtPods,
        ImportMode.MODULAR_HEADERS,
        null,
        mapOf("kotlin-library" to "FirstMultiplatformLibrary", "second-library" to "SecondMultiplatformLibrary")
    )

    private fun doTestSpecReposImport(podName: String, podRepo: String, family: Family? = null) {
        with(project.gradleBuildScript()) {
            addPod(podName, family)
            addSpecRepo(podRepo)
        }
        project.testImportWithAsserts(listOf(podRepo))
    }

    @Test
    fun testSpecReposImport() {
        val podName = "example"
        val podRepo = "https://github.com/alozhkin/spec_repo"
        doTestSpecReposImport(podName, podRepo)
    }

    @Test
    fun testSpecReposImportPerPlatformConfiguration() {
        val podName = "example"
        val podRepo = "https://github.com/alozhkin/spec_repo"
        val family = defaultFamily
        doTestSpecReposImport(podName, podRepo, family)
    }

    @Test
    fun testPodDownloadGitNoTagNorCommit() {
        doTestGit()
    }

    @Test
    fun testPodDownloadGitNoTagNorCommitPerPlatformConfiguration() {
        doTestGit(family = defaultFamily)
    }

    @Test
    fun testPodDownloadGitTag() {
        doTestGit(tag = "4.0.0")
    }

    @Test
    fun testPodDownloadGitTagPerPlatformConfiguration() {
        doTestGit(tag = "4.0.0", family = defaultFamily)
    }

    @Test
    fun testPodDownloadGitCommit() {
        doTestGit(commit = "9c07ac0a5645abb58850253eeb109ed0dca515c1")
    }

    @Test
    fun testPodDownloadGitCommitPerPlatformConfiguration() {
        doTestGit(commit = "9c07ac0a5645abb58850253eeb109ed0dca515c1", family = defaultFamily)
    }

    @Test
    fun testPodDownloadGitBranch() {
        doTestGit(branch = "2974")
    }

    @Test
    fun testPodDownloadGitBranchPerPlatformConfiguration() {
        doTestGit(branch = "2974", family = defaultFamily)
    }

    private fun doTestPodDownloadGitSubspec(family: Family? = null) {
        doTestGit(
            repo = "https://github.com/SDWebImage/SDWebImage.git",
            pod = "SDWebImage/MapKit",
            tag = "5.9.2",
            family = family
        )
    }

    @Test
    fun testPodDownloadGitSubspec() = doTestPodDownloadGitSubspec()

    @Test
    fun testPodDownloadGitSubspecPerPlatformConfiguration() = doTestPodDownloadGitSubspec(defaultFamily)

    private fun doTestPodDownloadGitBranchAndCommit(family: Family? = null) {
        val branch = "2974"
        val commit = "21637dd6164c0641e414bdaf3885af6f1ef15aee"
        with(project.gradleBuildScript()) {
            addPod(defaultPodName, family, produceGitBlock(defaultPodRepo, branchName = branch, commitName = commit))
        }
        hooks.addHook {
            checkGitRepo(commitName = commit)
        }
        project.testDownload(listOf(defaultPodRepo))
    }

    fun testPodDownloadGitBranchAndCommit() = doTestPodDownloadGitBranchAndCommit()

    @Test
    fun testPodDownloadGitBranchAndCommitPerPlatformConfiguration() = doTestPodDownloadGitBranchAndCommit(defaultFamily)

    // tag priority is bigger than branch priority
    private fun doTestPodDownloadGitBranchAndTag(family: Family? = null) {
        val branch = "2974"
        val tag = "4.0.0"
        with(project.gradleBuildScript()) {
            addPod(defaultPodName, family, produceGitBlock(defaultPodRepo, branchName = branch, tagName = tag))
        }
        hooks.addHook {
            checkGitRepo(tagName = tag)
        }
        project.testDownload(listOf(defaultPodRepo))
    }

    @Test
    fun testPodDownloadGitBranchAndTag() = doTestPodDownloadGitBranchAndTag()

    @Test
    fun testPodDownloadGitBranchAndTagPerPlatformConfiguration() = doTestPodDownloadGitBranchAndTag(defaultFamily)

    @Test
    fun testPodDownloadUrlZip() = doTestPodDownloadUrl("zip")

    @Test
    fun testPodDownloadUrlZipPerPlatformConfiguration() = doTestPodDownloadUrl("zip", family = defaultFamily)

    @Test
    fun testPodDownloadUrlTar() = doTestPodDownloadUrl("tar")

    @Test
    fun testPodDownloadUrlTarPerPlatformConfiguration() = doTestPodDownloadUrl("tar", family = defaultFamily)

    @Test
    fun testPodDownloadUrlGZ() = doTestPodDownloadUrl("tar.gz")

    @Test
    fun testPodDownloadUrlGZPerPlatformConfiguration() = doTestPodDownloadUrl("tar.gz", family = defaultFamily)

    @Test
    fun testPodDownloadUrlBZ2() = doTestPodDownloadUrl("tar.bz2")

    @Test
    fun testPodDownloadUrlBZ2PerPlatformConfiguration() = doTestPodDownloadUrl("tar.bz2", family = defaultFamily)

    @Test
    fun testPodDownloadUrlJar() = doTestPodDownloadUrl("jar")

    @Test
    fun testPodDownloadUrlJarPerPlatformConfiguration() = doTestPodDownloadUrl("jar", family = defaultFamily)

    @Test
    fun testPodDownloadUrlWrongName() = doTestPodDownloadUrl(fileExtension = "zip", archiveName = "wrongName")

    @Test
    fun testPodDownloadUrlWrongNamePerPlatformConfiguration() =
        doTestPodDownloadUrl(fileExtension = "zip", archiveName = "wrongName", family = defaultFamily)

    private fun doTestDownloadAndImport(family: Family? = null) {
        val tag = "4.0.0"
        with(project.gradleBuildScript()) {
            addPod(defaultPodName, family, produceGitBlock(defaultPodRepo, tagName = tag))
        }
        hooks.addHook {
            checkGitRepo(tagName = tag)
        }
        project.testImportWithAsserts(listOf(defaultPodRepo))
    }

    @Test
    fun testDownloadAndImport() = doTestDownloadAndImport()

    @Test
    fun testDownloadAndImportPerPlatformConfiguration() = doTestDownloadAndImport(defaultFamily)

    @Test
    fun warnIfDeprecatedPodspecPathIsUsed() {
        project = getProjectByName(cocoapodsSingleKtPod)
        hooks.addHook {
            assertContains(
                listOf("Deprecated DSL found on ${project.projectDir.absolutePath}", "kotlin-library", "build.gradle.kts")
                    .joinToString(separator = File.separator)
            )
        }
        project.test(":kotlin-library:podDownload")
    }

    @Test
    fun errorIfVersionIsNotSpecified() {
        with(project.gradleBuildScript()) {
            useLines { lines ->
                lines.filter { line -> "version = \"1.0\"" !in line }.joinToString(separator = "\n")
            }.also { writeText(it) }
        }
        hooks.addHook {
            assertContains("Cocoapods Integration requires version of this project to be specified.")
        }

        project.build(POD_IMPORT_TASK_NAME, "-Pkotlin.native.cocoapods.generate.wrapper=true") {
            assertFailed()
            hooks.trigger(this)
        }
    }

    // up-to-date tests

    @Test
    fun testDummyUTD() {
        hooks.addHook {
            assertTasksExecuted(dummyTaskName)
        }
        project.testWithWrapper(dummyTaskName)

        hooks.rewriteHooks {
            assertTasksUpToDate(dummyTaskName)
        }
        project.testWithWrapper(dummyTaskName)
    }

    @Test
    fun testPodDownloadUTDWithoutPods() {
        hooks.addHook {
            assertTasksUpToDate(podDownloadTaskName)
        }
        project.testWithWrapper(podDownloadTaskName)
    }

    private fun doBasicUTDTest(family: Family? = null) {
        val tasks = listOf(
            podspecTaskName,
            defaultPodDownloadTaskName,
            defaultPodGenTaskName,
            defaultSetupBuildTaskName,
            defaultBuildTaskName,
            defaultCinteropTaskName
        )
        with(project.gradleBuildScript()) {
            addPod(defaultPodName, family, produceGitBlock(defaultPodRepo))
        }
        hooks.addHook {
            assertTasksExecuted(tasks)
        }
        project.testImportWithAsserts(listOf(defaultPodRepo))

        hooks.rewriteHooks {
            assertTasksUpToDate(tasks)
        }
        project.testImport(listOf(defaultPodRepo))
    }

    @Test
    fun basicUTDTest() = doBasicUTDTest()

    @Test
    fun basicUTDTestPerPlatformConfiguration() = doBasicUTDTest(defaultFamily)

    private fun doTestSpecReposUTD(family: Family? = null) {
        with(project.gradleBuildScript()) {
            addPod("AFNetworking", family)
        }
        hooks.addHook {
            assertTasksExecuted(defaultPodGenTaskName)
        }
        project.testSynthetic(defaultPodGenTaskName)
        with(project.gradleBuildScript()) {
            addSpecRepo("https://github.com/alozhkin/spec_repo_example.git")
        }
        project.testSynthetic(defaultPodGenTaskName)
        hooks.rewriteHooks {
            assertTasksUpToDate(defaultPodGenTaskName)
        }
        project.testSynthetic(defaultPodGenTaskName)
    }

    @Test
    fun testSpecReposUTD() = doTestSpecReposUTD()

    @Test
    fun testSpecReposUTDPerPlatformConfiguration() = doTestSpecReposUTD(defaultFamily)

    private fun doTestUTDPodAdded(family: Family? = null) {
        with(project.gradleBuildScript()) {
            addPod(defaultPodName, family, produceGitBlock(defaultPodRepo))
        }
        project.testImport(listOf(defaultPodRepo))

        val anotherPodName = "Alamofire"
        val anotherPodRepo = "https://github.com/Alamofire/Alamofire"
        with(project.gradleBuildScript()) {
            addPod(anotherPodName, family, produceGitBlock(anotherPodRepo))
        }
        hooks.rewriteHooks {
            assertTasksExecuted(
                podspecTaskName,
                podDownloadFullTaskName(anotherPodName),
                defaultPodGenTaskName,
                podSetupBuildFullTaskName(anotherPodName),
                podBuildFullTaskName(anotherPodName),
                cinteropFullTaskName(anotherPodName)
            )
            assertTasksUpToDate(
                defaultPodDownloadTaskName,
                defaultSetupBuildTaskName,
                defaultBuildTaskName,
                defaultCinteropTaskName
            )
        }
        project.testImport(listOf(defaultPodRepo, anotherPodRepo))

        with(project.gradleBuildScript()) {
            removePod(anotherPodName)
        }
        hooks.rewriteHooks {
            assertTasksNotRegisteredByPrefix(
                listOf(
                    podDownloadFullTaskName(anotherPodName),
                    podBuildFullTaskName(anotherPodName),
                    cinteropFullTaskName(anotherPodName)
                )
            )
            assertTasksUpToDate(
                defaultBuildTaskName,
                defaultPodDownloadTaskName,
                defaultSetupBuildTaskName,
                defaultCinteropTaskName
            )
        }
        project.testImport(listOf(defaultPodRepo))
    }

    @Test
    fun testUTDPodAdded() = doTestUTDPodAdded()

    @Test
    fun testUTDPodAddedPerPlatformConfiguration() = doTestUTDPodAdded(defaultFamily)

    private fun doTestImportSubspecs(family: Family? = null) {
        with(project.gradleBuildScript()) {
            addPod("SDWebImage/Core", family)
            addPod("SDWebImage/MapKit", family)
        }
        project.testImport(listOf(defaultPodRepo))
    }

    @Test
    fun testImportSubspecs() = doTestImportSubspecs()

    @Test
    fun testImportSubspecsPerPlatformConfiguration() = doTestImportSubspecs(defaultFamily)

    private fun doTestUTDTargetAdded(family: Family? = null) {
        with(project.gradleBuildScript()) {
            addPod(defaultPodName, family, produceGitBlock(defaultPodRepo))
            appendToCocoapodsBlock("ios.deploymentTarget = \"13.5\"")
        }
        project.testImport(listOf(defaultPodRepo))

        val anotherTarget = "MacosX64"
        val anotherSdk = "macosx"
        val anotherFamily = Family.OSX
        with(project.gradleBuildScript()) {
            appendToKotlinBlock(anotherTarget.decapitalize() + "()")
            if (family != null) {
                addPod(defaultPodName, anotherFamily, produceGitBlock(defaultPodRepo))
            }
            appendToCocoapodsBlock("osx.deploymentTarget = \"10.10\"")
        }
        hooks.rewriteHooks {
            assertTasksExecuted(
                podspecFullTaskName(anotherFamily.name),
                podGenFullTaskName(anotherFamily.name),
                podSetupBuildFullTaskName(sdkName = anotherSdk),
                podBuildFullTaskName(sdkName = anotherSdk),
                cinteropFullTaskName(targetName = anotherTarget)
            )
            assertTasksUpToDate(
                defaultPodspecTaskName,
                defaultPodDownloadTaskName,
                defaultPodGenTaskName,
                defaultSetupBuildTaskName,
                defaultBuildTaskName,
                defaultCinteropTaskName
            )
        }
        project.testImport(listOf(defaultPodRepo))

        with(project.gradleBuildScript()) {
            var text = readText()
            text = text.replace(anotherTarget.decapitalize() + "()", "")
            writeText(text)
        }
        hooks.rewriteHooks {
            assertTasksNotRegisteredByPrefix(
                listOf(
                    podspecFullTaskName(anotherFamily.name),
                    podGenFullTaskName(anotherFamily.name),
                    podSetupBuildFullTaskName(sdkName = anotherSdk),
                    podBuildFullTaskName(sdkName = anotherSdk),
                    cinteropFullTaskName(targetName = anotherTarget)
                )
            )
            assertTasksUpToDate(
                podspecTaskName,
                defaultPodspecTaskName,
                defaultPodDownloadTaskName,
                defaultPodGenTaskName,
                defaultSetupBuildTaskName,
                defaultBuildTaskName,
                defaultCinteropTaskName
            )
        }
        project.testImport(listOf(defaultPodRepo))
    }

    @Test
    fun testUTDTargetAdded() = doTestUTDTargetAdded()

    @Test
    fun testUTDTargetAddedToExisting() = doTestUTDTargetAdded(defaultFamily)

    @Test
    fun testUTDPodspec() {
        project.testWithWrapper(podspecTaskName)
        hooks.addHook {
            assertTasksExecuted(podspecTaskName)
        }
        with(project.gradleBuildScript()) {
            appendToCocoapodsBlock("license = \"new license name\"")
        }
        project.testWithWrapper(podspecTaskName)
        with(project.gradleBuildScript()) {
            appendToCocoapodsBlock("license = \"new license name\"")
        }
        hooks.rewriteHooks {
            assertTasksUpToDate(podspecTaskName)
        }
        project.testWithWrapper(podspecTaskName)
    }

    @Test
    fun testUTDPodspecDeploymentTarget() {
        project.testWithWrapper(podspecTaskName, defaultPodspecTaskName)
        hooks.addHook {
            assertTasksExecuted(podspecTaskName)
            assertTasksExecuted(defaultPodspecTaskName)
        }
        with(project.gradleBuildScript()) {
            appendToCocoapodsBlock("ios.deploymentTarget = \"14.0\"")
        }
        project.testWithWrapper(podspecTaskName, defaultPodspecTaskName)
        hooks.rewriteHooks {
            assertTasksUpToDate(podspecTaskName)
            assertTasksUpToDate(defaultPodspecTaskName)
        }
        project.testWithWrapper(podspecTaskName, defaultPodspecTaskName)
    }

    private fun doTestUTDDownload(family: Family? = null) {
        val gitRepo = downloadUrlRepoName.substringBeforeLast("/").substringBeforeLast("/")
        with(project.gradleBuildScript()) {
            addPod(downloadUrlPodName, family, produceGitBlock(repo = gitRepo))
        }
        hooks.addHook {
            assertTasksExecuted(downloadUrlTaskName)
        }
        project.testDownload(listOf(gitRepo))
        with(project.gradleBuildScript()) {
            changePod(downloadUrlPodName, family, produceGitBlock(repo = gitRepo, commitName = "f52f035018b4f3fe253d50ce85a7e0652a62ee9b"))
        }
        project.testDownload(listOf(gitRepo))
        hooks.rewriteHooks {
            assertTasksUpToDate(downloadUrlTaskName)
        }
        project.testDownload(listOf(gitRepo))
        val podArchivePath = "$downloadUrlRepoName/$downloadUrlPodName.tar.gz"
        with(project.gradleBuildScript()) {
            changePod(downloadUrlPodName, family, "source = url(\"$podArchivePath\")")
        }
        hooks.rewriteHooks {
            assertTasksExecuted(downloadUrlTaskName)
        }
        project.testDownload(listOf(podArchivePath))
    }

    @Test
    fun testUTDDownload() = doTestUTDDownload()

    @Test
    fun testUTDDownloadPerPlatformConfiguration() = doTestUTDDownload(defaultFamily)

    private fun doTestUTDPodGen(family: Family? = null) {
        with(project.gradleBuildScript()) {
            addPod(defaultPodName, family)
        }
        val repos = listOf(
            "https://github.com/alozhkin/spec_repo_example",
            "https://github.com/alozhkin/spec_repo_example_2"
        )
        for (repo in repos) {
            assumeTrue(isRepoAvailable(repo))
        }
        hooks.addHook {
            assertTasksExecuted(defaultPodGenTaskName)
        }
        project.testSynthetic(defaultPodGenTaskName)
        with(project.gradleBuildScript()) {
            addSpecRepo("https://github.com/alozhkin/spec_repo_example")
        }
        project.testSynthetic(defaultPodGenTaskName)
        with(project.gradleBuildScript()) {
            addSpecRepo("https://github.com/alozhkin/spec_repo_example_2")
        }
        project.testSynthetic(defaultPodGenTaskName)
        hooks.rewriteHooks {
            assertTasksUpToDate(defaultPodGenTaskName)
        }
        project.testSynthetic(defaultPodGenTaskName)
    }

    @Test
    fun testUTDPodGen() = doTestUTDPodGen()

    @Test
    fun testUTDPodGenPerPlatformConfiguration() = doTestUTDPodGen(defaultFamily)

    private fun doTestUTDBuild(family: Family? = null) {
        with(project.gradleBuildScript()) {
            addPod(defaultPodName, configuration = produceGitBlock())
        }
        hooks.addHook {
            assertTasksExecuted(defaultBuildTaskName)
        }
        project.testImport()

        val repo = "$downloadUrlRepoName/$downloadUrlPodName.tar.gz"
        val urlSource = "source = url(\"$repo\")"
        with(project.gradleBuildScript()) {
            addPod(downloadUrlPodName, family, urlSource)
        }
        val urlTaskName = podBuildFullTaskName(downloadUrlPodName)
        hooks.rewriteHooks {
            assertTasksUpToDate(defaultBuildTaskName)
            assertTasksExecuted(urlTaskName)
        }
        project.testImport()

        val anotherTarget = "MacosX64"
        val anotherSdk = "macosx"
        with(project.gradleBuildScript()) {
            appendToKotlinBlock(anotherTarget.decapitalize() + "()")
            if (family != null) addPod(downloadUrlPodName, Family.OSX, urlSource)
            appendToCocoapodsBlock("osx.deploymentTarget = \"10.15\"")
        }
        val anotherSdkDefaultPodTaskName = podBuildFullTaskName(sdkName = anotherSdk)
        val anotherTargetUrlTaskName = podBuildFullTaskName(downloadUrlPodName, anotherSdk)
        hooks.rewriteHooks {
            assertTasksUpToDate(defaultBuildTaskName, urlTaskName)
            assertTasksExecuted(anotherSdkDefaultPodTaskName, anotherTargetUrlTaskName)
        }
        project.testImport()

        hooks.rewriteHooks {
            assertTasksUpToDate(defaultBuildTaskName, urlTaskName, anotherSdkDefaultPodTaskName, anotherTargetUrlTaskName)
        }
        project.testImport()
    }

    @Test
    fun testUTDBuild() = doTestUTDBuild()

    @Test
    fun testUTDBuildPerPlatformConfiguration() = doTestUTDBuild(defaultFamily)

    private fun doTestPodBuildUTDClean(family: Family? = null) {
        with(project.gradleBuildScript()) {
            addPod(defaultPodName, family, produceGitBlock())
        }
        hooks.addHook {
            assertTasksExecuted(defaultBuildTaskName)
        }
        project.testImport()

        hooks.rewriteHooks {}
        project.test(":clean")

        hooks.addHook {
            assertTasksExecuted(defaultBuildTaskName)
        }
        project.testImport()
    }

    @Test
    fun testPodBuildUTDClean() = doTestPodBuildUTDClean()

    @Test
    fun testPodBuildUTDCleanPerPlatformConfiguration() = doTestPodBuildUTDClean(defaultFamily)

    @Test
    fun testPodInstallWithoutPodFile() {
        project.testSynthetic(podInstallTaskName)
    }


    // groovy tests

    private fun doTestGroovyDownloadAndImport(family: Family? = null) {
        val project = getProjectByName(groovyTemplateProjectName)
        val tag = "4.0.0"
        with(project.gradleBuildScript()) {
            addPod(defaultPodName, family, produceGitBlock(defaultPodRepo, tagName = tag))
        }
        hooks.addHook {
            checkGitRepo(tagName = tag)
        }
        project.testImportWithAsserts(listOf(defaultPodRepo))
    }

    @Test
    fun testGroovyDownloadAndImport() = doTestGroovyDownloadAndImport()

    @Test
    fun testGroovyDownloadAndImportPerPlatformConfiguration() = doTestGroovyDownloadAndImport(defaultFamily)


    // other tests

    private fun doTestDownloadUrlTestSupportDashInNames(family: Family? = null) {
        val fileExtension = "tar.gz"
        val podName = "Pod-with-dashes"
        val repoPath = "https://github.com/alozhkin/Pod-with-dashes/raw/master"
        val flatten = true
        val repo = "$repoPath/$podName.$fileExtension"
        with(project.gradleBuildScript()) {
            addPod(
                podName,
                family,
                """
                  |source = url("$repo", $flatten)
                  |moduleName = "Pod_with_dashes"
            """.trimMargin()
            )
        }
        hooks.addHook {
            assertTrue(url().resolve(podName).exists())
        }
        project.testImportWithAsserts(listOf(repo))
    }

    @Test
    fun testDownloadUrlTestSupportDashInNames() = doTestDownloadUrlTestSupportDashInNames()

    @Test
    fun testDownloadUrlTestSupportDashInNamesPerPlatformConfiguration() = doTestDownloadUrlTestSupportDashInNames(defaultFamily)

    private fun doTestSupportPodsWithDependencies(family: Family? = null) {
        with(project.gradleBuildScript()) {
            addPod("AlamofireImage", family)
        }
        project.testImportWithAsserts()
    }

    @Test
    fun testSupportPodsWithDependencies() = doTestSupportPodsWithDependencies()

    @Test
    fun testSupportPodsWithDependenciesPerPlatformConfiguration() = doTestSupportPodsWithDependencies(defaultFamily)

    private fun doTestCustomPackageName(family: Family? = null) {
        with(project.gradleBuildScript()) {
            addPod("AFNetworking", family, "packageName = \"AFNetworking\"")
        }
        with(project) {
            File(projectDir, "src/iosMain/kotlin/A.kt").modify {
                it.replace(
                    "fun foo() {", """
                import AFNetworking
                fun foo() {
            """.trimIndent()
                )
                it.replace("println(\"hi!\")", "println(AFNetworking.AFNetworkingReachabilityNotificationStatusItem)")
            }

            testWithWrapper("assemble")
        }
    }

    @Test
    fun testCustomPackageName() = doTestCustomPackageName()

    @Test
    fun testCustomPackageNamePerPlatformConfiguration() = doTestCustomPackageName(defaultFamily)

    private fun doTestCinteropExtraOpts(family: Family? = null) {
        with(project) {
            gradleBuildScript().addPod("AFNetworking", family, configuration = "extraOpts = listOf(\"-help\")")
            hooks.addHook {
                assertContains("Usage: cinterop options_list")
            }
            testWithWrapper("cinteropAFNetworkingIOS")
        }
    }

    @Test
    fun testCinteropExtraOpts() = doTestCinteropExtraOpts()

    @Test
    fun testCinteropExtraOptsPerPlatformConfiguration() = doTestCinteropExtraOpts(defaultFamily)

    private fun doTestUseLibrariesMode(family: Family? = null) {
        with(project) {
            gradleBuildScript().appendToCocoapodsBlock("useLibraries()")
            gradleBuildScript().addPod(defaultLibraryPodName, family)
            testImport()
        }
    }

    @Test
    fun testUseLibrariesMode() = doTestUseLibrariesMode()

    @Test
    fun testUseLibrariesModePerPlatformConfiguration() = doTestUseLibrariesMode(defaultFamily)

    @Test
    fun testCommaSeparatedTargets() {
        with(project) {
            gradleBuildScript().modify {
                // Replace a single target with a pair (iosX64 + iosArm64) to test building a fat framework.
                it.replace("iosX64(\"iOS\")", "ios()")
            }
            hooks.addHook {
                // Check that a built universal framework includes both device and simulator architectures.
                val framework = fileInWorkingDir("build/cocoapods/framework/cocoapods.framework/cocoapods")
                with(runProcess(listOf("file", framework.absolutePath), projectDir)) {
                    assertTrue(isSuccessful)
                    assertTrue(output.contains("\\(for architecture x86_64\\):\\s+current ar archive".toRegex()))
                    assertTrue(output.contains("\\(for architecture arm64\\):\\s+current ar archive".toRegex()))
                }
            }
            // Run the build.
            test(
                "syncFramework",
                "-Pkotlin.native.cocoapods.target=ios_x64,ios_arm64",
                "-Pkotlin.native.cocoapods.configuration=DEBUG"
            )
        }
    }

    // paths

    private fun CompiledProject.url() = externalSources().resolve("url")

    private fun CompiledProject.git() = externalSources().resolve("git")

    private fun CompiledProject.externalSources() =
        fileInWorkingDir("build").resolve("cocoapods").resolve("externalSources")


    // test configuration phase

    private class CustomHooks {
        private val hooks = mutableSetOf<CompiledProject.() -> Unit>()

        fun addHook(hook: CompiledProject.() -> Unit) {
            hooks.add(hook)
        }

        fun rewriteHooks(hook: CompiledProject.() -> Unit) {
            hooks.clear()
            hooks.add(hook)
        }

        fun trigger(project: CompiledProject) {
            hooks.forEach { function ->
                project.function()
            }
        }
    }

    private fun doTestImportSingle() {
        val project = getProjectByName(cocoapodsSingleKtPod)
        val subprojects = listOf("kotlin-library")
        doTestPodImport(project, subprojects)
    }

    private fun doTestImportMultiple() {
        val project = getProjectByName(cocoapodsMultipleKtPods)
        val subprojects = listOf("kotlin-library", "second-library")
        doTestPodImport(project, subprojects)
    }

    private fun doTestPodImport(project: BaseGradleIT.Project, subprojects: List<String>) {
        with(project) {
            preparePodfile("ios-app", ImportMode.FRAMEWORKS)
        }
        project.testImportWithAsserts()
        subprojects.forEach {
            hooks.rewriteHooks {
                podImportAsserts(it)
            }
            project.testSynthetic(":$it:podImport")
        }
    }

    private fun doTestGit(
        repo: String = defaultPodRepo,
        pod: String = defaultPodName,
        branch: String? = null,
        commit: String? = null,
        tag: String? = null,
        family: Family? = null
    ) {
        with(project.gradleBuildScript()) {
            addPod(pod, family, configuration = produceGitBlock(repo, branch, commit, tag))
        }
        hooks.addHook {
            checkGitRepo(branch, commit, tag, pod)
        }
        project.testDownload(listOf(repo))
    }

    private fun doTestPodDownloadUrl(
        fileExtension: String,
        podName: String = downloadUrlPodName,
        repoPath: String = downloadUrlRepoName,
        archiveName: String = podName,
        flatten: Boolean = false,
        family: Family? = null
    ) {
        val repo = "$repoPath/$archiveName.$fileExtension"
        with(project.gradleBuildScript()) {
            addPod(podName, family, "source = url(\"$repo\", $flatten)")
        }
        hooks.addHook {
            assertTrue(url().resolve(podName).exists())
        }
        project.testImportWithAsserts(listOf(repo))
    }

    private fun Project.testImportWithAsserts(
        repos: List<String> = listOf(),
        vararg args: String
    ) {
        hooks.addHook {
            podImportAsserts()
        }
        testImport(repos, *args)
    }

    private fun Project.testImport(
        repos: List<String> = listOf(),
        vararg args: String
    ) {
        for (repo in repos) {
            assumeTrue(isRepoAvailable(repo))
        }
        testSynthetic(podImportTaskName, *args)
    }

    private fun Project.testDownload(
        repos: List<String>,
        vararg args: String
    ) {
        for (repo in repos) {
            assumeTrue(isRepoAvailable(repo))
        }
        test(podDownloadTaskName, *args)
    }

    private fun Project.testSynthetic(
        taskName: String,
        vararg args: String
    ) {
        assumeTrue(KotlinCocoapodsPlugin.isAvailableToProduceSynthetic)
        testWithWrapper(taskName, *args)
    }

    private fun Project.testWithWrapper(
        taskName: String,
        vararg args: String
    ) {
        test(taskName, "-Pkotlin.native.cocoapods.generate.wrapper=true", *args)
    }

    private fun Project.test(
        taskName: String,
        vararg args: String
    ) {
        // check that test executable
        build(taskName, *args) {
            //base checks
            assertSuccessful()
            hooks.trigger(this)
        }
    }

    private fun getProjectByName(projectName: String) = transformProjectWithPluginsDsl(projectName, gradleVersion)


    // build script configuration phase

    private fun File.addPod(podName: String, family: Family? = null, configuration: String? = null) {
        fun File.addGlobalPod(podName: String, configuration: String? = null) {
            val pod = "pod(\"$podName\")"
            val podBlock = configuration?.wrap(pod) ?: pod
            appendToCocoapodsBlock(podBlock)
        }

        fun File.addPodForFamily(family: Family, podName: String, configuration: String? = null) {
            val familyName = family.name.toLowerCase()
            val pod = "pod(\"$podName\")"
            val podBlock = configuration?.wrap(pod) ?: pod
            val familyBlock = podBlock.wrap(familyName)
            appendToCocoapodsBlock(familyBlock)
        }


        if (family == null) addGlobalPod(podName, configuration)
        else addPodForFamily(family, podName, configuration)
    }

    private fun File.removePod(podName: String) {
        val text = readText()
        val begin = text.indexOf("""pod("$podName")""")
        require(begin != -1) { "Pod doesn't exist in file" }
        var index = begin + """pod("$podName")""".length - 1
        if (text.indexOf("""pod("$podName") {""", startIndex = begin) != -1) {
            index += 2
            var bracket = 1
            while (bracket != 0) {
                if (text[++index] == '{') {
                    bracket++
                } else if (text[index] == '}') {
                    bracket--
                }
            }
        }
        writeText(text.removeRange(begin..index))
    }

    private fun File.changePod(podName: String, family: Family? = null, newConfiguration: String? = null) {
        removePod(podName)
        addPod(podName, family, newConfiguration)
    }

    private fun File.addSpecRepo(specRepo: String) = appendToCocoapodsBlock("url(\"$specRepo\")".wrap("specRepos"))

    private fun File.appendToKotlinBlock(str: String) = appendLine(str.wrap("kotlin"))

    private fun File.appendToCocoapodsBlock(str: String) = appendToKotlinBlock(str.wrap("cocoapods"))

    private fun String.wrap(s: String): String = """
        |$s {
        |    $this
        |}
    """.trimMargin()

    private fun File.appendLine(s: String) = appendText("\n$s")

    private fun produceGitBlock(
        repo: String = defaultPodRepo,
        branchName: String? = null,
        commitName: String? = null,
        tagName: String? = null
    ): String {
        val branch = if (branchName != null) "branch = \"$branchName\"" else ""
        val commit = if (commitName != null) "commit = \"$commitName\"" else ""
        val tag = if (tagName != null) "tag = \"$tagName\"" else ""
        return """source = git("$repo") {
                      |    $branch
                      |    $commit
                      |    $tag
                      |}
                    """.trimMargin()
    }


    // proposition phase

    private fun CompiledProject.checkGitRepo(
        branchName: String? = null,
        commitName: String? = null,
        tagName: String? = null,
        aPodDownloadName: String = defaultPodName,
        podspecName: String = aPodDownloadName.split("/")[0]
    ) {
        val gitDir = git().resolve(aPodDownloadName.validTaskName)
        val podspecFile = gitDir.resolve("$podspecName.podspec")
        assertTrue(podspecFile.exists())
        if (tagName != null) {
            checkTag(gitDir, tagName)
        }
        checkPresentCommits(gitDir, commitName)
        if (branchName != null) {
            checkBranch(gitDir, branchName)
        }
    }

    private fun checkTag(gitDir: File, tagName: String) {
        runCommand(
            gitDir,
            "git", "name-rev",
            "--tags",
            "--name-only",
            "HEAD"
        ) {
            val (retCode, out, errorMessage) = this
            assertEquals(0, retCode, errorMessage)
            assertTrue(out.contains(tagName), errorMessage)
        }
    }

    private fun checkPresentCommits(gitDir: File, commitName: String?) {
        runCommand(
            gitDir,
            "git", "log", "--pretty=oneline"
        ) {
            val (retCode, out, _) = this
            assertEquals(0, retCode)
            // get rid of '\n' at the end
            assertEquals(1, out.trimEnd().lines().size)
            if (commitName != null) {
                assertEquals(commitName, out.substringBefore(" "))
            }
        }
    }

    private fun checkBranch(gitDir: File, branchName: String) {
        runCommand(
            gitDir,
            "git", "show-branch"
        ) {
            val (retCode, out, errorMessage) = this
            assertEquals(0, retCode)
            // get rid of '\n' at the end
            assertEquals(1, out.trimEnd().lines().size, errorMessage)
            assertEquals("[$branchName]", out.substringBefore(" "), errorMessage)
        }
    }

    private fun isRepoAvailable(repo: String): Boolean {
        var responseCode = 0
        runCommand(
            File("/"),
            "curl",
            "-s",
            "-o",
            "/dev/null",
            "-w",
            "%{http_code}",
            "-L",
            repo,
            "--retry", "2"
        ) {
            val (retCode, out, errorMessage) = this
            assertEquals(0, retCode, errorMessage)
            responseCode = out.toInt()
        }
        return responseCode == 200
    }

    private fun CompiledProject.podImportAsserts(projectName: String? = null) {

        val buildScriptText = project.gradleBuildScript(projectName).readText()
        val taskPrefix = projectName?.let { ":$it" } ?: ""
        val podspec = "podspec"
        val podInstall = "podInstall"
        assertSuccessful()

        if ("noPodspec()" in buildScriptText) {
            assertTasksSkipped("$taskPrefix:$podspec")
        }

        if ("podfile" in buildScriptText) {
            assertTasksExecuted("$taskPrefix:$podInstall")
        } else {
            assertTasksSkipped("$taskPrefix:$podInstall")
        }
        assertTasksRegisteredByPrefix(listOf("$taskPrefix:$POD_GEN_TASK_NAME"))
        if (buildScriptText.matches("pod\\(.*\\)".toRegex())) {
            assertTasksExecutedByPrefix(listOf("$taskPrefix:$POD_GEN_TASK_NAME"))
        }

        with(listOf(POD_SETUP_BUILD_TASK_NAME, POD_BUILD_TASK_NAME).map { "$taskPrefix:$it" }) {
            if (buildScriptText.matches("pod\\(.*\\)".toRegex())) {
                assertTasksRegisteredByPrefix(this)
                assertTasksExecutedByPrefix(this)
            }
        }
    }

    private fun Project.useCustomFrameworkName(subproject: String, frameworkName: String, iosAppLocation: String? = null) {
        // Change the name at the Gradle side.
        gradleBuildScript(subproject).appendText(
            """
                |kotlin {
                |    cocoapods {
                |        frameworkName = "$frameworkName"
                |    }
                |}
            """.trimMargin()
        )

        // Change swift sources import if needed.
        if (iosAppLocation != null) {
            val iosAppDir = projectDir.resolve(iosAppLocation)
            iosAppDir.resolve("ios-app/ViewController.swift").modify {
                it.replace("import ${subproject.validFrameworkName}", "import $frameworkName")
            }
        }
    }

    private fun doTestPodspec(
        projectName: String,
        subprojectsToFrameworkNamesMap: Map<String, String?>,
        subprojectsToPodspecContentMap: Map<String, String?>
    ) {
        val gradleProject = transformProjectWithPluginsDsl(projectName, gradleVersion)

        for ((subproject, frameworkName) in subprojectsToFrameworkNamesMap) {
            frameworkName?.let {
                gradleProject.useCustomFrameworkName(subproject, it)
            }

            // Check that we can generate the wrapper along with the podspec if the corresponding property specified
            gradleProject.build(":$subproject:podspec", "-Pkotlin.native.cocoapods.generate.wrapper=true") {
                assertSuccessful()
                assertTasksExecuted(":$subproject:podspec")

                // Check that the podspec file is correctly generated.
                val podspecFileName = "$subproject/${subproject.validFrameworkName}.podspec"

                assertFileExists(podspecFileName)
                val actualPodspecContentWithoutBlankLines = fileInWorkingDir(podspecFileName).readText()
                    .lineSequence()
                    .filter { it.isNotBlank() }
                    .joinToString("\n")

                assertEquals(subprojectsToPodspecContentMap[subproject], actualPodspecContentWithoutBlankLines)
            }
        }
    }

    private enum class ImportMode(val directive: String) {
        FRAMEWORKS("use_frameworks!"),
        MODULAR_HEADERS("use_modular_headers!")
    }

    private data class CommandResult(
        val exitCode: Int,
        val stdOut: String,
        val stdErr: String
    )

    private fun runCommand(
        workingDir: File,
        command: String,
        vararg args: String,
        timeoutSec: Long = 120,
        inheritIO: Boolean = false,
        block: CommandResult.() -> Unit
    ) {
        val process = ProcessBuilder(command, *args).apply {
            directory(workingDir)
            if (inheritIO) {
                inheritIO()
            }
        }.start()

        val isFinished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
        val stdOut = process.inputStream.bufferedReader().use { it.readText() }
        val stdErr = process.errorStream.bufferedReader().use { it.readText() }

        if (!isFinished) {
            process.destroyForcibly()
            println("Stdout:\n$stdOut")
            println("Stderr:\n$stdErr")
            fail("Command '$command ${args.joinToString(" ")}' killed by timeout.".trimIndent())
        }
        CommandResult(process.exitValue(), stdOut, stdErr).block()
    }

    private fun doTestXcode(
        projectName: String,
        mode: ImportMode,
        iosAppLocation: String?,
        subprojectsToFrameworkNamesMap: Map<String, String?>
    ) {
        val gradleProject = transformProjectWithPluginsDsl(projectName, gradleVersion)

        with(gradleProject) {
            setupWorkingDir()

            for ((subproject, frameworkName) in subprojectsToFrameworkNamesMap) {

                // Add property with custom framework name
                frameworkName?.let {
                    useCustomFrameworkName(subproject, it, iosAppLocation)
                }

                // Generate podspec.
                build(":$subproject:podspec", "-Pkotlin.native.cocoapods.generate.wrapper=true") {
                    assertSuccessful()
                }
                iosAppLocation?.also {
                    // Set import mode for Podfile.
                    preparePodfile(it, mode)
                    // Install pods.
                    build(":$subproject:podInstall", "-Pkotlin.native.cocoapods.generate.wrapper=true") {
                        assertSuccessful()
                    }

                    projectDir.resolve(it).apply {
                        // Run Xcode build.
                        runCommand(
                            this, "xcodebuild",
                            "-sdk", "iphonesimulator",
                            "-arch", "x86_64",
                            "-configuration", "Release",
                            "-workspace", "$name.xcworkspace",
                            "-scheme", name,
                            inheritIO = true // Xcode doesn't finish the process if the PIPE redirect is used.
                        ) {
                            assertEquals(
                                0, exitCode, """
                        |Exit code mismatch for `xcodebuild`.
                        |stdout:
                        |$stdOut
                        |
                        |stderr:
                        |$stdErr
                    """.trimMargin()
                            )
                        }
                    }
                }
            }

        }
    }

    private fun Project.preparePodfile(iosAppLocation: String, mode: ImportMode) {
        val iosAppDir = projectDir.resolve(iosAppLocation)

        // Set import mode for Podfile.
        iosAppDir.resolve("Podfile").takeIf { it.exists() }?.modify {
            it.replace(PODFILE_IMPORT_DIRECTIVE_PLACEHOLDER, mode.directive)
        }
    }


    private fun kotlinLibraryPodspecContent(frameworkName: String? = null) = """
                Pod::Spec.new do |spec|
                    spec.name                     = 'kotlin_library'
                    spec.version                  = '1.0'
                    spec.homepage                 = 'https://github.com/JetBrains/kotlin'
                    spec.source                   = { :git => "Not Published", :tag => "Cocoapods/#{spec.name}/#{spec.version}" }
                    spec.authors                  = ''
                    spec.license                  = ''
                    spec.summary                  = 'CocoaPods test library'
                    spec.static_framework         = true
                    spec.vendored_frameworks      = "build/cocoapods/framework/${frameworkName ?: "kotlin_library"}.framework"
                    spec.libraries                = "c++"
                    spec.module_name              = "#{spec.name}_umbrella"
                    spec.dependency 'pod_dependency', '1.0'
                    spec.dependency 'subspec_dependency/Core', '1.0'
                    spec.pod_target_xcconfig = {
                        'KOTLIN_TARGET[sdk=iphonesimulator*]' => 'ios_x64',
                        'KOTLIN_TARGET[sdk=iphoneos*]' => 'ios_arm',
                        'KOTLIN_TARGET[sdk=watchsimulator*]' => 'watchos_x64',
                        'KOTLIN_TARGET[sdk=watchos*]' => 'watchos_arm',
                        'KOTLIN_TARGET[sdk=appletvsimulator*]' => 'tvos_x64',
                        'KOTLIN_TARGET[sdk=appletvos*]' => 'tvos_arm64',
                        'KOTLIN_TARGET[sdk=macosx*]' => 'macos_x64'
                    }
                    spec.script_phases = [
                        {
                            :name => 'Build kotlin_library',
                            :execution_position => :before_compile,
                            :shell_path => '/bin/sh',
                            :script => <<-SCRIPT
                                set -ev
                                REPO_ROOT="${'$'}PODS_TARGET_SRCROOT"
                                "${'$'}REPO_ROOT/../gradlew" -p "${'$'}REPO_ROOT" :kotlin-library:syncFramework \
                                    -Pkotlin.native.cocoapods.target=${'$'}KOTLIN_TARGET \
                                    -Pkotlin.native.cocoapods.configuration=${'$'}CONFIGURATION \
                                    -Pkotlin.native.cocoapods.cflags="${'$'}OTHER_CFLAGS" \
                                    -Pkotlin.native.cocoapods.paths.headers="${'$'}HEADER_SEARCH_PATHS" \
                                    -Pkotlin.native.cocoapods.paths.frameworks="${'$'}FRAMEWORK_SEARCH_PATHS"
                            SCRIPT
                        }
                    ]
                end
            """.trimIndent()

    private fun secondLibraryPodspecContent(frameworkName: String? = null) = """
                Pod::Spec.new do |spec|
                    spec.name                     = 'second_library'
                    spec.version                  = '1.0'
                    spec.homepage                 = 'https://github.com/JetBrains/kotlin'
                    spec.source                   = { :git => "Not Published", :tag => "Cocoapods/#{spec.name}/#{spec.version}" }
                    spec.authors                  = ''
                    spec.license                  = ''
                    spec.summary                  = 'CocoaPods test library'
                    spec.static_framework         = true
                    spec.vendored_frameworks      = "build/cocoapods/framework/${frameworkName ?: "second_library"}.framework"
                    spec.libraries                = "c++"
                    spec.module_name              = "#{spec.name}_umbrella"
                    spec.pod_target_xcconfig = {
                        'KOTLIN_TARGET[sdk=iphonesimulator*]' => 'ios_x64',
                        'KOTLIN_TARGET[sdk=iphoneos*]' => 'ios_arm',
                        'KOTLIN_TARGET[sdk=watchsimulator*]' => 'watchos_x64',
                        'KOTLIN_TARGET[sdk=watchos*]' => 'watchos_arm',
                        'KOTLIN_TARGET[sdk=appletvsimulator*]' => 'tvos_x64',
                        'KOTLIN_TARGET[sdk=appletvos*]' => 'tvos_arm64',
                        'KOTLIN_TARGET[sdk=macosx*]' => 'macos_x64'
                    }
                    spec.script_phases = [
                        {
                            :name => 'Build second_library',
                            :execution_position => :before_compile,
                            :shell_path => '/bin/sh',
                            :script => <<-SCRIPT
                                set -ev
                                REPO_ROOT="${'$'}PODS_TARGET_SRCROOT"
                                "${'$'}REPO_ROOT/../gradlew" -p "${'$'}REPO_ROOT" :second-library:syncFramework \
                                    -Pkotlin.native.cocoapods.target=${'$'}KOTLIN_TARGET \
                                    -Pkotlin.native.cocoapods.configuration=${'$'}CONFIGURATION \
                                    -Pkotlin.native.cocoapods.cflags="${'$'}OTHER_CFLAGS" \
                                    -Pkotlin.native.cocoapods.paths.headers="${'$'}HEADER_SEARCH_PATHS" \
                                    -Pkotlin.native.cocoapods.paths.frameworks="${'$'}FRAMEWORK_SEARCH_PATHS"
                            SCRIPT
                        }
                    ]
                end
            """.trimIndent()

    companion object {
        @BeforeClass
        @JvmStatic
        fun assumeItsMac() {
            assumeTrue(HostManager.hostIsMac)
        }
    }
}