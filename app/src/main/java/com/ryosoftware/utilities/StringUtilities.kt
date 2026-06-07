package com.ryosoftware.utilities;

object StringUtilities {
    fun join(values: Array<String?>?, separator: String, lastSeparator: String, from: Int, to: Int): String {
        var string = ""; var end = to
        if (values != null) {
            for (i in to downTo from) if (values[i] != null) { end = i; break }
            if (end >= from) for (i in from..end) if (values[i] != null)
                string += (if (string.isEmpty()) "" else if (i == end) lastSeparator else separator) + values[i]
        }
        return string
    }

    fun join(values: Array<String?>?, separator: String, lastSeparator: String): String {
        if (values == null) return ""
        return join(values, separator, lastSeparator, 0, values.size - 1)
    }

    fun join(values: Array<String?>?, separator: String, from: Int, to: Int) = join(values, separator, separator, from, to)
    fun join(values: Array<String?>?, separator: String) = join(values, separator, separator)

    fun join(values: Set<String>?, separator: String, lastSeparator: String): String {
        if (values != null) return join(values.toTypedArray(), separator, lastSeparator)
        return ""
    }

    fun join(values: Set<String>?, separator: String) = join(values, separator, separator)

    fun <T> join(values: Array<T>?, separator: String, lastSeparator: String): String {
        var string = ""
        if (values != null) {
            val lastValid = values.indices.lastOrNull { values[it] != null } ?: -1
            if (lastValid >= 0) for (i in 0..lastValid) if (values[i] != null)
                string += (if (string.isEmpty()) "" else if (i == lastValid) lastSeparator else separator) + values[i].toString()
        }
        return string
    }

    fun <T> join(values: Array<T>?, separator: String) = join(values, separator, separator)

    fun <T> join(values: List<T>?, separator: String, lastSeparator: String, from: Int, to: Int): String {
        var string = ""; var end = to
        if (values != null) {
            for (i in to downTo from) if (values[i] != null) { end = i; break }
            if (end >= from) for (i in from..end) if (values[i] != null)
                string += (if (string.isEmpty()) "" else if (i == end) lastSeparator else separator) + values[i].toString()
        }
        return string
    }

    fun <T> join(values: List<T>?, separator: String, lastSeparator: String): String {
        if (values == null) return ""
        return join(values, separator, lastSeparator, 0, values.size - 1)
    }

    fun <T> join(values: List<T>?, separator: String) = join(values, separator, separator)

    fun join(values: IntArray?, separator: String, lastSeparator: String): String {
        var string = ""; var actualSep = ""
        if (values != null) for (i in values.indices) {
            string += actualSep + values[i].toString()
            actualSep = if (i == values.size - 2) lastSeparator else separator
        }
        return string
    }

    fun join(values: IntArray?, separator: String) = join(values, separator, separator)

    fun join(values: LongArray?, separator: String): String {
        var string = ""; var actualSep = ""
        if (values != null) for (v in values) { string += actualSep + v.toString(); actualSep = separator }
        return string
    }
}
