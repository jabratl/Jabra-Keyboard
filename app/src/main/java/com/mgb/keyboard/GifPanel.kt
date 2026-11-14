package com.mgb.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mgb.keyboard.gif.GifPlan
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * v0.18.5: GIF panel (route "gif", opened from the "GIF" tab of the emoji panel).
 * Source: KLIPY (free key pasted by the user, pref gif_klipy_key). Tap a GIF = it is sent into the field (commitContent), or copied when the app refuses.
 * Network ONLY while this panel is open and only for: trending / the search word you typed / the GIF you tapped. Nothing in the background.
 * Search word = a tiny letters keyboard drawn inside the panel (no EditText in the IME window), same trick as the emoji search.
 * Every call is wrapped in try/catch; a failure = the panel is blank and MgbIme goes back to the letters (PanelGuard counts the crash).
 */
class GifPanel(private val ctx: Context, private val host: JabraHost) {

    private class Item(val slug: String, val files: Map<String, String>)
    private class Reply(val code: Int, val body: ByteArray?)

    private val prefs = JabraPrefs(ctx)
    private val main = Handler(Looper.getMainLooper())
    private var typing = false          // the tiny keyboard is open
    private var query = ""              // what is being typed
    private var shown = ""              // the word of the results on screen ("" = trending)
    private var items: List<Item> = emptyList()
    private var status = ""
    private var gen = 0                 // number of the last request; older answers are dropped
    private var gridBox: LinearLayout? = null
    private var statusTv: TextView? = null
    private var rowPx = 0
    private var cellPx = 0

