// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectSwitcher

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.wm.ToolWindowAnchor
import javax.swing.Icon
import javax.swing.JComponent

interface ApplicationToolWindowFactory {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<ApplicationToolWindowFactory> = ExtensionPointName("com.intellij.applicationToolWindowFactory")
  }

  val id: String
  val stripeTitle: String
  val anchor: ToolWindowAnchor
    get() = ToolWindowAnchor.LEFT
  val icon: Icon?
    get() = null

  fun isApplicable(): Boolean = true

  fun createToolWindowContent(): JComponent
}
