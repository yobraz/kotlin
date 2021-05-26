/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.codeMetaInfo

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.impl.source.tree.PsiErrorElementImpl
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.io.exists
import gnu.trove.TIntArrayList
import org.jetbrains.kotlin.checkers.BaseDiagnosticsTest
import org.jetbrains.kotlin.checkers.diagnostics.DebugInfoDiagnostic
import org.jetbrains.kotlin.checkers.diagnostics.SyntaxErrorDiagnostic
import org.jetbrains.kotlin.checkers.diagnostics.factories.DebugInfoDiagnosticFactory0
import org.jetbrains.kotlin.checkers.utils.CheckerTestUtil
import org.jetbrains.kotlin.checkers.utils.DiagnosticsRenderingConfiguration
import org.jetbrains.kotlin.daemon.common.OSKind
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.diagnostics.AbstractDiagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeMetaInfo.models.*
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingCodeMetaInfoRenderer
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.LineMarkerCodeMetaInfoRenderer
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromTextFile
import org.jetbrains.kotlin.idea.resolve.getDataFlowValueFactory
import org.jetbrains.kotlin.idea.resolve.getLanguageVersionSettings
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.util.sourceRoots
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.codeMetaInfo.CodeMetaInfoParser
import org.jetbrains.kotlin.test.codeMetaInfo.CodeMetaInfoRenderingUtils
import org.jetbrains.kotlin.test.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.test.codeMetaInfo.model.DiagnosticCodeMetaInfo
import org.jetbrains.kotlin.test.codeMetaInfo.rendering.CodeMetaInfoRenderer
import org.jetbrains.kotlin.test.codeMetaInfo.rendering.DiagnosticCodeMetaInfoRenderer
import org.jetbrains.kotlin.test.directives.DiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.model.ComposedDirectivesContainer
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives
import org.jetbrains.kotlin.test.services.impl.RegisteredDirectivesParser
import org.jetbrains.kotlin.test.util.JUnit4Assertions
import org.junit.Ignore
import java.io.File
import java.nio.file.Paths