    private companion object {
        val pool by lazy { Executors.newFixedThreadPool(3) }
        val thumbs = LruCache<String, Bitmap>(80)
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun btn(label: String, w: Int, weight: Float, h: Int, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        try { JabraThemes.style(prefs, b) } catch (_: Throwable) { }
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(w, h, weight)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    private fun toast(s: String) { try { Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show() } catch (_: Throwable) { } }

    private fun key(): String = GifPlan.cleanKey(prefs.str(GifPlan.PREF_KEY, ""))

    private fun cid(): String {
        var c = prefs.str(GifPlan.PREF_CID, "")
        if (c.isBlank()) { c = java.util.UUID.randomUUID().toString(); prefs.setStr(GifPlan.PREF_CID, c) }
        return c
    }

    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun leave() { typing = false; query = "" }

    // ---------------------------------------------------------------- screens

    private fun make(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { }
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        val ink = try { JabraThemes.iconColor(prefs) } catch (_: Throwable) { Color.parseColor("#222222") }
        if (key().isEmpty()) return setup(root, ink)

        gridBox = null
        statusTv = null

        // top row: Search / Done | word | KLIPY | Key
        val top = LinearLayout(ctx)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val qv = TextView(ctx)
        qv.textSize = 15f
        qv.setTextColor(ink)
        qv.setPadding(dp(8), dp(2), dp(8), dp(2))
        qv.minHeight = dp(32)
        qv.gravity = Gravity.CENTER_VERTICAL
        qv.maxLines = 1
        fun showQuery() { qv.text = if (typing) query + "\u258F" else if (shown.isBlank()) "Trending GIFs" else shown }
        showQuery()
        top.addView(btn(if (typing) "Done" else "Search", dp(70), 0f, dp(36)) {
            typing = !typing
            host.showPanel("gif")
        })
        top.addView(qv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val credit = TextView(ctx)
        credit.text = "KLIPY"
        credit.textSize = 10f
        credit.setTextColor(try { JabraThemes.noteColor(prefs) } catch (_: Throwable) { Color.parseColor("#555555") })
        credit.setPadding(dp(4), 0, dp(4), 0)
        top.addView(credit)
        val kb = btn("Key", dp(44), 0f, dp(36)) { toast("Hold the Key button to remove the key") }
        kb.setOnLongClickListener {
            try { prefs.setStr(GifPlan.PREF_KEY, ""); items = emptyList(); status = ""; host.showPanel("gif") } catch (_: Throwable) { }
            true
        }
        kb.textSize = 12f
        top.addView(kb)
        root.addView(top)

        // results: 2 rows that scroll sideways
        val rowH = if (typing) dp(44) else dp(host.keyHeightDp())
        rowPx = rowH
        cellPx = (rowH * 1.3f).toInt()
        val hs = HorizontalScrollView(ctx)
        hs.isHorizontalScrollBarEnabled = false
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        hs.addView(box)
        gridBox = box
        root.addView(hs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH * 2 + dp(8)))
        val sv = TextView(ctx)
        sv.textSize = 12f
        sv.setTextColor(ink)
        sv.setPadding(dp(8), 0, dp(8), 0)
        sv.maxLines = 2
        statusTv = sv
        root.addView(sv)
        render()
        if (items.isEmpty() && status.isEmpty() && gen == 0) load(shown)   // first time this phone session: trending

        if (typing) {
            fun upd() { showQuery() }
            for (letters in arrayOf("qwertyuiop", "asdfghjkl", "zxcvbnm")) {
                val r = LinearLayout(ctx)
                r.orientation = LinearLayout.HORIZONTAL
                letters.forEach { ch -> r.addView(btn(ch.toString(), 0, 1f, dp(34)) { if (query.length < GifPlan.MAX_QUERY) { query += ch; upd() } }) }
                if (letters == "asdfghjkl") r.addView(btn("\u2423", 0, 1f, dp(34)) { if (query.isNotEmpty() && query.length < GifPlan.MAX_QUERY) { query += " "; upd() } })
                if (letters == "zxcvbnm") r.addView(btn("\u232B", 0, 1.5f, dp(34)) { if (query.isNotEmpty()) { query = query.dropLast(1); upd() } })
                root.addView(r)
            }
            val bot = LinearLayout(ctx)
            bot.orientation = LinearLayout.HORIZONTAL
            bot.addView(btn("ABC", 0, 1f, dp(40)) { leave(); host.showLetters() })
            bot.addView(btn("Search GIFs", 0, 2.5f, dp(40)) {
                shown = GifPlan.cleanQuery(query)
                typing = false
                load(shown)
                host.showPanel("gif")
            })
            root.addView(bot)
            return root
        }

        val h = dp(host.keyHeightDp())
        val bot = LinearLayout(ctx)
        bot.orientation = LinearLayout.HORIZONTAL
        bot.addView(btn("ABC", 0, 1.5f, h) { leave(); host.showLetters() })
        bot.addView(btn("Emoji", 0, 1.5f, h) { leave(); host.showPanel("emoji") })
        bot.addView(btn("Trending", 0, 2f, h) { shown = ""; load(""); host.showPanel("gif") })
        root.addView(bot)
        return root
    }

    /** No key yet: 3 short steps, "Get free key" opens KLIPY in the browser, "Paste key" takes the copied key. */
    private fun setup(root: LinearLayout, ink: Int): View {
        val t = TextView(ctx)
        t.text = "GIF search uses KLIPY (free).\n1. Tap \"Get free key\" and sign up.\n2. Copy the API key.\n3. Come back here and tap \"Paste key\".\nThe key stays only on this phone."
        t.textSize = 13f
        t.setTextColor(ink)
        t.setPadding(dp(8), dp(4), dp(8), dp(4))
        root.addView(t)
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.addView(btn("Get free key", 0, 1f, dp(44)) {
            try {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://partner.klipy.com"))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
            } catch (_: Throwable) { toast("Open partner.klipy.com in your browser") }
        })
        r.addView(btn("Paste key", 0, 1f, dp(44)) {
            val raw = try {
                val d = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                if (d == null || d.itemCount == 0) "" else (d.getItemAt(0).coerceToText(ctx)?.toString() ?: "")
            } catch (_: Throwable) { "" }
            val k = GifPlan.cleanKey(raw)
            if (k.isEmpty()) toast("The copied text is not a KLIPY key")
            else {
                prefs.setStr(GifPlan.PREF_KEY, k)
                gen = 0; items = emptyList(); status = ""; shown = ""
                host.showPanel("gif")
            }
        })
        root.addView(r)
        val bot = LinearLayout(ctx)
        bot.orientation = LinearLayout.HORIZONTAL
        val h = dp(host.keyHeightDp())
        bot.addView(btn("ABC", 0, 1.5f, h) { leave(); host.showLetters() })
        bot.addView(btn("Emoji", 0, 1.5f, h) { leave(); host.showPanel("emoji") })
        root.addView(bot)
        return root
    }

    // ---------------------------------------------------------------- results

    private fun render() {
        try {
            val box = gridBox ?: return
            box.removeAllViews()
            val rows = arrayOf(LinearLayout(ctx), LinearLayout(ctx))
            for (r in rows) { r.orientation = LinearLayout.HORIZONTAL; box.addView(r) }
            for (i in items.indices) rows[i % 2].addView(cell(items[i]))
            statusTv?.text = status
        } catch (_: Throwable) { }
    }

    private fun previewUrl(item: Item): String? {
        for (s in GifPlan.PREVIEW_ORDER) { val u = item.files[s]; if (u != null) return u }
        return null
    }

    private fun cell(item: Item): View {
        val iv = ImageView(ctx)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.setBackgroundColor(0x22000000)
        iv.contentDescription = "GIF. Tap to send"
        val url = previewUrl(item)
        iv.tag = url
        val have = if (url == null) null else thumbs.get(url)
        if (have != null) iv.setImageBitmap(have) else if (url != null) loadThumb(url, iv)
        iv.setOnClickListener { try { host.feedback(iv) } catch (_: Throwable) { }; send(item) }
        val lp = LinearLayout.LayoutParams(cellPx, rowPx)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        iv.layoutParams = lp
        return iv
    }

    private fun loadThumb(url: String, iv: ImageView) {
        val maxPx = cellPx
        pool.execute {
            try {
                val r = http(url, GifPlan.MAX_THUMB)
                val b = r.body ?: return@execute
                val bmp = decode(b, maxPx) ?: return@execute
                thumbs.put(url, bmp)
                main.post { try { if (iv.tag == url) iv.setImageBitmap(bmp) } catch (_: Throwable) { } }
            } catch (_: Throwable) { }
        }
    }

    private fun decode(b: ByteArray, maxPx: Int): Bitmap? = try {
        val o = BitmapFactory.Options(); o.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(b, 0, b.size, o)
        var s = 1
        while (o.outWidth / (s * 2) >= maxPx && o.outHeight / (s * 2) >= maxPx) s *= 2
        val o2 = BitmapFactory.Options(); o2.inSampleSize = s
        BitmapFactory.decodeByteArray(b, 0, b.size, o2)
    } catch (_: Throwable) { null }

    /** Trending (word = "") or search. The answer is shown only if no newer request was made. */
    private fun load(q: String) {
        val my = ++gen
        items = emptyList()
        status = "Loading\u2026"
        render()
        val url = GifPlan.searchUrl(key(), q, cid(), 1) ?: run { status = ""; return }
        pool.execute {
            val r = try { http(url, GifPlan.MAX_RESPONSE) } catch (_: Throwable) { Reply(0, null) }
            val list = try { if (r.code == 200 && r.body != null) parse(r.body) else null } catch (_: Throwable) { null }
            main.post {
                if (my == gen) {
                    if (list != null) { items = list; status = if (list.isEmpty()) "No GIFs found" else "" }
                    else { items = emptyList(); status = GifPlan.errorText(if (r.code == 200) 400 else r.code) }
                    render()
                }
            }
        }
    }

    /** KLIPY answer: {"result":true,"data":{"data":[{"slug":..,"file":{"xs":{"gif":{"url":..}},"sm":..,"md":..,"hd":..}}]}}. null = not usable. */
    private fun parse(body: ByteArray): List<Item>? {
        val root = JSONObject(String(body, Charsets.UTF_8))
        if (!root.optBoolean("result", true)) return null
        val arr = root.optJSONObject("data")?.optJSONArray("data") ?: return null
        val out = ArrayList<Item>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("type", "") == "ad") continue
            val file = o.optJSONObject("file") ?: continue
            val m = LinkedHashMap<String, String>()
            for (s in GifPlan.PREVIEW_ORDER) {
                val u = file.optJSONObject(s)?.optJSONObject("gif")?.optString("url", "") ?: ""
                if (GifPlan.isHttps(u)) m[s] = u
            }
            if (m.isNotEmpty()) out.add(Item(o.optString("slug", "gif" + i), m))
        }
        return out
    }

