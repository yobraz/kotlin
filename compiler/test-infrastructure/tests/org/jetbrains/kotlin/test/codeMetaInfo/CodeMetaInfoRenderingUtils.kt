/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.codeMetaInfo

import com.intellij.util.containers.Stack
import org.jetbrains.kotlin.test.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.test.codeMetaInfo.rendering.CodeMetaInfoRenderer
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives
import java.io.File

object CodeMetaInfoRenderingUtils {
    fun renderTagsToText(
        codeMetaInfos: List<CodeMetaInfo>,
        renderers: Collection<CodeMetaInfoRenderer>,
        registeredDirectives: RegisteredDirectives,
        originalText: String
    ): StringBuilder {
        return StringBuilder().apply {
            renderTagsToText(this, codeMetaInfos, renderers, registeredDirectives, originalText)
        }
    }

    fun renderTagsToText(
        builder: StringBuilder,
        codeMetaInfos: List<CodeMetaInfo>,
        renderers: Collection<CodeMetaInfoRenderer>,
        registeredDirectives: RegisteredDirectives,
        originalText: String
    ) {
        if (codeMetaInfos.isEmpty()) {
            builder.append(originalText)
            return
        }
        val sortedMetaInfos = getSortedCodeMetaInfos(codeMetaInfos).groupBy { it.start }
        val opened = Stack<CodeMetaInfo>()

        for ((i, c) in originalText.withIndex()) {
            processMetaInfosStartedAtOffset(i, renderers, sortedMetaInfos, registeredDirectives, opened, builder)
            builder.append(c)
        }
        val lastSymbolIsNewLine = builder.last() == '\n'
        if (lastSymbolIsNewLine) {
            builder.deleteCharAt(builder.length - 1)
        }
        processMetaInfosStartedAtOffset(originalText.length, renderers, sortedMetaInfos, registeredDirectives, opened, builder)
        if (lastSymbolIsNewLine) {
            builder.appendLine()
        }
    }

    private fun processMetaInfosStartedAtOffset(
        offset: Int,
        renderers: Collection<CodeMetaInfoRenderer>,
        sortedMetaInfos: Map<Int, List<CodeMetaInfo>>,
        registeredDirectives: RegisteredDirectives,
        opened: Stack<CodeMetaInfo>,
        builder: StringBuilder
    ) {
        checkOpenedAndCloseStringIfNeeded(opened, offset, builder)
        val matchedCodeMetaInfos = sortedMetaInfos[offset] ?: emptyList()
        if (matchedCodeMetaInfos.isNotEmpty()) {
            val iterator = matchedCodeMetaInfos.listIterator()
            var current: CodeMetaInfo? = iterator.next()

            if (current != null) builder.append(current.tagPrefix)

            while (current != null) {
                val next: CodeMetaInfo? = if (iterator.hasNext()) iterator.next() else null
                opened.push(current)
                builder.append(current.renderWithSuitableRenderer(renderers, registeredDirectives))
                when {
                    next == null ->
                        builder.append(current.tagPostfix)
                    next.end == current.end ->
                        builder.append(", ")
                    else -> {
                        builder.append(current.tagPostfix)
                        builder.append(next.tagPrefix)
                    }
                }
                current = next
            }
        }
        // Here we need to handle meta infos which has start == end and close them immediately
        checkOpenedAndCloseStringIfNeeded(opened, offset, builder)
    }

    @Suppress("UnnecessaryVariable")
    private fun CodeMetaInfo.renderWithSuitableRenderer(
        renderers: Collection<CodeMetaInfoRenderer>,
        registeredDirectives: RegisteredDirectives
    ): String {
        val relevantRenderersToRenderedStrings: List<Pair<CodeMetaInfoRenderer, String>> = renderers
            .map { renderer -> renderer to renderer.asString(this, registeredDirectives) }
            .filter { (_, renderedMetaInfo) -> renderedMetaInfo.isNotEmpty() }

        val unambiguousRenderedString = when (relevantRenderersToRenderedStrings.size) {
            1 -> relevantRenderersToRenderedStrings.single().second

            0 -> error(
                "Can't find the suitable RenderConfiguration for CodeMetaInto $this.\n" +
                        "Available RenderConfigurations: ${renderers.joinToString()}"
            )

            // >1
            else -> error(
                "Ambiguity while rendering CodeMetaInfo $this.\n" +
                        "Applicable RenderConfigurations: ${relevantRenderersToRenderedStrings.joinToString { it.first.toString() }}"
            )
        }

        return unambiguousRenderedString
    }

    private val metaInfoComparator = (compareBy<CodeMetaInfo> { it.start } then compareByDescending { it.end }) then compareBy { it.tag }

    private fun getSortedCodeMetaInfos(metaInfos: Collection<CodeMetaInfo>): List<CodeMetaInfo> {
        return metaInfos.sortedWith(metaInfoComparator)
    }

    private fun checkOpenedAndCloseStringIfNeeded(opened: Stack<CodeMetaInfo>, end: Int, result: StringBuilder) {
        var prev: CodeMetaInfo? = null
        while (!opened.isEmpty() && end == opened.peek().end) {
            if (prev == null || prev.start != opened.peek().start)
                result.append(opened.peek().closingTag)
            prev = opened.pop()
        }
    }
}

fun clearFileFromDiagnosticMarkup(file: File) {
    val text = file.readText()
    val cleanText = clearTextFromDiagnosticMarkup(text)
    file.writeText(cleanText)
}

fun clearTextFromDiagnosticMarkup(text: String): String = text.replace(CodeMetaInfoParser.openingOrClosingRegex, "")
