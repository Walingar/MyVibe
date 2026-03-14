// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectSwitcher

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel

class ApplicationProjectsToolWindowFactory : ApplicationToolWindowFactory {
  override val id: String = "Projects"
  override val stripeTitle: String = IdeBundle.message("toolwindow.stripe.Projects")
  override val anchor: ToolWindowAnchor = ToolWindowAnchor.LEFT
  override val icon: Icon = AllIcons.Toolwindows.ToolWindowProject

  override fun createToolWindowContent(): JComponent {
    val model = DefaultListModel<AppProjectEntry>()
    val list = JBList(model)
    val openButton = javax.swing.JButton(IdeBundle.message("project.switcher.action.open.selected"))
    val newButton = javax.swing.JButton(IdeBundle.message("project.switcher.action.new.project"))
    val refreshButton = javax.swing.JButton(IdeBundle.message("project.switcher.action.refresh"))

    fun refresh() {
      val selectedKey = list.selectedValue?.key
      val entries = computeAppEntries()
      model.clear()
      for (entry in entries) {
        model.addElement(entry)
      }
      val selectedIndex = entries.indexOfFirst { it.key == selectedKey }.let { if (it >= 0) it else 0 }
      if (selectedIndex >= 0 && selectedIndex < model.size()) {
        list.selectedIndex = selectedIndex
        list.ensureIndexIsVisible(selectedIndex)
      }
    }

    fun openSelected() {
      val entry: AppProjectEntry = list.selectedValue ?: return
      entry.openProject?.let {
        ProjectUtil.focusProjectWindow(it)
        return
      }
      val nioPath = runCatching { Path.of(entry.path ?: return) }.getOrNull() ?: return
      ProjectUtil.openOrImport(nioPath)
    }

    fun openNewProjectDialog(parent: JComponent) {
      val actionManager = ActionManager.getInstance()
      val action = sequenceOf("NewProject", "WelcomeScreen.CreateNewProject", "GoIdeNewProjectAction")
        .mapNotNull(actionManager::getAction)
        .firstOrNull() ?: return
      actionManager.tryToExecute(action, null, parent, ActionPlaces.UNKNOWN, true)
    }

    list.emptyText.text = IdeBundle.message("project.switcher.empty")
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.cellRenderer = AppEntryRenderer()
    list.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount == 2) {
          openSelected()
        }
      }
    })

    val component = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty(8)
      add(JScrollPane(list), BorderLayout.CENTER)
      add(JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
        border = JBUI.Borders.empty(8, 0, 0, 0)
        add(newButton, BorderLayout.WEST)
        add(openButton, BorderLayout.CENTER)
        add(refreshButton, BorderLayout.EAST)
      }, BorderLayout.SOUTH)
    }

    openButton.addActionListener { openSelected() }
    newButton.addActionListener { openNewProjectDialog(component) }
    refreshButton.addActionListener { refresh() }

    refresh()
    return component
  }
}

private data class AppProjectEntry(
  val key: String,
  val displayName: String,
  val path: String?,
  val pathToShow: String,
  val openProject: com.intellij.openapi.project.Project?,
)

private fun computeAppEntries(): List<AppProjectEntry> {
  val manager = RecentProjectsManagerBase.getInstanceEx()
  val openProjects = ProjectManager.getInstance().openProjects
  val openByPath = LinkedHashMap<String, com.intellij.openapi.project.Project>()
  val entriesWithoutPath = ArrayList<AppProjectEntry>()
  val paths = LinkedHashSet<String>()

  for (project in openProjects) {
    val path = manager.getProjectPath(project)?.toString()
    if (path == null) {
      entriesWithoutPath.add(AppProjectEntry(
        key = "open:${project.locationHash}",
        displayName = project.name,
        path = null,
        pathToShow = IdeBundle.message("project.switcher.open.project.without.path"),
        openProject = project,
      ))
      continue
    }
    openByPath[path] = project
    paths.add(path)
  }

  paths.addAll(manager.getRecentPaths())
  val result = ArrayList<AppProjectEntry>(paths.size + entriesWithoutPath.size)
  result.addAll(entriesWithoutPath)
  for (path in paths) {
    val openProject = openByPath[path]
    result.add(AppProjectEntry(
      key = path,
      displayName = openProject?.name ?: manager.getDisplayName(path) ?: manager.getProjectName(path),
      path = path,
      pathToShow = path,
      openProject = openProject,
    ))
  }
  return result
}

private class AppEntryRenderer : ColoredListCellRenderer<AppProjectEntry>() {
  override fun customizeCellRenderer(
    list: JList<out AppProjectEntry>,
    value: AppProjectEntry,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean,
  ) {
    append(value.displayName)
    append("  " + value.pathToShow, SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }
}
