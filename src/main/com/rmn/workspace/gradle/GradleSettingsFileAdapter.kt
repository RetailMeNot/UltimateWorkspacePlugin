package com.rmn.workspace.gradle

import com.intellij.openapi.vfs.LocalFileSystem
import com.rmn.workspace.Global.LOG
import org.apache.log4j.lf5.util.StreamUtils
import java.io.IOException

class GradleSettingsFileAdapter(val workspace: Workspace, val settingsGradlePath: String) {

    var gradleSettingsInfo: GradleSettingsInfo? = null
        private set

    val hasError
        get() = error?.isNotBlank() ?: false
    var error: String? = null

    var fileLines: List<String>? = null

    val parseLock = Any()

    fun refreshGradleSettingsFromDisk() {
        // REVIEW: Update spam might lock the file if resource constrained... will this crash?
        synchronized(parseLock) {
            val file = LocalFileSystem.getInstance().findFileByPath(settingsGradlePath)
            if (file == null) {
                error = ("Unable to find settings.gradle file '$settingsGradlePath'")
                LOG.warn(error)
                return
            }

            var inputStream: java.io.InputStream? = null
            var bytes: ByteArray?
            try {
                file.refresh(false, false)
                inputStream = file.inputStream
                bytes = StreamUtils.getBytes(inputStream)
            } catch (ex: IOException) {
                error = "Unable to open settings.gradle file '$settingsGradlePath'"
                LOG.warn(error)
                return
            } finally {
                inputStream!!.close()
            }

            val fileContents = String(bytes!!)
            fileLines = fileContents.split("\n", ",", ignoreCase = true, limit = 0)

            gradleSettingsInfo = GradleDependencyLineMatcher.createGradleSettingsInfo(fileLines!!)
        }
    }

    fun writeGradleSettingsToDisk() {
        synchronized(parseLock) {
            val file = LocalFileSystem.getInstance().findFileByPath(settingsGradlePath)
            if (file == null) {
                error = ("Unable to find settings.gradle file '$settingsGradlePath'")
                LOG.warn(error)
                return
            }

            var outputStream: java.io.OutputStream? = null
            try {
                outputStream = file.getOutputStream(this)
                val settings = gradleSettingsInfo!!
                settings.allProjects.forEach {
                    outputStream.write("include ':$it'\n".toByteArray())
                    if (settings.externalProjects.contains(it)) {
                        val path = workspace.basePath + settings.externalProjects.get(it)
                        outputStream.write("project(':$it').projectDir = new File('$path')\n".toByteArray())
                    }
                }
            } catch (ex: IOException) {
                error = "Unable to open settings.gradle file '$settingsGradlePath'"
                return
            } finally {
                outputStream?.close()
            }
        }
    }
}