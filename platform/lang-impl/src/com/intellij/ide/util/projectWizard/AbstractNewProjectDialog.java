// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.util.projectWizard.actions.ProjectSpecificAction;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperPeer;
import com.intellij.openapi.util.Disposer;
import com.intellij.platform.ProjectGeneratorPeer;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.ListModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * @author Dennis.Ushakov
 */
public abstract class AbstractNewProjectDialog extends DialogWrapper {
  private static final int DEFAULT_DIALOG_WIDTH = 1000;
  private static final int DEFAULT_DIALOG_HEIGHT = 670;

  private JBList<AnAction> myActionList;
  private JPanel mySettingsPanel;
  private final Map<AnAction, JComponent> myActionPanels = new HashMap<>();

  public AbstractNewProjectDialog() {
    super(ProjectManager.getInstance().getDefaultProject());
    init();
  }

  @Override
  protected final void init() {
    super.init();
    DialogWrapperPeer peer = getPeer();
    JRootPane pane = peer.getRootPane();
    if (pane != null) {
      JBDimension size = JBUI.size(DEFAULT_DIALOG_WIDTH, DEFAULT_DIALOG_HEIGHT);
      pane.setMinimumSize(size);
      pane.setPreferredSize(size);
    }
  }

  @Override
  protected final @Nullable JComponent createCenterPanel() {
    setTitle(AbstractNewProjectStep.EP_NAME.hasAnyExtensions() ? ProjectBundle.message("dialog.title.new.project")
                                                               : ProjectBundle.message("dialog.title.create.project"));
    AbstractNewProjectStep<?> root = createNewProjectStep();
    Disposer.register(getDisposable(), () -> root.removeAll());
    root.setWizardContext(new WizardContext(null, getDisposable()));

    List<AnAction> actions = collectProjectActions(root);
    myActionList = new JBList<>(actions);
    myActionList.setCellRenderer(SimpleListCellRenderer.create("", action -> action.getTemplateText()));
    myActionList.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        showSelectedActionPanel(myActionList.getSelectedValue());
      }
    });

    mySettingsPanel = new JPanel(new BorderLayout());
    if (!actions.isEmpty()) {
      myActionList.setSelectedIndex(0);
      showSelectedActionPanel(actions.get(0));
    }

    JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.setBorder(JBUI.Borders.emptyRight(8));
    leftPanel.add(new JBScrollPane(myActionList), BorderLayout.CENTER);
    leftPanel.setPreferredSize(JBUI.size(320, DEFAULT_DIALOG_HEIGHT));

    JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(leftPanel, BorderLayout.WEST);
    mainPanel.add(mySettingsPanel, BorderLayout.CENTER);

    UiNotifyConnector.doWhenFirstShown(myActionList, () -> ScrollingUtil.ensureSelectionExists(myActionList));
    return mainPanel;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myActionList;
  }

  @Override
  protected @Nullable JComponent createSouthPanel() {
    return null;
  }

  @Override
  protected @NotNull DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  /**
   * @deprecated use {@link #createNewProjectStep()}
   */
  @Deprecated
  protected @Nullable DefaultActionGroup createRootStep() {
    return null;
  }

  protected @NotNull AbstractNewProjectStep<?> createNewProjectStep() {
    var step = createRootStep();
    if (step instanceof AbstractNewProjectStep<?> abstractNewProjectStep) {
      return abstractNewProjectStep;
    }
    throw PluginException.createByClass(new AssertionError("override 'createNewProjectStep' instead of deprecated 'createRootStep'"), getClass());
  }


  @Override
  protected String getHelpId() {
    return "create_new_project_dialog";
  }

  @Override
  protected final Action @NotNull [] createActions() {
    return new Action[0];
  }

  @ApiStatus.Internal
  public boolean setSelectedAction(@NotNull Predicate<AnAction> actionSelector) {
    if (myActionList == null) return false;
    JBList<AnAction> actionList = myActionList;
    ListModel<AnAction> model = actionList.getModel();

    for (int i = 0; i < model.getSize(); i++) {
      AnAction action = model.getElementAt(i);
      if (actionSelector.test(action)) {
        actionList.setSelectedIndex(i);
        return true;
      }
    }
    return false;
  }

  private static @NotNull List<AnAction> collectProjectActions(@NotNull DefaultActionGroup root) {
    List<AnAction> result = new ArrayList<>();
    collectProjectActionsRecursively(root, result);
    return result;
  }

  private static void collectProjectActionsRecursively(@NotNull DefaultActionGroup group, @NotNull List<AnAction> result) {
    for (AnAction action : group.getChildren(ActionManager.getInstance())) {
      if (action instanceof ProjectSettingsStepBase<?> || action instanceof ProjectSpecificAction) {
        result.add(action);
      }
      else if (action instanceof DefaultActionGroup nestedGroup) {
        collectProjectActionsRecursively(nestedGroup, result);
      }
    }
  }

  private void showSelectedActionPanel(@Nullable AnAction action) {
    if (mySettingsPanel == null || action == null) return;
    mySettingsPanel.removeAll();

    if (action instanceof ProjectSpecificAction group) {
      AnAction[] children = group.getChildren(ActionManager.getInstance());
      if (children.length > 0) {
        action = children[0];
      }
    }

    if (action instanceof ProjectSettingsStepBase<?> step) {
      JComponent panel = myActionPanels.computeIfAbsent(action, __ -> step.createPanel());
      step.onPanelSelected();
      mySettingsPanel.add(panel, BorderLayout.CENTER);
    }

    mySettingsPanel.revalidate();
    mySettingsPanel.repaint();
  }

  static class ProjectStepPeerHolder extends StepAdapter {
    private final ProjectGeneratorPeer<?> myPeer;

    ProjectStepPeerHolder(ProjectGeneratorPeer<?> peer) {
      myPeer = peer;
    }

    public ProjectGeneratorPeer<?> getPeer() {
      return myPeer;
    }

    @Override
    public JComponent getComponent() {
      return myPeer.getComponent();
    }
  }
}
