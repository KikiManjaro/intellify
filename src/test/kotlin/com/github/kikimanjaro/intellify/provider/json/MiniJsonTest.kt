package com.github.kikimanjaro.intellify.provider.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    @Test
    fun `objects are parsed with their field types`() {
        val parsed = MiniJson.parseObject("""{"title":"Song","durationMs":180000,"ratio":1.5,"playing":true,"album":null}""")
        assertTrue(parsed != null)
        parsed!!
        assertEquals("Song", parsed["title"])
        assertEquals(180000L, parsed["durationMs"])
        assertEquals(1.5, parsed["ratio"] as Double, 0.0001)
        assertEquals(true, parsed["playing"])
        assertNull(parsed["album"])
    }

    @Test
    fun `nested values and arrays are supported`() {
        val parsed = MiniJson.parseObject("""{"outer":{"inner":[1,2,3]},"empty":{},"list":["a"]}""")
        assertTrue(parsed != null)
        val outer = parsed!!["outer"]
        assertTrue(outer is Map<*, *>)
        val inner = (outer as Map<*, *>)["inner"]
        assertEquals(listOf(1L, 2L, 3L), inner)
    }

    @Test
    fun `escapes and unicode are decoded`() {
        assertEquals("a\"b\nc", MiniJson.parse("""{"k":"a\"b\nc"}""")?.let { (it as Map<*, *>)["k"] })
        assertEquals("\u00e9", MiniJson.parse("""{"k":"\u00e9"}""")?.let { (it as Map<*, *>)["k"] })
    }

    @Test
    fun `malformed input returns null instead of throwing`() {
        assertNull(MiniJson.parse("{"))
        assertNull(MiniJson.parse(""))
        assertNull(MiniJson.parse("not json"))
        assertNull(MiniJson.parse("""{"a":1} trailing"""))
        assertNull(MiniJson.parseObject("[1,2,3]"))
        assertNull(MiniJson.parseObject("{\"a\":}"))
    }

    @Test
    fun `the typed accessors normalise what a command line may print`() {
        val json = MiniJson.parseObject("""{"blank":"   ","text":" Song ","number":"180000","float":42.7,"missing":null}""")
        assertTrue(json != null)
        json!!
        assertNull(json.string("blank"))
        assertEquals("Song", json.string("text"))
        assertNull(json.string("missing"))
        assertEquals(180000L, json.long("number"))
        assertEquals(42L, json.long("float"))
        assertNull(json.long("missing"))
    }
}
