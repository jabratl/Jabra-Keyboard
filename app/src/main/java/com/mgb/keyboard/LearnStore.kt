package com.mgb.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import com.mgb.keyboard.typing.UserWords
import java.io.File

/**
 * v0.10.2 (G3.1) the words the user typed, kept ON THE PHONE ONLY (filesDir/learn_v1.txt, AtomicFile). Used only while pref learn_on is ON (default OFF).
 * One process-wide instance (the keyboard and the settings screen share it, so "Forget" really empties the memory too).
 * Loading = background thread; saving = 8 s after the last learned word (background thread) + when the keyboard closes. Every call is try/catch: any problem = nothing learned, typing unaffected.
 * Nothing is sent anywhere. A broken file is renamed to learn_v1.bad and learning starts empty.
 */
object LearnStore {
    private const val NAME = "learn_v1.txt"
    private const val SAVE_MS = 8000L
    private val lock = Any()
    private val h = Handler(Looper.getMainLooper())
    @Volatile private var words: UserWords? = null
    @Volatile private var loading = false
    @Volatile private var app: Context? = null
    private val saveRun = Runnable { flush() }

    private fun file(c: Context) = File(c.filesDir, NAME)

    /** Starts loading the file in the background (once). Cheap when already loaded. */
    fun ensure(ctx: Context) {
        try {
            if (words != null || loading) return
            val c = ctx.applicationContext ?: ctx
            app = c
            loading = true
            Thread {
                try {
                    synchronized(lock) {
                        if (words == null) {
                            val f = file(c)
                            var u: UserWords? = null
                            if (f.exists()) {
                                u = try { UserWords.parseOrNull(String(AtomicFile(f).readFully(), Charsets.UTF_8)) } catch (_: Throwable) { null }
                                if (u == null) { try { f.renameTo(File(c.filesDir, "learn_v1.bad")) } catch (_: Throwable) { } }
                            }
                            words = u ?: UserWords()
                        }
                    }
                } catch (_: Throwable) { }
                loading = false
            }.start()
        } catch (_: Throwable) { loading = false }
    }

    /** The loaded words, or null while still loading (then nothing is merged / learned this time). */
    fun words(): UserWords? = words

    /** Counts one finished word and schedules a save. Ignored while the file is still loading. */
    fun learn(w: String) {
        try {
            val u = words ?: return
            if (u.learn(w)) {
                h.removeCallbacks(saveRun)
                h.postDelayed(saveRun, SAVE_MS)
            }
        } catch (_: Throwable) { }
    }

    /** Writes the words to the file now (background thread) when something changed. */
    fun flush() {
        try {
            h.removeCallbacks(saveRun)
            val u = words ?: return
            val c = app ?: return
            if (!u.isDirty()) return
            Thread {
                try {
                    synchronized(lock) {
                        if (u === words && u.isDirty()) {
                            val text = u.serialize()
                            val af = AtomicFile(file(c))
                            val os = af.startWrite()
                            try { os.write(text.toByteArray(Charsets.UTF_8)); af.finishWrite(os); u.markClean() } catch (t: Throwable) { af.failWrite(os) }
                        }
                    }
                } catch (_: Throwable) { }
            }.start()
        } catch (_: Throwable) { }
    }

    /** "Forget my words": empties the memory and deletes the file. Returns how many words were forgotten (0 when none / unknown). */
    fun clearAll(ctx: Context): Int {
        var n = 0
        try {
            val c = ctx.applicationContext ?: ctx
            h.removeCallbacks(saveRun)
            synchronized(lock) {
                n = words?.size() ?: 0
                words = UserWords()
                try { file(c).delete() } catch (_: Throwable) { }
                try { File(c.filesDir, "learn_v1.bad").delete() } catch (_: Throwable) { }
                try { File(c.filesDir, NAME + ".new").delete(); File(c.filesDir, NAME + ".bak").delete() } catch (_: Throwable) { }
            }
        } catch (_: Throwable) { }
        return n
    }
}
