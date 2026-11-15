package com.mgb.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.view.inputmethod.EditorInfo
import com.mgb.keyboard.hist.HistLog
import com.mgb.keyboard.hist.HistPause
import com.mgb.keyboard.hist.HistPolicy
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * v0.20.0 (H1) typing history: the log of what you typed (time, app, text), kept ON THE PHONE ONLY (filesDir/typing_history_v1.txt, AtomicFile, plain text).
 * Everything is OFF by default (pref hist_on). The keyboard calls type() / back() / backWord() / enter() from MgbIme; every call is try/catch and
 * does nothing unless the current field was accepted by HistPolicy in onField() (password fields never, email / URL / number and incognito fields by setting, own app never).
 * Memory log = typing/hist/HistLog (main thread only). Loading = background thread (then merged on the main thread); saving = 8 s after the last change + when the keyboard closes
 * (text is built on the main thread, written on a background thread; a counter stops an older snapshot from overwriting a newer one).
 * Old entries are removed by the keep time every time the keyboard opens (onField) and when the Data & Backup card opens. Nothing is sent anywhere.
 * A broken file is renamed to typing_history_v1.bad and the log starts empty. The file is NOT part of Backup (BackupPlan.FILES) and the hist_* prefs are skipped by Backup / Restore.
 */
object HistStore {
    const val NAME = "typing_history_v1.txt"
    private const val SAVE_MS = 8000L
    private val lock = Any()
    private val h = Handler(Looper.getMainLooper())
    private var log = HistLog()                     // main thread only
    @Volatile private var loaded = false
    @Volatile private var loading = false
    @Volatile private var epoch = 0                 // changes on Clear: a load that is still running must not bring old text back
    @Volatile private var app: Context? = null
    private var prefs: JabraPrefs? = null
    private var active = false                      // the current field is accepted by HistPolicy
    private var activeApp = ""
    private var reason = HistPolicy.OFF
    private var lastType = 0                        // v0.23.0 (H3): the open field's type / options / package, to re-check when a pause ends in the middle of a field
    private var lastIme = 0
    private var lastPkg = ""
    private val seq = AtomicLong(0)
    private var written = 0L                        // guarded by lock
    private var onLoaded: (() -> Unit)? = null
    private val saveRun = Runnable { flush() }

    fun file(c: Context) = File(c.filesDir, NAME)

    private fun attach(ctx: Context, p: JabraPrefs? = null) {
        val c = ctx.applicationContext ?: ctx
        app = c
        prefs = p ?: prefs ?: JabraPrefs(c)
    }

    /** Starts loading the file in the background (once). No file = nothing to load. */
    fun ensure(ctx: Context) {
        try {
            if (loaded || loading) return
            attach(ctx)
            val c = app ?: return
            val f = file(c)
            if (!f.exists()) { loaded = true; return }
            loading = true
            val ep = epoch
            Thread {
                var parsed: HistLog? = null
                try {
                    synchronized(lock) {
                        parsed = try { HistLog.parseOrNull(String(AtomicFile(f).readFully(), Charsets.UTF_8)) } catch (_: Throwable) { null }
                        if (parsed == null) { try { f.renameTo(File(c.filesDir, "typing_history_v1.bad")) } catch (_: Throwable) { } }
                    }
                } catch (_: Throwable) { }
                val p = parsed ?: HistLog()
                h.post { adopt(p, ep) }
            }.start()
        } catch (_: Throwable) { loading = false }
    }

    private fun adopt(p: HistLog, ep: Int) {
        try {
            if (ep != epoch) return                  // cleared while loading: the old text stays gone
            log.mergeOlder(p)
            loaded = true
            loading = false
            pruneNow()
            schedule()
            val cb = onLoaded; onLoaded = null
            cb?.invoke()
        } catch (_: Throwable) { loading = false; loaded = true }
    }

    fun isLoaded(): Boolean = loaded

    /** Runs cb once, on the main thread, when the file has been loaded (the Data & Backup card uses it to refresh the count). */
    fun whenLoaded(cb: () -> Unit) { if (loaded) cb() else onLoaded = cb }

