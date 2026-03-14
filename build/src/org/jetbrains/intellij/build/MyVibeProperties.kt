// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Modified
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import org.jetbrains.intellij.build.BuildPaths.Companion.MY_VIBE_ROOT
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.impl.qodana.QodanaProductProperties
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.intellij.build.productLayout.CommunityModuleSets
import org.jetbrains.intellij.build.productLayout.CommunityProductFragments
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.productModules
import java.nio.file.Path

val MAVEN_ARTIFACTS_ADDITIONAL_MODULES: PersistentList<String> = persistentListOf(
  "intellij.tools.jps.build.standalone",
  "intellij.devkit.runtimeModuleRepository.jps",
  "intellij.devkit.jps",
  "intellij.idea.community.build.tasks",
  "intellij.platform.debugger.testFramework",
  "intellij.platform.vcs.testFramework",
  "intellij.platform.externalSystem.testFramework",
  "intellij.platform.uast.testFramework",
  "intellij.maven.testFramework",
  "intellij.tools.reproducibleBuilds.diff",
  "intellij.space.java.jps",
)

internal suspend fun createMyVibeBuildContext(
  options: BuildOptions,
  projectHome: Path = MY_VIBE_ROOT.myVibeRoot,
): BuildContext {
  return createBuildContext(
    projectHome = projectHome,
    productProperties = MyVibeProperties(MY_VIBE_ROOT.myVibeRoot),
    setupTracer = true,
    options = options,
  )
}

open class MyVibeProperties(private val communityHomeDir: Path) : JetBrainsProductProperties() {
  init {
    configurePropertiesForAllEditionsOfIntelliJIdea(this)
    platformPrefix = "MyVibe"
    applicationInfoModule = "intellij.idea.community.customization"
    scrambleMainJar = false
    useSplash = true
    buildCrossPlatformDistribution = true
    buildSourcesArchive = true

    productLayout.productImplementationModules = listOf(
      "intellij.platform.starter",
      "intellij.idea.community.customization",
    )

    productLayout.bundledPluginModules = IDEA_BUNDLED_PLUGINS

    productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
    productLayout.buildAllCompatiblePlugins = true
    productLayout.pluginLayouts = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS

    productLayout.skipUnresolvedContentModules = true

    mavenArtifacts.forIdeModules = true
    mavenArtifacts.additionalModules += MAVEN_ARTIFACTS_ADDITIONAL_MODULES
    mavenArtifacts.squashedModules += persistentListOf(
      "intellij.platform.util.base",
      "intellij.platform.util.base.multiplatform",
      "intellij.platform.util.zip",
    )

    versionCheckerConfig = CE_CLASS_VERSIONS
    buildDocAuthoringAssets = true

    @Suppress("SpellCheckingInspection")
    qodanaProductProperties = QodanaProductProperties("QDJVMC", "Qodana Community for JVM")
    additionalVmOptions = persistentListOf("-Dllm.show.ai.promotion.window.on.start=false")
  }

  override val baseFileName: String
    get() = "idea"

  override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
    include(intellijCommunityBaseFragment())
  }

  override suspend fun copyAdditionalFiles(targetDir: Path, context: BuildContext) {
    super.copyAdditionalFiles(targetDir, context)

    copyFileToDir(context.paths.myVibeHomeDir.resolve("LICENSE.txt"), targetDir)
    copyFileToDir(context.paths.myVibeHomeDir.resolve("NOTICE.txt"), targetDir)
    copyFileToDir(context.paths.myVibeHomeDir.resolve("MODIFICATIONS.md"), targetDir)

    copyDir(
      sourceDir = context.paths.myVibeHomeDir.resolve("build/conf/MyVibe/common/bin"),
      targetDir = targetDir.resolve("bin"),
    )

    bundleExternalPlugins(context, targetDir)
  }

  protected open suspend fun bundleExternalPlugins(context: BuildContext, targetDirectory: Path) {}

  override fun createWindowsCustomizer(projectHome: Path): WindowsDistributionCustomizer = communityWindowsCustomizer(communityHomeDir)

  override fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer = communityLinuxCustomizer(communityHomeDir)

  override fun createMacCustomizer(projectHome: Path): MacDistributionCustomizer = communityMacCustomizer(communityHomeDir)

  override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    return "MyVibe${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"
  }

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "MyVibe-$buildNumber"

  override fun getOutputDirectoryName(appInfo: ApplicationInfoProperties): String = "MyVibe"
}

/**
 * Base IntelliJ Community content fragment.
 * This fragment is composable - subclasses can include this and optionally add community extensions.
 */
fun intellijCommunityBaseFragment(): ProductModulesContentSpec = productModules {
  alias("com.intellij.modules.idea")
  alias("com.intellij.modules.idea.community")

  alias("com.intellij.modules.java-capable")
  alias("com.intellij.modules.python-core-capable")
  alias("com.intellij.modules.python-in-non-pycharm-ide-capable")

  alias("com.intellij.platform.ide.provisioner")
  alias("com.intellij.modules.jcef")

  include(CommunityProductFragments.javaIdeBaseFragment())
  deprecatedInclude("intellij.idea.community.customization", "META-INF/tips-intellij-idea-community.xml")

  module("intellij.platform.coverage")
  module("intellij.platform.coverage.agent")
  module("intellij.xml.xmlbeans")
  module("intellij.platform.ide.newUiOnboarding")
  module("intellij.platform.ide.newUsersOnboarding")
  module("intellij.ide.startup.importSettings")
  module("intellij.platform.customization.min")
  module("intellij.idea.customization.base")
  module("intellij.idea.customization.backend")
  module("intellij.platform.tips")

  moduleSet(CommunityModuleSets.ideCommon())
  moduleSet(CommunityModuleSets.rdCommon())

  deprecatedInclude("intellij.idea.community.customization", "META-INF/community-customization.xml")
}