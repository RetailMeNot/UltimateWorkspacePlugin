package com.rmn.workspace.gradle

import com.intellij.openapi.project.Project
import com.rmn.workspace.AppJsonMapper
import com.rmn.workspace.LibraryInfo
import java.io.IOException
import java.util.*

class Workspace(val basePath: String, var info: WorkspaceInfo) {

    val name: String
        get() = info.name

    private val settingsGradlePath: String
        get() = basePath + info.settingsPath + "/settings.gradle"

    private val gradleSettingsAdapter: GradleSettingsFileAdapter

    private val lockObject = Any()

    /**
     * All modules not in the current workspace potentially that can be added and removed by the plugin.
     */
    val allExternalModules = mutableMapOf<String, ExternalGradleLibraryInfo>()
    /**
     * Modules included in the workspace (IE - checked in the plugin window.  These are the modules being managed by the plugin)
     */
    val includedModules = mutableMapOf<String, ExternalGradleLibraryInfo>()

    /**
     * All potential modules in the workspace, even excluded ones.  This is external + internal modules already a part of the workspace.
     * REVIEW: This might not scale well with large workspaces.  We parse all potential workspace and store here even if not included
     * to prevent having to rebuild the entire dependency graph when adding and removing a module to the workspace.  Consider
     * using only internal + included.
     */
    val allPotentialWorkspaceModules = mutableMapOf<String, BaseGradleLibraryInfo>()

    /**
     * The root workspace at the top of the workspace dependency graph for all modules including external modules that are not included.
     */
    private val rootModules = mutableListOf<BaseGradleLibraryInfo>()

    init {
        gradleSettingsAdapter = GradleSettingsFileAdapter(this, settingsGradlePath)
        updateWorkspace()
    }

    fun updateWorkspace(updateExisting: Boolean = false) {
        synchronized(lockObject) {
            if (!updateExisting) {
                allPotentialWorkspaceModules.clear()
                allExternalModules.clear()
                rootModules.clear()
            }

            // Single Refresh for Gradle Settings.
            gradleSettingsAdapter.refreshGradleSettingsFromDisk()

            updateModuleList(updateExisting)
            rebuildModuleInfoGraph()
            updateIncludedModules()
            matchWorkspaceModuleDependenciesWithIncluded()
        }
    }

    // Updates the module list and each library's dependencies.  Does NOT update the LibraryInfo graph.
    private fun updateModuleList(updateExisting: Boolean) {
        // Copy our existing workspace.  We will remove from this map as we update them with incoming modules.  Any left
        // in this map either were not updated and will be rebuilt from scratch
        val existingLibs : MutableMap<String, BaseGradleLibraryInfo> = mutableMapOf()
        existingLibs.putAll(allPotentialWorkspaceModules)

        // Find all entities in workspace
        info.modules.forEach {
            val libInfo = ExternalGradleLibraryInfo(this, it)
            if (updateExisting) {
                // Look for existing library, if one exists, update it, if not, add to our list of new
                val existingLib = existingLibs.get(libInfo.name)
                if (existingLib != null) {
                    existingLib.updateFrom(libInfo)
                    // Remove from existing workspace indicating we've handled this one.
                    existingLibs.remove(libInfo.name)
                } else {
                    // There is no existing, so this is new - add to all libs.
                    allExternalModules.put(libInfo.name, libInfo)
                }
            } else {
                allExternalModules.put(libInfo.name, libInfo)
            }
        }

        // Anything left in existingLibs is old and no longer in the workspace, remove it
        if (updateExisting) {
            existingLibs.values.forEach {
                allExternalModules.remove(it.name)
            }
        }

        allPotentialWorkspaceModules.putAll(allExternalModules)

        // Find internal workspace and add to all our project workspace, updating those if needed.  If a project is external
        // we treat it as such.  There is no conversion without refreshing.
        gradleSettingsAdapter.gradleSettingsInfo?.internalProjects?.forEach {
            val internalLib = InternalGradleLibraryInfo(this, it)
            if (!allPotentialWorkspaceModules.containsKey(it) || !updateExisting) {
                allPotentialWorkspaceModules.put(it, internalLib)
            } else {
                allPotentialWorkspaceModules[it]!!.updateFrom(internalLib)
            }
        }
    }

