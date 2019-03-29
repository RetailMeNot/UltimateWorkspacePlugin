package com.rmn.workspace

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import java.io.InputStream
import java.io.OutputStream

interface JsonMapper {
    fun read(json: String): MutableMap<String, Any?>
    fun <T> readInto(json: String, item: T): T
    fun readStream(stream: InputStream): MutableMap<String, Any?>
    fun <T> readStreamInto(stream: InputStream, item: T): T
    fun <T> write(item: T): String
    fun <T> writeStream(item: T, stream: OutputStream)
}

object AppJsonMapper : JsonMapper {

    val mapper = ObjectMapper()

    init {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
        val printer = DefaultPrettyPrinter()
                printer.withSpacesInObjectEntries().indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter())
        mapper.writer(printer)
    }

    override fun read(json: String): MutableMap<String, Any?> = readInto(json, hashMapOf())

    override fun <T> readInto(json: String, item: T): T = mapper.readerForUpdating(item).readValue(json)

    override fun readStream(stream: InputStream): MutableMap<String, Any?> = readStreamInto(stream, hashMapOf())

    override fun <T> readStreamInto(stream: InputStream, item: T): T = mapper.readerForUpdating(item).readValue(stream)

    override fun <T> write(item: T): String = mapper.writeValueAsString(item)

    override fun <T> writeStream(item: T, stream: OutputStream) = mapper.writeValue(stream, item)

}
