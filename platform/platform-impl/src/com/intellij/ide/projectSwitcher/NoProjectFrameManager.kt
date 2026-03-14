// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectSwitcher

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.createIdeFrame
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.FrameInfo
import com.intellij.openapi.wm.impl.FrameLoadingState
import com.intellij.openapi.wm.impl.IdeProjectFrameHelper
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton

object NoProjectFrameManager {
  fun ensureNoProjectFrameShownAsync() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }
    ApplicationManager.getApplication().service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      ensureNoProjectFrameShown()
    }
  }

  suspend fun ensureNoProjectFrameShown() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }
    if (ProjectManager.getInstance().openProjects.isNotEmpty()) {
      return
    }

    withContext(Dispatchers.EDT) {
      val wm = service<com.intellij.openapi.wm.WindowManager>() as WindowManagerImpl
      val frameHelper = getOrCreateNoProjectFrameHelper(wm)
      installNoProjectUi(frameHelper)
      val frame = frameHelper.frame
      frame.isVisible = true
      frame.toFront()
      wm.setRootFrameToReuse(frame)
    }
  }

  private fun getOrCreateNoProjectFrameHelper(windowManager: WindowManagerImpl): IdeProjectFrameHelper {
    val reusedFrame = windowManager.removeAndGetRootFrame()
    val existingHelper = reusedFrame?.frameHelper?.helper as? IdeProjectFrameHelper
    if (existingHelper != null && existingHelper.project == null) {
      return existingHelper
    }

    val frame = reusedFrame ?: createIdeFrame(FrameInfo())
    val helper = IdeProjectFrameHelper(
      frame = frame,
      loadingState = NoProjectFrameLoadingState,
      projectFrameTypeId = null,
    )
    helper.init()
    helper.postInit()
    return helper
  }

  private fun installNoProjectUi(frameHelper: IdeProjectFrameHelper) {
    frameHelper.toolWindowPane.setDocumentComponent(createToolWindowShell())
    frameHelper.setFrameTitle(ApplicationNamesInfo.getInstance().fullProductName)
  }

  private fun createToolWindowShell(): JComponent {
    val factories = ApplicationToolWindowFactory.EP_NAME.extensionList.filter { it.isApplicable() }
    val cards = JPanel(CardLayout())
    val leftStripe = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    val rightStripe = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    val group = ButtonGroup()

    for (factory in factories) {
      val id = factory.id
      cards.add(factory.createToolWindowContent(), id)
      val button = JToggleButton(factory.stripeTitle, factory.icon).apply {
        addActionListener {
          (cards.layout as CardLayout).show(cards, id)
        }
      }
      group.add(button)
      when (factory.anchor) {
        ToolWindowAnchor.RIGHT -> rightStripe.add(button)
        else -> leftStripe.add(button)
      }
    }

    (leftStripe.components.firstOrNull() as? JToggleButton)?.isSelected = true
    factories.firstOrNull()?.let { (cards.layout as CardLayout).show(cards, it.id) }

    return JPanel(BorderLayout()).apply {
      if (leftStripe.componentCount > 0) {
        add(leftStripe, BorderLayout.WEST)
      }
      add(cards, BorderLayout.CENTER)
      if (rightStripe.componentCount > 0) {
        add(rightStripe, BorderLayout.EAST)
      }
    }
  }
}

private object NoProjectFrameLoadingState : FrameLoadingState {
  override val done: Job = Job().apply { complete() }
}
