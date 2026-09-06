package com.jemcik.gloaming.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The routine files Samsung's importer is handed, read back the way its reader
 * reads them: 512 bytes of header, the sizes in it, the body after it, the
 * footer after that. The importer was measured accepting this layout on the
 * phone, for both effects; this pins the layout so a refactor cannot quietly
 * move a byte or a parameter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutineFileTest {

    private fun header(b: ByteArray): JSONObject {
        val text = String(b.copyOfRange(0, RoutineFile.HEADER), Charsets.UTF_8)
        return JSONObject(text.substring(0, text.lastIndexOf('}') + 1))
    }

    private fun body(b: ByteArray): JSONObject {
        val end = RoutineFile.HEADER + header(b).getInt("body_size")
        return JSONObject(String(b.copyOfRange(RoutineFile.HEADER, end), Charsets.UTF_8))
    }

    private fun params(b: ByteArray): Map<String, JSONObject> {
        val act = body(b).getJSONArray("actions").getJSONObject(0)
        val list = act.getJSONArray("intent_param")
        return (0 until list.length()).map { list.getJSONObject(it) }.associateBy { it.getString("KEY") }
    }

    @Test
    fun `the header is 512 bytes of JSON and NUL, and its sizes are true`() {
        val b = RoutineFile.bytes("Gloaming: grayscale", RoutineEffect.GRAYSCALE)
        val h = header(b)
        assertEquals("1.0", h.getString("version"))
        val body = h.getInt("body_size")
        val footer = h.getInt("footer_size")
        assertEquals("header + body + footer is the whole file", RoutineFile.HEADER + body + footer, b.size)
        // NUL padding, which the reader trims as whitespace. A space would
        // read the same; a missing byte would shift the body.
        val json = h.toString().length
        assertTrue(b.copyOfRange(json, RoutineFile.HEADER).all { it == 0.toByte() })
    }

    @Test
    fun `every file is a manual routine, so the provider can start and end it`() {
        for (e in RoutineEffect.entries) {
            val body = body(RoutineFile.bytes("x", e))
            val cond = body.getJSONArray("conditions").getJSONObject(0)
            assertEquals("manual_execute", cond.getString("tag"))
            assertEquals("com.samsung.android.app.routines", cond.getString("package"))
            assertEquals(1, body.getJSONArray("actions").length())
        }
    }

    @Test
    fun `grayscale is the built-in toggle action, switched on`() {
        val b = RoutineFile.bytes("Gloaming: grayscale", RoutineEffect.GRAYSCALE)
        assertEquals("Gloaming: grayscale", body(b).getString("name"))
        assertEquals("gray_scale", body(b).getJSONArray("actions").getJSONObject(0).getString("tag"))
        val p = params(b)
        assertEquals("true", p.getValue("toggle_value").getString("VALUE"))
        assertEquals("BOOLEAN", p.getValue("toggle_value").getString("TYPE"))
    }

    @Test
    fun `the dark theme is UiModeManager's own YES, as the action's handler reads it`() {
        val b = RoutineFile.bytes("Gloaming: dark theme", RoutineEffect.DARK)
        assertEquals("dark_mode_v3", body(b).getJSONArray("actions").getJSONObject(0).getString("tag"))
        val p = params(b)
        // "2" is MODE_NIGHT_YES. Measured: "true" here drew the editor's row as
        // OFF, and "2" as ON with a revert on the routine's end.
        assertEquals("2", p.getValue("enable_dark_mode").getString("VALUE"))
        assertEquals("STRING", p.getValue("enable_dark_mode").getString("TYPE"))
        assertEquals("true", p.getValue("enable_dark_theme").getString("VALUE"))
    }

    @Test
    fun `the footer is an empty resource table, where the reader expects one`() {
        val b = RoutineFile.bytes("x", RoutineEffect.GRAYSCALE)
        val h = header(b)
        val start = RoutineFile.HEADER + h.getInt("body_size")
        val footer = JSONObject(String(b.copyOfRange(start, start + h.getInt("footer_size")), Charsets.UTF_8))
        assertEquals(0, footer.getJSONArray("resource_table").length())
    }
}
