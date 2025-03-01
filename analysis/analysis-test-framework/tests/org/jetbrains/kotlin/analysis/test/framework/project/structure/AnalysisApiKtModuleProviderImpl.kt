/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.test.framework.project.structure

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.project.structure.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestService
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.moduleStructure

abstract class AnalysisApiKtModuleProvider : TestService {
    protected abstract val testServices: TestServices

    abstract fun getModule(moduleName: String): KtModule

    abstract fun getModuleFiles(module: TestModule): List<PsiFile>

    abstract fun registerProjectStructure(modules: KtTestModuleProjectStructure)

    abstract fun getModuleStructure(): KtTestModuleProjectStructure
}

class AnalysisApiKtModuleProviderImpl(
    override val testServices: TestServices,
) : AnalysisApiKtModuleProvider() {
    private lateinit var modulesStructure: KtTestModuleProjectStructure
    private lateinit var modulesByName: Map<String, KtTestModule>

    override fun getModule(moduleName: String): KtModule {
        return modulesByName.getValue(moduleName).ktModule
    }

    override fun getModuleFiles(module: TestModule): List<PsiFile> =
        (modulesByName[module.name] ?: modulesByName.getValue(module.files.single().name)).files

    override fun registerProjectStructure(modules: KtTestModuleProjectStructure) {
        require(!this::modulesStructure.isInitialized)
        require(!this::modulesByName.isInitialized)

        this.modulesStructure = modules
        this.modulesByName = modulesStructure.mainModules.associateByName()
    }

    override fun getModuleStructure(): KtTestModuleProjectStructure = modulesStructure
}

fun AnalysisApiKtModuleProvider.getKtFiles(module: TestModule): List<KtFile> = getModuleFiles(module).filterIsInstance<KtFile>()

val AnalysisApiKtModuleProvider.mainModules: List<KtTestModule> get() = getModuleStructure().mainModules

fun TestServices.allKtFiles(): List<KtFile> = moduleStructure.modules.flatMap(ktModuleProvider::getKtFiles)

val TestServices.ktModuleProvider: AnalysisApiKtModuleProvider by TestServices.testServiceAccessor()

fun TestModule.getKtModule(testServices: TestServices): KtModule = testServices.ktModuleProvider.getModule(name)

fun List<KtTestModule>.associateByName(): Map<String, KtTestModule> {
    return associateBy { ktTestModule ->
        when (val ktModule = ktTestModule.ktModule) {
            is KtModuleByCompilerConfiguration -> ktModule.moduleName
            is KtSourceModule -> ktModule.moduleName
            is KtLibraryModule -> ktModule.libraryName
            is KtLibrarySourceModule -> ktModule.libraryName
            is KtSdkModule -> ktModule.sdkName
            is KtBuiltinsModule -> "Builtins for ${ktModule.platform}"
            is KtNotUnderContentRootModule -> ktModule.name
            is KtScriptModule -> ktModule.file.name
            is KtDanglingFileModule -> ktModule.file.name
            else -> error("Unsupported module type: " + ktModule.javaClass.name)
        }
    }
}
