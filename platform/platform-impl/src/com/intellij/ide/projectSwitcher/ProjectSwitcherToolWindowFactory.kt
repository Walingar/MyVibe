// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectSwitcher

import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManager.RecentProjectsChange
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel

internal class ProjectSwitcherToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val panel = ProjectSwitcherPanel(project)
    val content = ContentFactory.getInstance().createContent(panel.component, "", false)
    content.preferredFocusableComponent = panel.preferredFocusableComponent
    content.setDisposer(panel)
    toolWindow.contentManager.addContent(content)
  }
}

private class ProjectSwitcherPanel(private val currentProject: Project) : Disposable {
  private val model = DefaultListModel<ProjectEntry>()
  private val list = JBList(model)
  private val newProjectButton = JButton(IdeBundle.message("project.switcher.action.new.project"))
  private val switchButton = JButton(IdeBundle.message("project.switcher.action.switch"))
  private val refreshButton = JButton(IdeBundle.message("project.switcher.action.refresh"))

  val component: JComponent
  val preferredFocusableComponent: JComponent

  init {
    list.emptyText.text = IdeBundle.message("project.switcher.empty")
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.cellRenderer = EntryRenderer()
    list.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount == 2 && list.selectedValue != null) {
          switchTo(list.selectedValue)
        }
      }
    })

    newProjectButton.addActionListener {
      openNewProjectDialog()
    }
    switchButton.addActionListener {
      switchTo(list.selectedValue)
    }
    refreshButton.addActionListener {
      refreshEntries()
    }

    val buttons = JPanel().apply {
      layout = BorderLayout(JBUI.scale(6), 0)
      border = JBUI.Borders.empty(8, 0, 0, 0)
      add(newProjectButton, BorderLayout.WEST)
      add(switchButton, BorderLayout.CENTER)
      add(refreshButton, BorderLayout.EAST)
    }

    component = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty(8)
      add(JScrollPane(list), BorderLayout.CENTER)
      add(buttons, BorderLayout.SOUTH)
    }
    preferredFocusableComponent = list

    val connection = currentProject.messageBus.connect(this)
    connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectOpened(project: Project) = queueRefresh()
      override fun projectClosed(project: Project) = queueRefresh()
    })
    connection.subscribe(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC, object : RecentProjectsChange {
      override fun change() = queueRefresh()
    })

    refreshEntries()
  }

  private fun queueRefresh() {
    ApplicationManager.getApplication().invokeLater(
      { refreshEntries() },
      { currentProject.isDisposed },
    )
  }

  private fun refreshEntries() {
    if (currentProject.isDisposed) {
      return
    }

    val selectedKey = list.selectedValue?.key
    val entries = computeEntries(currentProject)
    model.clear()
    for (entry in entries) {
      model.addElement(entry)
    }

    val selectedIndex = entries.indexOfFirst { it.key == selectedKey }.let {
      if (it >= 0) it else entries.indexOfFirst { entry -> entry.isCurrent }
    }
    if (selectedIndex >= 0) {
      list.selectedIndex = selectedIndex
      list.ensureIndexIsVisible(selectedIndex)
    }
  }

  private fun switchTo(entry: ProjectEntry?) {
    if (entry == null || currentProject.isDisposed) {
      return
    }

    entry.openProject?.let {
      ProjectUtil.focusProjectWindow(it)
      return
    }

    val path = entry.path ?: return
    val nioPath = runCatching { Path.of(path) }.getOrNull() ?: return
    ProjectUtil.openOrImport(nioPath, OpenProjectTask {
      projectToClose = currentProject
    })
  }

  private fun openNewProjectDialog() {
    val actionManager = ActionManager.getInstance()
    val action = sequenceOf("NewProject", "WelcomeScreen.CreateNewProject", "GoIdeNewProjectAction")
      .mapNotNull(actionManager::getAction)
      .firstOrNull() ?: return

    actionManager.tryToExecute(action, null, component, ActionPlaces.UNKNOWN, true)
  }

  override fun dispose() {
  }
}

private data class ProjectEntry(
  val key: String,
  val displayName: String,
  val path: String?,
  val pathToShow: String,
  val isCurrent: Boolean,
  val openProject: Project?,
)

private fun computeEntries(currentProject: Project): List<ProjectEntry> {
  val manager = RecentProjectsManagerBase.getInstanceEx()
  val openProjects = ProjectManager.getInstance().openProjects
  val currentPath = currentProject.projectIdentityPath()
  val openByPath = LinkedHashMap<String, Project>()
  val entriesWithoutPath = ArrayList<ProjectEntry>()
  val paths = LinkedHashSet<String>()

  for (project in openProjects) {
    val path = project.projectIdentityPath()
    if (path == null) {
      entriesWithoutPath.add(ProjectEntry(
        key = "open:${project.locationHash}",
        displayName = project.name,
        path = null,
        pathToShow = IdeBundle.message("project.switcher.open.project.without.path"),
        isCurrent = project == currentProject,
        openProject = project,
      ))
      continue
    }

    openByPath[path] = project
    paths.add(path)
  }

  paths.addAll(manager.getRecentPaths())
  val result = ArrayList<ProjectEntry>(paths.size + entriesWithoutPath.size)
  result.addAll(entriesWithoutPath)

  for (path in paths) {
    val openProject = openByPath[path]
    result.add(ProjectEntry(
      key = path,
      displayName = openProject?.name ?: manager.getDisplayName(path) ?: manager.getProjectName(path),
      path = path,
      pathToShow = path,
      isCurrent = path == currentPath,
      openProject = openProject,
    ))
  }
  return result
}

private fun Project.projectIdentityPath(): String? {
  return RecentProjectsManagerBase.getInstanceEx().getProjectPath(this)?.toString()
}

private class EntryRenderer : ColoredListCellRenderer<ProjectEntry>() {
  override fun customizeCellRenderer(
    list: JList<out ProjectEntry>,
    value: ProjectEntry,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean,
  ) {
    append(value.displayName)
    if (value.isCurrent) {
      append("  " + IdeBundle.message("project.switcher.current"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    }
    append("  " + value.pathToShow, SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }
}
