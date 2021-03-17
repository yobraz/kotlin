/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.arguments

import org.jetbrains.kotlin.cli.common.arguments.CommonToolArguments
import org.jdom.Element
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import kotlin.reflect.KProperty1

interface CompilerArgumentsSerializer<T : CommonToolArguments> {
    val element: Element
    fun fillElement(arguments: T): Element.() -> Unit
    fun serialize(arguments: T): Element = element.apply { fillElement(arguments) }
}

class CompilerArgumentsSerializerV4<T : CommonToolArguments> : CompilerArgumentsSerializer<T> {
    override val element: Element
        get() = Element(ROOT_ELEMENT_NAME)

    override fun fillElement(arguments: T): Element.() -> Unit = {
        val flagArguments = CompilerArgumentsContentProspector.getFlagCompilerArgumentProperties(arguments::class)
            .filter { it.safeAs<KProperty1<T, Boolean>>()?.get(arguments) == true }.map { it.name }
        saveFlagArguments(element, flagArguments)

        val stringArgumentsByName = CompilerArgumentsContentProspector.getStringCompilerArgumentProperties(arguments::class)
            .mapNotNull { prop -> prop.safeAs<KProperty1<T, String?>>()?.get(arguments)?.let { prop.name to it } }.toMap()
        saveStringArguments(element, stringArgumentsByName)

        val arrayArgumentsByName = CompilerArgumentsContentProspector.getArrayCompilerArgumentProperties(arguments::class)
            .mapNotNull { prop -> prop.safeAs<KProperty1<T, Array<String>?>>()?.get(arguments)?.let { prop.name to it } }
            .filterNot { it.second.isEmpty() }
            .toMap()
        saveArrayArguments(element, arrayArgumentsByName)
        val freeArgs = CompilerArgumentsContentProspector.freeArgsProperty.get(arguments)
        saveElementsList(element, FREE_ARGS_ROOT_ELEMENTS_NAME, FREE_ARGS_ELEMENT_NAME, freeArgs)
        val internalArguments = CompilerArgumentsContentProspector.internalArgumentsProperty.get(arguments).map { it.stringRepresentation }
        saveElementsList(element, INTERNAL_ARGS_ROOT_ELEMENTS_NAME, INTERNAL_ARGS_ELEMENT_NAME, internalArguments)
    }

    companion object {
        private fun saveElementConfigurable(element: Element, rootElementName: String, configurable: Element.() -> Unit) {
            element.addContent(Element(rootElementName).apply { configurable(this) })
        }

        private fun saveStringArguments(element: Element, argumentsByName: Map<String, String>) {
            if (argumentsByName.isEmpty()) return
            saveElementConfigurable(element, STRING_ROOT_ELEMENTS_NAME) {
                argumentsByName.entries.forEach { (name, arg) ->
                    Element(STRING_ELEMENT_NAME).also {
                        it.setAttribute(NAME_ATTR_NAME, name)
                        it.setAttribute(ARG_ATTR_NAME, arg)
                        addContent(it)
                    }
                }
            }
        }

        private fun saveFlagArguments(element: Element, argumentNames: List<String>) =
            saveElementsList(element, FLAG_ROOT_ELEMENTS_NAME, FLAG_ELEMENT_NAME, argumentNames)

        private fun saveElementsList(element: Element, rootElementName: String, elementName: String, elementList: List<String>) {
            if (elementList.isEmpty()) return
            saveElementConfigurable(element, rootElementName) {
                val singleModule = elementList.singleOrNull()
                if (singleModule != null) {
                    addContent(singleModule)
                } else {
                    elementList.forEach { elementValue -> addContent(Element(elementName).also { it.addContent(elementValue) }) }
                }
            }
        }

        private fun saveArrayArguments(element: Element, arrayArgumentsByName: Map<String, Array<String>>) {
            if (arrayArgumentsByName.isEmpty()) return
            saveElementConfigurable(element, ARRAY_ROOT_ELEMENTS_NAME) {
                arrayArgumentsByName.entries.forEach { (name, arg) ->
                    Element(ARRAY_ELEMENT_NAME).also {
                        it.setAttribute(NAME_ATTR_NAME, name)
                        saveElementsList(it, ARGS_ATTR_NAME, ARG_ATTR_NAME, arg.toList())
                        addContent(it)
                    }
                }
            }
        }
    }
}