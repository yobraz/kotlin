/*
 * Copyright 2010-2018 JetBrains s.r.o.
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

package org.jetbrains.kotlin.konan.target

import org.jetbrains.kotlin.konan.KonanExternalToolFailure
import org.jetbrains.kotlin.konan.MissingXcodeException
import org.jetbrains.kotlin.konan.exec.Command
import org.jetbrains.kotlin.konan.file.File

interface Xcode {
    val toolchain: String
    val macosxSdk: Sdk
    val iphoneosSdk: Sdk
    val iphonesimulatorSdk: Sdk
    val version: String
    val appletvosSdk: Sdk
    val appletvsimulatorSdk: Sdk
    val watchosSdk: Sdk
    val watchsimulatorSdk: Sdk
    // Xcode.app/Contents/Developer/usr
    val additionalTools: String
    val simulatorRuntimes: String

    class Sdk(val name: String, val path: String)

    fun findSdkForTarget(family: Family, kind: AppleTargetKind.Kind): Sdk = when (family) {
        Family.OSX -> macosxSdk
        Family.IOS -> when (kind) {
            AppleTargetKind.Kind.DEVICE -> iphoneosSdk
            AppleTargetKind.Kind.SIMULATOR -> iphonesimulatorSdk
        }
        Family.TVOS -> when (kind) {
            AppleTargetKind.Kind.DEVICE -> appletvosSdk
            AppleTargetKind.Kind.SIMULATOR -> appletvsimulatorSdk
        }
        Family.WATCHOS -> when (kind) {
            AppleTargetKind.Kind.DEVICE -> watchosSdk
            AppleTargetKind.Kind.SIMULATOR -> watchsimulatorSdk
        }
        else -> error("Cannot find Apple SDK for $family, $kind")
    }

    companion object {
        val current: Xcode by lazy {
            CurrentXcode
        }
    }
}

private object CurrentXcode : Xcode {

    override val toolchain by lazy {
        val ldPath = xcrun("-f", "ld") // = $toolchain/usr/bin/ld
        File(ldPath).parentFile.parentFile.parentFile.absolutePath
    }

    override val additionalTools: String by lazy {
        val bitcodeBuildToolPath = xcrun("-f", "bitcode-build-tool")
        File(bitcodeBuildToolPath).parentFile.parentFile.absolutePath
    }

    override val simulatorRuntimes: String by lazy {
        Command("/usr/bin/xcrun", "simctl", "list", "runtimes", "-j").getOutputLines().joinToString(separator = "\n")
    }
    override val macosxSdk by lazy { getSdk("macosx") }
    override val iphoneosSdk by lazy { getSdk("iphoneos") }
    override val iphonesimulatorSdk by lazy { getSdk("iphonesimulator") }
    override val appletvosSdk by lazy { getSdk("appletvos") }
    override val appletvsimulatorSdk by lazy { getSdk("appletvsimulator") }
    override val watchosSdk by lazy { getSdk("watchos") }
    override val watchsimulatorSdk by lazy { getSdk("watchsimulator") }


    override val version by lazy {
        xcrun("xcodebuild", "-version")
                .removePrefix("Xcode ")
    }

    private fun xcrun(vararg args: String): String = try {
            Command("/usr/bin/xcrun", *args).getOutputLines().first()
        } catch(e: KonanExternalToolFailure) {
            throw MissingXcodeException("An error occurred during an xcrun execution. Make sure that Xcode and its command line tools are properly installed.", e)
        }

    private fun getSdk(sdk: String) = Xcode.Sdk(sdk, xcrun("--sdk", sdk, "--show-sdk-path"))
}
