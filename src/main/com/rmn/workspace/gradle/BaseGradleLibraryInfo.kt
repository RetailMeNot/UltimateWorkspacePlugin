package com.rmn.workspace.gradle

import com.rmn.workspace.LibraryInfo
import com.rmn.workspace.Global.LOG

abstract class BaseGradleLibraryInfo(override val workspace: Workspace): LibraryInfo {

    protected val buildGradlePath by lazy { workspace.basePath + path + "/build.gradle" }
    protected lateinit var buildGradleAdapter: GradleBuildFileAdapter
    private var createdAdapter = false
    protected val lockObject: Any = Any()

    override val error: String?
        get() = buildGradleAdapter.error

    protected val allDependencies: MutableMap<String, GradleDependencyInfo> = mutableMapOf()
    /** (library) name -> deps */
    protected val sourceDependencies: MutableMap<String, GradleDependencyInfo> = mutableMapOf()
    /** (library) artifactName -> deps */
    protected val remoteDependencies: MutableMap<String, GradleDependencyInfo> = mutableMapOf()

    /** Libraries that this module depends on.
     * REVIEW: Can we just pull these out of the workspace? */
    override val dependentLibraries: MutableList<LibraryInfo> = mutableListOf()

    override fun refreshDependencies(updateExisting: Boolean) {
        synchronized(lockObject) {
            if (!createdAdapter) {
                buildGradleAdapter = GradleBuildFileAdapter(this, buildGradlePath)
                createdAdapter = true
            }
            buildGradleAdapter.refreshDependenciesFromDisk()
            populateDependencies(updateExisting, buildGradleAdapter.dependencies)
        }
    }

    private fun populateDependencies(updateExisting: Boolean, depList: List<GradleDependencyInfo> ) {
        if (!updateExisting) {
            allDependencies.clear()
        }

        // These maps represent current state so there is no "merging" them.
        sourceDependencies.clear()
        remoteDependencies.clear()

        depList.forEach {
            if (updateExisting) {
                val dep = it
                // Find matching dependency
                val matched = allDependencies.get(dep.name)
                // If matched, update with the incoming, if not add it
                if (matched != null) {
                    matched.updateFrom(it)
                }
            }
            allDependencies.put(it.name, it)

            if (it.isSourceDependency) {
                sourceDependencies.put(it.name, it)
            } else {
                remoteDependencies.put(it.name, it)
            }
        }
    }

    override fun updateFrom(library: LibraryInfo) {
        synchronized(lockObject) {
            if (library.javaClass != javaClass) {
                LOG.error("Unable to update dependency $name because it is not a gradle dependency or is no longer the same type.")
                return
            }

            val lib = library as BaseGradleLibraryInfo
            val update = path == lib.path || group == lib.group
            if (update) {
                // Copy over the adapter from the new library.
                buildGradleAdapter = lib.buildGradleAdapter
                populateDependencies(update, buildGradleAdapter.dependencies)
            } else {
                copyFrom(library)
            }
        }
    }

    protected open fun copyFrom(library: BaseGradleLibraryInfo) {
        buildGradleAdapter = library.buildGradleAdapter
        allDependencies.clear()
        allDependencies.putAll(library.allDependencies)
        sourceDependencies.clear()
        sourceDependencies.putAll(library.sourceDependencies)
        remoteDependencies.clear()
        remoteDependencies.putAll(library.remoteDependencies)
    }

    override fun ensureDependenciesMatchSettings() {
        // This might be slightly heavy, but correctness is important.
        allDependencies.forEach {
            if (workspace.allExternalModules.containsKey(it.key)) {
                it.value.isSourceDependency = workspace.includedModules.containsKey(it.key)
            }
        }
        buildGradleAdapter.writeDependenciesToDisk()
    }

    override fun dependsOn(library: LibraryInfo): Boolean {
        // REVIEW:  Should we account for group name in here to dedupe?!
        val lib = library as? BaseGradleLibraryInfo?
        if (lib == null) {
            LOG.error("Unable to update dependency because it is not a gradle dependency.")
            return false
        }
        return allDependencies.containsKey(library.name)
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}