    /** A new input field started (keyboard opened / field changed): decide if it may be recorded, end the old run, remove expired entries. */
    fun onField(ctx: Context, p: JabraPrefs, info: EditorInfo?, restarting: Boolean) {
        try {
            attach(ctx, p)
            ensure(ctx)
            if (!restarting) log.closeRun()
            val pkg = info?.packageName ?: ""
            lastType = info?.inputType ?: 0; lastIme = info?.imeOptions ?: 0; lastPkg = pkg
            val r = HistPolicy.decide(
                p.bool("hist_on", false), isPaused(p), p.bool("hist_skip_sens", true), p.bool("hist_skip_incog", true),
                lastType, lastIme, pkg, ctx.packageName, p.str("hist_apps_off", ""))
            reason = r
            if (r != HistPolicy.OK || pkg != activeApp) log.closeRun()
            active = r == HistPolicy.OK
            activeApp = pkg
            pruneNow()
        } catch (_: Throwable) { active = false }
    }

    /** Why the current field is (not) recorded: a HistPolicy.* value. */
    fun lastReason(): Int = reason

    /** The user typed [s] with the keyboard (never called for pasted clips / vault / tools: MgbIme decides). */
    fun type(s: String) {
        try {
            if (!active) recheck()                    // v0.23.0 (H3): a pause that ended (or was resumed) while this field is open
            if (!active) return
            val p = prefs ?: return
            if (!p.bool("hist_on", false) || isPaused(p)) { log.closeRun(); return }   // switched off / paused while this field is open
            if (log.type(s, activeApp, System.currentTimeMillis())) schedule()
        } catch (_: Throwable) { }
    }

    /** v0.23.0 (H3): paused = the old switch (hist_pause) or a running timed pause (hist_pause_until). */
    fun pauseUntil(p: JabraPrefs): Long = try { p.str("hist_pause_until", "0").toLongOrNull() ?: 0L } catch (_: Throwable) { 0L }
    fun isPaused(p: JabraPrefs): Boolean = try { HistPause.paused(p.bool("hist_pause", false), pauseUntil(p), System.currentTimeMillis()) } catch (_: Throwable) { p.bool("hist_pause", false) }

    /** The length picked last (pref hist_pause_lv: 0 = 15 min, 1 = 1 hour, 2 = 1 day). */
    fun pauseLevel(p: JabraPrefs): Int = HistPause.level(p.int("hist_pause_lv", HistPause.DEF_LEVEL))

    /**
     * Pause / resume. Paused (switch or timer) -> resume now. Saving -> pause for the chosen length ([level] 0..2; -1 = the length used last),
     * and that length becomes the new default. Returns the new paused state.
     */
    fun pauseTap(ctx: Context, p: JabraPrefs, level: Int = -1): Boolean {
        try {
            attach(ctx, p)
            val now = System.currentTimeMillis()
            val flag = p.bool("hist_pause", false)
            val lv = if (level in 0..2) level else pauseLevel(p)
            val until = HistPause.tapUntil(flag, pauseUntil(p), now, lv)
            if (flag) p.setBool("hist_pause", false)
            p.setStr("hist_pause_until", until.toString())
            if (until > 0L) { p.setInt("hist_pause_lv", lv); log.closeRun() }
        } catch (_: Throwable) { }
        return isPaused(p)
    }

    /** Pause just ended (or was resumed) while the field is still open: decide again, with the same rules as onField. */
    private fun recheck() {
        try {
            val p = prefs ?: return
            val c = app ?: return
            if (reason != HistPolicy.PAUSED || isPaused(p)) return
            val r = HistPolicy.decide(p.bool("hist_on", false), false, p.bool("hist_skip_sens", true), p.bool("hist_skip_incog", true),
                lastType, lastIme, lastPkg, c.packageName, p.str("hist_apps_off", ""))
            reason = r
            active = r == HistPolicy.OK
        } catch (_: Throwable) { }
    }

    /** v0.23.0 (H3): entries of one app (for the "also delete old entries?" question and the excluded list). */
    fun countApp(pkg: String): Int = try { log.countApp(pkg) } catch (_: Throwable) { 0 }

    /** v0.23.0 (H3): deletes every entry of one app (only after the person said yes). Returns how many were removed. */
    fun removeApp(pkg: String): Int = try { val n = log.removeApp(pkg); if (n > 0) schedule(); n } catch (_: Throwable) { 0 }

