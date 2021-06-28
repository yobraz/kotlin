/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.cli.jvm.modules

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.io.URLUtil
import org.jetbrains.kotlin.resolve.jvm.KotlinCliJavaFileManager
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModule
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModuleFinder
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModuleInfo

class CliJavaModuleFinder(
    jdkRootFile: VirtualFile?,
    jrtFileSystemRoot: VirtualFile?,
    private val javaFileManager: KotlinCliJavaFileManager,
    project: Project
) : JavaModuleFinder {
    private val modulesRoot = jrtFileSystemRoot?.findChild("modules")
    private val ctSymFile = jdkRootFile?.findChild("lib")?.findChild("ct.sym")
    private val userModules = linkedMapOf<String, JavaModule>()

    private val allScope = GlobalSearchScope.allScope(project)

    fun addUserModule(module: JavaModule) {
        userModules.putIfAbsent(module.name, module)
    }

    val allObservableModules: Sequence<JavaModule>
        get() = systemModules + userModules.values

    val systemModules: Sequence<JavaModule.Explicit>
        get() = modulesRoot?.children.orEmpty().asSequence().mapNotNull(this::findSystemModule)

    override fun findModule(name: String): JavaModule? =
        modulesRoot?.findChild(name)?.let(this::findSystemModule) ?: userModules[name]

    private fun findSystemModule(moduleRoot: VirtualFile): JavaModule.Explicit? {
        val file = moduleRoot.findChild(PsiJavaModule.MODULE_INFO_CLS_FILE) ?: return null
        val moduleInfo = JavaModuleInfo.read(file, javaFileManager, allScope) ?: return null
        return JavaModule.Explicit(moduleInfo, listOf(JavaModule.Root(moduleRoot, isBinary = true)), file)
    }

    private fun codeFor(release: Int): String = if (release < 10) release.toString() else ('A' + (release - 10)).toString()

    private fun matchesRelease(fileName: String, release: Int) =
        !fileName.contains("-") && fileName.contains(codeFor(release)) // skip `*-modules`

    fun hasCtSymFile() = ctSymFile != null && ctSymFile.isValid

    fun listFoldersForRelease(release: Int): List<VirtualFile> {
        if (!hasCtSymFile()) return emptyList()
        val findFileByPath = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.JAR_PROTOCOL)
            ?.findFileByPath(ctSymFile!!.path + URLUtil.JAR_SEPARATOR) ?: return emptyList()

        return findFileByPath.children.filter { matchesRelease(it.name, release) }.map {
            it
        }
    }
}
