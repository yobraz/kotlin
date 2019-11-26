package org.jetbrains.konan.resolve.symbols.swift

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.swift.psi.SwiftDeclarationKind
import com.jetbrains.swift.psi.types.SwiftFunctionType
import com.jetbrains.swift.symbols.SwiftCanBeStatic
import com.jetbrains.swift.symbols.SwiftFunctionSymbol
import com.jetbrains.swift.symbols.SwiftTypeSymbol
import org.jetbrains.kotlin.backend.konan.objcexport.ObjCMethod

class KtSwiftMethodSymbol : KtSwiftCallableSymbol, SwiftFunctionSymbol {
    constructor(
        stub: ObjCMethod,
        file: VirtualFile,
        project: Project,
        containingTypeSymbol: SwiftTypeSymbol
    ) : super(stub, file, project, containingTypeSymbol)

    constructor() : super()

    override val swiftDeclaredType: SwiftFunctionType
        get() = super.swiftDeclaredType

    override val declarationKind: SwiftDeclarationKind
        get() = SwiftDeclarationKind.method

    override fun getStaticness(): SwiftCanBeStatic.Staticness = SwiftCanBeStatic.Staticness.NOT_STATIC
}