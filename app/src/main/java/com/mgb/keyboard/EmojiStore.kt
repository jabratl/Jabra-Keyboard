package com.mgb.keyboard

import android.content.Context
import com.mgb.keyboard.emoji.EmojiDb

/**
 * v0.8.1 (E2): loads the emoji data file (assets/emoji/emoji_v1.txt) ONCE on a background thread and keeps it in memory.
 * get() = null until the file is ready, or for good if it is missing / does not parse: then the OLD emoji panel is used.
 * No timer, no service. Nothing here ever throws.
 */
object EmojiStore {
    @Volatile private var db: EmojiDb? = null
    private var started = false

    fun get(): EmojiDb? = db

    fun preload(ctx: Context) {
        synchronized(this) {
            if (started) return
            started = true
        }
        try {
            val app = ctx.applicationContext ?: ctx
            val t = Thread(Runnable {
                try {
                    val s = app.assets.open("emoji/emoji_v1.txt")
                    try { db = EmojiDb.parseOrNull(s) } finally { try { s.close() } catch (_: Throwable) { } }
                } catch (_: Throwable) { db = null }
            }, "jabra-emoji")
            t.priority = Thread.MIN_PRIORITY
            t.start()
        } catch (_: Throwable) { }
    }
}
