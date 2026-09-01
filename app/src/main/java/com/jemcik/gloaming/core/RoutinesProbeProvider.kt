package com.jemcik.gloaming.core

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * THROWAWAY SPIKE. Answers one question and is then deleted.
 *
 * Samsung's Modes and Routines discovers providers that declare the
 * com.samsung.android.sdk.routines.v3 contract - authority <pkg>.provider.routines.v3,
 * an intent-filter for ROUTINE_PROVIDER, and meta.CONDITION pointing at an XML
 * of conditions. Every app on the phone implementing it is a Samsung SYSTEM app,
 * so the open question is whether One UI will talk to an ordinary app at all.
 *
 * This does not implement the protocol - it cannot, the SDK is not published.
 * It only RECORDS being spoken to. Any line in the journal means discovery works
 * and the contract is worth implementing properly; silence, with the condition
 * absent from the Routines UI, means this route is closed and we stop.
 */
class RoutinesProbeProvider : ContentProvider() {

    private fun note(what: String) {
        context?.let { Journal.write(it, "ROUTINES-SDK $what") }
    }

    override fun onCreate(): Boolean { note("provider created"); return true }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        note("call method=$method arg=$arg extras=${extras?.keySet()?.joinToString(",")}")
        return null
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? { note("query $uri sel=$selection"); return null }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? { note("insert $uri"); return null }
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int { note("delete $uri"); return 0 }
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int {
        note("update $uri values=${v?.keySet()?.joinToString(",")}"); return 0
    }
}