    /** Backspace removed [n] characters of text we typed. */
    fun back(n: Int) {
        try { if (active && log.back(n, activeApp, System.currentTimeMillis())) schedule() } catch (_: Throwable) { }
    }

    /** Hold-backspace removed a whole word. */
    fun backWord() {
        try { if (active && log.backWord(activeApp, System.currentTimeMillis())) schedule() } catch (_: Throwable) { }
    }

    /** Enter: the message / line is finished = the entry ends. */
    fun enter() {
        try { log.closeRun(); schedule() } catch (_: Throwable) { }
    }

    /** Keyboard input ended: end the run and save now. */
    fun finish() {
        try { log.closeRun(); flush() } catch (_: Throwable) { }
    }

    /** Removes entries older than the keep time (pref hist_days_lv). Runs even while the feature is OFF, so old text never outlives its keep time. */
    fun pruneNow() {
        try {
            if (!loaded) return
            val p = prefs ?: return
            val n = log.prune(System.currentTimeMillis(), HistPolicy.ttlMs(p.int("hist_days_lv", HistPolicy.DEF_LEVEL)))
            if (n > 0) schedule()
        } catch (_: Throwable) { }
    }

    private fun schedule() {
        try { if (!loaded) return; h.removeCallbacks(saveRun); h.postDelayed(saveRun, SAVE_MS) } catch (_: Throwable) { }
    }

    /** Writes the log to the file now (background thread) when something changed. An empty log deletes the file. */
    fun flush() {
        try {
            h.removeCallbacks(saveRun)
            if (!loaded) return
            val c = app ?: return
            if (!log.isDirty) return
            val empty = log.size() == 0
            val text = if (empty) "" else log.serialize()
            log.markClean()
            val my = seq.incrementAndGet()
            Thread { writeNow(c, text, empty, my) }.start()
        } catch (_: Throwable) { }
    }

    /** Background thread: writes (or deletes) the file. A snapshot older than one already written is dropped. */
    private fun writeNow(c: Context, text: String, empty: Boolean, my: Long) {
        try {
            synchronized(lock) {
                if (my <= written) return
                if (empty) {
                    try { file(c).delete() } catch (_: Throwable) { }
                    try { File(c.filesDir, NAME + ".new").delete(); File(c.filesDir, NAME + ".bak").delete() } catch (_: Throwable) { }
                    written = my
                    return
                }
                val af = AtomicFile(file(c))
                val os = af.startWrite()
                var good = false
                try { os.write(text.toByteArray(Charsets.UTF_8)); af.finishWrite(os); good = true } catch (_: Throwable) { af.failWrite(os) }
                if (good) written = my else h.post { log.markDirty(); schedule() }
                Unit
            }
        } catch (_: Throwable) { }
    }

    fun count(): Int = try { log.size() } catch (_: Throwable) { 0 }

    /** Entries, oldest first (a copy). For the history page. */
    fun entries(): List<HistLog.Entry> = try { log.entries() } catch (_: Throwable) { emptyList() }

    fun remove(id: Long): Boolean = try { val r = log.remove(id); if (r) schedule(); r } catch (_: Throwable) { false }

    /** v0.21.0 (H2): puts back an entry that was just deleted on the history page (Undo). */
    fun restore(e: HistLog.Entry): Boolean = try { val r = log.restore(e); if (r) schedule(); r } catch (_: Throwable) { false }

    /** "Clear all now": empties the memory and deletes the file. Returns how many entries were removed (0 when none / unknown). */
    fun clearAll(ctx: Context): Int {
        var n = 0
        try {
            val c = ctx.applicationContext ?: ctx
            app = c
            h.removeCallbacks(saveRun)
            epoch++
            n = log.clear()
            log.markClean()
            loaded = true
            loading = false
            val my = seq.incrementAndGet()
            synchronized(lock) {
                written = my
                try { file(c).delete() } catch (_: Throwable) { }
                try { File(c.filesDir, "typing_history_v1.bad").delete() } catch (_: Throwable) { }
                try { File(c.filesDir, NAME + ".new").delete(); File(c.filesDir, NAME + ".bak").delete() } catch (_: Throwable) { }
            }
        } catch (_: Throwable) { }
        return n
    }
}
