package com.mgb.keyboard

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.mgb.keyboard.typing.ClipImgPlan
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * v0.18.1: copied images / screenshots in the clipboard panel. Pref clip_img (default ON, OFF = nothing is stored or shown).
 * Stored ONLY in the app cache folder "clip_img" (max 8, newest kept, no network). Tap a thumbnail = paste it, hold = delete it.
 * Every call is wrapped in try/catch; any problem = the clipboard panel looks and works exactly as before.
 */
object ClipImages {
    /** MgbIme sets this: (file, mime) -> true when the app took the image. */
    @Volatile var pasteHook: ((File, String) -> Boolean)? = null

    fun on(prefs: JabraPrefs): Boolean = try { prefs.bool(ClipImgPlan.PREF, true) } catch (_: Throwable) { true }

    private fun dir(ctx: Context): File { val d = File(ctx.cacheDir, "clip_img"); try { d.mkdirs() } catch (_: Throwable) { }; return d }

    /** Stored images, newest first. */
    fun list(ctx: Context): List<File> = try {
        (dir(ctx).listFiles() ?: emptyArray()).filter { it.isFile && ClipImgPlan.mimeOfName(it.name) != null }.sortedByDescending { it.lastModified() }
    } catch (_: Throwable) { emptyList() }

    /** v0.18.3: pictures follow "Clip auto-delete" (same time as the text clips; Never = no change). Called when the panel is drawn. */
    fun expire(ctx: Context, prefs: JabraPrefs) {
        try {
            val ttl = com.mgb.keyboard.clip.ClipMeta.ttlMs(prefs.int("clip_expire", 0))
            if (ttl <= 0L) return
            val now = System.currentTimeMillis()
            for (f in list(ctx)) if (ClipImgPlan.expired(f.lastModified(), now, ttl)) f.delete()
        } catch (_: Throwable) { }
    }

    fun clear(ctx: Context) { try { for (f in list(ctx)) f.delete() } catch (_: Throwable) { } }

    /** Reads the image items of a clip into the cache. true = at least one image is stored (or was already there). */
    fun capture(ctx: Context, d: ClipData): Boolean {
        var got = false
        try {
            for (i in 0 until d.itemCount) {
                val uri = try { d.getItemAt(i).uri } catch (_: Throwable) { null } ?: continue
                var mime: String? = null
                for (k in 0 until (d.description?.mimeTypeCount ?: 0)) { val m = d.description.getMimeType(k); if (ClipImgPlan.supported(m)) { mime = m; break } }
                if (mime == null) { val m = try { ctx.contentResolver.getType(uri) } catch (_: Throwable) { null }; if (ClipImgPlan.supported(m)) mime = m }
                if (mime == null) continue
                val bytes = try {
                    ctx.contentResolver.openInputStream(uri)?.use { ins ->
                        val bo = ByteArrayOutputStream()
                        val buf = ByteArray(16384)
                        var total = 0L
                        while (true) { val n = ins.read(buf); if (n < 0) break; total += n; if (total > ClipImgPlan.MAX_BYTES) return@use null; bo.write(buf, 0, n) }
                        bo.toByteArray()
                    }
                } catch (_: Throwable) { null } ?: continue
                if (ClipImgPlan.tooBig(bytes.size.toLong())) continue
                val h = java.util.Arrays.hashCode(bytes.copyOf(Math.min(bytes.size, 4096))) xor bytes.size
                val old = list(ctx).firstOrNull { ClipImgPlan.hashOf(it.name) == Integer.toHexString(h) }
                if (old != null) { try { old.setLastModified(System.currentTimeMillis()) } catch (_: Throwable) { }; got = true; continue }   // same picture again = move to the front
                val name = ClipImgPlan.fileName(System.currentTimeMillis(), h, mime) ?: continue
                File(dir(ctx), name).writeBytes(bytes)
                got = true
            }
            if (got) for (n in ClipImgPlan.overflow(list(ctx).map { it.name }, ClipImgPlan.MAX_IMAGES)) { try { File(dir(ctx), n).delete() } catch (_: Throwable) { } }
        } catch (_: Throwable) { }
        return got
    }

    private fun thumb(f: File, maxPx: Int): Bitmap? = try {
        val o = BitmapFactory.Options(); o.inJustDecodeBounds = true
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= maxPx && o.outHeight / (s * 2) >= maxPx) s *= 2
        val o2 = BitmapFactory.Options(); o2.inSampleSize = s
        BitmapFactory.decodeFile(f.path, o2)
    } catch (_: Throwable) { null }

    /** A row of thumbnails, or null when there is nothing to show. */
    fun strip(ctx: Context, prefs: JabraPrefs): View? = try {
        val files = list(ctx)
        if (files.isEmpty()) null else {
            val d = ctx.resources.displayMetrics.density
            fun dp(v: Int) = (v * d).toInt()
            val hs = HorizontalScrollView(ctx)
            hs.isHorizontalScrollBarEnabled = false
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(dp(6), dp(4), dp(6), dp(4))
            for (f in files) {
                val bmp = thumb(f, dp(64)) ?: continue
                val iv = ImageView(ctx)
                iv.setImageBitmap(bmp)
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(8).toFloat()) } }
                iv.clipToOutline = true
                iv.contentDescription = "Copied image. Tap to paste, hold to delete"
                iv.setOnClickListener {
                    try {
                        val ok = pasteHook?.invoke(f, ClipImgPlan.mimeOfName(f.name) ?: "image/png") == true
                        if (!ok) Toast.makeText(ctx, "This app does not accept images", Toast.LENGTH_SHORT).show()
                    } catch (_: Throwable) { }
                }
                iv.setOnLongClickListener {
                    try { f.delete(); row.removeView(iv); if (row.childCount == 0) hs.visibility = View.GONE } catch (_: Throwable) { }
                    true
                }
                val lp = LinearLayout.LayoutParams(dp(64), dp(64))
                lp.rightMargin = dp(6)
                row.addView(iv, lp)
            }
            if (row.childCount == 0) null else { hs.addView(row); hs }
        }
    } catch (_: Throwable) { null }

    /** The panel view with the image strip on top (no images / pref OFF = the view itself). */
    fun wrap(ctx: Context, v: View, prefs: JabraPrefs): View = try {
        if (!on(prefs)) v else {
            expire(ctx, prefs)   // v0.18.3
            val s = strip(ctx, prefs)
            if (s == null) v else {
                val box = LinearLayout(ctx)
                box.orientation = LinearLayout.VERTICAL
                box.addView(s, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                val lp = v.layoutParams?.let { LinearLayout.LayoutParams(it) } ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                box.addView(v, lp)
                box
            }
        }
    } catch (_: Throwable) { v }
}
