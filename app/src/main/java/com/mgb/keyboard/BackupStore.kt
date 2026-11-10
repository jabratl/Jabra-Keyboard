package com.mgb.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.widget.Toast
import com.mgb.keyboard.typing.BackupCodec
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v0.4.0 BACKUP CORE (no big UI; the Data & Backup page comes in v0.4.1).
 * One JSON file with ALL user data: every pref of "jabra" (except bk_* and update keys) + extra files of filesDir.
 * Saved in filesDir/backups/jabra_YYYYMMDD_HHmm.json (AtomicFile). Newest bk_keep copies are kept.
 * Prefs NEW: bk_auto(bool,true) bk_keep(int,5) bk_day(int,0) bk_last(str,"") schema_v(int,1).
 * Nothing runs on the key-press path: only onStartInputView (one int read) and a background Thread.
 */
object BackupStore {
    /** Extra data files (filesDir) included in a backup when they exist. New stores add their name here. */
    private val FILES = arrayOf("vault_v1.json", "profiles_v1.json")
    private const val DIR = "backups"

    private fun isSkipped(k: String): Boolean = k.startsWith("bk_") || k.startsWith("upd_") || k.startsWith("update")

    /** Day number in local time (changes at local midnight). */
    fun today(): Int = try { ((System.currentTimeMillis() + java.util.TimeZone.getDefault().rawOffset) / 86400000L).toInt() } catch (_: Throwable) { 0 }

    private fun dir(ctx: Context): File { val d = File(ctx.filesDir, DIR); if (!d.exists()) d.mkdirs(); return d }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("jabra", Context.MODE_PRIVATE)

    private fun read(f: File): String? = try { String(AtomicFile(f).readFully(), Charsets.UTF_8) } catch (_: Throwable) { null }

    private fun write(f: File, text: String): Boolean = try {
        val af = AtomicFile(f)
        val o = af.startWrite()
        try { o.write(text.toByteArray(Charsets.UTF_8)); af.finishWrite(o); true } catch (e: Throwable) { af.failWrite(o); false }
    } catch (_: Throwable) { false }

    /** Backup text of the current data, or null when something went wrong. */
    fun collect(ctx: Context): String? = try {
        val prefs = LinkedHashMap<String, Any>()
        for ((k, v) in sp(ctx).all) if (v != null && !isSkipped(k)) prefs[k] = v
        val files = LinkedHashMap<String, String>()
        for (n in FILES) { val f = File(ctx.filesDir, n); if (f.exists()) read(f)?.let { files[n] = it } }
        BackupCodec.build(prefs, files, BuildConfig.VERSION_NAME, System.currentTimeMillis())
    } catch (_: Throwable) { null }

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    /** Saves one backup file. Returns a short message (shown on screen). */
    fun backupNow(ctx: Context, prefix: String = "jabra_"): String {
        try {
            val text = collect(ctx) ?: return "Backup failed (could not read data)"
            if (text.toByteArray(Charsets.UTF_8).size > BackupCodec.MAX_BYTES) return "Backup refused: data is bigger than 5 MB"
            val f = File(dir(ctx), prefix + stamp() + ".json")
            if (!write(f, text)) return "Backup failed (could not save the file)"
            if (BackupCodec.validate(read(f) ?: "") != BackupCodec.OK) { try { f.delete() } catch (_: Throwable) { }; return "Backup failed (file check)" }
            val e = sp(ctx).edit()
            e.putString("bk_last", stamp()); e.putInt("schema_v", 1); e.apply()
            prune(ctx)
            return "Backup saved"
        } catch (_: Throwable) { return "Backup failed" }
    }

    /** Newest first. Includes "before restore" copies. */
    fun list(ctx: Context): List<File> = try {
        (dir(ctx).listFiles { x -> x.isFile && x.name.endsWith(".json") } ?: emptyArray()).sortedByDescending { it.lastModified() }
    } catch (_: Throwable) { emptyList() }

