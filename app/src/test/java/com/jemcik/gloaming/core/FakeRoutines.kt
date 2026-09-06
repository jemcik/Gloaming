package com.jemcik.gloaming.core

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * Samsung's external routines provider, as read from the decompiled original:
 * the same authority, the same columns, the same `call` methods and the same
 * `success` / `error` answers. What it holds is whatever a test puts in
 * [rows]; what it was asked is in [calls], oldest first, as "start 10".
 *
 * [install] puts the door on this phone the way [Routines.available] probes
 * for it - the provider registered under its authority AND the permission
 * held - because a test that stubbed the probe would not be testing it.
 */
internal class FakeRoutines : ContentProvider() {
    class Row(val uuid: Long, val name: String, val enabled: Boolean = true)

    val rows = mutableListOf<Row>()
    val running = mutableSetOf<Long>()
    val calls = mutableListOf<String>()

    /** When set, every call is refused with this as its `error`. */
    var refuse: String? = null

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor = MatrixCursor(
        arrayOf("uuid", "name", "icon_resource_id", "icon_color", "is_running", "is_enabled", "is_oneoff")
    ).apply {
        rows.forEach {
            addRow(arrayOf<Any?>(it.uuid, it.name, 0, "", if (it.uuid in running) 1 else 0, if (it.enabled) 1 else 0, 0))
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val id = extras?.getLong("uuid", -1L) ?: -1L
        calls += method.substringBefore('_') + " " + id
        refuse?.let { return bundleOf("success" to false, "error" to it) }
        when (method) {
            "start_manual_routine" -> running += id
            "end_manual_routine" -> running -= id
        }
        return bundleOf("success" to true)
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        fun install(ctx: Context): FakeRoutines {
            val info = ProviderInfo().apply {
                authority = Routines.AUTHORITY
                packageName = Routines.PACKAGE
                name = FakeRoutines::class.java.name
            }
            val provider = Robolectric.buildContentProvider(FakeRoutines::class.java).create(info).get()
            shadowOf(ctx.packageManager).addOrUpdateProvider(info)
            shadowOf(ctx.applicationContext as Application).grantPermissions(Routines.PERMISSION)
            return provider
        }
    }
}
