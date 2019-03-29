package com.rmn.workspace.gradle

import org.junit.After
import org.junit.Before
import org.junit.Test

import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleDependencyInfoTests {

    companion object Expected {
        val LEFT_REMOTE: GradleDependencyInfo = GradleDependencyInfo("name", "api", "'", false, "sample.group", "1.2.3")
        val RIGHT_REMOTE: GradleDependencyInfo = GradleDependencyInfo("name", "implementation", "\"", false, "sample.group", "3.4.5")
        val RIGHT_REMOTE_DIFFERENT_GROUP: GradleDependencyInfo = GradleDependencyInfo("name", "implementation", "\"", false, "sample.group", "3.4.5")
        val RIGHT_REMOTE_DIFFERENT_NAME_DIFFERENT_GROUP: GradleDependencyInfo = GradleDependencyInfo("otherName", "implementation", "\"", false, "different.group", "3.4.5")

        val LEFT_SOURCE:  GradleDependencyInfo = GradleDependencyInfo("name", "api", "'", true, null, null)
        val RIGHT_SOURCE: GradleDependencyInfo = GradleDependencyInfo("name", "implementation", "\"", false, null, null)
    }

    init {
    }

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
    }

    @Test
    fun testCopyConstructor() {
        val left = GradleDependencyInfo(LEFT_REMOTE)
        assertTrue(left.isSourceDependency == LEFT_REMOTE.isSourceDependency)
        assertTrue(left.quotationType == LEFT_REMOTE.quotationType)
        assertTrue(left.includeType == LEFT_REMOTE.includeType)
        assertTrue(left.lastKnownVersion == LEFT_REMOTE.lastKnownVersion)
        assertTrue(left.group == LEFT_REMOTE.group)
        assertTrue(left.name == LEFT_REMOTE.name)
    }

    @Test
    fun testUpdateRemoteDependencySameGroup() {
        val left = GradleDependencyInfo(LEFT_REMOTE)
        val right = GradleDependencyInfo(RIGHT_REMOTE)

        left.updateFrom(right)
        // We don't update name.
        assertTrue(left.name == LEFT_REMOTE.name)
        assertTrue(left.quotationType == RIGHT_REMOTE.quotationType)
        assertTrue(left.group == RIGHT_REMOTE.group)
        assertTrue(left.includeType == RIGHT_REMOTE.includeType)
        assertTrue(left.lastKnownVersion == RIGHT_REMOTE.lastKnownVersion)
        assertTrue(left.isSourceDependency == RIGHT_REMOTE.isSourceDependency)
    }


    @Test
    fun testUpdateRemoteDependencyDifferentGroup() {
        val left = GradleDependencyInfo(LEFT_REMOTE)
        val right = GradleDependencyInfo(RIGHT_REMOTE_DIFFERENT_GROUP)

        left.updateFrom(right)
        // We don't update name.
        assertTrue(left.name == LEFT_REMOTE.name)
        assertTrue(left.quotationType == RIGHT_REMOTE.quotationType)
        assertTrue(left.group == RIGHT_REMOTE.group)
        assertTrue(left.includeType == RIGHT_REMOTE.includeType)
        assertTrue(left.lastKnownVersion == RIGHT_REMOTE.lastKnownVersion)
        assertTrue(left.isSourceDependency == RIGHT_REMOTE.isSourceDependency)
    }

    @Test
    fun testUpdateSourceDependencyWithSource() {
        val left = GradleDependencyInfo(LEFT_SOURCE)
        val right = GradleDependencyInfo(RIGHT_SOURCE)

        left.updateFrom(right)
        // We don't update name.
        assertTrue(left.name == RIGHT_SOURCE.name)
        assertTrue(left.quotationType == RIGHT_SOURCE.quotationType)
        assertTrue(left.group == RIGHT_SOURCE.group)
        assertTrue(left.includeType == RIGHT_SOURCE.includeType)
        assertTrue(left.lastKnownVersion == RIGHT_SOURCE.lastKnownVersion)
        assertTrue(left.isSourceDependency == RIGHT_SOURCE.isSourceDependency)
    }


    @Test
    fun testUpdateSourceDependencyWithRemote() {
        val left = GradleDependencyInfo(LEFT_SOURCE)
        val right = GradleDependencyInfo(LEFT_REMOTE)

        left.updateFrom(right)
        // We don't update name.
        assertTrue(left.name == LEFT_REMOTE.name)
        assertTrue(left.quotationType == LEFT_REMOTE.quotationType)
        assertTrue(left.group == LEFT_REMOTE.group)
        assertTrue(left.includeType == LEFT_REMOTE.includeType)
        assertTrue(left.lastKnownVersion == LEFT_REMOTE.lastKnownVersion)
        assertTrue(left.isSourceDependency == LEFT_REMOTE.isSourceDependency)
    }

    @Test
    fun testUpdateRemoteDependencyWithSource() {
        val left = GradleDependencyInfo(LEFT_REMOTE)
        val right = GradleDependencyInfo(LEFT_SOURCE)

        left.updateFrom(right)
        // We don't update name.
        assertTrue(left.name == LEFT_REMOTE.name)
        assertTrue(left.quotationType == LEFT_SOURCE.quotationType)
        assertTrue(left.includeType == LEFT_SOURCE.includeType)
        assertTrue(left.isSourceDependency == LEFT_SOURCE.isSourceDependency)
        // Version and group dependency are additive
        assertTrue(left.group == LEFT_REMOTE.group)
        assertTrue(left.lastKnownVersion == LEFT_REMOTE.lastKnownVersion)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testUpdateRemoteDependencyDifferentName() {
        val left = GradleDependencyInfo(LEFT_REMOTE)
        val right = GradleDependencyInfo(RIGHT_REMOTE_DIFFERENT_NAME_DIFFERENT_GROUP)

        left.updateFrom(right)
        // We don't update name, throws exception.
    }


}