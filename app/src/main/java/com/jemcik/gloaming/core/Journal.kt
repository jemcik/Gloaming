package com.jemcik.gloaming.core

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * MagicOS encrypts logcat output for third-party apps (entries appear as
 * (HKS)...(HKE) blobs over adb), so an in-app journal is the only reliable
 * way to see what happened while the app was closed.
 */
object Journal {
    private val FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    private const val MAX_LINES = 500

    fun write(ctx: Context, msg: String) {
        runCatching {
            val f = File(ctx.filesDir, "journal.log")
            f.appendText(LocalDateTime.now().format(FMT) + "  " + msg + "\n")
            val lines = f.readLines()
            if (lines.size > MAX_LINES) f.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
        }
    }

    fun read(ctx: Context): List<String> = runCatching {
        File(ctx.filesDir, "journal.log").readLines().reversed()
    }.getOrDefault(emptyList())

    fun clear(ctx: Context) { runCatching { File(ctx.filesDir, "journal.log").delete() } }
}
