package com.rmn.workspace.gradle

/**
 * Makes Strong assertions that artifact name source inclusion name are the same.
 */
class GradleDependencyInfo {

    var name: String = ""

    var group: String? = null

    var includeType: String
        private set

    var lastKnownVersion: String? = null

    var isSourceDependency: Boolean

    var quotationType: String

    constructor(depInfo: GradleDependencyInfo) : this(depInfo.name, depInfo.includeType, depInfo.quotationType, depInfo.isSourceDependency, depInfo.group, depInfo.lastKnownVersion)

    constructor(name: String, includeType: String, quotationType: String, isSourceDependency: Boolean, group: String? = null, lastKnownVersion: String? = null) {
        this.name = name

        this.includeType = includeType
        this.isSourceDependency = isSourceDependency
        this.group = group
        this.lastKnownVersion = lastKnownVersion
        this.quotationType = quotationType
    }

    fun updateFrom(other: GradleDependencyInfo) {
        if (other.name != name) {
            val badName = other.name
            throw IllegalArgumentException("Dependency with name '$name' attempting to be updated by dependency with wrong name '$badName'")
        }
        includeType = other.includeType
        isSourceDependency = other.isSourceDependency

        if (other.group != null) {
            this.group = other.group
        }

        if (other.lastKnownVersion != null) {
            lastKnownVersion = other.lastKnownVersion
        }
        this.quotationType = other.quotationType
    }

    fun toBuildGradleLine(library: ExternalGradleLibraryInfo): String {
        if (isSourceDependency) {
            val name = library.name
            return "$includeType project(\":$name\")"
        }

        val group = library.group
        val artifactName = library.name
        val version = lastKnownVersion ?: "UNKNOWN"
        return "$includeType $quotationType$group:$artifactName:$version$quotationType"
    }
}