// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.projectSwitcher.NoProjectFrameManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.util.concurrency.annotations.RequiresEdt

open class CloseProjectWindowHelper {
  @RequiresEdt
  open fun windowClosing(project: Project?) {
    WriteIntentReadAction.run {
      if (project != null) {
        closeProjectAndShowNoProjectFrameIfNeeded(project)
        return@run
      }
      quitApp()
    }
  }

  protected open fun getNumberOfOpenedProjects(): Int = ProjectManager.getInstance().openProjects.size

  @RequiresEdt
  protected open fun closeProjectAndShowNoProjectFrameIfNeeded(project: Project?) {
    runInAutoSaveDisabledMode {
      if (project != null && project.isOpen) {
        WindowManagerEx.getInstanceEx().withFrameReuseEnabled().use {
          ProjectManager.getInstance().closeAndDispose(project)
        }
      }
      ApplicationManager.getApplication().messageBus.syncPublisher(AppLifecycleListener.TOPIC).projectFrameClosed()
      SaveAndSyncHandler.getInstance().scheduleSave(task = SaveAndSyncHandler.SaveTask(forceSavingAllSettings = true),
                                                    forceExecuteImmediately = true)
    }
    if (getNumberOfOpenedProjects() == 0) {
      NoProjectFrameManager.ensureNoProjectFrameShownAsync()
    }
  }

  protected open fun quitApp() {
    ApplicationManager.getApplication().exit()
  }
}
