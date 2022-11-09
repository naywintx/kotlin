/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.utils

import com.android.build.gradle.*
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Project
import org.gradle.api.internal.DefaultDomainObjectSet
import org.jetbrains.kotlin.gradle.targets.android.internal.AndroidDependencyResolver.getMethodOrNull

internal fun Project.forAllAndroidVariants(action: (BaseVariant) -> Unit) {
    val androidExtension = this.extensions.getByName("android")
    when (androidExtension) {
        is AppExtension -> androidExtension.applicationVariants.all(action)
        is LibraryExtension -> {
            androidExtension.libraryVariants.all(action)
            if (androidExtension is FeatureExtension) {
                val getFeature = androidExtension::class.java.getMethodOrNull("getFeatureVariants")
                val featureVariants = getFeature?.invoke(androidExtension) as? DefaultDomainObjectSet<BaseVariant>
                featureVariants?.all(action)
            }
        }

        is TestExtension -> androidExtension.applicationVariants.all(action)
    }
    if (androidExtension is TestedExtension) {
        androidExtension.testVariants.all(action)
        androidExtension.unitTestVariants.all(action)
    }
}
