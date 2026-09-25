package com.github.kikimanjaro.intellify.provider.json

/**
 * Minimal JSON reader: objects -> [Map], arrays -> [List], strings -> [String], numbers ->
 * [Long]/[Double], booleans -> [Boolean], `null` -> `null`.
 *
 * Hand-rolled on purpose: the system providers read a handful of flat fields from a command line,
 * the plugin must not add a runtime dependency, and the parser is pure so it is fully unit tested.
 * Malformed input never throws: [parse] returns `null`.
 */
internal object MiniJson {
    fun parse(raw: String): Any? = try {
        MiniJsonParser(raw).parse()
    } catch (e: Exception) {
        null
    }

    /** Parses a JSON object; `null` when the input is not an object or is malformed. */
    fun parseObject(raw: String): Map<String, Any?>? {
        val value = parse(raw)
        if (value !is Map<*, *>) return null
        val result = LinkedHashMap<String, Any?>(value.size)
        for ((key, entry) in value) {
            if (key is String) result[key] = entry
        }
        return result
    }
}

/** `null` when the key is absent, not a string, or blank. */
internal fun Map<String, Any?>.string(key: String): String? =
    (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }

/** `null` when the key is absent or not a number. */
internal fun Map<String, Any?>.long(key: String): Long? = when (val value = this[key]) {
    is Long -> value
    is Int -> value.toLong()
    is Double -> value.toLong()
    is String -> value.trim().toLongOrNull() ?: value.trim().toDoubleOrNull()?.toLong()
    else -> null
}

private class JsonSyntaxException(message: String) : Exception(message)

private class MiniJsonParser(private val text: String) {
    private var position = 0

    fun parse(): Any? {
        skipWhitespace()
        val value = readValue()
        skipWhitespace()
        require(position == text.length) { "trailing content at $position" }
        return value
    }

    private fun readValue(): Any? {
        require(position < text.length) { "unexpected end of input" }
        return when (val current = text[position]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't' -> readLiteral("true", true)
            'f' -> readLiteral("false", false)
            'n' -> readLiteral("null", null)
            else -> {
                if (current == '-' || current.isDigit()) readNumber()
                else throw JsonSyntaxException("unexpected '$current' at $position")
            }
        }
    }

    private fun readObject(): Map<String, Any?> {
        expect('{')
        val result = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (peek() == '}') {
            position++
            return result
        }
        while (true) {
            skipWhitespace()
            val key = readString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            result[key] = readValue()
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                '}' -> {
                    position++
                    return result
                }
                else -> throw JsonSyntaxException("expected ',' or '}' at $position")
            }
        }
    }

    private fun readArray(): List<Any?> {
        expect('[')
        val result = ArrayList<Any?>()
        skipWhitespace()
        if (peek() == ']') {
            position++
            return result
        }
        while (true) {
            skipWhitespace()
            result.add(readValue())
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                ']' -> {
                    position++
                    return result
                }
                else -> throw JsonSyntaxException("expected ',' or ']' at $position")
            }
        }
    }

    private fun readString(): String {
        expect('"')
        val result = StringBuilder()
        while (true) {
            require(position < text.length) { "unterminated string" }
            when (val current = text[position++]) {
                '"' -> return result.toString()
                '\\' -> result.append(readEscape())
                else -> result.append(current)
            }
        }
    }

    private fun readEscape(): Char {
        require(position < text.length) { "unterminated escape" }
        return when (val escaped = text[position++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                val hex = text.substring(position, position + 4)
                position += 4
                hex.toInt(16).toChar()
            }
            else -> throw JsonSyntaxException("invalid escape '\\$escaped'")
        }
    }

    private fun readNumber(): Any {
        val start = position
        if (peek() == '-') position++
        while (position < text.length && (text[position].isDigit() || text[position] in ".eE+-")) position++
        val raw = text.substring(start, position)
        return if (raw.any { it == '.' || it == 'e' || it == 'E' }) {
            raw.toDoubleOrNull() ?: throw JsonSyntaxException("invalid number '$raw'")
        } else {
            raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: throw JsonSyntaxException("invalid number '$raw'")
        }
    }

    private fun <T> readLiteral(literal: String, value: T): T {
        require(text.startsWith(literal, position)) { "invalid literal at $position" }
        position += literal.length
        return value
    }

    private fun peek(): Char = if (position < text.length) text[position] else '\u0000'

    private fun expect(expected: Char) {
        require(peek() == expected) { "expected '$expected' at $position" }
        position++
    }

    private fun skipWhitespace() {
        while (position < text.length && text[position].isWhitespace()) position++
    }
}
