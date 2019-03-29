package com.rmn.workspace.gradle

import com.rmn.workspace.Global.LOG
import java.util.regex.Matcher
import java.util.regex.Pattern

object GradleDependencyLineMatcher {

    internal object BuildGradleLineMatcher {
        // Capture Group Names
        /** The way this dependency exists in the build.gradle - EG "api/implementation/compile" */
        val includeType = "includetype"
        /** Whether or not 'project' exists.  If non-null, the project is a source dependency.*/
        val isProject = "isproject"
        /** The <group-name> for a remote dependency.
         *   EG - implementation('<group-name>:artifact-name:1.0.4')
         * Will be null if source dependency.
        *  EG - api project(":<artifact-name>")
        *  EG - api project(":<group>:artifact-name"). */
        val group = "group"
        /** The <artifact-name> for a remote dependency.
         *  EG - implementation('some.group.name:<artifact-name>:1.0.4')
         *  If source, this will be the <artifact-name> or the <group> if one exists.
         *  If a source dependency, this will be the <artifact-name> if <group> is present.  If <group> not present, will be null
         *  EG - api project(":<group>:<artifact-name>"). */
        val name = "name"
        /** The variable name representing the version of a remote dependency.  Will be null if version is not a gradle variable and dep is remote.
         *   EG - implementation('some.group.name:artifact-name:$gradleVariableRepresentingVersion') */
        val varVersion = "varversion"
        /** The variable representing the version of the remote dependency if hardcoded.
         *   EG - implementation('some.group.name:artifact-name:1.0.4') would match '1.0.4'.
         */
        val version = "version"
        val quoteType = "quoteType"
        val BUILD_GRADLE_DEPENDENCY_PATTERN = Pattern.compile("\\b(?<$includeType>compile|api|implementation)\\b ?(?<$isProject>project)? ?\\(?(?<$quoteType>[\"|']):?(?<$group>[a-z|A-Z|0-9|\\-|\\.|_]*)?:?(?<$name>[a-z|A-Z|0-9|\\-|_]*):?(?<$varVersion>\\$?[a-z|A-Z|0-9|\\.|_]+)?(?<$version>[0-9]+\\.[0-9]+\\.[0-9]+)?[\"|']\\)?")

        fun createSourceDependency(matcher: Matcher): GradleDependencyInfo {
            val includeType = matcher.group(includeType)
            // Looks strange but this is how we match source deps.
            val groupName = matcher.group(group)
            val libName: String? = matcher.group(name)
            var artifactName = groupName
            // If we have an artifact name, append to group, if not, assume groupName is artifact name.
            if (libName != null && !libName.isBlank()) {
                artifactName += ":$libName"
            }
            val quotationType = matcher.group(quoteType)
            return GradleDependencyInfo(artifactName, includeType, quotationType, true)
        }

        fun createRemoteDependency(matcher: Matcher): GradleDependencyInfo {
            val includeType = matcher.group(includeType)
            val groupName = matcher.group(group)
            val artifactName = matcher.group(name)
            val quotationType = matcher.group(quoteType)
            val version = matcher.group(version) ?: matcher.group(varVersion)
            return GradleDependencyInfo(artifactName, includeType, quotationType, false, groupName, version)
        }
    }

    private object SettingsGradleLineMatcher {
        // Capture Group Names
        /** The matched project name.  */
        val depName = "dep"
        /** The path belonging to the external project */
        val path = "path"

        val SETTINGS_GRADLE_INCLUDE_PROJECT_NAME_PATTERN = Pattern.compile(               "[\"|']:?(?<dep>[a-z|A-Z|0-9|\\-|:]+)[\"|']")
        val SETTINGS_GRADLE_INCLUDE_LINE_PATTERN = Pattern.compile("\\b(include)\\b ?+\\(?[\"|']?:?(?<dep>[a-z|A-Z|0-9|\\-|:|_]+)[\"|']?\\)?")
        val SETTINGS_GRADLE_EXTERNAL_PROJECT_LINE_PATTERN = Pattern.compile("\\b(project)\\b ?+\\([\\\"|']?:?(?<dep>[a-z|A-Z|0-9|\\-|:]+)[\\\"|']?\\)\\.projectDir ?+= ?+new ?+File ?+\\([\\\"|']?(?<path>[a-z|A-Z|0-9|\\-|:|\\.|/|\\-|\\\\|_]+)[\\\"|']?\\)")

        fun createGradleSettingsInfo(settingsGradleLines: List<String>): GradleSettingsInfo {
            val result = GradleSettingsInfo()

            var isIncludeContinuation: Boolean = false // Are we continuing a multi project definition?
            for (i in settingsGradleLines.indices) {
                val line = settingsGradleLines[i].trim()
                if (line.isEmpty()) {
                    continue
                }
                // Attempt to match multi-project def first.
                var matcher = SETTINGS_GRADLE_INCLUDE_PROJECT_NAME_PATTERN.matcher(line)
                if (matcher.matches()) {
                    if (isIncludeContinuation) {
                        val dep = matcher.group(depName)
                        result.allProjects.add(dep)
                        result.internalProjects.add(dep)
                    } else {
                        LOG.error("Somehow matched duplicate include continuation.")
                        // Something is wrong with our parsing or current stuff is unsupported.  We should only match this if our include succeeded.
                    }
                    continue
                }

                matcher = SETTINGS_GRADLE_INCLUDE_LINE_PATTERN.matcher(line)
                if (matcher.matches()) {
                    val dep = matcher.group(depName)
                    result.allProjects.add(dep)
                    result.internalProjects.add(dep)
                    isIncludeContinuation = true
                    continue
                }

                isIncludeContinuation = false

                matcher = SETTINGS_GRADLE_EXTERNAL_PROJECT_LINE_PATTERN.matcher(line)
                if (matcher.matches()) {
                    val dep = matcher.group(depName)
                    val depPath = matcher.group(path)
                    result.allProjects.add(dep)
                    result.externalProjects.put(dep, depPath)
                }
            }

            return result
        }
    }


    /**
     * Returns a GradleDependencyInfo if the line represents a dependency.
     */
    @JvmStatic
    fun createBuildGradleDependency(line: String): GradleDependencyInfo? {
        val matcher = BuildGradleLineMatcher.BUILD_GRADLE_DEPENDENCY_PATTERN.matcher(line.trim())
        if (!matcher.matches()) {
            return null
        }

        val isSource = matcher.group(BuildGradleLineMatcher.isProject) == "project"
        var result: GradleDependencyInfo = if (isSource) BuildGradleLineMatcher.createSourceDependency(matcher) else BuildGradleLineMatcher.createRemoteDependency(matcher)
        return result
    }

    @JvmStatic
    fun createGradleSettingsInfo(settingsGradleLines: List<String>): GradleSettingsInfo {
        return SettingsGradleLineMatcher.createGradleSettingsInfo(settingsGradleLines)
    }
}