@Ignore
class CodeMetaInfoTestCase(
    val codeMetaInfoTypes: Collection<CodeMetaInfoRenderer>,
    val checkNoDiagnosticError: Boolean = false,
    val registeredDirectives: RegisteredDirectives,
) : DaemonAnalyzerTestCase() {

    fun checkFile(file: VirtualFile, expectedFile: File, project: Project) {
        myProject = project
        myPsiManager = PsiManager.getInstance(myProject) as PsiManagerImpl
        configureByExistingFile(file)
        check(expectedFile)
    }

    private fun check(expectedFile: File) {
        val codeMetaInfoForCheck = mutableListOf<CodeMetaInfo>()
        PsiDocumentManager.getInstance(myProject).commitAllDocuments()

        //to load text
        ApplicationManager.getApplication().runWriteAction { TreeUtil.clearCaches(myFile.node as TreeElement) }

        //to initialize caches
        if (!DumbService.isDumb(myProject)) {
            CacheManager.SERVICE.getInstance(myProject)
                .getFilesWithWord("XXX", UsageSearchContext.IN_COMMENTS, GlobalSearchScope.allScope(myProject), true)
        }

        for (renderer in codeMetaInfoTypes) {
            when (renderer) {
                is DiagnosticCodeMetaInfoRenderer -> {
                    codeMetaInfoForCheck.addAll(getDiagnosticCodeMetaInfos(renderer))
                }
                is HighlightingCodeMetaInfoRenderer -> {
                    codeMetaInfoForCheck.addAll(getHighlightingCodeMetaInfos(renderer))
                }
                is LineMarkerCodeMetaInfoRenderer -> {
                    codeMetaInfoForCheck.addAll(getLineMarkerCodeMetaInfos(renderer))
                }
                else -> throw IllegalArgumentException("Unexpected code meta info configuration: $renderer")
            }
        }
        if (codeMetaInfoTypes.any { it is DiagnosticCodeMetaInfoRenderer } &&
            !codeMetaInfoTypes.any { it is HighlightingCodeMetaInfoRenderer }
        ) {
            checkHighlightErrorItemsInDiagnostics(
                getDiagnosticCodeMetaInfos(DiagnosticCodeMetaInfoRenderer(), false).filterIsInstance<DiagnosticCodeMetaInfo>()
            )
        }
        val parsedMetaInfo = CodeMetaInfoParser.getCodeMetaInfoFromText(expectedFile.readText()).toMutableList()
        codeMetaInfoForCheck.forEach { codeMetaInfo ->
            val correspondingParsed = parsedMetaInfo.firstOrNull { it == codeMetaInfo }
            if (correspondingParsed != null) {
                parsedMetaInfo.remove(correspondingParsed)
                codeMetaInfo.attributes.addAll(correspondingParsed.attributes)
                if (correspondingParsed.attributes.isNotEmpty() && OSKind.current.toString() !in correspondingParsed.attributes)
                    codeMetaInfo.attributes.add(OSKind.current.toString())
            }
        }
        parsedMetaInfo.forEach {
            if (it.attributes.isNotEmpty() && OSKind.current.toString() !in it.attributes) codeMetaInfoForCheck.add(
                it
            )
        }
        val textWithCodeMetaInfo = CodeMetaInfoRenderingUtils.renderTagsToText(
            codeMetaInfoForCheck,
            codeMetaInfoTypes,
            registeredDirectives,
            myEditor.document.text
        )
        KotlinTestUtils.assertEqualsToFile(
            expectedFile,
            textWithCodeMetaInfo.toString()
        )

        if (checkNoDiagnosticError) {
            val diagnosticsErrors =
                codeMetaInfoForCheck.filterIsInstance<DiagnosticCodeMetaInfo>().filter { it.diagnostic.severity == Severity.ERROR }
            assertTrue(
                "Diagnostics with severity ERROR were found: ${diagnosticsErrors.joinToString()}",
                diagnosticsErrors.isEmpty()
            )
        }
    }

    private fun getDiagnosticCodeMetaInfos(
        configuration: DiagnosticCodeMetaInfoRenderer = DiagnosticCodeMetaInfoRenderer(),
        parseDirective: Boolean = true
    ): List<CodeMetaInfo> {
        val tempSourceKtFile = PsiManager.getInstance(project).findFile(file.virtualFile) as KtFile
        val resolutionFacade = tempSourceKtFile.getResolutionFacade()
        val (bindingContext, moduleDescriptor, _) = resolutionFacade.analyzeWithAllCompilerChecks(listOf(tempSourceKtFile))
        val directives = KotlinTestUtils.parseDirectives(file.text)
        val diagnosticsFilter = BaseDiagnosticsTest.parseDiagnosticFilterDirective(directives, allowUnderscoreUsage = false)
        val diagnostics = CheckerTestUtil.getDiagnosticsIncludingSyntaxErrors(
            bindingContext,
            file,
            markDynamicCalls = false,
            dynamicCallDescriptors = mutableListOf(),
            configuration = DiagnosticsRenderingConfiguration(
                platform = null, // we don't need to attach platform-description string to diagnostic here
                withNewInference = false,
                languageVersionSettings = resolutionFacade.getLanguageVersionSettings(),
            ),
            dataFlowValueFactory = resolutionFacade.getDataFlowValueFactory(),
            moduleDescriptor = moduleDescriptor as ModuleDescriptorImpl
        ).map { it.diagnostic }.filter { !parseDirective || diagnosticsFilter.value(it) }
        configuration.renderParams = directives.contains(BaseDiagnosticsTest.RENDER_DIAGNOSTICS_MESSAGES)
        return getCodeMetaInfo(diagnostics, configuration)
    }

    private fun getLineMarkerCodeMetaInfos(configuration: LineMarkerCodeMetaInfoRenderer): Collection<CodeMetaInfo> {
        if ("!CHECK_HIGHLIGHTING" in file.text)
            return emptyList()

        CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, TIntArrayList().toNativeArray(), false)
        val lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(getDocument(file), project)
        return getCodeMetaInfo(lineMarkers, configuration)
    }

    private fun getHighlightingCodeMetaInfos(configuration: HighlightingCodeMetaInfoRenderer): Collection<CodeMetaInfo> {
        val infos = CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, TIntArrayList().toNativeArray(), false)

        return getCodeMetaInfo(infos, configuration)
    }

    private fun checkHighlightErrorItemsInDiagnostics(
        diagnostics: Collection<DiagnosticCodeMetaInfo>
    ) {
        val highlightItems: List<CodeMetaInfo> =
            getHighlightingCodeMetaInfos(HighlightingCodeMetaInfoRenderer).filter { (it as HighlightingCodeMetaInfo).highlightingInfo.severity == HighlightSeverity.ERROR }

        highlightItems.forEach { highlightingCodeMetaInfo ->
            assert(
                diagnostics.any { diagnosticCodeMetaInfo ->
                    diagnosticCodeMetaInfo.start == highlightingCodeMetaInfo.start &&
                            when (val diagnostic = diagnosticCodeMetaInfo.diagnostic) {
                                is SyntaxErrorDiagnostic -> {
                                    (highlightingCodeMetaInfo as HighlightingCodeMetaInfo).highlightingInfo.description in (diagnostic.psiElement as PsiErrorElementImpl).errorDescription
                                }
                                is AbstractDiagnostic<*> -> {
                                    diagnostic.factory.toString() in (highlightingCodeMetaInfo as HighlightingCodeMetaInfo).highlightingInfo.description
                                }
                                is DebugInfoDiagnostic -> {
                                    diagnostic.factory == DebugInfoDiagnosticFactory0.MISSING_UNRESOLVED &&
                                            "[DEBUG] Reference is not resolved to anything, but is not marked unresolved" in (highlightingCodeMetaInfo as HighlightingCodeMetaInfo).highlightingInfo.description
                                }
                                else -> throw java.lang.IllegalArgumentException("Unknown diagnostic type: ${diagnosticCodeMetaInfo.diagnostic}")
                            }
                },
            ) { "Could not find DIAGNOSTIC for ${(highlightingCodeMetaInfo as HighlightingCodeMetaInfo).highlightingInfo}" }
        }
    }
}