    private fun rebuildModuleInfoGraph() {
        // Copy our library map.  We're going to remove from here if anything depends on a library.  We should
        // end up with the root of our graph after wiring up all dependencies.  (Anything left has no dependencies on it
        // so it must be the root of dependencies we support).
        val topLevelLibs : MutableMap<String, BaseGradleLibraryInfo> = mutableMapOf()
        topLevelLibs.putAll(allPotentialWorkspaceModules)

        // Build dependency graph
        // First, wire up children
        allPotentialWorkspaceModules.forEach {
            val currentKey = it.key
            val current = it.value

            // Slightly cheeseball, but dependsOn doesn't use the LibraryInfo graph so this should be safe.  Faster to do in place
            // than loop over libs again.
            current.dependentLibraries.clear()

            // For all modules int he workspace, if something depends on current, current is not a root level library in the graph.
            allPotentialWorkspaceModules.forEach {
                if (current !== it.value) {
                    if (it.value.dependsOn(current)) {
                        // "It" depends on current library, it's not a root level library.
                        topLevelLibs.remove(currentKey)
                    } else if (current.dependsOn(it.value)) {
                        // Current depends on "it", wire it up as such.
                        current.dependentLibraries.add(it.value)
                    }
                }
            }
        }

        // Assign the root of our graph.
        rootModules.addAll(topLevelLibs.values)
    }

    private fun updateIncludedModules() {
        // Update our included module set
        includedModules.clear()
        if (gradleSettingsAdapter.gradleSettingsInfo == null) {
            return
        }
        for (libName in gradleSettingsAdapter.gradleSettingsInfo!!.allProjects) {
            if (allExternalModules.containsKey(libName)) {
                includedModules.put(libName, allExternalModules.get(libName)!!)
            }
        }
    }

    private fun matchWorkspaceModuleDependenciesWithIncluded() {
        // Level order traversal of dependency fixup for only modules in our workspace.
        val completed = mutableSetOf<LibraryInfo>()
        val workQueue = LinkedList<LibraryInfo>(rootModules)
        while (!workQueue.isEmpty()) {
            val libToUpdate = workQueue.removeFirst()
            if (!completed.contains(libToUpdate) && isInWorkspace(libToUpdate)) {
                completed.add(libToUpdate)

                libToUpdate.ensureDependenciesMatchSettings()
                workQueue.addAll(libToUpdate.dependentLibraries)
            }
        }
    }

    /**
     * Whether or not the module is a part of the current workspace (included + internal)
     */
    private fun isInWorkspace(module: LibraryInfo): Boolean {
        return module.isInternal || includedModules.containsKey(module.name)
    }

    fun includeModuleSourceInWorkspace(name: String) {
        synchronized(lockObject) {
            //REVIEW: This is convenient that potential modules are already parsed, but may not scale.
            val lib = allExternalModules.get(name)!!
            includedModules.put(lib.name, lib)
            gradleSettingsAdapter.gradleSettingsInfo!!.addProject(lib)
            gradleSettingsAdapter.writeGradleSettingsToDisk()

            matchWorkspaceModuleDependenciesWithIncluded()
        }
    }

    fun excludeModuleFromWorkspace(name: String) {
        // TODO: at some point, we should clean up the module on the way out, but for now let's ignore.  We're parsing it regardless.
        synchronized(lockObject) {
            val lib = allExternalModules.get(name)!!
            val removed = includedModules.remove(lib.name) != null
            gradleSettingsAdapter.gradleSettingsInfo!!.removeProject(lib)
            gradleSettingsAdapter.writeGradleSettingsToDisk()

            if (removed) {
                matchWorkspaceModuleDependenciesWithIncluded()
            }
        }
    }

    companion object {
        @JvmStatic
        fun create(name: String, project: Project, fileName: String): WorkspaceInfo? {
            val file = project.baseDir.createChildData(project, fileName)
            var outputStream: java.io.OutputStream? = null
            try {
                outputStream = file.getOutputStream(project)
                val workspace = WorkspaceInfo(name, "", mutableListOf())
                AppJsonMapper.writeStream(workspace, outputStream)
                return workspace
            } catch (ex: IOException) {
                return null
            } finally {
                outputStream?.close()
            }
        }
    }
}