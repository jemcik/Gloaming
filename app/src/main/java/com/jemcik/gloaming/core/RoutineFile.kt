package com.jemcik.gloaming.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * One screen effect, as a routine Samsung's own engine will run.
 *
 * Each is a BUILT-IN action of Modes and Routines, read off its
 * `builtin_actions_provider.xml`, with the parameters its handler reads:
 * grayscale is the TOGGLE template's `toggle_value`; the dark theme takes
 * `enable_dark_mode` as UiModeManager's own night-mode number ("2" is YES) and
 * `enable_dark_theme` as a boolean. Both are declared reversible, so ending
 * the routine ends the effect. Measured 6 Sep 2026: a routine carrying only
 * `gray_scale` flipped the Galaxy's `Global saturation` on and back off.
 */
enum class RoutineEffect(val key: String, val tag: String) {
    GRAYSCALE("gray", "gray_scale"),
    DARK("dark", "dark_mode_v3"),

    /**
     * One UI's "apply dark mode to wallpaper": a darker wallpaper WHILE DARK
     * MODE IS ON, and nothing at all while it is off. Its handler writes one
     * `Settings.System` key, which a third-party app is refused - Android lets
     * an app write only AOSP's public System keys, even holding WRITE_SETTINGS,
     * and the always-on key works only because Samsung allowlists that one.
     * So this too is a routine, with a reverse path measured: start took the
     * key to 1, stop put it back. It runs only with the dark theme - see
     * [Routines.wanted] - and its row lives under the dark-theme switch.
     */
    DIM("dim", "wallpaper_apply_dark_mode");

    internal fun params(): JSONArray = when (this) {
        GRAYSCALE, DIM -> JSONArray().put(param("toggle_value", "BOOLEAN", "true"))
        DARK -> JSONArray()
            .put(param("enable_dark_mode", "STRING", "2"))
            .put(param("enable_dark_theme", "BOOLEAN", "true"))
    }

    private fun param(key: String, type: String, value: String): JSONObject =
        JSONObject().put("KEY", key).put("TYPE", type).put("VALUE", value)
}

/**
 * A routine, in the file format Modes and Routines shares them in - built here
 * so nobody has to build the routine by hand.
 *
 * The format was read from the decompiled writer and reader (`RoutineFileWriter`
 * / `RoutineFileReader`, Routines 4.9.04.13) and then proved on the phone: a
 * file written this way opened in Samsung's own editor with the condition and
 * the action filled in, and Save inserted it. Nothing here is guessed.
 *
 *     512 bytes   header, JSON then NUL padding:
 *                 {"version","valid_state","body_size","footer_size","description"}
 *     body_size   the routine, JSON
 *     footer_size {"resource_table":[…]} - icon images; ours is empty
 *
 * Version "1.0" is the PLAIN body. Files at "2.0" carry the body AES-GCM
 * encrypted under the first 32 characters of the writer's signing certificate,
 * and `valid_state` is an HMAC of the body under the same key - neither of
 * which the reader checks on a 1.0 file, and the 1.0 path is what its own
 * writer takes for a QR share. So the plain form is a supported input, not a
 * loophole.
 *
 * The body names the built-in metas by package and tag, exactly as the preload
 * providers register them; the reader looks each up by `package=? AND tag=?`
 * and the editor draws the real rows. Parameters travel as `{KEY, TYPE, VALUE}`
 * triples, TYPE from Samsung's `ValueType`. The one condition is
 * `manual_execute` - "Start button tapped" - which is what makes the routine
 * one the external provider will start and end for us.
 */
object RoutineFile {
    const val HEADER = 512
    const val MIME = "application/vnd.samsung.routines"
    private const val SAMSUNG = "com.samsung.android.app.routines"

    fun bytes(name: String, effect: RoutineEffect): ByteArray {
        val body = body(name, effect).toString().toByteArray(Charsets.UTF_8)
        val footer = JSONObject().put("resource_table", JSONArray()).toString().toByteArray(Charsets.UTF_8)
        val header = JSONObject()
            .put("version", "1.0")
            .put("valid_state", "")
            .put("body_size", body.size)
            .put("footer_size", footer.size)
            .put("description", name)
            .toString().toByteArray(Charsets.UTF_8)
        check(header.size <= HEADER)
        return header.copyOf(HEADER) + body + footer
    }

    private fun body(name: String, effect: RoutineEffect): JSONObject = JSONObject()
        .put("version", "1.0.0")
        .put("name", name)
        .put("conditions", JSONArray().put(
            JSONObject().put("package", SAMSUNG).put("tag", "manual_execute").put("version", 1)
        ))
        .put("actions", JSONArray().put(
            JSONObject().put("package", SAMSUNG).put("tag", effect.tag).put("version", 1)
                .put("intent_param", effect.params())
        ))
}
