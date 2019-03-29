package com.rmn.workspace

import com.rmn.workspace.gradle.Workspace

interface LibraryInfo {
    val workspace: Workspace
    val name: String
    val path: String
    val group: String

    val error: String?

    val isInternal: Boolean

    val dependentLibraries: MutableList<LibraryInfo>

    fun refreshDependencies(updateExisting: Boolean = false)

    fun updateFrom(library: LibraryInfo)

    fun ensureDependenciesMatchSettings()

    fun dependsOn(library: LibraryInfo): Boolean
}