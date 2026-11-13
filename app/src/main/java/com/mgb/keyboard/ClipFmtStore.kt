package com.mgb.keyboard

import android.content.Context
import com.mgb.keyboard.clip.ClipFmt

/**
 * v0.17.39 (M6): disk side of ClipFmt. OWN prefs file "jabra_clipfmt" (big text never goes into "jabra"):
 * key "fmt_v1" = the serialized store, key "n" = how many entries (so an empty store is never loaded).
 * Never throws. Nothing reads it for pasting yet (M7). No new permission, no network.
 */
class ClipFmtStore(private val ctx: Context) {
    private var fmt: ClipFmt? = null

    private fun sp() = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun load(): ClipFmt {
        val f0 = fmt
        if (f0 != null) return f0
        val f = try { ClipFmt.parse(sp().getString(KEY, "")) } catch (_: Throwable) { ClipFmt() }
        fmt = f
        return f
    }
    private fun save(f: ClipFmt) { try { sp().edit().putString(KEY, f.serialize()).putInt(N, f.size()).apply() } catch (_: Throwable) { } }

    fun count(): Int = try { fmt?.size() ?: sp().getInt(N, 0) } catch (_: Throwable) { 0 }

    /** html null = the newest copy of this text was plain: the stored format goes. */
    fun put(text: String, html: String?) { try { if (html == null && count() == 0) return; val f = load(); if (f.put(text, html)) save(f) } catch (_: Throwable) { } }
    fun get(text: String): String? = try { if (count() == 0) null else load().get(text) } catch (_: Throwable) { null }
    fun has(text: String): Boolean = try { get(text) != null } catch (_: Throwable) { false }
    /** Keeps only the formats of clips that still exist. */
    fun prune(keep: Collection<String>) { try { if (count() == 0) return; val f = load(); if (f.prune(keep)) save(f) } catch (_: Throwable) { } }
    fun clearAll() { try { val f = load(); f.clear(); save(f) } catch (_: Throwable) { } }
    /** Forget the in-memory copy (another object changed the file). */
    fun reload() { fmt = null }

    companion object {
        const val FILE = "jabra_clipfmt"
        private const val KEY = "fmt_v1"
        private const val N = "n"
        /** For code that has no ClipStore (Privacy page): prune straight in the file. */
        fun pruneNow(ctx: Context, keep: Collection<String>) { try { ClipFmtStore(ctx).prune(keep) } catch (_: Throwable) { } }
    }
}
