/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.test.cases.generated.cases.components.compilerFacility;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.analysis.api.fir.test.configurators.AnalysisApiFirTestConfiguratorFactory;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.AnalysisApiTestConfiguratorFactoryData;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.AnalysisApiTestConfigurator;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.TestModuleKind;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.FrontendKind;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.AnalysisSessionMode;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.AnalysisApiMode;
import org.jetbrains.kotlin.analysis.api.impl.base.test.cases.components.compilerFacility.AbstractFirPluginPrototypeMultiBinaryModuleCompilerFacilityTest;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.analysis.api.GenerateAnalysisApiTestsKt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("analysis/analysis-api/testData/components/compilerFacility/firPluginPrototypeMultiBinaryModule")
@TestDataPath("$PROJECT_ROOT")
public class FirIdeNormalAnalysisLibraryBinaryModuleFirPluginPrototypeMultiBinaryModuleCompilerFacilityTestGenerated extends AbstractFirPluginPrototypeMultiBinaryModuleCompilerFacilityTest {
  @NotNull
  @Override
  public AnalysisApiTestConfigurator getConfigurator() {
    return AnalysisApiFirTestConfiguratorFactory.INSTANCE.createConfigurator(
      new AnalysisApiTestConfiguratorFactoryData(
        FrontendKind.Fir,
        TestModuleKind.LibraryBinary,
        AnalysisSessionMode.Normal,
        AnalysisApiMode.Ide
      )
    );
  }

  @Test
  public void testAllFilesPresentInFirPluginPrototypeMultiBinaryModule() {
    KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("analysis/analysis-api/testData/components/compilerFacility/firPluginPrototypeMultiBinaryModule"), Pattern.compile("^(.+)\\.(kt)$"), null, true);
  }

  @Test
  @TestMetadata("composableFunctionMultiModules.kt")
  public void testComposableFunctionMultiModules() {
    runTest("analysis/analysis-api/testData/components/compilerFacility/firPluginPrototypeMultiBinaryModule/composableFunctionMultiModules.kt");
  }

  @Test
  @TestMetadata("composableFunctionMultiModules2.kt")
  public void testComposableFunctionMultiModules2() {
    runTest("analysis/analysis-api/testData/components/compilerFacility/firPluginPrototypeMultiBinaryModule/composableFunctionMultiModules2.kt");
  }
}
