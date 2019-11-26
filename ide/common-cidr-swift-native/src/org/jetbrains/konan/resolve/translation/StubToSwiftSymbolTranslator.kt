package org.jetbrains.konan.resolve.translation

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.cidr.lang.OCLog
import com.jetbrains.swift.psi.types.SwiftTypeFactory
import com.jetbrains.swift.symbols.*
import org.jetbrains.konan.resolve.symbols.swift.*
import org.jetbrains.kotlin.backend.konan.objcexport.*
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor

class StubToSwiftSymbolTranslator(val project: Project) {
    fun translate(stub: Stub<*>, file: VirtualFile): SwiftSymbol? {
        return when (stub) {
            is ObjCProtocol -> KtSwiftProtocolSymbol(stub, project, file)
            is ObjCInterface -> {
                if (stub.categoryName != null) {
                    KtSwiftExtensionSymbol(stub, project, file)
                } else {
                    KtSwiftClassSymbol(stub, project, file)
                }
            }
            else -> {
                OCLog.LOG.error("unknown kotlin declaration: " + stub::class)
                null
            }
        }
    }

    fun translateMember(stub: Stub<*>, clazz: SwiftTypeSymbol, file: VirtualFile): SwiftMemberSymbol? {
        return when (stub) {
            is ObjCMethod -> {
                val isConstructor = stub.descriptor is ConstructorDescriptor && stub.name.startsWith("init")
                when (isConstructor) {
                    true -> KtSwiftInitializerSymbol(stub, file, project, clazz)
                    false -> KtSwiftMethodSymbol(stub, file, project, clazz)
                }.also { method ->
                    val parameters = translateParameters(stub, method, file)
                    val returnType = stub.returnType.convertType(method)
                    method.swiftType = SwiftTypeFactory.getInstance().run {
                        val functionType = createFunctionType(createDomainType(parameters), returnType, false)
                        when (isConstructor) {
                            true -> functionType
                            false -> createImplicitSelfMethodType(functionType)
                        }
                    }
                }
            }
            is ObjCProperty -> KtSwiftPropertySymbol(stub, project, file, clazz).also { property ->
                property.swiftType = stub.type.convertType(property)
            }
            else -> {
                OCLog.LOG.error("unknown kotlin objective-c declaration: " + stub::class)
                null
            }
        }
    }

    private fun translateParameters(
        methodStub: ObjCMethod,
        callableSymbol: SwiftCallableSymbol,
        file: VirtualFile
    ): List<SwiftParameterSymbol> =
        methodStub.parameters.map { parameterStub ->
            KtSwiftParameterSymbol(parameterStub, project, file, callableSymbol).apply {
                swiftType = parameterStub.type.convertType(this@apply)
            }
        }
}