abstract class AbstractDiagnosticCodeMetaInfoTest : AbstractCodeMetaInfoTest() {
    override fun getRenderers(registeredDirectives: RegisteredDirectives): List<CodeMetaInfoRenderer> = listOf(
        DiagnosticCodeMetaInfoRenderer,
        LineMarkerCodeMetaInfoRenderer,
    )
}

abstract class AbstractLineMarkerCodeMetaInfoTest : AbstractCodeMetaInfoTest() {
    override fun getRenderers(registeredDirectives: RegisteredDirectives): List<CodeMetaInfoRenderer> = listOf(
        LineMarkerCodeMetaInfoRenderer,
    )
}

abstract class AbstractCodeMetaInfoTest : AbstractMultiModuleTest() {
    open val checkNoDiagnosticError get() = false

    open fun getRenderers(registeredDirectives: RegisteredDirectives): List<CodeMetaInfoRenderer> = listOf(
        DiagnosticCodeMetaInfoRenderer,
        LineMarkerCodeMetaInfoRenderer,
        HighlightingCodeMetaInfoRenderer
    )

    protected open fun setupProject(testDataPath: String) {
        val dependenciesTxt = File(testDataPath, "dependencies.txt")
        require(dependenciesTxt.exists()) {
            "${dependenciesTxt.absolutePath} does not exist. dependencies.txt is required"
        }
        setupMppProjectFromTextFile(File(testDataPath))
    }

    fun doTest(testDataPath: String) {
        val testRoot = File(testDataPath)
        setupProject(testDataPath)

        for (module in ModuleManager.getInstance(project).modules) {
            for (sourceRoot in module.sourceRoots) {
                VfsUtilCore.processFilesRecursively(sourceRoot) { file ->
                    if (file.isDirectory) return@processFilesRecursively true
                    val expectedFile = file.findCorrespondingFileInTestDir(sourceRoot, testRoot)

                    val directives: RegisteredDirectives = RegisteredDirectivesParser.parseAllDirectivesFromSingleModuleTest(
                        expectedFile.readLines(),
                        ComposedDirectivesContainer(HighlightingDirectives, DiagnosticsDirectives),
                        JUnit4Assertions
                    )
                    val checker = CodeMetaInfoTestCase(getRenderers(directives), checkNoDiagnosticError, directives)

                    checker.checkFile(file, expectedFile, project)
                    true
                }
            }
        }
    }

    private fun VirtualFile.findCorrespondingFileInTestDir(containingRoot: VirtualFile, testDir: File): File {
        val tempRootPath = Paths.get(containingRoot.path)
        val tempProjectDirPath = tempRootPath.parent
        val tempSourcePath = Paths.get(path)
        val relativeToProjectRootPath = tempProjectDirPath.relativize(tempSourcePath)
        val testSourcesProjectDirPath = testDir.toPath()
        val testSourcePath = testSourcesProjectDirPath.resolve(relativeToProjectRootPath)

        require(testSourcePath.exists()) {
            "Can't find file in testdata for copied file $this: checked at path ${testSourcePath.toAbsolutePath()}"
        }
        return testSourcePath.toFile()
    }
}
