/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.project.structure

import org.jetbrains.kotlin.analysis.project.structure.KtNotUnderContentRootModule
import org.jetbrains.kotlin.analysis.providers.KotlinDeclarationProvider
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addIfNotNull

internal class LLFirNonUnderContentRootDeclarationProvider(private val module: KtNotUnderContentRootModule) : KotlinDeclarationProvider() {
    private val kotlinFile: KtFile?
        get() = module.file as? KtFile

    override fun getClassLikeDeclarationByClassId(classId: ClassId): KtClassLikeDeclaration? {
        if (classId.isLocal) {
            return null
        }

        var current: KtElement = kotlinFile?.takeIf { it.packageFqName == classId.packageFqName } ?: return null

        for (segment in classId.relativeClassName.pathSegments()) {
            val container = current as? KtDeclarationContainer ?: return null
            for (child in container.declarations) {
                if (child is KtClassLikeDeclaration && child.nameAsName == segment) {
                    current = child
                }
            }
        }

        return current as? KtClassLikeDeclaration
    }

    override fun getAllClassesByClassId(classId: ClassId): Collection<KtClassOrObject> {
        return listOfNotNull(getClassLikeDeclarationByClassId(classId) as? KtClassOrObject)
    }

    override fun getAllTypeAliasesByClassId(classId: ClassId): Collection<KtTypeAlias> {
        val name = classId.relativeClassName.pathSegments().singleOrNull() ?: return emptyList()
        return getTopLevelDeclarations(classId.packageFqName, name)
    }

    override fun getTopLevelKotlinClassLikeDeclarationNamesInPackage(packageFqName: FqName): Set<Name> {
        return getTopLevelDeclarationNames<KtClassLikeDeclaration>(packageFqName)
    }

    override fun getTopLevelProperties(callableId: CallableId): Collection<KtProperty> {
        return getTopLevelCallables(callableId)
    }

    override fun getTopLevelFunctions(callableId: CallableId): Collection<KtNamedFunction> {
        return getTopLevelCallables(callableId)
    }

    override fun getTopLevelCallableFiles(callableId: CallableId): Collection<KtFile> {
        return buildSet {
            getTopLevelProperties(callableId).mapTo(this) { it.containingKtFile }
            getTopLevelFunctions(callableId).mapTo(this) { it.containingKtFile }
        }
    }

    override fun getTopLevelCallableNamesInPackage(packageFqName: FqName): Set<Name> {
        return getTopLevelDeclarationNames<KtCallableDeclaration>(packageFqName)
    }

    override fun findFilesForFacadeByPackage(packageFqName: FqName): Collection<KtFile> {
        val kotlinFile = kotlinFile?.takeIf { it.packageFqName == packageFqName } ?: return emptyList()
        return listOf(kotlinFile)
    }

    override fun findFilesForFacade(facadeFqName: FqName): Collection<KtFile> {
        val kotlinFile = kotlinFile ?: return emptyList()

        for (declaration in kotlinFile.declarations) {
            if (declaration !is KtClassLikeDeclaration) {
                return listOf(kotlinFile)
            }
        }

        return emptyList()
    }

    private inline fun <reified T : KtCallableDeclaration> getTopLevelCallables(callableId: CallableId): Collection<T> {
        require(callableId.classId == null)
        return getTopLevelDeclarations(callableId.packageName, callableId.callableName)
    }

    private inline fun <reified T : KtNamedDeclaration> getTopLevelDeclarations(packageFqName: FqName, name: Name): Collection<T> {
        val kotlinFile = kotlinFile?.takeIf { it.packageFqName == packageFqName } ?: return emptyList()

        return buildList {
            for (declaration in kotlinFile.declarations) {
                if (declaration is T && declaration.nameAsName == name) {
                    add(declaration)
                }
            }
        }
    }

    private inline fun <reified T : KtNamedDeclaration> getTopLevelDeclarationNames(packageFqName: FqName): Set<Name> {
        val kotlinFile = kotlinFile?.takeIf { it.packageFqName == packageFqName } ?: return emptySet()

        return buildSet {
            for (declaration in kotlinFile.declarations) {
                if (declaration is T) {
                    addIfNotNull(declaration.nameAsName)
                }
            }
        }
    }
}