    /** Keeps the newest bk_keep normal copies (3 / 5 / 10). "before_restore_" copies are kept separately (newest 3). */
    private fun prune(ctx: Context) {
        try {
            val keep = sp(ctx).getInt("bk_keep", 5).let { if (it < 1) 5 else it }
            val all = dir(ctx).listFiles { x -> x.isFile && x.name.endsWith(".json") } ?: return
            val normal = all.filter { it.name.startsWith("jabra_") }.sortedByDescending { it.name }
            for (f in normal.drop(keep)) try { f.delete() } catch (_: Throwable) { }
            val pre = all.filter { it.name.startsWith("before_restore_") }.sortedByDescending { it.name }
            for (f in pre.drop(3)) try { f.delete() } catch (_: Throwable) { }
        } catch (_: Throwable) { }
    }

    /** Preview counts for the restore screen (v0.4.1): {prefs, files, timeMillis} or {-1,-1,0}. */
    fun preview(f: File): LongArray = BackupCodec.summary(read(f) ?: "")

    /** Restores a backup file. Caller must show the preview + two-tap confirm first. Returns a short message. */
    fun restore(ctx: Context, f: File): String {
        try {
            val text = read(f) ?: return "Restore stopped: file not readable"
            val v = BackupCodec.validate(text)
            if (v != BackupCodec.OK) return "Restore stopped: backup file is damaged (code $v). Nothing changed."
            val pre = backupNow(ctx, "before_restore_")   // automatic copy of the current data first
            if (!pre.startsWith("Backup saved")) return "Restore stopped: could not save a safety copy. Nothing changed."
            val e = sp(ctx).edit()
            for ((k, v2) in BackupCodec.prefs(text)) {
                if (isSkipped(k)) continue
                when (v2) {
                    is Boolean -> e.putBoolean(k, v2)
                    is Int -> e.putInt(k, v2)
                    is Long -> e.putLong(k, v2)
                    is Float -> e.putFloat(k, v2)
                    is String -> e.putString(k, v2)
                }
            }
            if (!e.commit()) return "Restore failed while saving settings. A safety copy exists."
            for ((n, t) in BackupCodec.files(text)) {
                if (n !in FILES) continue
                if (!write(File(ctx.filesDir, n), t)) return "Restore: file $n could not be saved. A safety copy exists."
            }
            return "Restored. Close and reopen the keyboard."
        } catch (_: Throwable) { return "Restore failed. A safety copy may exist." }
    }

    /** v0.4.1: checks text from an imported file; if valid it is saved as import_<time>.json in the backups folder (restore it from the list). */
    fun importText(ctx: Context, text: String): String {
        return try {
            val v = BackupCodec.validate(text)
            if (v != BackupCodec.OK) return "Import stopped: file is not a valid Jabra backup (code $v). Nothing changed."
            val f = File(dir(ctx), "import_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".json")
            if (write(f, text)) "Imported. Find it in the Backups list and tap Restore." else "Import failed (could not save the file). Nothing changed."
        } catch (_: Throwable) { "Import failed. Nothing changed." }
    }

    /** Once per day (bk_auto ON): marks the day first, then saves on a background Thread. Safe to call from onStartInputView. */
    fun autoIfDue(ctx: Context) {
        try {
            val s = sp(ctx)
            if (!s.getBoolean("bk_auto", true)) return
            val d = today()
            if (s.getInt("bk_day", 0) == d) return
            s.edit().putInt("bk_day", d).apply()
            val app = ctx.applicationContext
            Thread {
                val msg = try { backupNow(app) } catch (_: Throwable) { "Backup failed" }
                if (!msg.startsWith("Backup saved")) toast(app, msg)   // a failed save is never silent
            }.start()
        } catch (_: Throwable) { }
    }

    fun toast(ctx: Context, msg: String) {
        try { Handler(Looper.getMainLooper()).post { try { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() } catch (_: Throwable) { } } } catch (_: Throwable) { }
    }
}
