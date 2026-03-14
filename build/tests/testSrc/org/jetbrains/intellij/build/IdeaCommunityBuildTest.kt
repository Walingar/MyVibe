// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import MyVibeInstallersBuildTarget
import com.intellij.openapi.application.PathManager
import com.intellij.platform.buildScripts.testFramework.createBuildOptionsForTest
import com.intellij.platform.buildScripts.testFramework.runEssentialPluginsTest
import com.intellij.platform.buildScripts.testFramework.runTestBuild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildPaths.Companion.MY_VIBE_ROOT
import org.jetbrains.intellij.build.impl.createBuildContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

class IdeaCommunityBuildTest {
  @Test
  fun build(testInfo: TestInfo) {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    val productProperties = MyVibeProperties(MY_VIBE_ROOT.myVibeRoot)
    runTestBuild(
      homeDir = MY_VIBE_ROOT.myVibeRoot,
      testInfo = testInfo,
      productProperties = productProperties,
    ) {
      it.classOutDir = it.classOutDir ?: "$homePath/out/classes"
      /**
       * [com.intellij.platform.buildScripts.testFramework.customizeBuildOptionsForTest] modified [BuildOptions.buildStepsToSkip]
       * which should never be changed for this test because it's expected to match the production behavior
       */
      it.buildStepsToSkip = MyVibeInstallersBuildTarget.OPTIONS.buildStepsToSkip +
                            // no need to publish TeamCity artifacts from a test
                            BuildOptions.TEAMCITY_ARTIFACTS_PUBLICATION_STEP
    }
  }

  @Test
  fun jpsStandalone(testInfo: TestInfo) {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    runBlocking(Dispatchers.Default) {
      runTestBuild(
        testInfo = testInfo,
        context = {
          val productProperties = MyVibeProperties(MY_VIBE_ROOT.myVibeRoot)
          val options = createBuildOptionsForTest(
            productProperties = productProperties,
            homeDir = homePath,
            skipDependencySetup = true,
            testInfo = testInfo,
          )
          createBuildContext(projectHome = homePath, productProperties = productProperties, setupTracer = false, options = options)
        },
      ) {
        buildMyVibeStandaloneJpsBuilder(targetDir = it.paths.artifactDir.resolve("jps"), context = it)
      }
    }
  }

  @Test
  fun `essential plugins depend only on essential plugins`() {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    runEssentialPluginsTest(
      homePath = homePath,
      productProperties = MyVibeProperties(MY_VIBE_ROOT.myVibeRoot),
      buildTools = ProprietaryBuildTools.DUMMY,
    )
  }
}