    // ---------------------------------------------------------------- send

    private fun send(item: Item) {
        toast("Loading GIF\u2026")
        pool.execute {
            val f = try { download(item) } catch (_: Throwable) { null }
            main.post {
                if (f == null) toast("Could not load this GIF")
                else {
                    val ok = try { ClipImages.pasteHook?.invoke(f, "image/gif") == true } catch (_: Throwable) { false }
                    if (!ok) toast("This app does not accept GIFs here")
                }
            }
        }
    }

    private fun download(item: Item): File? {
        val dir = File(ctx.cacheDir, "gif")
        try { dir.mkdirs() } catch (_: Throwable) { }
        for (sz in GifPlan.SEND_ORDER) {
            val url = item.files[sz] ?: continue
            val body = http(url, GifPlan.MAX_GIF_BYTES).body ?: continue   // too big / failed = try the next smaller size
            if (body.isEmpty()) continue
            val f = File(dir, GifPlan.fileName(item.slug))
            f.writeBytes(body)
            try {
                val names = (dir.listFiles() ?: emptyArray()).filter { it.isFile }.sortedByDescending { it.lastModified() }.map { it.name }
                for (n in GifPlan.overflow(names, GifPlan.KEEP_FILES)) File(dir, n).delete()
            } catch (_: Throwable) { }
            return f
        }
        return null
    }

    // ---------------------------------------------------------------- network (https only, size limited, runs on the pool, never on the main thread)

    private fun http(url: String, max: Int): Reply {
        var c: HttpURLConnection? = null
        try {
            if (!GifPlan.isHttps(url)) return Reply(0, null)
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.setRequestProperty("User-Agent", "JabraKeyboard/" + BuildConfig.VERSION_NAME)
            val code = c.responseCode
            if (code != 200) return Reply(code, null)
            val len = c.contentLength
            if (len > max) return Reply(code, null)
            val bo = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16384)
            var total = 0
            c.inputStream.use { ins ->
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > max) return Reply(code, null)
                    bo.write(buf, 0, n)
                }
            }
            return Reply(code, bo.toByteArray())
        } catch (_: Throwable) {
            return Reply(0, null)
        } finally {
            try { c?.disconnect() } catch (_: Throwable) { }
        }
    }
}
