package com.zirakusa.redes

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.zirakusa.redes.data.History
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.Result
import com.zirakusa.redes.data.SharedValues
import com.zirakusa.redes.data.Status

/** The lines of «Hoy» for the family summary: the connection now and the last speed test of the week. Pure. */
object SummaryLines {
    fun lines(s: Status, last: Result?, now: Long, connection: (Status) -> String, test: (Result) -> String): List<String> =
        listOfNotNull(connection(s).takeIf { s.link != Link.NONE }, last?.takeIf { now - it.at in 0..7 * 86_400_000L }?.let(test))
}

/**
 * The family summary (see Inicio's FAMILIA.md): what Inicio shows in its «Hoy» card for Redes. Only apps signed by the
 * same developer may ask (Android's signature permission and the caller's signature checked here). No test is run.
 */
class SummaryProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "summary") return null
        val context = context ?: return null
        val caller = callingPackage ?: return null
        if (context.packageManager.checkSignatures(context.packageName, caller) != PackageManager.SIGNATURE_MATCH) return null
        val r = context.resources
        val lines = SummaryLines.lines(Phone(context).status(), History(SharedValues(context)).list().firstOrNull(), System.currentTimeMillis(),
            { s -> s.wifi?.let { w -> r.getString(R.string.summary_wifi, w.ssid ?: r.getString(R.string.wifi), r.getString(com.zirakusa.redes.ui.screens.levelName(w.level))) }
                ?: r.getString(com.zirakusa.redes.ui.screens.linkName(s.link)) },
            { t -> r.getString(R.string.summary_test, t.down, t.up, t.ping.toInt()) })
        return Bundle().apply {
            putString("title", context.getString(R.string.summary_title))
            putStringArrayList("lines", ArrayList(lines))
            putString("empty", context.getString(R.string.no_connection))
        }
    }
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
