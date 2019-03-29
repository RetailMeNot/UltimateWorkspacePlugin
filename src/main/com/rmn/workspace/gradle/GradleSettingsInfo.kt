package com.rmn.workspace.gradle

class GradleSettingsInfo {
    val allProjects: MutableSet<String> = mutableSetOf()
    /**
     * Map of project name to path.
     */
    val externalProjects: MutableMap<String, String> = mutableMapOf()
    val internalProjects: MutableSet<String> = mutableSetOf()

    fun removeProject(lib: ExternalGradleLibraryInfo) {
        allProjects.remove(lib.name)
        internalProjects.remove(lib.name)
        externalProjects.remove(lib.name)
    }

    fun addProject(lib: ExternalGradleLibraryInfo) {
        allProjects.add(lib.name)
        if (lib.isInternal) {
            internalProjects.add(lib.name)
        } else {
            externalProjects.put(lib.name, lib.path)
        }
    }
}