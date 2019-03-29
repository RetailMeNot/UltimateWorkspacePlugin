package com.rmn.workspace.gradle

import org.junit.After
import org.junit.Before
import org.junit.Test

import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleDependencyLineMatcherTest {

    companion object Expected {
        val COMPILE: String = "compile"
        val IMPLEMENTATION: String = "implementation"
        val API: String = "api"

        val ALL_INCLUDE_TYPES = listOf(COMPILE, IMPLEMENTATION, API)

        val SUPPORT_GROUP: String = "com.android.support.constraint"
        val NUMBER_GROUP: String = "com.123asdf.1a3.fffff1.2222334"
        val OTHER_GROUP: String = "com.123_.asdfeef.asdfdff"
        val ALL_GROUPS = listOf(SUPPORT_GROUP, NUMBER_GROUP, OTHER_GROUP)


        val SINGLE_WORD_ARTIFACT = "artifact"
        val HYPHEN_WORD_ARTIFACT = "module-n_ame"
        val ALL_REMOTE_ARTIFACTS = listOf(SINGLE_WORD_ARTIFACT, HYPHEN_WORD_ARTIFACT)
        val DOUBLE_SCOPED_SUBMODULE_NAME = "primary:secondary"
        val DOUBLE_SCOPED_SUBMODULE_WITH_OTHERS = "primar324y:secon13_-ry"
        val ALL_SOURCE_ARTIFACTS = mutableListOf(DOUBLE_SCOPED_SUBMODULE_NAME, DOUBLE_SCOPED_SUBMODULE_WITH_OTHERS)

        val DOUBLE_QUOTE = "\""
        val SINGLE_QUOTE = "'"
        val ALL_QUOTE_TYPES = listOf(DOUBLE_QUOTE, SINGLE_QUOTE)

        val NUMBER_VERSION: String = "1.3.4"
        val NUMBER_VERSION_LONG: String = "0.31234123123.4412312414124124"
        val NUMBER_VERSIONS = listOf (NUMBER_VERSION, NUMBER_VERSION_LONG)
        val VAR_VERSION_SINGLE = "\$someVersion"
        val VAR_VERSION_UNDERSCORE = "\$some_version"
        val VAR_VERSION_MEMBER_VARIABLE = "\$someVersion.someName"
        val VARIABLE_VERSIONS = listOf(VAR_VERSION_SINGLE, VAR_VERSION_UNDERSCORE, VAR_VERSION_MEMBER_VARIABLE)

        val ALL_POTENTIAL_VERSIONS: MutableList<String> = mutableListOf()

        val REMOTE_DEPENDENCY_FORMAT_WITH_PARENTHESES = "%s (%s%s:%s:%s%s)"
        val REMOTE_DEPENDENCY_FORMAT_WITHOUT_PARENTHESES = "%s %s%s:%s:%s%s"
        val ALL_REMOTE_DEPENDENCY_FORMATS = listOf(REMOTE_DEPENDENCY_FORMAT_WITH_PARENTHESES, REMOTE_DEPENDENCY_FORMAT_WITHOUT_PARENTHESES)

        val SOURCE_DEPENDENCY_FORMAT_WITH_PARENTHESES = "%s (%s:%s%s)"
        val SOURCE_DEPENDENCY_FORMAT_WITHOUT_PARENTHESES = "%s %s:%s%s"
        val ALL_SOURCE_DEPENDENCY_FORMATS = listOf(SOURCE_DEPENDENCY_FORMAT_WITH_PARENTHESES, SOURCE_DEPENDENCY_FORMAT_WITHOUT_PARENTHESES)
    }

    init {
        ALL_POTENTIAL_VERSIONS.addAll(NUMBER_VERSIONS)
        ALL_POTENTIAL_VERSIONS.addAll(VARIABLE_VERSIONS)

        ALL_SOURCE_ARTIFACTS.addAll(ALL_REMOTE_ARTIFACTS)
    }

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
    }

    @Test
    fun testExhaustiveBuildGradleRemoteDependencyLinePattern() {
        var permutations = 0;

        ALL_QUOTE_TYPES.forEach {
            val quote = it
            val isSingleQuote = quote == "'"

            val potentialGroups = ALL_GROUPS
            val potentialNames = ALL_REMOTE_ARTIFACTS
            val potentialVersions = if (isSingleQuote) ALL_POTENTIAL_VERSIONS else NUMBER_VERSIONS

            ALL_INCLUDE_TYPES.forEach {
                val includeType = it
                potentialGroups.forEach {
                    val group = it
                    potentialNames.forEach {
                       val name = it
                        potentialVersions.forEach {
                            val version = it
                            ALL_REMOTE_DEPENDENCY_FORMATS.forEach {
                                var potentialLine = String.format(it,
                                        includeType, quote, group, name, version, quote)
                                verifyRemoteDependencyMatch(potentialLine,
                                        includeType, quote, group, name, version)

                                potentialLine = potentialLine.replace(" ", "")
                                verifyRemoteDependencyMatch(potentialLine,
                                        includeType, quote, group, name, version)

                                permutations += 2;
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testExhaustiveBuildGradleSourceDependencyLinePattern() {
        var permutations = 0;

        ALL_QUOTE_TYPES.forEach {
            val quote = it

            val potentialNames = ALL_REMOTE_ARTIFACTS

            ALL_INCLUDE_TYPES.forEach {
                val includeType = it
                potentialNames.forEach {
                    val name = it
                    ALL_SOURCE_DEPENDENCY_FORMATS.forEach {
                        var potentialLine = String.format(it,
                                includeType, quote, name, quote)
                        verifySourceDependencyMatch(potentialLine,
                                includeType, quote, name)

                        potentialLine = potentialLine.replace(" ", "")
                        verifySourceDependencyMatch(potentialLine,
                                includeType, quote, name)

                        permutations += 2;
                    }
                }
            }
        }
    }

    private fun verifyRemoteDependencyMatch(lineToMatch: String, includeType: String, quoteType:String, group: String, artifactName: String, version: String) {
        val matcher = GradleDependencyLineMatcher.BuildGradleLineMatcher.BUILD_GRADLE_DEPENDENCY_PATTERN.matcher(lineToMatch)
        assertTrue(matcher.matches(), "Build Gradle Line Matcher failed with input \n$lineToMatch")

        val dep = GradleDependencyLineMatcher.BuildGradleLineMatcher.createRemoteDependency(matcher)
        val includeTypeMatches = includeType == dep.includeType
        val quoteMatches = quoteType == dep.quotationType
        val groupMatches = group == dep.group
        val nameMatches = artifactName == dep.name
        val versionMatches = version == dep.lastKnownVersion

        assertTrue(includeTypeMatches, "Build Gradle Line Matcher failed includeType match \n$lineToMatch")
        assertTrue(quoteMatches, "Build Gradle Line Matcher failed quoteType match \n$lineToMatch")
        assertTrue(groupMatches, "Build Gradle Line Matcher failed group match \n$lineToMatch")
        assertTrue(nameMatches, "Build Gradle Line Matcher failed name match \n$lineToMatch")
        assertTrue(versionMatches, "Build Gradle Line Matcher failed version match \n$lineToMatch")
        assertFalse(dep.isSourceDependency, "Build Gradle Line Matcher failed isSourceDepdendency is true \n$lineToMatch")
    }

    private fun verifySourceDependencyMatch(lineToMatch: String, includeType: String, quoteType:String, artifactName: String) {
        val matcher = GradleDependencyLineMatcher.BuildGradleLineMatcher.BUILD_GRADLE_DEPENDENCY_PATTERN.matcher(lineToMatch)
        assertTrue(matcher.matches(), "Build Gradle Line Matcher failed with input \n$lineToMatch")

        val dep = GradleDependencyLineMatcher.BuildGradleLineMatcher.createSourceDependency(matcher)
        val includeTypeMatches = includeType == dep.includeType
        val quoteMatches = quoteType == dep.quotationType
        val groupMatches = null == dep.group
        val nameMatches = artifactName == dep.name
        val versionMatches = null == dep.lastKnownVersion

        assertTrue(includeTypeMatches, "Build Gradle Line Matcher failed includeType match \n$lineToMatch")
        assertTrue(quoteMatches, "Build Gradle Line Matcher failed quoteType match \n$lineToMatch")
        assertTrue(groupMatches, "Build Gradle Line Matcher failed group match \n$lineToMatch")
        assertTrue(nameMatches, "Build Gradle Line Matcher failed name match \n$lineToMatch")
        assertTrue(versionMatches, "Build Gradle Line Matcher failed version match \n$lineToMatch")
        assertTrue(dep.isSourceDependency, "Build Gradle Line Matcher failed isSourceDepdendency is false \n$lineToMatch")
    }
}