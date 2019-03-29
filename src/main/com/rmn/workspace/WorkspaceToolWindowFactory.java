package com.rmn.workspace;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.CheckBoxListListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.content.*;
import com.rmn.workspace.gradle.ExternalGradleLibraryInfo;
import com.rmn.workspace.gradle.Workspace;
import com.rmn.workspace.gradle.WorkspaceInfo;
import com.rmn.workspace.gradle.ExternalGradleLibraryInfo;
import com.rmn.workspace.gradle.Workspace;
import com.rmn.workspace.gradle.WorkspaceInfo;

import javax.swing.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The main workhorse for dealing with the {@link ToolWindow}.
 * @author trader
 */
public class WorkspaceToolWindowFactory implements ToolWindowFactory {

    private JButton refreshToolWindowButton;
    private JPanel myToolWindowContent;
    private CheckBoxList<String> libraryList;
    private JButton createWorkspaceButton;
    private JButton addModuleToWorkspaceButton;
    private ToolWindow myToolWindow;

    // Hard coded for now.
    private static final String WORKSPACE_FILE_NAME = "/workspace.manifest";
    private static final String EMPTY_MODULE_LIST_TEXT = "Workspace has no modules";

    private Project project;

    private final AtomicBoolean isRepopulating = new AtomicBoolean();

    private Workspace workspace;

    public WorkspaceToolWindowFactory() {
        refreshToolWindowButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                repopulateWorkspaceInfo();
            }
        });

        createWorkspaceButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String path = getWorkspacePath();
                VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
                if (file == null) {
                    String workspaceName = (String)JOptionPane.showInputDialog(myToolWindow.getComponent(), "Workspace Name", "Create Workspace", JOptionPane.PLAIN_MESSAGE, IconLoader.getIcon("/general/createNewProject.png"), null, null);
                    ApplicationManager.getApplication().runWriteAction(new Runnable() {
                        @Override
                        public void run() {
                            if (workspaceName != null && !workspaceName.isEmpty()) {
                                WorkspaceInfo workspaceInfo = Workspace.create(workspaceName, project, WORKSPACE_FILE_NAME.substring(1));
                                if (workspaceInfo != null) {
                                    repopulateWorkspaceInfo();
                                } else {
                                    setError("Unable to find workspace file '" + getWorkspacePath() + "'");
                                }
                                onRepopulateFinished();
                            }
                        }
                    });
                } else {
                    repopulateWorkspaceInfo();
                }
            }
        });

        libraryList.setCheckBoxListListener(new CheckBoxListListener() {
            @Override
            public void checkBoxSelectionChanged(final int index, final boolean checked) {
                String libName = libraryList.getItemAt(index);
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                        if (checked) {
                            workspace.includeModuleSourceInWorkspace(libName);
                        } else {
                            workspace.excludeModuleFromWorkspace(libName);
                        }
                    }
                });
            }
        });
    }

    // Create the tool window content.
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        this.project = project;
        myToolWindow = toolWindow;
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(myToolWindowContent, "", false);
        toolWindow.getContentManager().addContent(content);

        libraryList.getEmptyText().setText(EMPTY_MODULE_LIST_TEXT);


        // Populate
        repopulateWorkspaceInfo();
    }

    private void repopulateWorkspaceInfo() {
        if (!isRepopulating.getAndSet(true)) {
            libraryList.setPaintBusy(true);
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                    workspace = createWorkspace();

                    onRepopulateFinished();
                }
            });
        }
    }

    private String getWorkspacePath() {
        return project.getBasePath() + "/" + WORKSPACE_FILE_NAME;
    }

    private Workspace createWorkspace() {
        final String workspaceFilePath = getWorkspacePath();
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(workspaceFilePath);
        if (file == null) {
            setError("Unable to find workspace file '" + workspaceFilePath + "'");
            onRepopulateFinished();
            return null;
        }

        java.io.InputStream inputStream = null;
        try {
            file.refresh(false, false);
            inputStream = file.getInputStream();
            WorkspaceInfo workspaceInfo = AppJsonMapper.INSTANCE.getMapper().readerFor(WorkspaceInfo.class).readValue(inputStream);
            inputStream.close();
            return new Workspace(project.getBasePath() + "/", workspaceInfo);
        } catch (IOException ex) {
            setError("Unable to open workspace file '" + workspaceFilePath + "'");
            return null;
        }
    }

    private void onRepopulateFinished() {
        isRepopulating.set(false);

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                populateLibrariesUI(workspace);
                updateButtons();
            }
        });
        libraryList.setPaintBusy(false);
    }

    private void setError(String error) {
        libraryList.getEmptyText().setText(error);
        libraryList.setToolTipText(error);
    }

    private void clearErrors() {
        libraryList.getEmptyText().setText(EMPTY_MODULE_LIST_TEXT);
        libraryList.setToolTipText("");
    }

    private void updateButtons() {
        boolean hasWorkspace = workspace != null;
        refreshToolWindowButton.setText(hasWorkspace ? workspace.getName() : "");
        createWorkspaceButton.setVisible(!hasWorkspace);
        addModuleToWorkspaceButton.setVisible(hasWorkspace);
    }


    private void populateLibrariesUI(Workspace workspace) {
        if (workspace == null) {
            return;
        }
        clearErrors();

        final String primaryModule = workspace.getName();
        refreshToolWindowButton.setText(primaryModule);
        libraryList.clear();
        int numErrors = 0;
        StringBuilder sb = new StringBuilder();
        for (ExternalGradleLibraryInfo lib : workspace.getAllExternalModules().values()) {
            if (!lib.getName().equals(primaryModule)) {
                if (lib.getError() == null) {
                    final String libName = lib.getName();
                    libraryList.addItem(libName, libName, workspace.getIncludedModules().containsKey(libName));
                } else {
                    ++numErrors;
                    sb.append(String.format("Ignored module '%s' because of error '%s'\n", lib.getName(), lib.getError()));
                }
            }
        }

        if (numErrors > 0) {
            final long length = 5000 + 500 * numErrors;
            final String error = sb.toString();
            JBPopupFactory.getInstance()
                    .createHtmlTextBalloonBuilder(error, MessageType.ERROR, null)
                    .setHideOnClickOutside(true)
                    .setFadeoutTime(length)
                    .createBalloon().show(RelativePoint.getSouthOf(refreshToolWindowButton), Balloon.Position.below);
        }
    }
}
