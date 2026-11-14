package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.clip.ClipAge
import com.mgb.keyboard.clip.ClipTrash
import com.mgb.keyboard.typing.PanelLayout

/**
 * v0.18.4: the "Recently deleted" screen of the clipboard (opened from the \u22EF menu, "Deleted").
 * One clear job: bring clips back. Header = Back + title + count. List = one card per clip with ONE button (Restore).
 * Bottom bar = Restore all | Empty (two taps). Nothing else on the screen, so nothing can be hit by mistake.
 * Look = the same card / button parts as the rest of the clipboard (theme colours, key look). All state lives in ClipPanel.
 * build() may throw: ClipPanel catches it and shows the normal clipboard.
 */
object ClipTrashUi {

    class Acts(
        val back: () -> Unit,
        val restore: (ClipTrash.Entry) -> Unit,
        val restoreAll: () -> Unit,
        val empty: () -> Unit,        // first tap asks (caller sets confirmEmpty), second tap empties
        val cancelEmpty: () -> Unit
    )

    private fun dp(ctx: Context, v: Int): Int = JabraTokens.dp(ctx, v)

    fun build(
        ctx: Context, host: JabraHost, p: JabraPrefs, items: List<ClipTrash.Entry>, limit: Int,
        msg: String, confirmEmpty: Boolean, listPx: Int, acts: Acts
    ): View {
        val touch = PanelLayout.MIN_TOUCH
        val fb: (View) -> Unit = { v -> try { host.feedback(v) } catch (_: Throwable) { } }
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        try { JabraThemes.paint(p, root) } catch (_: Throwable) { root.setBackgroundColor(Color.parseColor("#DDDDDD")) }
        root.setPadding(dp(ctx, 2), dp(ctx, 4), dp(ctx, 2), dp(ctx, 4))

        // ---- header: Back | title (count)
        val top = UiKit.row(ctx)
        top.addView(ClipBar.func(p, UiKit.btn(ctx, p, "< Back", 1.2f, touch, 0f, fb) { acts.back() }))
        val ttl = TextView(ctx)
        ttl.text = if (items.isEmpty()) "Recently deleted" else "Recently deleted \u00B7 " + items.size
        ttl.textSize = 14f
        ttl.setTypeface(ttl.typeface, Typeface.BOLD)
        ttl.setTextColor(try { JabraTokens.panelText(p) } catch (_: Throwable) { Color.BLACK })
        ttl.gravity = Gravity.CENTER_VERTICAL
        ttl.setPadding(dp(ctx, 8), 0, dp(ctx, 4), 0)
        ttl.maxLines = 1
        ttl.ellipsize = TextUtils.TruncateAt.END
        ttl.layoutParams = LinearLayout.LayoutParams(0, dp(ctx, touch), 3.2f)
        top.addView(ttl)
        root.addView(top)

        // ---- one quiet line: message after an action, else what this list is
        val note = TextView(ctx)
        note.text = if (msg.isNotEmpty()) msg else "Keeps your last $limit deleted clips. Restore puts one back on top."
        note.textSize = 12f
        note.setTextColor(try { JabraTokens.dimText(p) } catch (_: Throwable) { Color.DKGRAY })
        note.setPadding(dp(ctx, 8), 0, dp(ctx, 8), dp(ctx, 2))
        note.maxLines = 1
        note.ellipsize = TextUtils.TruncateAt.END
        note.gravity = Gravity.CENTER_VERTICAL
        note.minimumHeight = dp(ctx, 24)
        root.addView(note)

        // ---- list
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, listPx)
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val now = System.currentTimeMillis()
        if (items.isEmpty()) {
            val e = TextView(ctx)
            e.text = "Nothing deleted yet\nDeleted clips wait here so you can bring them back"
            e.textSize = 14f
            e.setTextColor(try { JabraTokens.dimText(p) } catch (_: Throwable) { Color.DKGRAY })
            e.gravity = Gravity.CENTER
            e.setPadding(dp(ctx, 16), dp(ctx, 24), dp(ctx, 16), dp(ctx, 24))
            col.addView(e)
        }
        for (en in items) col.addView(card(ctx, p, en, now, fb, acts))
        sv.addView(col)
        root.addView(sv)

        // ---- bottom bar
        if (items.isNotEmpty()) {
            val bar = UiKit.row(ctx)
            if (confirmEmpty) {
                bar.addView(UiKit.btn(ctx, p, "Cancel", 1f, touch, 0f, fb) { acts.cancelEmpty() })
                bar.addView(ClipBar.danger(p, UiKit.btn(ctx, p, "Sure? Empty " + items.size, 2f, touch, 0f, fb) { acts.empty() }))
            } else {
                bar.addView(ClipBar.on(ctx, p, UiKit.btn(ctx, p, "Restore all", 1.4f, touch, 0f, fb) { acts.restoreAll() }))
                bar.addView(ClipBar.danger(p, UiKit.btn(ctx, p, "Empty", 1f, touch, 0f, fb) { acts.empty() }))
            }
            root.addView(bar)
        }
        return root
    }

    /** One deleted clip: text (tap = show more / less) + small meta line, and the single Restore button on the right. */
    private fun card(ctx: Context, p: JabraPrefs, en: ClipTrash.Entry, now: Long, fb: (View) -> Unit, acts: Acts): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.background = try { UiKit.cardBackground(ctx, p, false, en.pinned) } catch (_: Throwable) { null }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3))
        box.layoutParams = lp
        box.minimumHeight = dp(ctx, 56)

        val left = LinearLayout(ctx)
        left.orientation = LinearLayout.VERTICAL
        left.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val t = TextView(ctx)
        t.text = en.text.replace("\n", " \u23CE ")
        t.textSize = 13f
        t.setTextColor(try { JabraTokens.cardText(p) } catch (_: Throwable) { Color.BLACK })
        t.maxLines = 3
        t.ellipsize = TextUtils.TruncateAt.END
        t.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 6), dp(ctx, 2))
        var open = false
        t.setOnClickListener { open = !open; t.maxLines = if (open) 12 else 3; try { fb(t) } catch (_: Throwable) { } }
        left.addView(t)

        val age = try { ClipAge.shortAge(en.at, now) } catch (_: Throwable) { "" }
        val parts = ArrayList<String>()
        if (en.pinned) parts.add("Pinned")
        parts.add(if (age.isEmpty()) "deleted" else if (age == "now") "just now" else "$age ago")
        parts.add(en.text.length.toString() + " chars")
        val m = TextView(ctx)
        m.text = parts.joinToString(" \u00B7 ")
        m.textSize = 10f
        m.setTextColor(try { JabraTokens.cardNote(p) } catch (_: Throwable) { Color.GRAY })
        m.setPadding(dp(ctx, 10), 0, dp(ctx, 6), dp(ctx, 6))
        left.addView(m)
        box.addView(left)

        val r = UiKit.btn(ctx, p, "Restore", 0f, 36, 12f, fb) { acts.restore(en) }
        r.setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0)
        box.addView(ClipBar.on(ctx, p, r))
        return box
    }
}
