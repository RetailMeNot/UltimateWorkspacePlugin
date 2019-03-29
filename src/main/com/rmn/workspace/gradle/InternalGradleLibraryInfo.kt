package com.rmn.workspace.gradle

class InternalGradleLibraryInfo(workspace: Workspace, settingsGradleName: String): BaseGradleLibraryInfo(workspace) {

    override val name: String = settingsGradleName
    override val path: String = settingsGradleName.replace(':', '/')
    override val group: String = ""

    override val isInternal: Boolean = true

    init {
        refreshDependencies()
    }
}