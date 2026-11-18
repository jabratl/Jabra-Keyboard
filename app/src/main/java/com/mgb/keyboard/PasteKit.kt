package com.mgb.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.clip.ClipList
import com.mgb.keyboard.typing.QuickPaste

/*
 * v0.38.0 (P1): shared PASTE pieces for the small keyboards (clip Edit screen = ClipboardFeature.kt, Find & Replace typing view = ReplacePanel.kt).
 * Those keyboards could only type key by key; now: Paste key (latest copy), hold Paste or tap the arrow = pick from Recent / Pinned clips, hold on the text = paste there.
 * Pure text rules live in typing/QuickPaste.java (tested). Everything here is wrapped in try / catch: a problem = the old keyboard without the paste keys.
 * Nothing is stored, no permission: the keyboard may read the clipboard while it is the active keyboard (same as the Clip button of Replace).
 */
object PasteKit {

    /** Text of the system clipboard ("" = empty / not readable). */
    fun systemClip(ctx: Context): String = try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val d = cm.primaryClip
        if (d == null || d.itemCount == 0) "" else (d.getItemAt(0).coerceToText(ctx)?.toString() ?: "")
    } catch (_: Throwable) { "" }

    /** Picker tab "Recent": the latest copy first, then the clips that are not pinned. */
    fun recentTexts(items: List<ClipList.Item>, latest: String): List<String> =
        try { QuickPaste.pickList(latest, items.filter { !it.pinned }.map { it.text }, QuickPaste.LIST_MAX) } catch (_: Throwable) { emptyList<String>() }

    /** Picker tab "Pinned": the pinned clips (the saved templates). */
    fun pinnedTexts(items: List<ClipList.Item>): List<String> =
        try { QuickPaste.pickList("", items.filter { it.pinned }.map { it.text }, QuickPaste.LIST_MAX) } catch (_: Throwable) { emptyList<String>() }

    /** Long press on a button = [act] (the long press is consumed: no click after it). */
    fun onHold(b: View, feedback: (View) -> Unit, act: () -> Unit) {
        try {
            b.setOnLongClickListener { v ->
                try { feedback(v) } catch (_: Throwable) { }
                try { act() } catch (_: Throwable) { }
                true
            }
        } catch (_: Throwable) { }
    }

    /**
     * The picker screen: [Back] [Recent] [Pinned], one note line, a list of one-line buttons (tap = [onPick] with the FULL text).
     * [mk] makes a themed button the same way the calling panel does, [paint] gives the panel background.
     */
    fun picker(
        ctx: Context, pf: JabraPrefs, tab: Int, recent: List<String>, pinned: List<String>,
        mk: (String, Float, Int, (Button) -> Unit) -> Button,
        paint: (View) -> Unit,
        onTab: (Int) -> Unit, onPick: (String) -> Unit, onBack: () -> Unit
    ): View {
        val d = ctx.resources.displayMetrics.density
        val pad = (2 * d).toInt()
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paint(root)
        root.setPadding(pad, (4 * d).toInt(), pad, (4 * d).toInt())
        val dimInk = try { JabraTokens.dimText(pf) } catch (_: Throwable) { Color.DKGRAY }

        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.addView(mk("\u2190 Back", 1f, 34) { onBack() })
        head.addView(mk((if (tab == 0) "\u2713 " else "") + "Recent", 1f, 34) { onTab(0) })
        head.addView(mk((if (tab == 1) "\u2713 " else "") + "Pinned", 1f, 34) { onTab(1) })
        root.addView(head)

        val note = TextView(ctx)
        note.text = if (tab == 1) "Your pinned clips. Tap one to paste it at the cursor." else "Latest copy is on top. Tap one to paste it at the cursor."
        note.textSize = 12f
        note.setTextColor(dimInk)
        note.setPadding((8 * d).toInt(), pad, (8 * d).toInt(), pad)
        root.addView(note)

        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (170 * d).toInt())
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val list = if (tab == 1) pinned else recent
        if (list.isEmpty()) {
            val e = TextView(ctx)
            e.text = if (tab == 1) "No pinned clips yet. Pin a clip with the star in the clipboard." else "Nothing copied yet. Copy some text and it shows here."
            e.textSize = 13f
            e.setTextColor(dimInk)
            e.setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            col.addView(e)
        }
        for (t in list) {
            val r = LinearLayout(ctx)
            r.orientation = LinearLayout.HORIZONTAL
            r.addView(mk(QuickPaste.oneLine(t, 44), 1f, 40) { onPick(t) })
            col.addView(r)
        }
        sv.addView(col)
        root.addView(sv)
        return root
    }
}
