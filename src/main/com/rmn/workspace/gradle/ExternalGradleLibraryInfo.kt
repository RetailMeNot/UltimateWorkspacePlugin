package com.rmn.workspace.gradle

class ExternalGradleLibraryInfo(workspace: Workspace, private var moduleInfo: Module): BaseGradleLibraryInfo(workspace) {
    // Manifest fields
    override val name:String
            get() = moduleInfo.name
    override val path: String
            get() = moduleInfo.path
    override val group: String
            get() = moduleInfo.group

    override val isInternal: Boolean
        get() { return false }

    init {
        refreshDependencies()
    }

    override fun copyFrom(library: BaseGradleLibraryInfo) {
            val lib = library as ExternalGradleLibraryInfo
            moduleInfo = lib.moduleInfo
            super.copyFrom(library)
    }
}