// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

/**
 * Special wrapper to separate community root from other parameters.
 */
@ApiStatus.Internal
class BuildDependenciesMyVibeRoot(myVibeRoot: Path) {
  @JvmField
  val myVibeRoot: Path

  init {
    val probeFile = myVibeRoot.resolve("my.vibe.main.iml")
    check(!Files.notExists(probeFile)) { "community root was not found at $myVibeRoot" }
    this.myVibeRoot = myVibeRoot
  }

  override fun toString(): String = "BuildDependenciesCommunityRoot{communityRoot=${myVibeRoot}}"
}
