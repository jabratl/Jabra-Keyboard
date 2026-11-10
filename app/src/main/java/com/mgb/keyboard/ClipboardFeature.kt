package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.clip.ClipList

/*
 * v0.0.6: clipboard history (+ pinned, delete, search). All new code is here; MgbIme.kt has small hooks marked "v0.0.6".
 * Data stays ONLY on the phone: prefs file "jabra", new key "clips_v1". No network, no new permission.
 * New prefs keys: clip_on(bool,true) = remember copied text, edit_bar(bool,true) = show the Select all/Cut/Copy/Paste bar.
 */

/** Saves/loads the history. Never throws. */
class ClipStore(private val prefs: JabraPrefs) {
    private val list: ClipList = try { ClipList.parse(prefs.str("clips_v1", "")) } catch (_: Throwable) { ClipList() }

    private fun save() { try { prefs.setStr("clips_v1", list.serialize()) } catch (_: Throwable) { } }

    fun add(t: String) { try { if (list.add(t)) save() } catch (_: Throwable) { } }
    fun view(q: String): List<ClipList.Item> = try { list.view(q) } catch (_: Throwable) { emptyList() }
    fun togglePin(i: ClipList.Item): Boolean = try { val ok = list.togglePin(i); if (ok) save(); ok } catch (_: Throwable) { false }
    fun remove(i: ClipList.Item) { try { if (list.remove(i)) save() } catch (_: Throwable) { } }
    fun clearUnpinned() { try { list.clearUnpinned(); save() } catch (_: Throwable) { } }
}

/** The clipboard screen: list (pinned first), tap = paste, star = pin, x = delete, search, clear (asks twice). */
class ClipPanel(private val ctx: Context, private val host: JabraHost, private val store: ClipStore) {

    private var searching = false
    private var query = ""
    private var confirmClear = false

    /** Call when the panel is opened from outside (fresh start). */
    fun reset() { searching = false; query = ""; confirmClear = false }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun btn(label: String, weight: Float, hDp: Int, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(if (weight > 0f) 0 else LinearLayout.LayoutParams.WRAP_CONTENT, dp(hDp), if (weight > 0f) weight else 0f)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun refresh() { host.showPanel("clipboard") }

    /** Returns null on any problem -> keyboard shows normal letters. */
    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        // top bar
        val top = row()
        top.addView(btn("ABC", 1.2f, 40) { host.showLetters() })
        val searchBtn = btn(if (searching) "✕ Search" else "🔍 Search", 1.6f, 40) {
            searching = !searching
            query = ""
            confirmClear = false
            refresh()
        }
        JabraIcons.put(ctx, searchBtn, if (searching) "close" else "search")   // v0.0.7
        top.addView(searchBtn)
        top.addView(btn(if (confirmClear) "Sure? (unpinned)" else "Clear", 1.6f, 40) {
            if (confirmClear) { store.clearUnpinned(); confirmClear = false } else confirmClear = true
            refresh()
        })
        root.addView(top)

        if (searching) {
            val q = TextView(ctx)
            q.text = (if (JabraPrefs(ctx).bool("icons_on", true)) "" else "🔍 ") + query + "▏"   // v0.0.7: icon is on the button now
            q.textSize = 15f
            q.setTextColor(Color.BLACK)
            q.setPadding(dp(8), dp(2), dp(8), dp(2))
            root.addView(q)
        }

        // list
        val items = store.view(query)
        val listH = if (searching) 88 else 176
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(listH))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        if (items.isEmpty()) {
            val t = TextView(ctx)
            t.text = if (searching) "No match" else "Nothing copied yet. Copy some text and it will show here."
            t.setPadding(dp(8), dp(12), dp(8), dp(12))
            t.setTextColor(Color.DKGRAY)
            col.addView(t)
        }
        for (entry in items) col.addView(itemRow(entry))
        sv.addView(col)
        root.addView(sv)

        // tiny search keyboard (Latin letters only)
        if (searching) {
            for (letters in arrayOf("qwertyuiop", "asdfghjkl", "zxcvbnm")) {
                val r = row()
                letters.forEach { ch -> r.addView(btn(ch.toString(), 1f, 34) { query += ch; confirmClear = false; refresh() }) }
                if (letters == "asdfghjkl") r.addView(JabraIcons.put(ctx, btn("␣", 1f, 34) { query += " "; refresh() }, "space"))   // v0.0.7
                if (letters == "zxcvbnm") r.addView(JabraIcons.put(ctx, btn("⌫", 1.5f, 34) { if (query.isNotEmpty()) query = query.dropLast(1); refresh() }, "back"))   // v0.0.7
                root.addView(r)
            }
        }
        return root
    }

    private fun itemRow(item: ClipList.Item): View {
        val r = row()
        r.gravity = Gravity.CENTER_VERTICAL
        val t = btn((if (item.pinned && !JabraPrefs(ctx).bool("icons_on", true)) "★ " else "") + item.text.replace("\n", " ⏎ "), 1f, 44) {
            host.commitWord()
            host.typeText(item.text)
            host.showLetters()
        }
        t.textSize = 13f
        t.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        t.maxLines = 2
        t.ellipsize = TextUtils.TruncateAt.END
        t.setPadding(dp(8), 0, dp(8), 0)
        r.addView(t)
        r.addView(JabraIcons.put(ctx, btn(if (item.pinned) "★" else "☆", 0f, 44) { store.togglePin(item); refresh() }.also { it.minimumWidth = dp(44); it.minWidth = dp(44) }, if (item.pinned) "pinned" else "pin"))   // v0.0.7
        r.addView(JabraIcons.put(ctx, btn("✕", 0f, 44) { store.remove(item); refresh() }.also { it.minimumWidth = dp(44); it.minWidth = dp(44) }, "close"))   // v0.0.7
        return r
    }
}
