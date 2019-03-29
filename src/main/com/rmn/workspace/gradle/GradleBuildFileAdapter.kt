package com.rmn.workspace.gradle

import com.intellij.openapi.vfs.LocalFileSystem
import com.rmn.workspace.Global.LOG
import org.apache.log4j.lf5.util.StreamUtils
import java.io.IOException

class GradleBuildFileAdapter(val library: BaseGradleLibraryInfo, val buildGradlePath: String) {

    val dependencies: MutableList<GradleDependencyInfo> = mutableListOf()
    val lineMap: MutableMap<GradleDependencyInfo, Int> = mutableMapOf()

    val hasError
        get() = error?.isNotBlank() ?:false
    var error: String? = null

    var fileLines: MutableList<String>? = null

    val parseLock = Any()

    fun refreshDependenciesFromDisk() {
        // REVIEW: Update spam might lock the file if resource contrained... will this crash?
        synchronized(parseLock) {
            dependencies.clear()
            lineMap.clear()

            val file = LocalFileSystem.getInstance().findFileByPath(buildGradlePath)
            if (file == null) {
                error = ("Unable to find workspace file '$buildGradlePath'")
                LOG.warn(error)
                return
            }

            var bytes: ByteArray?
            var inputStream: java.io.InputStream? = null
            try {
                file.refresh(false, false)
                inputStream = file.inputStream
                bytes = StreamUtils.getBytes(inputStream)
            } catch (ex: IOException) {
                error = "Unable to open workspace file '$buildGradlePath'"
                LOG.warn(error)
                return
            } finally {
                inputStream?.close()
            }

            val fileContents = String(bytes!!)
            fileLines = fileContents.split("\n").toMutableList()

            for (i in fileLines!!.indices) {
                val dep = GradleDependencyLineMatcher.createBuildGradleDependency(fileLines!!.get(i))
                if (dep != null) {
                    dependencies.add(dep)
                    lineMap.put(dep, i)
                }
            }
        }
    }


    fun writeDependenciesToDisk() {
        synchronized(parseLock) {
            val file = LocalFileSystem.getInstance().findFileByPath(buildGradlePath)
            if (file == null) {
                error = ("Unable to find workspace file '$buildGradlePath'")
                LOG.warn(error)
                return
            }

            file.refresh(false, false)
            var bytes: ByteArray?
            var inputStream: java.io.InputStream? = null
            try {
                inputStream = file.inputStream
                bytes = StreamUtils.getBytes(inputStream)
            } catch (ex: IOException) {
                error = "Unable to open workspace file '$buildGradlePath'"
                LOG.warn(error)
                return
            } finally {
                inputStream?.close()
            }

            val fileContents = String(bytes!!)
            fileLines = fileContents.split("\n").toMutableList()

            // For all lines in the file, update any that need to match what we want our dependencies to be
            // (IE - if it's not a source dep and we want it to be, fix it)
            for (i in fileLines!!.indices) {
                val line = fileLines!![i]
                val dep = GradleDependencyLineMatcher.createBuildGradleDependency(line)
                if (dep != null) {
                    // trashy linear search but it can't be that bad because we're just dealing with dependencies.
                    val existingDep = dependencies.find { it.name == dep.name }
                    // If our existing dependency doesn't match what we want it to be, overwrite the line in the file.
                    if (existingDep != null && existingDep.isSourceDependency != dep.isSourceDependency) {
                        val lib = library.workspace.allExternalModules.get(dep.name)!!
                        val lineTrimmed = line.trimStart()
                        val whiteSpaces = line.indexOfFirst { it == lineTrimmed[0] }
                        // Keep whitespace the same while replacing the line
                        val newLine = CharArray(whiteSpaces, { _ -> ' '}).joinToString("") + existingDep.toBuildGradleLine(lib)
                        fileLines!!.set(i, newLine)
                    }
                }
            }

            // Actually write the lines to disk.
            var outputStream: java.io.OutputStream? = null
            try {
                outputStream = file.getOutputStream(this)
                for (i in fileLines!!.indices) {
                    val line = fileLines!![i]
                    if (i < fileLines!!.size - 1) {
                        outputStream!!.write("$line\n".toByteArray())
                    } else {
                        outputStream!!.write(line.toByteArray())
                    }
                }
            } catch (ex: IOException) {
                error = "Unable to write workspace file '$buildGradlePath'"
                LOG.warn(error)
                return
            } finally {
                outputStream!!.close()
            }
        }
    }
}