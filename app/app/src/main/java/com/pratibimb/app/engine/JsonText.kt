package com.pratibimb.app.engine

// A minimal JSON writer for the app's own small files (a held item's decision, the report
// history), read back with SimpleJsonParser. Plain Kotlin, so it runs in JVM tests.
object JsonText {
    fun write(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is Boolean, is Int, is Long -> out.append(value)
            // JSON has no NaN or infinity; they are written as null.
            is Double -> out.append(if (value.isFinite()) value.toString() else "null")
            is String -> writeString(out, value)
            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { i, (key, item) ->
                    if (i > 0) out.append(',')
                    writeString(out, key as String)
                    out.append(':')
                    write(out, item)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                value.forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    write(out, item)
                }
                out.append(']')
            }
            else -> error("Cannot write ${value::class.simpleName} as JSON")
        }
    }

    private fun writeString(out: StringBuilder, text: String) {
        out.append('"')
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
    }
}
