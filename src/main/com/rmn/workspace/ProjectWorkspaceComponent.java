package com.rmn.workspace;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;

public class ProjectWorkspaceComponent extends AbstractProjectComponent {

    private WorkspaceToolWindowFactory factory = new WorkspaceToolWindowFactory();


    public ProjectWorkspaceComponent(Project project) {
        super(project);
    }

    @Override
    public void projectOpened() {

        // Register the tool window.
        ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow("Workspace Modules", true, ToolWindowAnchor.LEFT, myProject, true, true);
        factory.createToolWindowContent(myProject, toolWindow);
        toolWindow.setIcon(IconLoader.getIcon("/general/recursive.png"));
    }

    @Override
    public void projectClosed() {

    }
}
