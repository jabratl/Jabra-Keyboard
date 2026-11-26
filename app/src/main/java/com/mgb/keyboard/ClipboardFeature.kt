package com.mgb.keyboard

import com.mgb.keyboard.typing.ClipFloat
import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.os.SystemClock   // v0.35.1 (CC3-B)
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.clip.ClipList
import com.mgb.keyboard.typing.ClipPop   // v0.35.1 (CC3-B)
import com.mgb.keyboard.clip.ClipMeta
import com.mgb.keyboard.clip.ClipExport   // v0.17.57 (C2)
import com.mgb.keyboard.clip.ClipName   // v0.17.63 (R3)
import com.mgb.keyboard.typing.ClipCaret   // v0.17.56 (C1)
import com.mgb.keyboard.typing.QuickPaste   // v0.38.0 (P1)
import com.mgb.keyboard.typing.ClipSelect
import com.mgb.keyboard.typing.ClipOrder   // v0.15.2 (N2)
import com.mgb.keyboard.typing.ClipSensitive   // v0.10.10 (W4)
import com.mgb.keyboard.typing.ClipFilter   // v0.16.9 (X9)
import com.mgb.keyboard.typing.ClipCompact   // v0.33.0 (CC1)
import com.mgb.keyboard.typing.ClipCopy   // v0.17.58 (C3)
import com.mgb.keyboard.typing.ClipFold   // v0.43.0
import com.mgb.keyboard.typing.ClipMockPlan   // v0.50.0 (CM1)
import com.mgb.keyboard.typing.ClipMultiPaste   // v0.43.0
import com.mgb.keyboard.typing.PanelLayout   // v0.16.9 (X9)

/*
 * v0.0.6: clipboard history (+ pinned, delete, search). All new code is here; MgbIme.kt has small hooks marked "v0.0.6".
 * Data stays ONLY on the phone: prefs file "jabra", new key "clips_v1". No network, no new permission.
 * New prefs keys: clip_on(bool,true) = remember copied text, edit_bar(bool,true) = show the Select all/Cut/Copy/Paste bar.
 */

/** Saves/loads the history. Never throws. */
class ClipStore(private val prefs: JabraPrefs, private val fmt: ClipFmtStore? = null) {   // v0.17.39 (M6): fmt = remembered html formats (null = none)
    private var list: ClipList = try { ClipList.parse(prefs.str("clips_v1", "")) } catch (_: Throwable) { ClipList() }   // v0.10.10: var (reload after "Clear clipboard history now")

    init { live = this; applyLimits() }   // v0.10.10 (W4); v0.49.0: + the Settings limits

    /** v0.49.0: gives ClipList the limits from Settings (clip_keep_n, clip_cap_k in thousands). Never throws; any problem = the defaults stay. */
    private fun applyLimits() { try { ClipList.configure(prefs.int("clip_keep_n", ClipList.MAX_UNPINNED), prefs.int("clip_cap_k", ClipList.MAX_CAPTURE / 1000) * 1000) } catch (_: Throwable) { } }

    /** v0.10.10 (W4): re-reads the list from prefs (used after the Privacy page cleared it) and drops meta of clips that are gone. */
    fun reload() { try { list = ClipList.parse(prefs.str("clips_v1", "")); if (meta.prune(list.view(""))) saveMeta(); fmt?.reload(); pruneFmt() } catch (_: Throwable) { } }

    /** v0.17.39 (M6): drops the stored formats of clips that are gone (cheap: nothing is read when no format is stored). */
    private fun pruneFmt() { try { fmt?.prune(list.view("").map { it.text }) } catch (_: Throwable) { } }

    companion object {   // v0.10.10 (W4)
        @Volatile private var live: ClipStore? = null
        /** Deletes all UNPINNED clips straight in prefs (Privacy page has no ClipStore). Keeps pinned. Returns how many were removed. */
        fun clearUnpinnedNow(prefs: JabraPrefs, ctx: Context? = null): Int = try {   // v0.17.39 (M6): ctx = also prune the stored formats
            val l = ClipList.parse(prefs.str("clips_v1", ""))
            val before = l.size()
            l.clearUnpinned()
            prefs.setStr("clips_v1", l.serialize())
            val m = ClipMeta.parse(prefs.str("clip_meta_v1", ""))
            m.prune(l.view(""))
            prefs.setStr("clip_meta_v1", m.serialize())
            if (ctx != null) ClipFmtStore.pruneNow(ctx, l.view("").map { it.text })   // v0.17.39 (M6)
            if (ctx != null) { try { ClipImages.clear(ctx) } catch (_: Throwable) { } }   // v0.18.1: copied images go with the history
            try { ClipTrashStore.clearNow(prefs) } catch (_: Throwable) { }   // v0.18.4: the Recently deleted clips go with the history
            live?.reload()
            before - l.size()
        } catch (_: Throwable) { 0 }
    }

    private fun save() { try { prefs.setStr("clips_v1", list.serialize()) } catch (_: Throwable) { } }

    // v0.4.3: side table (made / used / count) in NEW pref clip_meta_v1; clips_v1 is untouched. Loaded lazily.
    private val meta: ClipMeta by lazy { try { ClipMeta.parse(prefs.str("clip_meta_v1", "")) } catch (_: Throwable) { ClipMeta() } }
    private fun saveMeta() { try { prefs.setStr("clip_meta_v1", meta.serialize()) } catch (_: Throwable) { } }
    /** v0.4.3.1: edit one clip. Returns ClipList.replace(): 0 ok, 1 empty, 2 too long, 3 duplicate, 4 not found. */
    fun replace(i: ClipList.Item, t: String): Int = try {
        val old = i.text
        val rc = list.replace(i, t)
        if (rc == 0 && old != t) { save(); meta.rename(old, t); saveMeta(); pruneFmt() }   // v0.17.39 (M6): edited text = no format
        rc
    } catch (_: Throwable) { 4 }
    fun has(t: String): Boolean = try { list.view("").any { it.text == t } } catch (_: Throwable) { false }
    fun countOf(t: String): Int = try { meta.count(t) } catch (_: Throwable) { 0 }
    fun madeOf(t: String): Long = try { meta.madeAt(t) } catch (_: Throwable) { 0L }   // v0.10.24 (C1): when the clip was copied (0 = unknown)
    fun fmtOf(t: String): String? = try { fmt?.get(t) } catch (_: Throwable) { null }   // v0.17.40 (M7): stored html of a clip (null = none)
    fun hasFmt(t: String): Boolean = try { fmtOf(t) != null } catch (_: Throwable) { false }
    fun noteUse(i: ClipList.Item) { try { meta.used(i.text, System.currentTimeMillis()); saveMeta() } catch (_: Throwable) { } }
    fun sorted(l: List<ClipList.Item>, mode: Int): List<ClipList.Item> = try { if (mode == 0) l else meta.sort(l, mode) } catch (_: Throwable) { l }
    /** Auto-delete (pref clip_expire 0..3): unpinned only; runs when the panel opens or a clip is added, never on a timer. Returns how many were removed. */
    fun expireNow(): Int = try { expireCodes() + expireOld() } catch (_: Throwable) { 0 }   // v0.10.10 (W4): code guard first, then the old Auto-delete

    /** v0.10.10 (W4): code guard (pref clip_sens, default OFF): an unpinned clip that looks like a one-time code is removed 2 minutes after it was copied. Same trigger as Auto-delete (clip added / panel opened), no timer. */
    private fun expireCodes(): Int = try {
        if (!prefs.bool("clip_sens", false)) 0 else {
            val now = System.currentTimeMillis()
            var touched = false
            val gone = list.view("").filter { c ->
                var m = meta.madeAt(c.text)
                if (m <= 0L && !c.pinned && ClipSensitive.looksLikeCode(c.text)) { meta.made(c.text, now); touched = true; m = now }
                ClipSensitive.isDue(c.pinned, c.text, m, now)
            }
            val n = if (gone.isNotEmpty()) list.removeAll(gone) else 0
            if (n > 0) { save(); meta.prune(list.view("")); pruneFmt(); touched = true }   // v0.17.39 (M6)
            if (touched) saveMeta()
            n
        }
    } catch (_: Throwable) { 0 }

    private fun expireOld(): Int = try {
        val ttl = ClipMeta.ttlMs(prefs.int("clip_expire", 0))
        if (ttl <= 0L) 0 else {
            val before = meta.size()
            val gone = meta.expired(list.view(""), ttl, System.currentTimeMillis())
            val n = if (gone.isNotEmpty()) list.removeAll(gone) else 0
            if (n > 0) { save(); pruneFmt() }   // v0.17.39 (M6)
            val pr = meta.prune(list.view(""))
            if (pr || n > 0 || meta.size() != before) saveMeta()
            n
        }
    } catch (_: Throwable) { 0 }

    fun add(t: String) { try { applyLimits(); val isNew = !has(t); if (list.add(t)) save(); if (isNew) { meta.made(t, System.currentTimeMillis()); saveMeta() } else if (prefs.bool("clip_sens", false) && ClipSensitive.looksLikeCode(t)) { meta.remake(t, System.currentTimeMillis()); saveMeta() }; expireNow() } catch (_: Throwable) { } }   // v0.4.3: records "made" for new clips
    /** v0.16.15 (E3): for text copied in other apps: long texts (up to ClipList.MAX_CAPTURE) are kept. Same bookkeeping as add(). */
    fun addCaptured(t: String) { try { applyLimits(); val isNew = !has(t); if (list.addCaptured(t)) save(); if (isNew && has(t)) { meta.made(t, System.currentTimeMillis()); saveMeta() } else if (!isNew && prefs.bool("clip_sens", false) && ClipSensitive.looksLikeCode(t)) { meta.remake(t, System.currentTimeMillis()); saveMeta() }; expireNow() } catch (_: Throwable) { } }
    /** v0.17.39 (M6): same as addCaptured(t) and remembers the html copied with it (null = plain copy: an old stored format of the same text goes). */
    fun addCaptured(t: String, html: String?) {
        addCaptured(t)
        try { if (fmt != null && has(t)) { fmt.put(t, html); if (html != null) pruneFmt() } } catch (_: Throwable) { }
    }
    fun view(q: String): List<ClipList.Item> = try { list.view(q) } catch (_: Throwable) { emptyList() }
    fun togglePin(i: ClipList.Item): Boolean = try { val ok = list.togglePin(i); if (ok) save(); ok } catch (_: Throwable) { false }
    fun remove(i: ClipList.Item) { try { if (list.remove(i)) { save(); pruneFmt() } } catch (_: Throwable) { } }   // v0.17.39 (M6)
    fun clearUnpinned() { try { list.clearUnpinned(); save(); pruneFmt() } catch (_: Throwable) { } }   // v0.17.39 (M6)

    // v0.2.5.1: engine wrappers (all safe)
    fun pinnedView(q: String): List<ClipList.Item> = try { list.viewPinned(q) } catch (_: Throwable) { emptyList() }
    fun recentView(q: String): List<ClipList.Item> = try { list.viewRecent(q) } catch (_: Throwable) { emptyList() }
    fun pinnedCount(): Int = try { list.pinnedCount() } catch (_: Throwable) { 0 }
    fun setPinned(sel: List<ClipList.Item>, pin: Boolean): Int = try { val n = list.setPinned(sel, pin); if (n > 0) save(); n } catch (_: Throwable) { 0 }
    fun removeAll(sel: List<ClipList.Item>): Int = try { val n = list.removeAll(sel); if (n > 0) { save(); pruneFmt() }; n } catch (_: Throwable) { 0 }
    /** Saves a text as a new clip. Returns ClipList.check(): 0 = saved, 1 = empty, 2 = too long. */
    fun addChecked(t: String): Int = try { val c = ClipList.check(t); if (c == 0) add(t); c } catch (_: Throwable) { 1 }
}

/** The clipboard screen: list (pinned first), tap = paste, star = pin, x = delete, search, clear (asks twice). */
class ClipPanel(private val ctx: Context, private val host: JabraHost, private val store: ClipStore, private val fieldText: () -> String = { "" }) {   // v0.2.5.1: fieldText = selection or whole field text

    private var searching = false
    private var query = ""
    private var confirmClear = false
    private var msg = ""   // v0.2.5.1: one short message for the header line (shown once, then cleared)
    private val sel = ClipSelect()        // v0.2.5.3: select mode state lives HERE (not in the view), so it survives refresh()
    private var confirmDel = false        // v0.2.5.3: "Sure? delete N" two-tap safety
    private var editing: ClipList.Item? = null   // v0.4.3.1: clip being edited (null = normal panel)
    private var editText = ""
    private var editCaps = false
    private var editPos = 0   // v0.17.56 (C1): caret position inside editText (was: always the end)
    private var pasteOpen = false   // v0.38.0 (P1): the paste list (Recent / Pinned) of the Edit screen is open
    private var pasteTab = 0         // 0 = Recent, 1 = Pinned
    private var exporting = false   // v0.17.57 (C2): export screen is open
    private var expScope = 0        // 0 = selected clips, 1 = all clips, 2 = pinned clips
    private var expSel: List<ClipList.Item> = emptyList()   // the selection at the moment Export was tapped
    private var expMsg = ""
    private var expName = ""        // v0.17.63 (R3): custom file / zip name without extension ("" = automatic name)
    private var renaming = false    // v0.17.63 (R3): rename screen is open (inside the export screen)
    private var renText = ""
    private var renPos = 0
    private var renCaps = false
    private var confirmSave = false
    // v0.18.4: Recently deleted (the Undo list, up to 30 clips by default) + tidy look (pref clip_tidy). Delete these lines + the hooks marked "v0.18.4" = back to v0.18.3.
    private val trash: ClipTrashStore by lazy { ClipTrashStore(pf, store) }
    private var trashOpen = false
    private var trashSure = false
    private var trashMsg = ""
    private var undoSteps = 0   // how many delete actions of THIS visit can still be undone (Undo shows only while > 0; older deletes: menu > Deleted)
    private fun tidy(): Boolean = try { ClipLook.tidy(pf) } catch (_: Throwable) { false }
    private fun undoAvail(): Boolean = try { undoBuf.isNotEmpty() || (undoSteps > 0 && trash.on() && trash.count() > 0) } catch (_: Throwable) { undoBuf.isNotEmpty() }
    /** v0.4.4: set by MgbIme: saves the given clip texts into the Text Vault and returns a short message. null = no "To Vault" button. */
    var toVault: ((List<String>) -> String)? = null
    /** v0.15.1 (N1): height in px of the letters keyboard, set by MgbIme before build(). 0 = unknown = old fixed sizes. Used only when pref clip_fit is ON (default). */
    var fitPx: Int = 0

    /** Call when the panel is opened from outside (fresh start). */
    // v0.3.7: Undo for deleted clips (x, Delete, Clear). Kept until you leave the clipboard or delete something else.
    private var undoBuf: List<Pair<String, Boolean>> = emptyList()
    private fun keepUndo(items: List<ClipList.Item>) { if (items.isNotEmpty()) { undoBuf = items.map { Pair(it.text, it.pinned) }; try { if (trash.on()) { trash.keep(items); undoSteps++ } } catch (_: Throwable) { } } }   // v0.18.4: also remembered in the Recently deleted list; each delete = one more Undo step
    private fun doUndo() {
        var n = 0
        if (undoBuf.isNotEmpty()) {
            val fromTrash = try { trash.restoreTexts(undoBuf.map { it.first }) } catch (_: Throwable) { emptySet<String>() }   // v0.18.4: back with pinned flag AND format
            for ((t, p) in undoBuf.reversed()) {
                if (fromTrash.contains(t)) { n++; continue }
                try {
                    store.addCaptured(t)   // v0.18.4: was add(t), which refused clips longer than 2000 characters
                    if (p) { val x = store.view("").firstOrNull { c -> c.text == t }; if (x != null && !x.pinned) store.togglePin(x) }
                    n++
                } catch (_: Throwable) { }
            }
            undoBuf = emptyList()
        } else {
            n = try { trash.undoLast() } catch (_: Throwable) { 0 }   // v0.18.4: Undo again / after leaving the clipboard = the delete before
        }
        if (undoSteps > 0) undoSteps--   // v0.18.4
        msg = "Restored " + n
        confirmClear = false; confirmDel = false
        refresh()
    }

    fun reset() { try { ClipPopover.dismiss() } catch (_: Throwable) { }; try { openClips.clear() } catch (_: Throwable) { }; editing = null; confirmSave = false; undoBuf = emptyList(); searching = false; query = ""; confirmClear = false; msg = ""; sel.leave(); confirmDel = false; keepY = -1; fromRefresh = false; fChip = 0; moreOpen = false; pasteMenu = false; ovOpen = false; exporting = false; expMsg = ""; renaming = false; expName = ""; trashOpen = false; trashSure = false; trashMsg = ""; undoSteps = 0 }   // v0.18.4: Recently deleted closed, Undo steps start fresh; v0.17.63 (R3): rename closed + name cleared; v0.16.1 (X1); v0.17.57 (C2): export screen closed; v0.16.9 (X9): chip + More tray start fresh

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    // v0.8.7: theme helpers (Classic theme + Classic look = exactly the old colours; any error = old colour)
    private val pf: JabraPrefs by lazy { JabraPrefs(ctx) }
    private fun paintBg(v: View) { try { JabraThemes.paint(pf, v) } catch (_: Throwable) { v.setBackgroundColor(Color.parseColor("#DDDDDD")) } }
    private fun dim(): Int = try { JabraTokens.dimText(pf) } catch (_: Throwable) { Color.DKGRAY }
    private fun ink(): Int = try { if (JabraTokens.isClassic(pf)) Color.BLACK else JabraTokens.panelText(pf) } catch (_: Throwable) { Color.BLACK }
    private fun hush(): Int = try { JabraTokens.cardNote(pf) } catch (_: Throwable) { Color.GRAY }

    /** v0.8.7: themed button (UiKit.btn follows theme colour + look); old plain button = automatic fallback. */
    private fun btn(label: String, weight: Float, hDp0: Int, onClick: (Button) -> Unit): Button {
        val hDp = sz(hDp0)   // v0.17.32 (UI8): pref clip_btn_pct (100 = unchanged)
        try { return fontFit(UiKit.btn(ctx, pf, label, weight, hDp, 0f, { v -> try { host.feedback(v) } catch (_: Throwable) { } }, onClick)) } catch (_: Throwable) { }
        return btnOld(label, weight, hDp, onClick)
    }

    /** v0.17.32 (UI8): Clipboard button size % (pref clip_btn_pct, 70..140, 100 = old). Any problem = 100. */
    private fun bpct(): Int = try { com.mgb.keyboard.typing.ClipBtn.sanitize(JabraPrefs(ctx).int("clip_btn_pct", com.mgb.keyboard.typing.ClipBtn.DEF)) } catch (_: Throwable) { 100 }
    private fun sz(dp: Int): Int = try { com.mgb.keyboard.typing.ClipBtn.heightDp(dp, bpct()) } catch (_: Throwable) { dp }
    private fun fs(sp: Float): Float = try { com.mgb.keyboard.typing.ClipBtn.textSize(sp, bpct()) } catch (_: Throwable) { sp }
    private fun <T : TextView> fontFit(v: T): T { try { if (bpct() != 100) v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, com.mgb.keyboard.typing.ClipBtn.textSize(v.textSize, bpct())) } catch (_: Throwable) { }; return v }

    private fun btnOld(label: String, weight: Float, hDp: Int, onClick: (Button) -> Unit): Button {
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

    // v0.10.13 (D2.3): row() = UiKit.row (same horizontal LinearLayout). Old body kept as rowOld() = automatic fallback.
    private fun row(): LinearLayout {
        try { return UiKit.row(ctx) } catch (_: Throwable) { }
        return rowOld()
    }

    private fun rowOld(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    // v0.16.1 (X1): refresh() keeps the list position (the panel is rebuilt = new ScrollView at 0). refreshTop() = start at the top on purpose (search, sort, view, order).
    private var curSv: ScrollView? = null
    private var keepY = -1
    private var fromRefresh = false
    private fun refresh() {
        try { ClipPopover.dismiss() } catch (_: Throwable) { }   // v0.35.1 (CC3-B)
        try { if (editing == null) keepY = curSv?.scrollY ?: -1; fromRefresh = true } catch (_: Throwable) { keepY = -1; fromRefresh = false }
        host.showPanel("clipboard")
    }
    private fun refreshTop() { try { ClipPopover.dismiss() } catch (_: Throwable) { }; keepY = -1; fromRefresh = false; host.showPanel("clipboard") }

    // v0.17.37 (M4): ONE place for "after a paste". clip_stay (default ON) = stay in the clipboard, OFF = old way (back to letters).
    private var pasteToast: android.widget.Toast? = null
    private fun stayOn(): Boolean = try { JabraPrefs(ctx).bool("clip_stay", true) } catch (_: Throwable) { true }
    private fun afterPaste(rebuild: Boolean) {
        if (!stayOn()) { host.showLetters(); return }
        try { pasteToast?.cancel(); val t = android.widget.Toast.makeText(ctx, "Pasted", android.widget.Toast.LENGTH_SHORT); pasteToast = t; t.show() } catch (_: Throwable) { }
        if (rebuild) refresh()   // select mode was left: redraw, scroll position kept (keepY)
    }
    /** v0.17.42: one small grey line under the empty-clipboard text: what a tap / hold / star does. Same colour as the empty text, no new pref. */
    private fun emptyHint(): View {
        val h = TextView(ctx)
        h.text = "Tap a clip = paste  \u00B7  hold = select  \u00B7  \u2606 = pin (keeps it)"
        h.textSize = 11f
        h.gravity = Gravity.CENTER
        h.setPadding(dp(16), 0, dp(16), dp(12))
        try { h.setTextColor(dim()) } catch (_: Throwable) { }
        return h
    }
    private fun keepFmtOnTap(): Boolean = try { JabraPrefs(ctx).bool("clip_fmt_tap", true) } catch (_: Throwable) { true }   // v0.17.40 (M7)
    /** v0.17.40 (M7): a card tap (badge = the Aa tag): with or without format, by ClipPasteMode. */
    private fun pasteItem(item: ClipList.Item, badge: Boolean) {
        val mode = try { com.mgb.keyboard.typing.ClipPasteMode.decide(keepFmtOnTap(), badge, store.hasFmt(item.text)) } catch (_: Throwable) { com.mgb.keyboard.typing.ClipPasteMode.PLAIN }
        pasteClip(item.text, item, false, mode)
    }
    /** v0.17.40+ (M8): select-mode Paste: ONE chosen clip with a stored format follows the card-tap choice; several clips = plain, joined as before. */
    private fun pasteChosen(chosen: List<ClipList.Item>, text: String) {
        val one = if (chosen.size == 1) chosen[0] else null
        val mode = if (one != null) { try { com.mgb.keyboard.typing.ClipPasteMode.decide(keepFmtOnTap(), false, store.hasFmt(one.text)) } catch (_: Throwable) { com.mgb.keyboard.typing.ClipPasteMode.PLAIN } } else com.mgb.keyboard.typing.ClipPasteMode.PLAIN
        pasteClip(text, if (mode == com.mgb.keyboard.typing.ClipPasteMode.FORMAT) one else null, true, mode)
    }
    private fun pasteClip(text: String, item: ClipList.Item?, rebuild: Boolean, mode: Int = com.mgb.keyboard.typing.ClipPasteMode.PLAIN) {
        var done = false
        if (mode == com.mgb.keyboard.typing.ClipPasteMode.FORMAT && item != null) {   // v0.17.40 (M7): format paste, any problem = plain paste below
            val html = store.fmtOf(item.text)
            if (html != null) done = try { host.pasteFormatted(text, html) } catch (_: Throwable) { false }
        }
        if (!done) {
            host.commitWord()
            host.typeText(text)
        }
        if (item != null) store.noteUse(item)   // v0.4.3 (stay = no redraw, so the order does not jump)
        afterPaste(rebuild)
    }

    /** Returns null on any problem -> keyboard shows normal letters. */
    fun build(): View? = try { try { ClipPopover.dismiss() } catch (_: Throwable) { }; withImages(make()) } catch (_: Throwable) { null }
    /** v0.18.1: copied images / screenshots on top of the panel (not on the edit / export screens; pref clip_img OFF = nothing added). */
    private fun withImages(v: View): View = try { if (editing != null || exporting || trashOpen) v else ClipImages.wrap(ctx, v, JabraPrefs(ctx)) } catch (_: Throwable) { v }

    private fun make(): View {
        val restoreY = if (fromRefresh) keepY else -1   // v0.16.1 (X1)
        fromRefresh = false
        if (restoreY < 0 && editing == null) keepY = -1   // fresh open = start at the top
        if (editing == null) pasteOpen = false   // v0.38.0 (P1)
        if (exporting && renaming) { try { return fitEdit(makeRename()) } catch (_: Throwable) { renaming = false } }   // v0.17.63 (R3)
        if (exporting) { try { return fitEdit(makeExport()) } catch (_: Throwable) { exporting = false } }   // v0.17.57 (C2)
        if (trashOpen) { try { return makeTrash() } catch (_: Throwable) { trashOpen = false } }   // v0.18.4: Recently deleted screen
        if (editing != null) { try { return fitEdit(makeEdit()) } catch (_: Throwable) { editing = null } }   // v0.15.1: fitEdit   // v0.4.3.1
        // v0.16.9 (X9): pref panel_ui = 1 -> the NEW panel (makeNew below). Default 0 / any problem = the classic code that follows, untouched.
        val newUi = try { PanelLayout.isNewStyle(JabraPrefs(ctx).int("panel_ui", 0)) } catch (_: Throwable) { false }
        if (newUi || nl()) { try { return makeNew(restoreY) } catch (_: Throwable) { } }   // v0.17.58 (C3): new look ON = the redesigned panel for everyone; OFF = panel_ui decides as before
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)   // v0.8.7
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        // v0.2.5.3: forget selected clips that no longer exist (after delete / Clear)
        try { sel.prune(store.pinnedView("") + store.recentView("")) } catch (_: Throwable) { sel.leave() }
        val selOn = sel.isOn

        // top bar
        val top = row()
        top.addView(ClipBar.func(pf, btn("ABC", 1.0f, 40) { sel.leave(); confirmDel = false; host.showLetters() }))   // v0.2.5.3: ABC leaves select mode; v0.17.30 (UI6): function-key look
        val searchBtn = btn(if (searching) "✕ Search" else "🔍 Search", 1.6f, 40) {
            searching = !searching
            query = ""
            confirmClear = false
            refreshTop()   // v0.16.1
        }
        // v0.18.7: no second putThemed here - Glyphs.dress (inside btn) already drew ONE icon + the word "Search"; putThemed added a 2nd icon and removed the word
        top.addView(searchBtn)
        top.addView(btn("Add", 1.0f, 40) { addFromField() })   // v0.2.5.1: save the current field text as a clip
        val clearBtn = btn(if (confirmClear) "Sure? (unpinned)" else "Clear", 1.8f, 40) {
            if (confirmClear) { keepUndo(store.recentView("")); store.clearUnpinned(); try { ClipImages.clear(ctx) } catch (_: Throwable) { }; confirmClear = false } else confirmClear = true
            refresh()
        }
        if (confirmClear) clearBtn.textSize = fs(12f)   // v0.2.5.1: the button is a bit narrower now
        top.addView(ClipBar.danger(pf, clearBtn))   // v0.17.30 (UI6): warm red label
        root.addView(top)

        // v0.2.5.1: header line message (e.g. "Added", "Field is empty"); shown once
        // v0.2.5.2: the header line is now always there: left = message, right = [List|Cards] switch
        val shownMsg = msg
        msg = ""
        val cardsOn = try { JabraPrefs(ctx).int("clip_view", 0) == 1 } catch (_: Throwable) { false }
        val head = row()
        head.gravity = Gravity.CENTER_VERTICAL
        val m = TextView(ctx)
        m.text = if (selOn && shownMsg.isEmpty()) sel.count().toString() + " selected" else shownMsg   // v0.2.5.3
        m.textSize = 12f
        m.setTextColor(dim())
        m.setPadding(dp(8), 0, dp(4), 0)
        m.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        head.addView(m)
        if (undoAvail()) {   // v0.3.7; v0.18.4: also when only the Recently deleted list has something
            val undoBtn = btn("↶ Undo", 0f, 30) { doUndo() }
            undoBtn.textSize = fs(12f)
            undoBtn.setPadding(dp(10), 0, dp(10), 0)
            head.addView(ClipBar.pill(pf, undoBtn))   // v0.17.30 (UI6)
        }
        val sortLv = try { JabraPrefs(ctx).int("clip_sort", 0).coerceIn(0, 3) } catch (_: Throwable) { 0 }   // v0.4.3
        val sortBtn = btn("\u2195 " + arrayOf("Newest", "Oldest", "A-Z", "Used")[sortLv], 0f, 30) {
            try { JabraPrefs(ctx).setInt("clip_sort", (sortLv + 1) % 4) } catch (_: Throwable) { }
            confirmClear = false
            refreshTop()   // v0.16.1
        }
        sortBtn.textSize = fs(12f)
        sortBtn.setPadding(dp(10), 0, dp(10), 0)
        val viewBtn = btn(if (cardsOn) "View: Cards" else "View: List", 0f, 30) {
            try { JabraPrefs(ctx).setInt("clip_view", if (cardsOn) 0 else 1) } catch (_: Throwable) { }
            confirmClear = false
            refreshTop()   // v0.16.1
        }
        viewBtn.textSize = fs(12f)
        viewBtn.setPadding(dp(10), 0, dp(10), 0)
        if (!selOn) {   // v0.2.5.3: in select mode the action row below replaces View / Manage
            head.addView(ClipBar.pill(pf, sortBtn))   // v0.4.3; v0.17.30 (UI6): capsule
            head.addView(ClipBar.pill(pf, viewBtn))
            val manageBtn = btn("Manage", 0f, 30) { sel.enter(); confirmClear = false; confirmDel = false; refresh() }
            manageBtn.textSize = fs(12f)
            manageBtn.setPadding(dp(10), 0, dp(10), 0)
            head.addView(ClipBar.pill(pf, manageBtn))   // v0.17.30 (UI6)
        }
        root.addView(head)
        if (selOn) root.addView(selectBar())

        if (searching) {
            val q = TextView(ctx)
            q.text = (if (JabraPrefs(ctx).bool("icons_on", true)) "" else "🔍 ") + query + "▏"   // v0.0.7: icon is on the button now
            q.textSize = 15f
            q.setTextColor(try { JabraTokens.cardText(pf) } catch (_: Throwable) { ink() })
            try { q.background = UiKit.cardBackground(ctx, pf, false, false) } catch (_: Throwable) { }   // v0.18: rounded search box
            q.setPadding(dp(12), dp(6), dp(12), dp(6))
            try { q.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.setMargins(dp(4), dp(2), dp(4), dp(2)) } } catch (_: Throwable) { }
            root.addView(q)
        }

        // list
        try { store.expireNow() } catch (_: Throwable) { }   // v0.4.3: auto-delete (only when the panel is drawn, no timer)
        val items = store.view(query)
        val listH = if (searching) 88 else 176
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(listH))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        if (items.isEmpty()) {
            val t = TextView(ctx)
            t.text = if (searching) "No match" else "Nothing copied yet. Copy some text and it will show here."
            t.setPadding(dp(16), dp(12), dp(16), dp(12))
            t.setTextColor(dim())
            t.gravity = Gravity.CENTER   // v0.18
            if (!searching) t.text = "\uD83D\uDCCB\n" + t.text
            col.addView(t)
            if (!searching) col.addView(emptyHint())   // v0.17.42: how to use (first-time help)
        }
        // v0.2.5.1: "Pinned" / "Recent" header lines (only when both groups have items and not searching)
        val pinnedItems = store.sorted(store.pinnedView(query), sortLv)   // v0.4.3 (Newest = unchanged)
        val recentItems = store.sorted(store.recentView(query), sortLv)
        val showHeaders = ClipOrder.showHeaders(searching, pinnedItems.size, recentItems.size)   // v0.15.2 (same rule as before)
        val ordMode = try { ClipOrder.sanitize(JabraPrefs(ctx).int("clip_order", 0)) } catch (_: Throwable) { 0 }   // v0.15.2 (N2): 0 = Pinned first (old), 1 = Recent first
        val ordSwap: () -> Unit = { try { JabraPrefs(ctx).setInt("clip_order", ClipOrder.next(ordMode)) } catch (_: Throwable) { }; confirmClear = false; refreshTop() }   // v0.15.2: tap on a section header; v0.16.1: top
        // v0.2.5.2: Cards view (clip_view = 1); any problem -> null -> the old List code below
        val lines = try { JabraPrefs(ctx).int("clip_lines", 0).coerceIn(0, 2) } catch (_: Throwable) { 0 }
        val cards: View? = if (cardsOn && items.isNotEmpty()) {
            ClipCards.build(ctx, host, pinnedItems, recentItems, !searching, lines,
                { it2 -> store.togglePin(it2); refresh() },
                { it2 -> keepUndo(listOf(it2)); store.remove(it2); refresh() },
                SelectMode(selOn, { x -> sel.isSelected(x) }, { x -> sel.toggle(x); confirmDel = false; refresh() }, { x -> sel.enterWith(x); confirmDel = false; refresh() },
                    { x -> store.noteUse(x) }, { x -> infoLine(x) }, clipHoldMs(), { afterPaste(false) }, { x -> store.hasFmt(x.text) }, { x, b -> pasteItem(x, b) }), ClipOrder.recentFirst(ordMode), ordSwap)   // v0.4.3; v0.15.2: order + header tap
        } else null
        if (cards != null) {
            col.addView(cards)
        } else {
            // v0.15.2 (N2): groups in the order of pref clip_order (default Pinned first = as before); header tap swaps (arrow on the first one)
            val ordLabels = ClipOrder.labels(ordMode)
            val ordGroups = ClipOrder.groups(pinnedItems, recentItems, ordMode)
            for (gi in 0..1) {
                if (showHeaders) col.addView(sectionHeader(ordLabels[gi], gi == 0, ordSwap))
                for (entry in ordGroups[gi]) col.addView(itemRow(entry))
            }
        }
        sv.addView(col)
        root.addView(sv)
        try {   // v0.16.1 (X1): same list position after a rebuild (restored after the first layout, clamped to the new content height)
            curSv = sv
            if (com.mgb.keyboard.typing.ScrollKeep.wants(restoreY)) {
                keepY = -1
                sv.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        try {
                            if (sv.viewTreeObserver.isAlive) sv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            val ch = col.height
                            if (ch > 0) sv.scrollTo(0, com.mgb.keyboard.typing.ScrollKeep.clamp(restoreY, ch, sv.height))
                        } catch (_: Throwable) { }
                    }
                })
            }
        } catch (_: Throwable) { }

        // tiny search keyboard (Latin letters only)
        if (searching) {
            for (letters in arrayOf("qwertyuiop", "asdfghjkl", "zxcvbnm")) {
                val r = row()
                letters.forEach { ch -> r.addView(btn(ch.toString(), 1f, 34) { query += ch; confirmClear = false; refreshTop() }) }
                if (letters == "asdfghjkl") r.addView(JabraIcons.putThemed(ctx, btn("␣", 1f, 34) { query += " "; refreshTop() }, "space"))   // v0.0.7
                if (letters == "zxcvbnm") r.addView(JabraIcons.putThemed(ctx, btn("⌫", 1.5f, 34) { if (query.isNotEmpty()) query = query.dropLast(1); refreshTop() }, "back"))   // v0.0.7
                root.addView(r)
            }
        }
        fitList(root, sv)   // v0.15.1 (N1): panel keeps the letters keyboard height
        return root
    }

    // ===================== v0.16.9 (X9): NEW clipboard panel (pref panel_ui = 1) =====================
    // Same data, same actions, same scroll keeping (X1), same height rule (N1) as the classic make() above (which stays untouched and is the default).
    // New: sticky top bar (ABC | Search | Add | Manage / Done), filter chips (All | Pinned | Text | Links | Numbers), 40 dp tap targets,
    // and in select mode a bottom summary bar ("3 selected" + Pin | Delete | Paste | More). Any exception = make() goes on with the classic panel.
    private var fChip = 0            // v0.16.9: filter chip (not saved: a fresh open starts at All)
    private var ovOpen = false       // v0.17.58 (C3): the header overflow tray (Manage / Sort / View / Export all / Clear)
    private fun nl(): Boolean = try { ClipLook.newLook(pf) } catch (_: Throwable) { false }   // v0.17.58 (C3): pref clip_new_look (default ON)
    private fun simple(): Boolean = try { nl() && JabraPrefs(ctx).bool("clip_simple", true) } catch (_: Throwable) { false }   // v0.19.8: pref clip_simple (default ON) = flat text filter row + flat list rows with the pin on the right; OFF = exactly v0.19.7
    private fun compactOn(): Boolean = try { nl() && JabraPrefs(ctx).bool("clip_compact", true) } catch (_: Throwable) { false }   // v0.33.0 (CC1): pref clip_compact (default ON, needs the new look) = lower top row + smaller padding; OFF = exactly v0.32.0
    private fun slimBarOn(): Boolean = try { compactOn() && JabraPrefs(ctx).bool("clip_slimbar", true) } catch (_: Throwable) { false }   // v0.35.0 (CC3-A): pref clip_slimbar (default ON, needs the new look) = 34 dp select bar, icon + one word, count in Done; OFF = exactly v0.34.0
    private fun doneLbl(): String = try { if (slimBarOn()) ClipCompact.doneLabel(sel.count(), true) else "Done" } catch (_: Throwable) { "Done" }

    // ---- v0.43.0: COLLAPSIBLE parts (pref clip_fold, default ON, needs the new look; OFF = exactly v0.42.5) + paste WITH / WITHOUT format for the selection (pref clip_paste2, default ON; OFF = the one old Paste button). ----
    private fun foldOn(): Boolean = try { nl() && pf.bool(ClipFold.KEY_ON, true) } catch (_: Throwable) { false }
    private fun foldedOf(label: String): Boolean = try { pf.bool(ClipFold.keyFor(label), false) } catch (_: Throwable) { false }
    private fun toggleFold(key: String) { try { pf.setBool(key, !pf.bool(key, false)) } catch (_: Throwable) { }; confirmDel = false; refresh() }   // refresh() keeps the list position
    private val foldFn: (String) -> Boolean = { l -> foldedOf(l) }
    private val foldTap: (String) -> Unit = { l -> toggleFold(ClipFold.keyFor(l)) }
    private fun paste2On(): Boolean = try { nl() && pf.bool("clip_paste2", true) } catch (_: Throwable) { false }
    private fun selHasFmt(): Boolean = try { ClipMultiPaste.anyFormat(sel.selection().map { store.fmtOf(it.text) }) } catch (_: Throwable) { false }
    /** true = the select bar shows [Aa] [T] (with format / plain) instead of the one Paste: switch ON and at least one selected clip has a stored format. */
    private fun twoPaste(): Boolean = try { ClipMultiPaste.twoButtons(paste2On(), selHasFmt()) } catch (_: Throwable) { false }

    // ---- v0.35.1 (CC3-B): floating popover for select-mode More + the 3-dot menu (pref clip_pop, default ON, needs the new look). OFF or ANY failure = the old trays, exactly as v0.35.0. ----
    private var barAnchor: View? = null
    private var hdrAnchor: View? = null
    private var popVisible: List<ClipList.Item> = emptyList()
    private fun popOn(): Boolean = try { nl() && JabraPrefs(ctx).bool("clip_pop", true) } catch (_: Throwable) { false }
    private fun popGuardOk(): Boolean = ClipPop.reopenGuard(SystemClock.uptimeMillis(), ClipPopover.lastDismiss())   // the tap that just closed it must not re-open it

    // ---- v0.37.0: FLOATING select bar (pref clip_float, default ON, needs the new look). OFF or ANY failure = the slim bar of v0.36.5. ----
    private var floatShown = false
    private fun floatBarOn(): Boolean = try { nl() && ClipFloat.wants(sel.isOn, JabraPrefs(ctx).bool("clip_float", true), searching, moreOpen || pasteMenu) } catch (_: Throwable) { false }
    private fun floatBarV(visible: List<ClipList.Item>): View? {
        try {
            popVisible = visible
            val n = sel.count()
            val allPinned = n > 0 && sel.pinnedCount() == n
            val acts = ArrayList<ClipFloatBar.Act>()
            acts.add(ClipFloatBar.Act(if (allPinned) "Unpin" else "Pin", "pin", 1f, false) { actPin(!allPinned) })
            if (confirmDel) acts.add(ClipFloatBar.Act("Sure? delete " + n, null, 2.6f, true) { actDelete() })   // destructive step: wide, red, text only
            else acts.add(ClipFloatBar.Act("Delete", "trash", 1.1f, false) { actDelete() })
            if (twoPaste()) {   // v0.43.0: two small signs: Aa = paste with format, T = paste plain text
                acts.add(ClipFloatBar.Act(ClipMultiPaste.FORMAT_SIGN, "paste", 0.95f, false, "Paste with format") { actPasteAs(true) })
                acts.add(ClipFloatBar.Act(ClipMultiPaste.PLAIN_SIGN, "paste", 0.8f, false, "Paste plain text") { actPasteAs(false) })
            } else acts.add(ClipFloatBar.Act("Paste", "paste", 1f, false) { actPaste(false) })
            acts.add(ClipFloatBar.Act("More", "more", 1f, false) { moreToggle() })
            val b = ClipFloatBar.build(ctx, pf, acts, sz(ClipCompact.barDp(PanelLayout.MIN_TOUCH, true)), ClipFloat.animate(floatShown), n) { v -> try { host.feedback(v) } catch (_: Throwable) { } } ?: return null
            barAnchor = b.pill   // the More popover opens upward from the pill
            floatShown = true
            return b.wrap
        } catch (_: Throwable) { return null }
    }

    /** The More button: popover when possible, else the old tray toggle. */
    private fun moreToggle() {
        if (popOn() && popMore()) return
        moreOpen = !moreOpen; pasteMenu = false; confirmDel = false; refresh()
    }

    /** true = handled (opened / closed / ignored guard tap). false = could not show -> caller opens the old tray. */
    private fun popMore(): Boolean {
        try {
            if (ClipPopover.isOpen()) { ClipPopover.dismiss(); return true }
            if (!popGuardOk()) return true
            val a = barAnchor ?: return false
            moreOpen = false
            return ClipPopover.show(a, pf, host, ClipPop.moreItems(toVault != null), true) { picked -> popPick(picked.id) }
        } catch (_: Throwable) { return false }
    }

    private fun popHeader(a: View, reopen: Boolean): Boolean {
        try {
            if (!reopen) {
                if (ClipPopover.isOpen()) { ClipPopover.dismiss(); return true }
                if (!popGuardOk()) return true
            }
            hdrAnchor = a
            val sortLv = try { JabraPrefs(ctx).int("clip_sort", 0).coerceIn(0, 3) } catch (_: Throwable) { 0 }
            val cardsOn = try { JabraPrefs(ctx).int("clip_view", 0) == 1 } catch (_: Throwable) { false }
            val pillNow = nl() && !simple() && !mockOn() && try { pf.bool("ui_pill", true) } catch (_: Throwable) { true }   // v0.50.0 (CM1): mock tabs = no Sort / View pills, so the menu holds them
            val lim = try { trash.limit() } catch (_: Throwable) { com.mgb.keyboard.clip.ClipTrash.DEFAULT_LIMIT }
            val delN = try { trash.count() } catch (_: Throwable) { 0 }
            ovOpen = false; confirmClear = false
            return ClipPopover.show(a, pf, host, ClipPop.headerItems(sortLv, cardsOn, pillNow, lim, delN), false, !reopen) { picked -> popPick(picked.id) }   // v0.37.3: the Undo re-show (reopen) has no fade = no blink
        } catch (_: Throwable) { return false }
    }

    /** Undo-limit row: next value, menu stays open with the new value (no refresh, so the popover is not closed). */
    private fun popUndo() {
        try {
            val cur = try { trash.limit() } catch (_: Throwable) { com.mgb.keyboard.clip.ClipTrash.DEFAULT_LIMIT }
            JabraPrefs(ctx).setInt("clip_undo_n", ClipPop.nextUndo(cur, com.mgb.keyboard.clip.ClipTrash.QUICK))
            try { trash.count() } catch (_: Throwable) { }
            val a = hdrAnchor
            if (a != null) { ClipPopover.dismiss(); popHeader(a, true) }
        } catch (_: Throwable) { }
    }

    /** Every popover row runs the SAME handler code as the old tray pill. */
    private fun popPick(id: String) {
        when (id) {
            "all" -> { sel.selectAll(popVisible); confirmDel = false; refresh() }
            "none" -> { sel.clear(); confirmDel = false; refresh() }
            "selrecent" -> { sel.selectOnly(popVisible, false); confirmDel = false; refresh() }   // v0.41.1
            "selpinned" -> { sel.selectOnly(popVisible, true); confirmDel = false; refresh() }   // v0.41.1
            "edit" -> actEdit()
            "replace" -> actReplaceIn()
            "merge" -> actMerge()
            "unpin" -> actPin(false)
            "paste1" -> actPaste(true)
            "vault" -> actToVault()
            "export" -> actExport()
            "sort0", "sort1", "sort2", "sort3" -> { try { JabraPrefs(ctx).setInt("clip_sort", id.substring(4).toInt()) } catch (_: Throwable) { }; refreshTop() }
            "view0" -> { try { JabraPrefs(ctx).setInt("clip_view", 0) } catch (_: Throwable) { }; refreshTop() }
            "view1" -> { try { JabraPrefs(ctx).setInt("clip_view", 1) } catch (_: Throwable) { }; refreshTop() }
            "manage" -> { ovOpen = false; sel.enter(); confirmClear = false; confirmDel = false; refresh() }
            "deleted" -> { ovOpen = false; trashOpen = true; trashSure = false; trashMsg = ""; confirmClear = false; refreshTop() }
            "exportall" -> { ovOpen = false; expSel = emptyList(); expScope = 1; expMsg = ""; expName = ""; renaming = false; exporting = true; moreOpen = false; confirmDel = false; refresh() }
            "undo" -> popUndo()
            "clear" -> { ovOpen = false; confirmClear = true; refresh() }
        }
    }
    private fun mockOn(): Boolean = try { ClipMock.on(pf) } catch (_: Throwable) { false }   // v0.50.0 (CM1): pref clip_mock (default ON, needs the tidy look) = tabs / pinned strip / one-line swipe rows; OFF = v0.49.0
    private val openClips = HashSet<String>()   // v0.50.0 (CM1): clips opened with the small arrow (text of the clip)
    private fun compact2On(): Boolean = try { compactOn() && JabraPrefs(ctx).bool("clip_compact2", true) } catch (_: Throwable) { false }   // v0.34.0 (CC2): pref clip_compact2 (default ON, needs the new look) = Undo is a small icon at the end of the filter row, no count line; OFF = exactly v0.33.0 (the Undo line + count line)
    /** v0.34.0 (CC2): small "undo arrow" icon button for the end of the filter row. Any problem = null (the Undo then stays in the info line). */
    private fun undoIconBtn(hDp: Int): View? = try {
        val b = btn("\u21B6", 0f, hDp) { doUndo() }
        try { JabraIcons.putThemed(ctx, b, "undo") } catch (_: Throwable) { }
        try { b.contentDescription = "Undo" } catch (_: Throwable) { }
        val lp = LinearLayout.LayoutParams(dp(sz(40)), dp(sz(hDp)))
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        ClipBar.pill(pf, b)
    } catch (_: Throwable) { null }
    /** v0.19.8: a flat row + a thin divider line under it. Any problem = the row itself. */
    private fun flatLine(v: View): View = try {
        val w = LinearLayout(ctx)
        w.orientation = LinearLayout.VERTICAL
        w.addView(v)
        val ln = View(ctx)
        ln.setBackgroundColor(JabraTokens.withAlpha(dim(), 0x30))
        ln.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxOf(1, dp(1))).also { it.setMargins(dp(8), 0, dp(8), 0) }
        w.addView(ln)
        w
    } catch (_: Throwable) { v }
    private var pasteMenu = false    // v0.19.2 (S3): the chevron tray of the split \"Paste\" button (Paste 1 line / To Vault / Export)
    private var moreOpen = false     // v0.16.9: the "More" tray of the bottom bar (All / Unpin / Merge / Edit / Paste in 1 line / To Vault)

    private fun makeNew(restoreY: Int): View {
        val t40 = PanelLayout.MIN_TOUCH   // btn() scales it (v0.17.32 UI8)
        try { UiKit.closeSelect() } catch (_: Throwable) { }   // v0.19.1: a dropdown list of the old screen never stays over the new one
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        val cp = compactOn()   // v0.33.0 (CC1)
        root.setPadding(dp(2), dp(ClipCompact.padV(cp)), dp(2), dp(ClipCompact.padV(cp)))
        try { sel.prune(store.pinnedView("") + store.recentView("")) } catch (_: Throwable) { sel.leave() }
        val selOn = sel.isOn
        if (!selOn) { moreOpen = false; pasteMenu = false }
        val nlOn = nl()   // v0.17.58 (C3)
        if (selOn || searching || !nlOn) ovOpen = false
        fChip = ClipFilter.sanitize(fChip)

        // ---- sticky top bar
        val top = row()
        val tTop = ClipCompact.rowDp(t40, cp)   // v0.33.0 (CC1): compact top row (OFF = t40)
        top.addView(ClipBar.func(pf, btn("ABC", ClipCompact.weight(ClipCompact.ABC, cp, false), tTop) { sel.leave(); confirmDel = false; moreOpen = false; host.showLetters() }))   // v0.17.30 (UI6): function-key look
        val slim = nlOn && !selOn && !searching   // v0.17.58 (C3): header = ABC | title + count | search | Add | more
        val toolsFold = slim && ClipFold.hidden(foldOn(), foldedOf(ClipFold.KEY_TOOLS), selOn, searching)   // v0.43.0: the Filter / Sort / View row is folded (tap the title to open it)
        if (slim) {
            val ttl = TextView(ctx)
            ttl.text = ClipFold.title(store.pinnedView("").size + store.recentView("").size, foldOn(), toolsFold, fChip, ClipFilter.label(fChip))   // v0.43.0: switch OFF = ClipCopy.title exactly
            if (foldOn()) { ttl.isClickable = true; ttl.contentDescription = if (toolsFold) "Show filters" else "Hide filters"; ttl.setOnClickListener { toggleFold(ClipFold.KEY_TOOLS) } }   // v0.43.0
            ttl.textSize = fs(14f)
            ttl.setTextColor(ink())
            ttl.setTypeface(ttl.typeface, android.graphics.Typeface.BOLD)
            ttl.gravity = Gravity.CENTER_VERTICAL
            ttl.setPadding(dp(8), 0, dp(4), 0)
            ttl.maxLines = 1
            ttl.ellipsize = TextUtils.TruncateAt.END
            ttl.layoutParams = LinearLayout.LayoutParams(0, dp(sz(tTop)), 2.2f)
            top.addView(ttl)
        }
        val searchBtn = btn(if (searching) "✕ Search" else if (slim) "🔍" else "🔍 Search", ClipCompact.weight(ClipCompact.SEARCH, cp, slim), tTop) {
            searching = !searching
            query = ""
            confirmClear = false
            ovOpen = false
            refreshTop()
        }
        if (slim) JabraIcons.putThemed(ctx, searchBtn, "search")   // v0.18.7: only the icon-only (slim) button; with words (select mode / searching) Glyphs.dress already drew the icon, a 2nd one doubled it and hid "Search"
        if (slim && mockOn()) ClipMock.flat(ctx, pf, searchBtn)   // v0.53.1 (CM2): mock icon button = no box
        top.addView(searchBtn)
        if (!selOn) { val addB = btn("Add", 1.0f, tTop) { addFromField() }; top.addView(if (nlOn) ClipBar.on(ctx, pf, addB) else addB) }   // v0.17.58 (C3): Add = the one filled (primary) button
        val manageNew = btn(if (selOn) doneLbl() else if (slim) (if (ovOpen) "\u25B4" else "\u22EF") else "Manage", ClipCompact.weight(ClipCompact.DONE, cp, slim), tTop) {
            if (slim) { if (popOn() && popHeader(it, false)) return@btn; ovOpen = !ovOpen; confirmClear = false; refresh(); return@btn }   // v0.35.1 (CC3-B): popover first, old tray = fallback   // v0.17.58 (C3): \u22EF = overflow tray
            if (selOn) { sel.leave(); moreOpen = false } else sel.enter()
            confirmClear = false
            confirmDel = false
            refresh()
        }
        if (slim && !selOn && mockOn()) ClipMock.flat(ctx, pf, manageNew)   // v0.53.1 (CM2): the \u22EF button = no box
        if (selOn) ClipBar.on(ctx, pf, manageNew)   // v0.17.30 (UI6): select mode is ON = soft blue
        top.addView(manageNew)
        root.addView(top)

        // ---- v0.19.1 (dropdown plan S2): pref ui_pill (default ON) = filter / sort / view are 3 dropdown pills in ONE row (instead of the 5 chips + Sort / View rows in the tray); OFF = the old chips
        val mk = mockOn()   // v0.50.0 (CM1)
        val pillOn = nlOn && !simple() && !mk && try { pf.bool("ui_pill", true) } catch (_: Throwable) { true }   // v0.50.0 (CM1): mock tabs replace the pills   // v0.19.8: simple look = one flat chip row instead of the 3 dropdown pills
        // v0.34.0 (CC2): which header rows exist + does Undo live in the filter row (pref clip_compact2; OFF = the v0.33.0 rows)
        val c2 = compact2On()
        val filterShown = !(selOn && moreOpen) && (pillOn || !(ovOpen && nlOn)) && !toolsFold   // v0.43.0: && !toolsFold
        val rowPlan = ClipCompact.rowPlan(c2, filterShown, selOn, undoAvail(), msg.isNotEmpty(), confirmClear, searching)
        val undoIn = ClipCompact.undoInFilter(rowPlan)
        if (pillOn && !(selOn && moreOpen) && !toolsFold) {   // v0.43.0: && !toolsFold
            val prow = row()
            try {
                val fb: (View) -> Unit = { v -> try { host.feedback(v) } catch (_: Throwable) { } }
                val psp = fs(12f)
                val fNames = Array(ClipFilter.COUNT) { c -> ClipFilter.label(c) }
                prow.addView(UiKit.pillSelect(ctx, pf, "Filter", { fNames }, { fChip }, sz(t40), 1f, fb, psp) { c ->
                    if (c != fChip) {
                        fChip = c
                        if (sel.isOn) sel.clear()   // a hidden selection could be pinned / deleted by mistake
                        confirmDel = false
                        confirmClear = false
                        refreshTop()
                    }
                })
                if (!selOn) {   // no Sort / View in select mode (same as before)
                    val sNames = com.mgb.keyboard.clip.ClipMeta.SORT_NAMES
                    prow.addView(UiKit.pillSelect(ctx, pf, "Sort", { sNames }, { try { JabraPrefs(ctx).int("clip_sort", 0).coerceIn(0, 3) } catch (_: Throwable) { 0 } }, sz(t40), 1f, fb, psp) { i ->
                        try { JabraPrefs(ctx).setInt("clip_sort", i) } catch (_: Throwable) { }
                        confirmClear = false
                        refreshTop()
                    })
                    val vNames = arrayOf("List", "Cards")
                    prow.addView(UiKit.pillSelect(ctx, pf, "View", { vNames }, { try { if (JabraPrefs(ctx).int("clip_view", 0) == 1) 1 else 0 } catch (_: Throwable) { 0 } }, sz(t40), 1f, fb, psp) { i ->
                        try { JabraPrefs(ctx).setInt("clip_view", i) } catch (_: Throwable) { }
                        confirmClear = false
                        refreshTop()
                    })
                }
            } catch (_: Throwable) { }
            if (undoIn) undoIconBtn(t40)?.let { prow.addView(it) }   // v0.34.0 (CC2)
            root.addView(prow)
        }
        // ---- v0.50.0 (CM1): underline tabs (All 12 | Pinned 3 | ...) instead of the pills / chips; Undo icon at the end
        var tabsDone = false
        if (mk && !(selOn && moreOpen) && !(ovOpen && nlOn) && !toolsFold) {
            try {
                val allClips = store.pinnedView("") + store.recentView("")
                val names = List(ClipFilter.COUNT) { c -> ClipFilter.label(c) }
                val cnts = List(ClipFilter.COUNT) { c -> ClipFilter.filter(allClips, c).size }
                root.addView(ClipMock.tabs(ctx, pf, names, cnts, fChip, fs(13f), sz(32), ink(), dim(), { c ->
                    if (c != fChip) {
                        fChip = c
                        if (sel.isOn) sel.clear()
                        confirmDel = false
                        confirmClear = false
                        refreshTop()
                    }
                }, if (undoIn) undoIconBtn(t40) else null))
                tabsDone = true
            } catch (_: Throwable) { tabsDone = false }
        }
        // ---- filter chips (hidden while the More tray is open: the space goes to the list)
        if (!pillOn && !tabsDone && !(selOn && moreOpen) && !(ovOpen && nlOn) && !toolsFold) {   // v0.43.0: && !toolsFold   // v0.18.8: also hide the chips while the \u22EF menu is open (the space goes to the list)
            val chips = row()
            for (c in 0 until ClipFilter.COUNT) {
                val ch = UiKit.chip(ctx, pf, ClipFilter.label(c), c == fChip, sz(t40), { v -> try { host.feedback(v) } catch (_: Throwable) { } }) {
                    if (c != fChip) {
                        fChip = c
                        if (sel.isOn) sel.clear()   // a hidden selection could be pinned / deleted by mistake
                        confirmDel = false
                        confirmClear = false
                        refreshTop()
                    }
                }
                ch.setPadding(dp(2), 0, dp(2), 0)
                if (simple() && c != fChip) { try { ch.background = null; ch.backgroundTintList = null; ch.stateListAnimator = null; ch.setTextColor(dim()) } catch (_: Throwable) { } }   // v0.19.8: only the chosen filter has a background
                fontFit(ch)   // v0.17.32 (UI8)
                ch.maxLines = 1
                ch.layoutParams = LinearLayout.LayoutParams(0, dp(sz(t40)), 1f).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
                chips.addView(ch)
            }
            if (undoIn) undoIconBtn(t40)?.let { chips.addView(it) }   // v0.34.0 (CC2)
            root.addView(chips)
        }

        // ---- info line: message | Undo | Sort | View | Clear (not while searching; no Sort / View / Clear in select mode)
        val sortLv = try { JabraPrefs(ctx).int("clip_sort", 0).coerceIn(0, 3) } catch (_: Throwable) { 0 }
        val cardsOn = try { JabraPrefs(ctx).int("clip_view", 0) == 1 } catch (_: Throwable) { false }
        if (!searching) {
            val shownMsg = msg
            msg = ""
            val head = row()
            head.gravity = Gravity.CENTER_VERTICAL
            val idleSel = if (c2) !ClipCompact.hasInfo(rowPlan) else selOn && shownMsg.isEmpty() && !confirmClear && !undoAvail()   // v0.18.7: select mode with nothing to say = no empty band (~38 dp) above the list; v0.34.0 (CC2): the plan decides when the pref is ON
            if (tidy() && !idleSel && !c2) head.minimumHeight = dp(sz(34) + 4)   // v0.18.4: the line keeps its height with or without Undo, so the list never jumps
            val m = TextView(ctx)
            m.text = if (confirmClear) "Delete all unpinned clips?" else if (shownMsg.isEmpty() && nlOn && !selOn && !c2) ClipCopy.countLine(store.pinnedView("").size + store.recentView("").size, store.pinnedCount()) else shownMsg   // v0.17.58 (C3): quiet count line
            m.textSize = 12f
            m.setTextColor(dim())
            m.setPadding(dp(8), 0, dp(4), 0)
            m.maxLines = 1
            m.ellipsize = TextUtils.TruncateAt.END
            m.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            head.addView(m)
            fun small(label: String, onClick: (Button) -> Unit): Button {
                val b = btn(label, 0f, 34, onClick)
                b.textSize = fs(12f)
                b.setPadding(dp(10), 0, dp(10), 0)
                return ClipBar.pill(pf, b)   // v0.17.30 (UI6): capsule like the space bar
            }
            if (confirmClear) {
                head.addView(small("Cancel") { confirmClear = false; refresh() })
                head.addView(ClipBar.danger(pf, small("Yes, clear") { keepUndo(store.recentView("")); store.clearUnpinned(); try { ClipImages.clear(ctx) } catch (_: Throwable) { }; confirmClear = false; refresh() }))   // v0.17.30 (UI6)
            } else {
                if (undoAvail() && !undoIn) head.addView(small("↶ Undo") { doUndo() })   // v0.18.4; v0.34.0 (CC2): not when the icon in the filter row has it
                if (!selOn && !nlOn) {   // v0.17.58 (C3): new look moves Sort / View / Clear into the overflow tray
                    head.addView(small("\u2195 " + arrayOf("Newest", "Oldest", "A-Z", "Used")[sortLv]) {
                        try { JabraPrefs(ctx).setInt("clip_sort", (sortLv + 1) % 4) } catch (_: Throwable) { }
                        confirmClear = false
                        refreshTop()
                    })
                    head.addView(small(if (cardsOn) "View: Cards" else "View: List") {
                        try { JabraPrefs(ctx).setInt("clip_view", if (cardsOn) 0 else 1) } catch (_: Throwable) { }
                        confirmClear = false
                        refreshTop()
                    })
                    head.addView(ClipBar.danger(pf, small("Clear") { confirmClear = true; refresh() }))   // v0.17.30 (UI6)
                }
            }
            if (!idleSel) root.addView(head)   // v0.18.7: nothing to show in select mode = no empty line at all
            if (nlOn && !selOn && ovOpen && !confirmClear) {   // v0.17.58 (C3): overflow tray = every rare action in one place
                // v0.17.66 -> v0.17.67 (R7): the "..." tray is now a dropdown menu card: Sort + View show ALL choices (the chosen one is ticked), actions below
                val menu = menuCard()
                val ovCols = maxOf(5, com.mgb.keyboard.clip.ClipTrash.QUICK.size)   // v0.18.8: EVERY row of this menu uses the same 5 columns, so the pills line up (Undo has 5, the others 4 / 2)
                if (!pillOn) {   // v0.19.1: with the dropdown pills Sort and View live in the header row, not in this tray
                val sortNames = arrayOf("Newest", "Oldest", "A-Z", "Used")
                menu.addView(menuRow("Sort", ovCols, *Array<View>(4) { i ->
                    ovPill((if (i == sortLv) "\u2713 " else "") + sortNames[i], i == sortLv) {
                        try { JabraPrefs(ctx).setInt("clip_sort", i) } catch (_: Throwable) { }
                        refreshTop()
                    }
                }))
                menu.addView(menuRow("View", ovCols,
                    ovPill((if (!cardsOn) "\u2713 " else "") + "List", !cardsOn) { try { JabraPrefs(ctx).setInt("clip_view", 0) } catch (_: Throwable) { }; refreshTop() },
                    ovPill((if (cardsOn) "\u2713 " else "") + "Cards", cardsOn) { try { JabraPrefs(ctx).setInt("clip_view", 1) } catch (_: Throwable) { }; refreshTop() }))
                }
                // v0.18.4: Undo = how many deleted clips are kept (Off / 10 / 30 / 50 / 100); Do = Manage | Deleted n | Export all | Clear
                val undoLim = try { trash.limit() } catch (_: Throwable) { com.mgb.keyboard.clip.ClipTrash.DEFAULT_LIMIT }
                val doItems = ArrayList<View>()
                doItems.add(ovPill("Manage") { ovOpen = false; sel.enter(); confirmClear = false; confirmDel = false; refresh() })
                if (undoLim > 0) {
                    val delN = try { trash.count() } catch (_: Throwable) { 0 }
                    doItems.add(ovPill(if (delN > 0) "Deleted " + delN else "Deleted") { ovOpen = false; trashOpen = true; trashSure = false; trashMsg = ""; confirmClear = false; refreshTop() })
                }
                doItems.add(ovPill("Export all") { ovOpen = false; expSel = emptyList(); expScope = 1; expMsg = ""; expName = ""; renaming = false; exporting = true; moreOpen = false; confirmDel = false; refresh() })
                doItems.add(ClipBar.danger(pf, ovPill("Clear") { ovOpen = false; confirmClear = true; refresh() }))
                if (pillOn) {
                    // v0.19.2 (S3): Undo = ONE dropdown pill (\"Undo 30 v\") + the 4 actions: ONE row (3 per row when the clip buttons are big), no row labels
                    var undoPill: View? = null
                    try {
                        val uVals = ArrayList<Int>()
                        for (q in com.mgb.keyboard.clip.ClipTrash.QUICK) uVals.add(q)
                        if (!uVals.contains(undoLim)) { uVals.add(undoLim); uVals.sort() }   // a value set in Settings (e.g. 20) stays visible + ticked
                        val uNames = Array(uVals.size) { i -> "Undo " + (if (uVals[i] == 0) "Off" else uVals[i].toString()) }
                        val ufb: (View) -> Unit = { v -> try { host.feedback(v) } catch (_: Throwable) { } }
                        undoPill = UiKit.pillSelect(ctx, pf, "Undo", { uNames },
                            { uVals.indexOf(try { trash.limit() } catch (_: Throwable) { undoLim }).coerceAtLeast(0) },
                            sz(34), 1f, ufb, fs(11f)) { i ->
                            try { JabraPrefs(ctx).setInt("clip_undo_n", uVals[i.coerceIn(0, uVals.size - 1)]) } catch (_: Throwable) { }
                            try { trash.count() } catch (_: Throwable) { }
                            refresh()
                        }
                    } catch (_: Throwable) { undoPill = null }
                    if (undoPill != null) {
                        doItems.add(0, undoPill)
                        val per = if (bpct() > 110) 3 else 5
                        var k = 0
                        while (k < doItems.size) { menu.addView(menuRow("", per, *doItems.subList(k, minOf(k + per, doItems.size)).toTypedArray())); k += per }
                    } else {
                        menu.addView(oldUndoRow(ovCols, undoLim))
                        menu.addView(menuRow("Do", ovCols, *doItems.toTypedArray()))
                    }
                } else {
                    menu.addView(oldUndoRow(ovCols, undoLim))
                    menu.addView(menuRow("Do", ovCols, *doItems.toTypedArray()))
                }
                root.addView(menu)
            }
        }

        if (searching) {
            val q = TextView(ctx)
            q.text = (if (pf.bool("icons_on", true)) "" else "🔍 ") + query + "▏"
            q.textSize = 15f
            q.setTextColor(try { JabraTokens.cardText(pf) } catch (_: Throwable) { ink() })
            try { q.background = UiKit.cardBackground(ctx, pf, false, false) } catch (_: Throwable) { }   // v0.18: rounded search box
            q.setPadding(dp(12), dp(6), dp(12), dp(6))
            try { q.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.setMargins(dp(4), dp(2), dp(4), dp(2)) } } catch (_: Throwable) { }
            root.addView(q)
        }

        // ---- list (scrolls; the top bar, chips and bottom bar stay)
        try { store.expireNow() } catch (_: Throwable) { }
        val pinnedItems = ClipFilter.filter(store.sorted(store.pinnedView(query), sortLv), fChip)
        val recentItems = ClipFilter.filter(store.sorted(store.recentView(query), sortLv), fChip)
        val visible = pinnedItems + recentItems
        if (ClipMockPlan.stripShown(mk, fChip, searching, selOn, pinnedItems.size)) {   // v0.50.0 (CM1): one-line strip of pinned clips (tap = paste, hold = select)
            try {
                root.addView(ClipMock.pins(ctx, pf, pinnedItems, fs(13f), sz(30), clipHoldMs(),
                    { c -> pasteItem(c, false) }, { c -> sel.enterWith(c); confirmDel = false; refresh() }, { v -> try { host.feedback(v) } catch (_: Throwable) { } }))
            } catch (_: Throwable) { }
        }
        val listH = if (fitOn()) 72 else if (searching) 88 else 176   // fitList() grows the 72 dp list to the letters keyboard height (N1)
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(listH))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        if (visible.isEmpty()) {
            val t = TextView(ctx)
            t.text = if (nlOn) ClipCopy.empty(fChip, searching, query) else ClipFilter.emptyText(fChip, searching)   // v0.17.58 (C3)
            if (nlOn) t.textSize = 14f
            t.setPadding(dp(16), dp(12), dp(16), dp(12))
            t.setTextColor(dim())
            t.gravity = Gravity.CENTER   // v0.18
            if (!searching) t.text = "\uD83D\uDCCB\n" + t.text
            col.addView(t)
            if (!searching && fChip == 0) col.addView(emptyHint())   // v0.17.42: how to use (first-time help)
        }
        val showHeaders = ClipOrder.showHeaders(searching, pinnedItems.size, recentItems.size) && !mk   // v0.50.0 (CM1): mock list = one flat list, no Pinned / Recent headers
        val ordMode = try { ClipOrder.sanitize(JabraPrefs(ctx).int("clip_order", 0)) } catch (_: Throwable) { 0 }
        val ordSwap: () -> Unit = { try { JabraPrefs(ctx).setInt("clip_order", ClipOrder.next(ordMode)) } catch (_: Throwable) { }; confirmClear = false; refreshTop() }
        val lines = try { JabraPrefs(ctx).int("clip_lines", 0).coerceIn(0, 2) } catch (_: Throwable) { 0 }
        val foldable = showHeaders && ClipFold.interactive(foldOn(), selOn, searching)   // v0.43.0: headers can fold (never while selecting / searching)
        val cards: View? = if (cardsOn && visible.isNotEmpty()) {
            ClipCards.build(ctx, host, pinnedItems, recentItems, !searching, lines,
                { it2 -> store.togglePin(it2); refresh() },
                { it2 -> keepUndo(listOf(it2)); store.remove(it2); if (nlOn) msg = ClipCopy.deleted(1); refresh() },
                SelectMode(selOn, { x -> sel.isSelected(x) }, { x -> sel.toggle(x); confirmDel = false; refresh() }, { x -> sel.enterWith(x); confirmDel = false; refresh() },
                    { x -> store.noteUse(x) }, { x -> infoLine(x) }, clipHoldMs(), { afterPaste(false) }, { x -> store.hasFmt(x.text) }, { x, b -> pasteItem(x, b) }), ClipOrder.recentFirst(ordMode), ordSwap,
                if (foldable) foldFn else null, if (foldable) foldTap else null)   // v0.43.0: chevron on the Pinned / Recent headers
        } else null
        if (cards != null) {
            col.addView(cards)
        } else {
            val ordLabels = ClipOrder.labels(ordMode)
            val ordGroups = ClipOrder.groups(pinnedItems, recentItems, ordMode)
            if (mk) {   // v0.50.0 (CM1): ONE flat list, newest copied first (pinned clips keep their gold bar); other Sort choices keep the sorted order
                val flat = pinnedItems + recentItems
                val idx = if (sortLv == 0) ClipMockPlan.order(LongArray(flat.size) { i -> store.madeOf(flat[i].text) }) else IntArray(flat.size) { i -> i }
                for (i in idx) col.addView(itemRow(flat[i]))
            } else
            for (gi in 0..1) {
                val folded = foldable && foldedOf(ordLabels[gi])   // v0.43.0
                if (showHeaders) {
                    var hv = sectionHeader(ordLabels[gi], gi == 0, ordSwap)
                    if (foldable) hv = ClipFoldHeader.wrap(ctx, hv, folded, ordGroups[gi].size, dim()) { toggleFold(ClipFold.keyFor(ordLabels[gi])) }   // v0.43.0
                    col.addView(hv)
                }
                if (!folded) for (entry in ordGroups[gi]) col.addView(itemRow(entry))
            }
        }
        sv.addView(col)
        val floatV: View? = if (floatBarOn()) floatBarV(visible) else null   // v0.37.0
        if (!selOn) floatShown = false
        if (floatV != null) { try { sv.clipToPadding = false; sv.setPadding(0, 0, 0, dp(ClipFloat.listPadDp(ClipCompact.barDp(PanelLayout.MIN_TOUCH, true)))) } catch (_: Throwable) { } }   // last clip can scroll above the pill
        if (floatV != null) { try { ClipFloatBar.followScroll(sv, floatV, dp(6), dp(10)) } catch (_: Throwable) { } }   // v0.37.2: hide while scrolling down
        root.addView(sv)
        try {   // same scroll keeping as X1
            curSv = sv
            if (com.mgb.keyboard.typing.ScrollKeep.wants(restoreY)) {
                keepY = -1
                sv.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        try {
                            if (sv.viewTreeObserver.isAlive) sv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            val ch = col.height
                            if (ch > 0) sv.scrollTo(0, com.mgb.keyboard.typing.ScrollKeep.clamp(restoreY, ch, sv.height))
                        } catch (_: Throwable) { }
                    }
                })
            }
        } catch (_: Throwable) { }

        // ---- bottom summary bar (select mode)
        if (selOn) root.addView(floatV ?: selectBarNew(visible, pillOn))   // v0.37.0: floating pill, else the slim bar

        // ---- tiny search keyboard (Latin letters only), same as the classic panel
        if (searching) {
            for (letters in arrayOf("qwertyuiop", "asdfghjkl", "zxcvbnm")) {
                val r = row()
                letters.forEach { ch -> r.addView(btn(ch.toString(), 1f, 34) { query += ch; confirmClear = false; refreshTop() }) }
                if (letters == "asdfghjkl") r.addView(JabraIcons.putThemed(ctx, btn("␣", 1f, 34) { query += " "; refreshTop() }, "space"))
                if (letters == "zxcvbnm") r.addView(JabraIcons.putThemed(ctx, btn("⌫", 1.5f, 34) { if (query.isNotEmpty()) query = query.dropLast(1); refreshTop() }, "back"))
                root.addView(r)
            }
        }
        fitList(root, sv)   // N1: the panel keeps the letters keyboard height
        return root
    }

    // ---- v0.18.4: Recently deleted screen (see ClipTrashUi.kt). Every action rebuilds the panel from the top; nothing here changes what the other screens do. ----
    private fun makeTrash(): View {
        val listPx = if (fitPx > 0) (fitPx - dp(8) - dp(PanelLayout.MIN_TOUCH * 2) - dp(sz(PanelLayout.MIN_TOUCH)) - dp(30)).coerceIn(dp(120), dp(440)) else dp(176)
        val shown = trashMsg
        trashMsg = ""
        val items = trash.list()
        if (items.isEmpty()) trashSure = false
        return ClipTrashUi.build(ctx, host, pf, items, trash.limit(), shown, trashSure, listPx, ClipTrashUi.Acts(
            { trashOpen = false; trashSure = false; refreshTop() },
            { e -> val n = trash.restore(listOf(e)); trashMsg = if (n > 0) "Restored 1 \u00B7 it is on top of Recent" else "Could not restore"; trashSure = false; refreshTop() },
            { val n = trash.restoreAll(); trashMsg = "Restored " + n; trashSure = false; refreshTop() },
            { if (trashSure) { trash.emptyAll(); trashMsg = "Emptied"; trashSure = false } else trashSure = true; refreshTop() },
            { trashSure = false; refreshTop() }
        ))
    }

    // ---- v0.17.67 (R7): dropdown menu pieces (a card, labelled rows with equal columns, pill buttons; the chosen choice is ticked + blue) ----
    private fun menuCard(): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(8), dp(4), dp(8), dp(4))
        try { if (nl()) c.background = UiKit.cardBackground(ctx, pf) } catch (_: Throwable) { }
        c.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.setMargins(dp(4), dp(2), dp(4), dp(2)) }
        return c
    }

    /** v0.19.2: the old Undo row (5 chips Off / 10 / 30 / 50 / 100) - used when the dropdown pills are off (or the pill cannot be built). Same code as v0.19.1. */
    private fun oldUndoRow(ovCols: Int, undoLim: Int): LinearLayout =
        menuRow("Undo", ovCols, *Array<View>(com.mgb.keyboard.clip.ClipTrash.QUICK.size) { i ->
            val qv = com.mgb.keyboard.clip.ClipTrash.QUICK[i]
            ovPill((if (qv == undoLim) "\u2713 " else "") + (if (qv == 0) "Off" else qv.toString()), qv == undoLim) {
                try { JabraPrefs(ctx).setInt("clip_undo_n", qv) } catch (_: Throwable) { }
                try { trash.count() } catch (_: Throwable) { }
                refresh()
            }
        })

    /** One menu row: small label + [cols] equal columns (missing items = empty space, so every row lines up). v0.19.2: an EMPTY label = no label column (more room for the pills). */
    private fun menuRow(label: String, cols: Int, vararg items: View): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        val l = TextView(ctx)
        l.text = label
        l.textSize = fs(11f)
        l.setTextColor(hush())
        l.maxLines = 1
        l.layoutParams = LinearLayout.LayoutParams(dp(50), LinearLayout.LayoutParams.WRAP_CONTENT)
        if (label.isNotEmpty()) r.addView(l)
        for (v in items) r.addView(v)
        for (i in items.size until cols) { val f = View(ctx); f.layoutParams = LinearLayout.LayoutParams(0, dp(sz(34)), 1f); r.addView(f) }
        return r
    }

    /** v0.18.8: pill for the \u22EF menu (5 equal columns): slightly smaller text so "Deleted 10" / "\u2713 Newest" fit on a 360 dp phone. */
    private fun ovPill(label: String, selected: Boolean = false, onClick: () -> Unit): Button {
        val b = menuPill(label, selected, onClick)
        try { b.textSize = fs(11f) } catch (_: Throwable) { }
        return b
    }

    private fun menuPill(label: String, selected: Boolean = false, onClick: () -> Unit): Button {
        val b = btn(label, 1f, 34) { onClick() }
        b.textSize = fs(12f)
        b.setPadding(dp(4), 0, dp(4), 0)
        b.maxLines = 1
        return if (selected) ClipBar.on(ctx, pf, b) else ClipBar.pill(pf, b)
    }

    /** v0.16.9 (X9): bottom bar of the NEW panel in select mode: "N selected" + Pin | Delete | Paste | More; the More tray holds All / Unpin / Merge / Edit / Paste in 1 line / To Vault (every classic action stays reachable). */
    /** v0.35.0 (CC3-A): slim bottom bar = Pin | Delete | [Paste | v] | More, icon + ONE word, 34 dp (via sz). Same actions as the old bar. Any problem = null (old bar). */
    private fun slimMain(n: Int, allPinned: Boolean, pillOn: Boolean): LinearLayout? {
        try {
            val h = sz(ClipCompact.barDp(PanelLayout.MIN_TOUCH, true))
            val fb: (View) -> Unit = { v -> try { host.feedback(v) } catch (_: Throwable) { } }
            val r = UiKit.row(ctx)
            val two = twoPaste()   // v0.43.0
            fun fmtBtn(w: Float): Button { val b = ClipSlimBar.btn(ctx, pf, ClipMultiPaste.FORMAT_SIGN, "paste", w, h, fb) { actPasteAs(true) }; try { b.contentDescription = "Paste with format" } catch (_: Throwable) { }; return b }
            fun plainBtn(w: Float): Button { val b = ClipSlimBar.btn(ctx, pf, ClipMultiPaste.PLAIN_SIGN, "paste", w, h, fb) { actPasteAs(false) }; try { b.contentDescription = "Paste plain text" } catch (_: Throwable) { }; return b }
            r.addView(ClipSlimBar.btn(ctx, pf, if (allPinned) "Unpin" else "Pin", "pin", 1f, h, fb) { actPin(!allPinned) })
            if (confirmDel) r.addView(ClipSlimBar.btn(ctx, pf, "Sure? delete $n", null, 3f, h, fb) { actDelete() })   // destructive step: wide, text only
            else r.addView(ClipSlimBar.btn(ctx, pf, "Delete", "trash", 1.2f, h, fb) { actDelete() })
            if (pillOn) {
                val split = LinearLayout(ctx)
                split.orientation = LinearLayout.HORIZONTAL
                split.gravity = Gravity.CENTER_VERTICAL
                split.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, if (two) 2.2f else 1.5f)
                if (two) { split.addView(fmtBtn(1f)); split.addView(plainBtn(0.9f)) }
                else split.addView(ClipSlimBar.btn(ctx, pf, "Paste", "paste", 1f, h, fb) { actPaste(false) })
                val chev = btn("\u25BE", 0f, ClipCompact.barDp(PanelLayout.MIN_TOUCH, true)) { pasteMenu = !pasteMenu; moreOpen = false; confirmDel = false; refresh() }
                chev.layoutParams = LinearLayout.LayoutParams(dp(30), dp(h)).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
                chev.contentDescription = "More paste options"
                split.addView(if (pasteMenu) ClipBar.on(ctx, pf, chev) else chev)
                r.addView(split)
            } else if (two) { r.addView(fmtBtn(0.95f)); r.addView(plainBtn(0.85f)) }
            else r.addView(ClipSlimBar.btn(ctx, pf, "Paste", "paste", 1.1f, h, fb) { actPaste(false) })
            r.addView(ClipSlimBar.btn(ctx, pf, if (moreOpen) "Less" else "More", "more", 1.1f, h, fb) { moreToggle() })
            return r
        } catch (_: Throwable) { return null }
    }

    private fun selectBarNew(visible: List<ClipList.Item>, pillOn: Boolean = false): View {
        popVisible = visible   // v0.35.1 (CC3-B)
        val t40 = PanelLayout.MIN_TOUCH   // btn() scales it (v0.17.32 UI8)
        val all = LinearLayout(ctx)
        all.orientation = LinearLayout.VERTICAL
        if (moreOpen && pillOn) {   // v0.19.2 (S3): ONE row (All | None | Edit | Merge | Unpin; 3 per row when the clip buttons are big). Send moved to the Paste split button.
            val menu = menuCard()
            val items = listOf<View>(
                menuPill("All") { sel.selectAll(visible); confirmDel = false; refresh() },
                menuPill("None") { sel.clear(); confirmDel = false; refresh() },
                menuPill("Recent") { sel.selectOnly(visible, false); confirmDel = false; refresh() },   // v0.41.1
                menuPill("Pinned") { sel.selectOnly(visible, true); confirmDel = false; refresh() },   // v0.41.1
                menuPill("Edit") { actEdit() },
                menuPill("Replace") { actReplaceIn() },   // v0.29.0
                menuPill("Merge") { actMerge() },
                menuPill("Unpin") { actPin(false) })
            val per = if (bpct() > 110) 3 else 5
            var k = 0
            while (k < items.size) { menu.addView(menuRow("", per, *items.subList(k, minOf(k + per, items.size)).toTypedArray())); k += per }
            all.addView(menu)
        } else if (moreOpen) {   // v0.17.67 (R7): grouped dropdown menu (same actions as before + "None")
            val menu = menuCard()
            menu.addView(menuRow("Select", 3, menuPill("All") { sel.selectAll(visible); confirmDel = false; refresh() }, menuPill("None") { sel.clear(); confirmDel = false; refresh() }))
            menu.addView(menuRow("", 3, menuPill("Recent") { sel.selectOnly(visible, false); confirmDel = false; refresh() }, menuPill("Pinned") { sel.selectOnly(visible, true); confirmDel = false; refresh() }))   // v0.41.1
            menu.addView(menuRow("Change", 3, menuPill("Edit") { actEdit() }, menuPill("Merge") { actMerge() }, menuPill("Unpin") { actPin(false) }))
            val send = ArrayList<View>()
            send.add(menuPill("Paste 1 line") { actPaste(true) })
            if (toVault != null) send.add(menuPill("To Vault") { actToVault() })
            send.add(menuPill("Export") { actExport() })   // v0.17.57 (C2)
            send.add(menuPill("Replace") { actReplaceIn() })   // v0.29.0: Find & Replace inside ONE clip
            menu.addView(menuRow("Send", 3, *send.toTypedArray()))
            all.addView(menu)
        }
        val n = sel.count()
        val allPinned = n > 0 && sel.pinnedCount() == n
        if (!pillOn) pasteMenu = false
        if (pasteMenu && !moreOpen && pillOn) {   // v0.19.2 (S3): the chevron tray of the split Paste button (same actions as the old More > Send row)
            val menu = menuCard()
            val send = ArrayList<View>()
            send.add(menuPill("Paste 1 line") { actPaste(true) })
            if (toVault != null) send.add(menuPill("To Vault") { actToVault() })
            send.add(menuPill("Export") { actExport() })
            menu.addView(menuRow("", 3, *send.toTypedArray()))
            all.addView(menu)
        }
        val slimMainV = if (slimBarOn()) slimMain(n, allPinned, pillOn) else null   // v0.35.0 (CC3-A); null = old bar
        val main = slimMainV ?: if (!pillOn) PanelKit.stickyBar(ctx, pf, host, listOf<Pair<String, () -> Unit>>(
            (if (allPinned) "Unpin" else "Pin") to { actPin(!allPinned) },
            (if (confirmDel) "Sure? delete $n" else "Delete") to { actDelete() },
            "Paste" to { actPaste(false) },
            (if (moreOpen) "Less \u25B4" else "More \u25BE") to { moreToggle() }),
            listOf(1f, if (confirmDel) 3f else 1.2f, 1.1f, 1.1f))
        else {
            // v0.19.2 (S3): Pin | Delete | [Paste | v] (split button) | More. Tap Paste = paste at once; the chevron opens Paste 1 line / To Vault / Export.
            val m = PanelKit.stickyBar(ctx, pf, host, listOf<Pair<String, () -> Unit>>(
                (if (allPinned) "Unpin" else "Pin") to { actPin(!allPinned) },
                (if (confirmDel) "Sure? delete $n" else "Delete") to { actDelete() },
                (if (moreOpen) "Less \u25B4" else "More \u25BE") to { moreToggle() }),
                listOf(1f, if (confirmDel) 3f else 1.2f, 1.1f))
            try {
                val split = LinearLayout(ctx)
                split.orientation = LinearLayout.HORIZONTAL
                split.gravity = Gravity.CENTER_VERTICAL
                split.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
                split.addView(btn("Paste", 1f, t40) { actPaste(false) })
                val chev = btn("\u25BE", 0f, t40) { pasteMenu = !pasteMenu; moreOpen = false; confirmDel = false; refresh() }
                chev.layoutParams = LinearLayout.LayoutParams(dp(30), dp(sz(t40))).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
                chev.contentDescription = "More paste options"
                split.addView(if (pasteMenu) ClipBar.on(ctx, pf, chev) else chev)
                m.addView(split, minOf(2, m.childCount))
            } catch (_: Throwable) {
                m.addView(btn("Paste", 1.1f, t40) { actPaste(false) }, minOf(2, m.childCount))   // never lose Paste
            }
            m
        }
        if (!confirmDel && slimMainV == null) {
            val c = TextView(ctx)
            c.text = ClipFilter.selectedText(n)
            c.textSize = fs(12f)
            c.setTextColor(ink())
            c.setTypeface(c.typeface, android.graphics.Typeface.BOLD)
            c.setPadding(dp(8), 0, dp(4), 0)
            c.maxLines = 2   // v0.18.7: was 1 (cut as "Tap clips to sele..."); a long text now wraps to 2 lines
            c.ellipsize = TextUtils.TruncateAt.END
            c.gravity = Gravity.CENTER_VERTICAL
            c.layoutParams = LinearLayout.LayoutParams(0, dp(sz(t40)), 1.4f)
            main.addView(c, 0)
        }
        barAnchor = main   // v0.35.1 (CC3-B): the popover opens from the bar
        all.addView(main)
        return all
    }

    // v0.16.9: actions of the NEW bottom bar (same behaviour and messages as the classic selectBar(), which is untouched)
    private fun actPin(pin: Boolean) {
        val chosen = sel.selection()
        if (chosen.isEmpty()) msg = "Nothing selected"
        else if (pin) {
            val want = chosen.count { !it.pinned }
            val n = store.setPinned(chosen, true)
            msg = if (n < want) "Pinned " + n + " of " + want + " (limit " + ClipList.MAX_PINNED + ")" else "Pinned " + n
        } else msg = "Unpinned " + store.setPinned(chosen, false)
        confirmDel = false
        refresh()
    }

    private fun actDelete() {
        val n = sel.count()
        if (n == 0) { msg = "Nothing selected"; confirmDel = false }
        else if (nl() && !confirmDel && ClipCopy.quickDelete(n)) {   // v0.17.58 (C4): 1-2 clips = gone at once + Undo bar (no "Sure?")
            keepUndo(sel.selection())
            val gone = store.removeAll(sel.selection())
            sel.clear()
            msg = ClipCopy.deleted(gone)
            confirmDel = false
        }
        else if (confirmDel) {
            keepUndo(sel.selection())
            val gone = store.removeAll(sel.selection())
            sel.clear()
            msg = "Deleted " + gone
            confirmDel = false
        } else confirmDel = true
        refresh()
    }

    private fun actPaste(oneLine: Boolean) {
        val chosen = sel.selection()
        if (chosen.isEmpty()) { msg = "Nothing selected"; confirmDel = false; refresh(); return }
        val text = if (oneLine) chosen.joinToString(" ") { it.text.replace(Regex("\\s*\\n\\s*"), " ").trim() } else chosen.joinToString("\n\n") { it.text }
        sel.leave(); confirmDel = false; moreOpen = false
        if (oneLine) pasteClip(text, null, true) else pasteChosen(chosen, text)   // v0.17.37 (M4); v0.17.41 (M8): one clip = with format
    }

    /** v0.43.0: select-mode paste with an explicit choice: Aa = WITH format (all selected clips as one html, a clip without format stays plain inside it), T = plain text (the old joined text). Any problem with the format = plain paste. */
    private fun actPasteAs(format: Boolean) {
        val chosen = sel.selection()
        if (chosen.isEmpty()) { msg = "Nothing selected"; confirmDel = false; refresh(); return }
        val text = chosen.joinToString("\n\n") { it.text }
        sel.leave(); confirmDel = false; moreOpen = false
        if (!format) { pasteClip(text, null, true, com.mgb.keyboard.typing.ClipPasteMode.PLAIN); return }
        val html = try { ClipMultiPaste.combine(chosen.map { it.text }, chosen.map { store.fmtOf(it.text) }) } catch (_: Throwable) { null }
        var done = false
        if (html != null) done = try { host.pasteFormatted(text, html) } catch (_: Throwable) { false }
        if (!done) { host.commitWord(); host.typeText(text) }   // the same plain fallback as pasteClip
        if (chosen.size == 1) store.noteUse(chosen[0])
        afterPaste(true)
    }

    private fun actMerge() {
        val chosen = sel.selection()
        if (chosen.size < 2) msg = "Select 2 or more"
        else when (store.addChecked(chosen.joinToString("\n\n") { it.text })) {
            0 -> { msg = "Merged " + chosen.size + " into 1 (see Recent)"; sel.leave() }
            2 -> msg = "Too long (max " + ClipList.MAX_LEN + ")"
            else -> msg = "Nothing to merge"
        }
        confirmDel = false
        refresh()
    }

    /** v0.29.0: Find & Replace inside ONE selected clip (hook = toReplace, set by MgbIme). */
    var toReplace: ((ClipList.Item) -> Unit)? = null
    private fun actReplaceIn() {
        val chosen = sel.selection()
        if (chosen.size != 1) { msg = "Select ONE clip to replace in"; confirmDel = false; refresh(); return }
        moreOpen = false; confirmDel = false
        try { toReplace?.invoke(chosen[0]) } catch (_: Throwable) { msg = "Could not open Replace"; refresh() }
    }

    private fun actEdit() {
        val chosen = sel.selection()
        if (chosen.size != 1) msg = "Select ONE clip to edit"
        else { editing = chosen[0]; editText = chosen[0].text; editPos = editText.length; editCaps = false; confirmSave = false }
        confirmDel = false
        refresh()
    }

    // v0.17.57 (C2): Export (More tray in select mode). Selected clips if any, else the "All" scope.
    private fun actExport() {
        val chosen = sel.selection()
        expSel = chosen
        expScope = if (chosen.isEmpty()) 1 else 0
        expMsg = ""; expName = ""; renaming = false   // v0.17.63 (R3)
        exporting = true; moreOpen = false; confirmDel = false
        refresh()
    }

    private fun expOrder(): List<ClipList.Item> = store.pinnedView("") + store.recentView("")

    /** The clips of the chosen scope, in panel order (pinned first), as export entries. */
    private fun expSource(): List<ClipList.Item> = when (expScope) {
        0 -> { val o = expOrder(); expSel.sortedBy { x -> o.indexOfFirst { it === x }.let { if (it < 0) Int.MAX_VALUE else it } } }
        1 -> expOrder()
        else -> store.pinnedView("")
    }

    private fun expEntries(skip: Boolean, skipped: IntArray): List<ClipExport.Entry> =
        ClipExport.prepare(
            expSource().map { ClipExport.Entry(it.text, store.fmtOf(it.text), it.pinned, store.madeOf(it.text)) }, skip, skipped)

    private fun doExport(docx: Boolean, save: Boolean) {
        val pr = JabraPrefs(ctx)
        val o = ClipExport.Options()
        o.details = pr.bool("clip_exp_details", true); o.format = pr.bool("clip_exp_fmt", true); o.skipCode = pr.bool("clip_exp_skip", true)
        o.now = System.currentTimeMillis()
        val list = expEntries(o.skipCode, IntArray(1))
        if (list.isEmpty()) { expMsg = "Nothing to export"; refresh(); return }
        val zip = expZip(list.size)   // v0.17.63 (R3): ZIP = one file per clip
        val tar = expTar(list.size)   // v0.17.70 (S3): TAR = one file per clip
        val bytes = if (zip) ClipName.toZip(list, docx, o) else if (tar) ClipName.toTar(list, docx, o) else if (docx) ClipExport.toDocx(list, o) else ClipExport.toTxtBytes(list, o)
        if (bytes == null) { expMsg = "Could not build the file"; refresh(); return }
        val ext = if (zip) "zip" else if (tar) "tar" else if (docx) "docx" else "txt"
        val auto = ClipName.autoBase(list, o.now, o.tz)
        val name = ClipName.withExt(if (expName.isNotEmpty()) expName else auto, ext, auto)
        val mime = if (zip) ClipName.MIME_ZIP else if (tar) ClipName.MIME_TAR else if (docx) ClipExport.MIME_DOCX else ClipExport.MIME_TXT
        expMsg = if (save) ClipExporter.saveToDownloads(ctx, name, mime, bytes) else ClipExporter.share(ctx, name, mime, bytes)
        refresh()
    }

    /** v0.17.57 (C2): export screen: scope chips, option chips, summary, Share / Save buttons. */
    /** v0.17.58 (C4): the info line of the Edit / Export screens as a rounded card (new look only). Look only. */
    private fun cardInfo(t: TextView) {
        try {
            t.background = UiKit.cardBackground(ctx, pf)
            t.setPadding(dp(12), dp(8), dp(12), dp(8))
            t.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) }
        } catch (_: Throwable) { }
    }

    private fun makeExport(): View {
        val pr = JabraPrefs(ctx)
        val t40 = PanelLayout.MIN_TOUCH
        val outer = LinearLayout(ctx)
        outer.orientation = LinearLayout.VERTICAL
        paintBg(outer)
        outer.setPadding(dp(2), dp(4), dp(2), dp(4))
        fun fb(v: View) { try { host.feedback(v) } catch (_: Throwable) { } }

        val top = row()
        top.addView(btn("\u2039 Back", 1f, t40) { exporting = false; expMsg = ""; refresh() })
        val title = TextView(ctx)
        title.text = "Export clips"
        title.textSize = fs(14f)
        title.setTextColor(ink())
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        title.gravity = Gravity.CENTER_VERTICAL
        title.setPadding(dp(10), 0, dp(4), 0)
        title.layoutParams = LinearLayout.LayoutParams(0, dp(sz(t40)), 2f)
        top.addView(title)
        outer.addView(top)

        val nAll = expOrder().size
        val nPin = store.pinnedView("").size
        val pillOn = nl() && try { pf.bool("ui_pill", true) } catch (_: Throwable) { true }   // v0.19.3 (dropdown plan S4)
        try { UiKit.closeSelect() } catch (_: Throwable) { }
        val labels = arrayOf(/* v0.18.9: the chosen chip of every Export row now shows a tick */ "Selected (" + expSel.size + ")", "All ($nAll)", "Pinned ($nPin)")
        var pillsDone = false
        if (pillOn) {   // v0.19.3 (S4): Scope v + Pack v = ONE row of dropdown pills (Pack only when there is more than one clip). Any problem = the old chips below.
            try {
                val optS0 = pr.bool("clip_exp_skip", true)
                val n0 = expEntries(optS0, IntArray(1)).size
                val prow = row()
                prow.addView(UiKit.pillSelect(ctx, pf, "Scope", { labels }, { expScope.coerceIn(0, 2) }, sz(t40), 1f, { v -> fb(v) }, fs(12f)) { i -> expScope = i.coerceIn(0, 2); expMsg = ""; refresh() })
                if (n0 > 1) {
                    val pNames = arrayOf("One file", "ZIP (file per clip)", "TAR")
                    prow.addView(UiKit.pillSelect(ctx, pf, "Pack", { pNames },
                        { val z = pr.bool("clip_exp_zip", false); val t = pr.bool("clip_exp_tar", false) && !z; if (z) 1 else if (t) 2 else 0 },
                        sz(t40), 1f, { v -> fb(v) }, fs(12f)) { i ->
                        pr.setBool("clip_exp_zip", i == 1); pr.setBool("clip_exp_tar", i == 2); expMsg = ""; refresh()
                    })
                }
                outer.addView(prow)
                pillsDone = true
            } catch (_: Throwable) { pillsDone = false }
        }
        if (!pillsDone) {
        val scopes = row()
        for (i in 0 until 3) scopes.addView(UiKit.chip(ctx, pf, (if (i == expScope) "\u2713 " else "") + labels[i], i == expScope, sz(t40), { v -> fb(v) }) { expScope = i; expMsg = ""; refresh() })
        outer.addView(scopes)
        }

        val optD = pr.bool("clip_exp_details", true); val optF = pr.bool("clip_exp_fmt", true); val optS = pr.bool("clip_exp_skip", true)
        val opts = row()
        opts.addView(UiKit.chip(ctx, pf, if (optD) "\u2713 Details" else "Details", optD, sz(t40), { v -> fb(v) }) { pr.setBool("clip_exp_details", !optD); expMsg = ""; refresh() })
        opts.addView(UiKit.chip(ctx, pf, if (optF) "\u2713 Format" else "Format", optF, sz(t40), { v -> fb(v) }) { pr.setBool("clip_exp_fmt", !optF); expMsg = ""; refresh() })
        opts.addView(UiKit.chip(ctx, pf, if (optS) "\u2713 Skip codes" else "Skip codes", optS, sz(t40), { v -> fb(v) }) { pr.setBool("clip_exp_skip", !optS); expMsg = ""; refresh() })
        outer.addView(opts)

        val skipped = IntArray(1)
        val es = expEntries(optS, skipped)
        val zipOn = expZip(es.size)   // v0.17.63 (R3)
        val tarOn = expTar(es.size)   // v0.17.70 (S3)
        val pk3 = if (zipOn) "ZIP" else if (tarOn) "TAR" else ""
        if (es.size > 1 && !pillsDone) {   // v0.17.63 (R3): one file with all clips, or a ZIP with one file per clip
            val pk = row()
            val packZip = pr.bool("clip_exp_zip", false); val packTar = pr.bool("clip_exp_tar", false) && !packZip
            pk.addView(UiKit.chip(ctx, pf, (if (!packZip && !packTar) "\u2713 " else "") + "One file", !packZip && !packTar, sz(t40), { v -> fb(v) }) { pr.setBool("clip_exp_zip", false); pr.setBool("clip_exp_tar", false); expMsg = ""; refresh() })
            pk.addView(UiKit.chip(ctx, pf, (if (packZip) "\u2713 " else "") + "ZIP (file per clip)", packZip, sz(t40), { v -> fb(v) }) { pr.setBool("clip_exp_zip", true); pr.setBool("clip_exp_tar", false); expMsg = ""; refresh() })
            pk.addView(UiKit.chip(ctx, pf, (if (packTar) "\u2713 " else "") + "TAR", packTar, sz(t40), { v -> fb(v) }) { pr.setBool("clip_exp_zip", false); pr.setBool("clip_exp_tar", true); expMsg = ""; refresh() })
            outer.addView(pk)
        }
        val withFmt = if (optF) es.count { it.html != null && it.html.isNotEmpty() } else 0
        val info = TextView(ctx)
        info.text = if (expMsg.isNotEmpty()) expMsg else (es.size.toString() + (if (es.size == 1) " clip" else " clips") +
            (if (withFmt > 0) "  \u2022  $withFmt with format (DOCX)" else "") + (if (skipped[0] > 0) "  \u2022  ${skipped[0]} code-like left out" else ""))
        info.textSize = fs(12f)
        info.setTextColor(if (expMsg.isNotEmpty()) ink() else dim())
        info.setPadding(dp(8), dp(4), dp(8), dp(2))
        if (nl()) cardInfo(info)   // v0.17.58 (C4): same card look as the clips
        outer.addView(info)
        val hint = TextView(ctx)
        hint.text = if (zipOn || tarOn) "$pk3 has one file per clip, named from its first words (01_..., 02_...).  TXT = plain text, DOCX = Word file." else "TXT = plain text.  DOCX = Word file; keeps bold / italic / lists when the clip has format."
        hint.textSize = fs(11f)
        hint.setTextColor(hush())
        hint.setPadding(dp(8), 0, dp(8), dp(4))
        outer.addView(hint)

        // v0.17.63 (R3): file / zip name, rename before export
        val auto = try { ClipName.autoBase(es, System.currentTimeMillis(), java.util.TimeZone.getDefault()) } catch (_: Throwable) { "Jabra-Clips" }
        val nameRow = row()
        val nameTv = TextView(ctx)
        nameTv.text = (if (pk3.isNotEmpty()) "$pk3 name: " else "File name: ") + (if (expName.isNotEmpty()) expName else auto) + (if (zipOn) ".zip" else if (tarOn) ".tar" else ".txt / .docx")
        nameTv.textSize = fs(12f)
        nameTv.setTextColor(ink())
        nameTv.gravity = Gravity.CENTER_VERTICAL
        nameTv.setPadding(dp(8), 0, dp(4), 0)
        nameTv.maxLines = 2
        nameTv.layoutParams = LinearLayout.LayoutParams(0, dp(sz(t40)), 2.4f)
        nameRow.addView(nameTv)
        nameRow.addView(btn("Rename", 1f, t40) { renText = if (expName.isNotEmpty()) expName else auto; renPos = renText.length; renCaps = false; renaming = true; expMsg = ""; refresh() })
        if (expName.isNotEmpty()) nameRow.addView(btn("Auto", 0.8f, t40) { expName = ""; expMsg = ""; refresh() })
        outer.addView(nameRow)
        try {   // v0.17.66 (R6): where Save puts the file + where to change it
            if (ClipExporter.canSave(ctx)) {
                val wh = TextView(ctx)
                wh.text = "Save to: " + (if (ClipFolder.accessOk(ctx)) ClipFolder.label(ctx) else "Downloads") + "  (change: Jabra app > Data & About > Clipboard export)"
                wh.textSize = fs(11f)
                wh.setTextColor(hush())
                wh.setPadding(dp(8), 0, dp(8), dp(4))
                outer.addView(wh)
            }
        } catch (_: Throwable) { }

        val share = row()
        share.addView(btn(if (pk3.isNotEmpty()) "Share $pk3 .txt" else "Share .txt", 1f, t40) { doExport(false, false) })
        val shDocx = btn(if (pk3.isNotEmpty()) "Share $pk3 .docx" else "Share .docx", 1f, t40) { doExport(true, false) }
        share.addView(if (nl()) ClipBar.on(ctx, pf, shDocx) else shDocx)   // v0.17.58 (C4): primary button
        outer.addView(share)
        if (ClipExporter.canSave(ctx)) {   // v0.17.66 (R6)
            val save = row()
            save.addView(btn(if (pk3.isNotEmpty()) "Save $pk3 .txt" else "Save .txt", 1f, t40) { doExport(false, true) })
            save.addView(btn(if (pk3.isNotEmpty()) "Save $pk3 .docx" else "Save .docx", 1f, t40) { doExport(true, true) })
            outer.addView(save)
        }
        val sv = ScrollView(ctx)
        sv.addView(outer)
        return sv
    }

    /** v0.17.63 (R3): ZIP is used only when chosen AND there is more than one clip to export. */
    private fun expZip(n: Int): Boolean = n > 1 && try { JabraPrefs(ctx).bool("clip_exp_zip", false) } catch (_: Throwable) { false }

    /** v0.17.70 (S3): TAR is used only when chosen (and ZIP is not) AND there is more than one clip. */
    private fun expTar(n: Int): Boolean = n > 1 && !expZip(n) && try { JabraPrefs(ctx).bool("clip_exp_tar", false) } catch (_: Throwable) { false }

    /** v0.17.63 (R3): rename screen for the export file / zip name: Cancel | Use field text | OK, text box with caret, mini keys. Name is cleaned by ClipName.clean() on OK. */
    private fun makeRename(): View {
        val t40 = PanelLayout.MIN_TOUCH
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        fun touched(a: () -> Unit) { a(); refresh() }
        fun ins(s: String) { if (renText.length + s.length > ClipName.MAX_BASE) return; val np = ClipCaret.afterInsert(renText, renPos, s); renText = ClipCaret.insert(renText, renPos, s); renPos = np }

        val top = row()
        top.addView(btn("Cancel", 1f, 40) { renaming = false; refresh() })
        top.addView(btn("Use field text", 1.6f, 40) {
            val t = try { fieldText() } catch (_: Throwable) { "" }
            val c = ClipName.clean(t)
            if (c.isEmpty()) expMsg = "Field is empty" else { renText = c; renPos = c.length }
            refresh()
        })
        val okBtn = btn("OK", 1.2f, 40) {
            expName = ClipName.clean(renText)   // "" = automatic name
            expMsg = ""; renaming = false; refresh()
        }
        top.addView(if (nl()) ClipBar.on(ctx, pf, okBtn) else okBtn)
        root.addView(top)

        val info = TextView(ctx)
        val shown = ClipName.clean(renText)
        info.text = (if (expMsg.isEmpty()) "File name (no extension)" else expMsg) + "   " + renText.length + "/" + ClipName.MAX_BASE + (if (shown.isNotEmpty() && shown != renText) "   \u2192 " + shown else "")
        expMsg = ""
        info.textSize = 12f
        info.setTextColor(dim())
        info.setPadding(dp(8), dp(2), dp(8), dp(2))
        if (nl()) cardInfo(info)
        root.addView(info)

        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
        val tv = TextView(ctx)
        renPos = ClipCaret.clamp(renText, renPos)
        val shownPos = renPos
        tv.text = ClipCaret.display(renText, shownPos)
        tv.textSize = 14f
        tv.setTextColor(ink())
        tv.setPadding(dp(8), dp(2), dp(8), dp(2))
        if (nl()) { try { sv.background = UiKit.cardBackground(ctx, pf) } catch (_: Throwable) { }; tv.setPadding(dp(12), dp(6), dp(12), dp(6)) }
        sv.addView(tv)
        try {   // tap on the text = put the caret there (same as the clip edit screen)
            tv.setOnTouchListener { v, ev ->
                if (ev.action == android.view.MotionEvent.ACTION_UP && ev.eventTime - ev.downTime < 400L) {
                    try {
                        val t = v as TextView
                        val lay = t.layout
                        if (lay != null) {
                            val y = (ev.y - t.totalPaddingTop + t.scrollY).toInt().coerceIn(0, lay.height - 1)
                            val line = lay.getLineForVertical(y)
                            val off = lay.getOffsetForHorizontal(line, ev.x - t.totalPaddingLeft)
                            renPos = ClipCaret.posFromDisplay(renText, shownPos, off)
                            refresh()
                        }
                    } catch (_: Throwable) { }
                }
                true
            }
        } catch (_: Throwable) { }
        root.addView(sv)

        val quick = row()
        quick.addView(btn("Auto name", 1.3f, 34) { touched { renText = try { ClipName.autoBase(expEntries(JabraPrefs(ctx).bool("clip_exp_skip", true), IntArray(1)), System.currentTimeMillis(), java.util.TimeZone.getDefault()) } catch (_: Throwable) { "Jabra-Clips" }; renPos = renText.length } })
        quick.addView(btn("+ Date", 1f, 34) { touched { renText = ClipName.addDate(renText, System.currentTimeMillis(), java.util.TimeZone.getDefault()); renPos = renText.length } })
        quick.addView(btn("Clear", 1f, 34) { touched { renText = ""; renPos = 0 } })
        root.addView(quick)

        val nav = row()
        nav.addView(btn("|\u25C0", 1f, 34) { touched { renPos = 0 } })
        nav.addView(btn("\u25C0", 1f, 34) { touched { renPos = ClipCaret.left(renText, renPos) } })
        nav.addView(btn("\u25B6", 1f, 34) { touched { renPos = ClipCaret.right(renText, renPos) } })
        nav.addView(btn("\u25B6|", 1f, 34) { touched { renPos = renText.length } })
        root.addView(nav)

        fun ch(c: Char): String = if (renCaps) c.uppercaseChar().toString() else c.toString()
        for (letters in arrayOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")) {
            val r = row()
            if (letters == "zxcvbnm") r.addView(btn(if (renCaps) "\u21E7 ON" else "\u21E7", 1.5f, 34) { touched { renCaps = !renCaps } })
            letters.forEach { c -> r.addView(btn(ch(c), 1f, 34) { touched { ins(ch(c)); renCaps = false } }) }
            if (letters == "zxcvbnm") r.addView(btn("\u232B", 1.5f, 34) { touched { val np = ClipCaret.afterBackspace(renText, renPos); renText = ClipCaret.backspace(renText, renPos); renPos = np } })
            root.addView(r)
        }
        val last = row()
        for (p in arrayOf("-", "_", ".", "(", ")")) last.addView(btn(p, 1f, 34) { touched { ins(p) } })
        last.addView(btn("space", 2.5f, 34) { touched { ins(" ") } })
        root.addView(last)
        return root
    }

    private fun actToVault() {
        val chosen = sel.selection()
        if (chosen.isEmpty()) msg = "Nothing selected"
        else { msg = try { toVault?.invoke(chosen.map { it.text }) ?: "" } catch (_: Throwable) { "Could not save to Vault" }; sel.leave() }
        confirmDel = false
        refresh()
    }
    // ===================== end v0.16.9 (X9) =====================

    // v0.15.1 (N1): clip_fit (default ON) = the clipboard is as tall as the letters keyboard. Built as before, measured, the list grows by the missing height (never shrinks; any problem = old size).
    private fun fitOn(): Boolean = try { JabraPrefs(ctx).bool("clip_fit", true) && fitPx > 0 } catch (_: Throwable) { false }
    private fun fitList(root: View, sv: ScrollView) {
        try {
            if (fitOn()) {
                root.measure(View.MeasureSpec.makeMeasureSpec(ctx.resources.displayMetrics.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                val extra = com.mgb.keyboard.typing.ClipFit.extraPx(fitPx, root.measuredHeight, dp(com.mgb.keyboard.typing.ClipFit.MAX_EXTRA_DP))
                if (extra > 0) { val lp = sv.layoutParams; lp.height = lp.height + extra; sv.layoutParams = lp }
            }
        } catch (_: Throwable) { }
        try {   // v0.17.38 (M5): own list height %, only shrinks the final list (works with clip_fit ON and OFF); 100 = untouched
            val pct = com.mgb.keyboard.typing.ClipListH.sanitize(JabraPrefs(ctx).int("clip_list_pct", com.mgb.keyboard.typing.ClipListH.DEF))
            if (pct != com.mgb.keyboard.typing.ClipListH.DEF) {
                val lp = sv.layoutParams
                val h = com.mgb.keyboard.typing.ClipListH.heightPx(lp.height, pct, dp(com.mgb.keyboard.typing.ClipListH.MIN_DP))
                if (h != lp.height) { lp.height = h; sv.layoutParams = lp }
            }
        } catch (_: Throwable) { }
    }
    private fun fitEdit(v: View): View {
        try { if (fitOn()) v.minimumHeight = maxOf(v.minimumHeight, minOf(fitPx, dp(560))) } catch (_: Throwable) { }
        return v
    }

    /** v0.4.3.1: edit screen for ONE clip: Cancel | Use field text | Save (two taps), text box, mini keys (Latin). Bangla = type it in the field, then "Use field text". */
    private fun makeEdit(): View {
        val item = editing ?: return make()
        if (pasteOpen) { try { return editPicker() } catch (_: Throwable) { pasteOpen = false } }   // v0.38.0 (P1)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)   // v0.8.7
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        fun touched(a: () -> Unit) { a(); confirmSave = false; refresh() }

        val top = row()
        top.addView(btn("Cancel", 1f, 40) { editing = null; confirmSave = false; refresh() })
        top.addView(btn("Use field text", 1.6f, 40) {
            val t = try { fieldText() } catch (_: Throwable) { "" }
            if (t.isBlank()) msg = "Field is empty" else { editText = t; editPos = t.length }
            confirmSave = false
            refresh()
        })
        val saveBtn = btn(if (confirmSave) "Sure? Save" else "Save", 1.2f, 40) {
            if (!confirmSave) { confirmSave = true; refresh() }
            else {
                when (store.replace(item, editText)) {
                    0 -> { msg = "Clip saved"; editing = null; sel.leave() }
                    1 -> msg = "Text is empty"
                    2 -> msg = "Too long (max " + ClipList.MAX_LEN + ")"
                    3 -> msg = "Another clip has this text"
                    else -> { msg = "Clip not found"; editing = null }
                }
                confirmSave = false
                refresh()
            }
        }
        top.addView(if (nl()) ClipBar.on(ctx, pf, saveBtn) else saveBtn)   // v0.17.58 (C4): Save = primary
        root.addView(top)

        val info = TextView(ctx)
        info.text = (if (msg.isEmpty()) "Edit clip \u00B7 hold text = paste" else msg) + "   " + editText.length + "/" + ClipList.MAX_LEN
        msg = ""
        info.textSize = 12f
        info.setTextColor(dim())
        info.setPadding(dp(8), dp(2), dp(8), dp(2))
        if (nl()) cardInfo(info)   // v0.17.58 (C4)
        root.addView(info)

        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64))
        val tv = TextView(ctx)
        editPos = ClipCaret.clamp(editText, editPos)   // v0.17.56 (C1)
        val shownPos = editPos
        tv.text = ClipCaret.display(editText, shownPos)
        tv.textSize = 14f
        tv.setTextColor(ink())
        tv.setPadding(dp(8), dp(2), dp(8), dp(2))
        if (nl()) { try { sv.background = UiKit.cardBackground(ctx, pf) } catch (_: Throwable) { }; tv.setPadding(dp(12), dp(6), dp(12), dp(6)) }   // v0.17.58 (C4): text box = card
        sv.addView(tv)
        try {   // v0.17.56 (C1): tap on the text = put the caret there (nearest gap); a drag still scrolls. v0.38.0 (P1): hold (0.6 s) = caret there + paste the latest copy
            tv.setOnTouchListener { v, ev ->
                val heldMs = ev.eventTime - ev.downTime
                if (ev.action == android.view.MotionEvent.ACTION_UP && (heldMs < 400L || heldMs >= 600L)) {
                    try {
                        val t = v as TextView
                        val lay = t.layout
                        if (lay != null) {
                            val y = (ev.y - t.totalPaddingTop + t.scrollY).toInt().coerceIn(0, lay.height - 1)
                            val line = lay.getLineForVertical(y)
                            val off = lay.getOffsetForHorizontal(line, ev.x - t.totalPaddingLeft)
                            editPos = ClipCaret.posFromDisplay(editText, shownPos, off)
                            if (heldMs >= 600L) { try { host.feedback(v) } catch (_: Throwable) { }; pasteEdit(PasteKit.systemClip(ctx)) }
                            confirmSave = false
                            refresh()
                        }
                    } catch (_: Throwable) { }
                }
                true
            }
        } catch (_: Throwable) { }
        root.addView(sv)

        // v0.38.0 (P1): paste keys. Paste = latest copy at the caret; hold Paste or tap the arrow = pick from Recent / Pinned; chips = words that are slow to type
        try {
            val pr = row()
            val pasteBtn = btn("Paste", 1.5f, 34) { touched { pasteEdit(PasteKit.systemClip(ctx)) } }
            PasteKit.onHold(pasteBtn, { v -> host.feedback(v) }) { pasteOpen = true; pasteTab = 0; confirmSave = false; refresh() }
            pr.addView(pasteBtn)
            pr.addView(btn("\u25BE", 0.7f, 34) { pasteOpen = true; pasteTab = 0; confirmSave = false; refresh() })
            pr.addView(btn("Clear", 0.9f, 34) { touched { editText = ""; editPos = 0; msg = "Cleared (Cancel brings the old text back)" } })
            for (q in QuickPaste.QUICK) pr.addView(btn(q, if (q.length > 8) 2.1f else if (q.length > 6) 2f else 1f, 34) { touched { insEdit(q) } })
            root.addView(pr)
        } catch (_: Throwable) { }

        // v0.17.56 (C1): caret keys
        val nav = row()
        nav.addView(btn("|\u25C0", 1f, 34) { touched { editPos = ClipCaret.lineStart(editText, editPos) } })
        nav.addView(btn("\u25C0", 1f, 34) { touched { editPos = ClipCaret.left(editText, editPos) } })
        nav.addView(btn("\u25B2", 1f, 34) { touched { editPos = ClipCaret.up(editText, editPos) } })
        nav.addView(btn("\u25BC", 1f, 34) { touched { editPos = ClipCaret.down(editText, editPos) } })
        nav.addView(btn("\u25B6", 1f, 34) { touched { editPos = ClipCaret.right(editText, editPos) } })
        nav.addView(btn("\u25B6|", 1f, 34) { touched { editPos = ClipCaret.lineEnd(editText, editPos) } })
        root.addView(nav)

        fun ins(s: String) { val np = ClipCaret.afterInsert(editText, editPos, s); editText = ClipCaret.insert(editText, editPos, s); editPos = np }

        fun ch(c: Char): String = if (editCaps) c.uppercaseChar().toString() else c.toString()
        for (letters in arrayOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")) {
            val r = row()
            if (letters == "zxcvbnm") r.addView(btn(if (editCaps) "\u21E7 ON" else "\u21E7", 1.5f, 34) { touched { editCaps = !editCaps } })
            letters.forEach { c -> r.addView(btn(ch(c), 1f, 34) { touched { ins(ch(c)); editCaps = false } }) }
            if (letters == "zxcvbnm") r.addView(btn("\u232B", 1.5f, 34) { touched { val np = ClipCaret.afterBackspace(editText, editPos); editText = ClipCaret.backspace(editText, editPos); editPos = np } })
            root.addView(r)
        }
        val last = row()
        for (p in arrayOf(".", ",", "?", "!", "-", "'")) last.addView(btn(p, 1f, 34) { touched { ins(p) } })
        last.addView(btn("space", 2.5f, 34) { touched { ins(" ") } })
        last.addView(btn("\u23CE", 1.2f, 34) { touched { ins("\n") } })
        root.addView(last)
        return root
    }

    /** v0.38.0 (P1): puts [raw] (a copied text) at the caret of the Edit screen. Cut to the room that is left (max ClipList.MAX_LEN); sets the one-line message. */
    private fun pasteEdit(raw: String) {
        val room = ClipList.MAX_LEN - editText.length
        val t = QuickPaste.fit(raw, room)
        if (t.isEmpty()) { msg = QuickPaste.noneText(raw, room); return }
        val np = ClipCaret.afterInsert(editText, editPos, t)
        editText = ClipCaret.insert(editText, editPos, t)
        editPos = np
        msg = QuickPaste.doneText(t.length, QuickPaste.wasCut(raw, room))
    }

    /** v0.38.0 (P1): a quick chip (.com, https:// ...) at the caret. */
    private fun insEdit(s: String) {
        val room = ClipList.MAX_LEN - editText.length
        val t = QuickPaste.fit(s, room)
        if (t.isEmpty()) { msg = "Too long (max " + ClipList.MAX_LEN + ")"; return }
        val np = ClipCaret.afterInsert(editText, editPos, t)
        editText = ClipCaret.insert(editText, editPos, t)
        editPos = np
    }

    /** v0.38.0 (P1): the paste list of the Edit screen (Recent / Pinned). A tap puts the text at the caret and goes back to the Edit screen. */
    private fun editPicker(): View {
        val all: List<ClipList.Item> = try { store.view("") } catch (_: Throwable) { emptyList<ClipList.Item>() }
        val latest = PasteKit.systemClip(ctx)
        return PasteKit.picker(ctx, pf, pasteTab, PasteKit.recentTexts(all, latest), PasteKit.pinnedTexts(all),
            { l, w, h, c -> btn(l, w, h, c) }, { v -> paintBg(v) },
            { t -> pasteTab = t; refresh() },
            { t -> pasteOpen = false; pasteEdit(t); confirmSave = false; refresh() },
            { pasteOpen = false; refresh() })
    }

    /** v0.2.5.1: tap on Add = save selected text (or the whole field) as a new clip. */
    private fun addFromField() {
        val t = try { fieldText() } catch (_: Throwable) { "" }
        val dup = store.has(t)   // v0.4.3
        when (store.addChecked(t)) {
            0 -> msg = if (dup) "Already saved (moved to top)" else "Added to Recent"
            2 -> msg = "Too long (max " + ClipList.MAX_LEN + ")"
            else -> msg = "Field is empty"
        }
        confirmClear = false
        refresh()
    }

    /** v0.2.5.3: action row in select mode: All | Pin | Unpin | Delete | Done. */
    private fun selectBar(): View {
        val r = row()
        r.addView(btn("All", 1f, 34) {
            sel.selectAll(store.pinnedView(query) + store.recentView(query))
            confirmDel = false
            refresh()
        })
        r.addView(btn("Pin", 1f, 34) {
            val chosen = sel.selection()
            val want = chosen.count { !it.pinned }
            if (chosen.isEmpty()) msg = "Nothing selected"
            else {
                val n = store.setPinned(chosen, true)
                msg = if (n < want) "Pinned " + n + " of " + want + " (limit " + ClipList.MAX_PINNED + ")" else "Pinned " + n
            }
            confirmDel = false
            refresh()
        })
        r.addView(btn("Unpin", 1f, 34) {
            val chosen = sel.selection()
            if (chosen.isEmpty()) msg = "Nothing selected" else msg = "Unpinned " + store.setPinned(chosen, false)
            confirmDel = false
            refresh()
        })
        val n = sel.count()
        val del = btn(if (confirmDel) "Sure? delete " + n else "Delete", if (confirmDel) 2.2f else 1.2f, 34) {
            if (n == 0) { msg = "Nothing selected"; confirmDel = false }
            else if (confirmDel) {
                keepUndo(sel.selection())
                val gone = store.removeAll(sel.selection())
                sel.clear()
                msg = "Deleted " + gone
                confirmDel = false
            } else confirmDel = true
            refresh()
        }
        if (confirmDel) del.textSize = fs(12f)
        r.addView(del)
        // v0.3.6: Paste = type ALL selected clips in the order you tapped them, joined by a blank line, then back to letters
        r.addView(btn("Paste", 1.2f, 34) {
            val chosen = sel.selection()
            if (chosen.isEmpty()) { msg = "Nothing selected"; confirmDel = false; refresh() }
            else {
                val joined = chosen.joinToString("\n\n") { it.text }
                sel.leave(); confirmDel = false
                pasteChosen(chosen, joined)   // v0.17.37 (M4); v0.17.41 (M8): one clip = with format, several = plain
            }
        })
        r.addView(btn("Done", 1f, 34) { sel.leave(); confirmDel = false; refresh() })
        // v0.3.8: second row: Merge = save the ticked clips as ONE new clip; 1 line = paste them joined by a space (line breaks removed)
        val r2 = row()
        r2.addView(btn("Merge → 1 clip", 1f, 34) {
            val chosen = sel.selection()
            if (chosen.size < 2) msg = "Select 2 or more"
            else when (store.addChecked(chosen.joinToString("\n\n") { it.text })) {
                0 -> { msg = "Merged " + chosen.size + " into 1 (see Recent)"; sel.leave() }
                2 -> msg = "Too long (max " + ClipList.MAX_LEN + ")"
                else -> msg = "Nothing to merge"
            }
            confirmDel = false
            refresh()
        })
        r2.addView(btn("Edit", 0.6f, 34) {   // v0.4.3.1: edit ONE ticked clip
            val chosen = sel.selection()
            if (chosen.size != 1) msg = "Select ONE clip to edit"
            else { editing = chosen[0]; editText = chosen[0].text; editPos = editText.length; editCaps = false; confirmSave = false }
            confirmDel = false
            refresh()
        })
        r2.addView(btn("Paste in 1 line", 1f, 34) {
            val chosen = sel.selection()
            if (chosen.isEmpty()) { msg = "Nothing selected"; confirmDel = false; refresh() }
            else {
                val one = chosen.joinToString(" ") { it.text.replace(Regex("\\s*\\n\\s*"), " ").trim() }
                sel.leave(); confirmDel = false
                pasteClip(one, null, true)   // v0.17.37 (M4)
            }
        })
        if (toVault != null) {   // v0.4.4: copy the ticked clips into the Text Vault (folder last used there); clips stay in the history
            val vb = btn("To Vault", 0.8f, 34) {
                val chosen = sel.selection()
                if (chosen.isEmpty()) msg = "Nothing selected"
                else { msg = try { toVault?.invoke(chosen.map { it.text }) ?: "" } catch (_: Throwable) { "Could not save to Vault" }; sel.leave() }
                confirmDel = false
                refresh()
            }
            vb.textSize = fs(12f)
            r2.addView(vb)
        }
        val eb = btn("Export", 0.8f, 34) { actExport() }   // v0.17.57 (C2): classic panel gets Export too (panel_ui default is Classic)
        eb.textSize = fs(12f)
        r2.addView(eb)
        val colAll = LinearLayout(ctx)
        colAll.orientation = LinearLayout.VERTICAL
        colAll.addView(r)
        colAll.addView(r2)
        return colAll
    }

    private fun sectionHeader(label: String, arrow: Boolean = false, onTap: (() -> Unit)? = null): View {   // v0.15.2: onTap = swap Pinned / Recent order
        val h = TextView(ctx)
        val base = if (label == "Pinned") "\u2605 " + label else label   // v0.18: gold star on the Pinned header
        h.text = if (arrow) base + "  \u21C5" else base
        if (onTap != null) { h.minimumHeight = dp(30); h.gravity = Gravity.CENTER_VERTICAL; h.setOnClickListener { try { onTap() } catch (_: Throwable) { } } }
        h.textSize = 12f
        h.setTypeface(h.typeface, android.graphics.Typeface.BOLD)
        h.setTextColor(if (label == "Pinned") (try { ClipLook.pinText(pf, JabraTokens.isDark(pf)) } catch (_: Throwable) { dim() }) else dim())
        h.setPadding(dp(8), dp(4), dp(8), dp(2))
        return h
    }

    /** v0.4.3: "123" (chars) or "123 · 2x" (pasted twice). Empty when clip_count is OFF. */
    private fun infoLine(item: ClipList.Item): String = try {
        if (!JabraPrefs(ctx).bool("clip_count", true)) "" else {
            val n = store.countOf(item.text)
            val age = try { com.mgb.keyboard.clip.ClipAge.shortAge(store.madeOf(item.text), System.currentTimeMillis()) } catch (_: Throwable) { "" }   // v0.10.24 (C1)
            item.text.length.toString() + (if (n > 0) " \u00B7 " + n + "x" else "") + (if (age.isNotEmpty()) " \u00B7 " + age else "")
        }
    } catch (_: Throwable) { "" }

    /** v0.10.24 (C1): list row + a thin divider line under it. Any problem = the plain row as before. */
    private fun itemRow(item: ClipList.Item): View {
        if (mockOn()) { try { return itemRowMock(item) } catch (_: Throwable) { } }   // v0.50.0 (CM1): one-line swipe row; any problem = the row below
        if (tidy()) { try { val tr = itemRowTidy(item); return if (simple()) flatLine(tr) else tr } catch (_: Throwable) { } }   // v0.18.4: tidy card row; any problem = the row below
        val r = itemRowBase(item)
        return try {
            val w = LinearLayout(ctx)
            w.orientation = LinearLayout.VERTICAL
            w.addView(r)
            val ln = View(ctx)
            ln.setBackgroundColor(JabraTokens.withAlpha(dim(), 0x40))
            ln.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxOf(1, dp(1))).also { it.setMargins(dp(8), dp(1), dp(8), dp(1)) }
            w.addView(ln)
            w
        } catch (_: Throwable) { r }
    }

    /**
     * v0.18.4: TIDY list row = one rounded card (same look as the Cards view): [pin] [clip text + small meta line] [x].
     * Pin sits on the LEFT and x on the RIGHT with the whole text between them, so one tap can never hit the wrong one.
     * Select mode: check box instead of pin / x. Tap = paste, hold = select (same listeners as the classic row).
     */
    private fun itemRowTidy(item: ClipList.Item): View {
        val lv = try { JabraPrefs(ctx).int("clip_lines", 0).coerceIn(0, 2) } catch (_: Throwable) { 0 }
        val selOn = sel.isOn
        val flat = simple()   // v0.19.8
        val picked = selOn && sel.isSelected(item)
        val tt = try { ClipLook.tight(pf) } catch (_: Throwable) { false }   // v0.36.0 (CC4)
        val iconsOn = try { JabraPrefs(ctx).bool("icons_on", true) } catch (_: Throwable) { true }
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.background = try { UiKit.cardBackground(ctx, pf, picked, item.pinned) } catch (_: Throwable) { null }
        if (item.pinned && !picked) { try { box.background = ClipCards.pinnedBg(ctx, pf) } catch (_: Throwable) { } }
        if (flat && !picked) box.background = null   // v0.19.8: flat row (a picked row keeps its card so the selection stays visible)
        val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val bm = if (tt) 2 else 3
        blp.setMargins(dp(3), dp(bm), dp(3), dp(bm))   // v0.36.0 (CC4): tight = 2 dp above / below
        box.layoutParams = blp
        box.minimumHeight = dp(sz(if (tt) 44 + 12 * lv else 52 + 14 * lv))

        // left: pin (or the check box in select mode)
        if (selOn) {
            val ck = TextView(ctx)
            ck.text = if (picked) "\u2611" else "\u2610"
            ck.textSize = if (tt) 15f else 18f   // v0.36.0 (CC4)
            ck.setTextColor(try { if (picked) JabraTokens.cardStroke(pf, true, false) else JabraTokens.onCardDim(pf) } catch (_: Throwable) { if (picked) Color.parseColor("#2F6FD0") else Color.DKGRAY })
            Glyphs.dress(ck)
            ck.gravity = Gravity.CENTER
            try {   // v0.18.7: a drawn check box sticks to the left edge of its view (touched the card border) - centre it with left padding
                val gw = ck.compoundDrawablesRelative[0]?.intrinsicWidth ?: 0
                if (gw > 0) ck.setPadding(((dp(if (tt) 36 else 44) - gw) / 2).coerceAtLeast(dp(6)), 0, 0, 0)
            } catch (_: Throwable) { }
            ck.layoutParams = LinearLayout.LayoutParams(dp(if (tt) 36 else 44), dp(if (tt) 28 else 36))
            box.addView(ck)
        } else if (!flat) {   // v0.19.8: flat row has the pin on the RIGHT
            box.addView(ClipCards.small(ctx, host, if (item.pinned) "\u2605" else "\u2606", if (item.pinned) "pinned" else "pin", iconsOn) { store.togglePin(item); refresh() })
        }

        // middle: clip text + meta line
        val mid = LinearLayout(ctx)
        mid.orientation = LinearLayout.VERTICAL
        mid.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val t = TextView(ctx)
        t.text = item.text.replace("\n", " \u23CE ")
        t.textSize = fs(13f)
        t.setTextColor(if (flat && !picked) ink() else try { JabraTokens.cardText(pf) } catch (_: Throwable) { Color.BLACK })   // v0.19.8: flat row sits on the panel colour
        t.maxLines = 2 + 2 * lv
        t.ellipsize = TextUtils.TruncateAt.END
        t.setPadding(dp(if (flat && !selOn) 10 else 4), dp(if (tt) 4 else 6), dp(4), dp(if (tt) 0 else 2))   // v0.36.0 (CC4)
        // v0.18.6: the WHOLE row is the tap target (text, meta line, check box, empty space); the text view itself is no longer clickable
        try {   // soft tint while pressed (same as the cards), now on the whole row
            val pr = android.graphics.drawable.GradientDrawable()
            pr.setColor(JabraTokens.withAlpha(try { JabraTokens.cardText(pf) } catch (_: Throwable) { Color.BLACK }, 0x1F))
            pr.cornerRadius = dp(JabraTokens.R_CARD).toFloat()
            val sl = android.graphics.drawable.StateListDrawable()
            sl.addState(intArrayOf(android.R.attr.state_pressed), pr)
            sl.addState(intArrayOf(), android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            box.foreground = sl
        } catch (_: Throwable) { }
        val act: () -> Unit = {
            if (sel.isOn) { sel.toggle(item); confirmDel = false; refresh() } else pasteItem(item, false)   // same as the classic row
        }
        ClipHold.wire(box, clipHoldMs(), selOn, { sel.enterWith(item); confirmDel = false; refresh() }, { act() }, { try { host.feedback(box) } catch (_: Throwable) { } })   // v0.18.6: whole row (was: text only)
        mid.addView(t)
        try {   // meta line: kind tag | Aa (stored format) | 123 \u00B7 2x \u00B7 5m
            val tg = ClipLook.tag(ctx, pf, item.text)
            val ft = if (!selOn && store.hasFmt(item.text)) ClipLook.fmtTag(ctx, pf) else null
            val inf = infoLine(item)
            if (tg != null || ft != null || inf.isNotEmpty()) {
                val line = LinearLayout(ctx)
                line.orientation = LinearLayout.HORIZONTAL
                line.gravity = Gravity.CENTER_VERTICAL
                line.setPadding(dp(4), 0, dp(4), dp(4))
                if (tg != null) { tg.setPadding(dp(5), dp(1), dp(5), dp(1)); line.addView(tg) }
                if (ft != null && tt) { try { ft.textSize = 9f } catch (_: Throwable) { } }   // v0.36.0 (CC4)
                if (ft != null) {
                    ft.setOnClickListener { try { host.feedback(ft) } catch (_: Throwable) { }; pasteItem(item, true) }
                    val flp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    flp.setMargins(dp(4), 0, dp(2), 0)
                    ft.layoutParams = flp
                    line.addView(ft)
                }
                if (inf.isNotEmpty()) {
                    val ct = TextView(ctx)
                    ct.text = inf
                    ct.textSize = if (tt) 11f else 10f   // v0.36.0 (CC4): one dim 11 sp meta line
                    ct.setTextColor(hush())
                    ct.setPadding(dp(4), 0, dp(4), 0)
                    line.addView(ct)
                }
                mid.addView(line)
            }
        } catch (_: Throwable) { }
        box.addView(mid)

        // right: x (not in select mode)
        if (!selOn) {
            if (flat) box.addView(ClipCards.small(ctx, host, if (item.pinned) "\u2605" else "\u2606", if (item.pinned) "pinned" else "pin", iconsOn) { store.togglePin(item); refresh() })   // v0.19.8: pin on the right, no x (delete = hold the row, then Delete; Undo stays)
            else box.addView(ClipCards.small(ctx, host, "\u2715", "close", iconsOn) { keepUndo(listOf(item)); store.remove(item); msg = ClipCopy.deleted(1); refresh() })
        }
        return box
    }

    /**
     * v0.50.0 (CM1): ONE-LINE row of the mock look: [bar] text [Aa] [age] [arrow]. Bar = gold (pinned) / blue (selected).
     * Tap = paste (select mode: toggle), hold = select mode, swipe right = pin / unpin, swipe left = delete (Undo as always).
     * Long clips get a small arrow that opens up to 6 lines. Every action runs the same code as the old pin / x buttons.
     */
    private fun itemRowMock(item: ClipList.Item): View {
        val selOn = sel.isOn
        val picked = selOn && sel.isSelected(item)
        val open = openClips.contains(item.text)
        val ink2 = try { JabraTokens.cardText(pf) } catch (_: Throwable) { Color.BLACK }
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = if (open) Gravity.TOP else Gravity.CENTER_VERTICAL
        box.background = try { ClipMock.rowBg(ctx, pf, picked) } catch (_: Throwable) { try { UiKit.cardBackground(ctx, pf, picked, false) } catch (_: Throwable) { null } }   // v0.53.1 (CM2): flat mock row, theme colours, no stroke
        box.minimumHeight = dp(sz(38))
        val bar = View(ctx)
        bar.setBackgroundColor(if (picked) ClipMock.accent(pf) else if (item.pinned) ClipMock.gold(pf) else Color.TRANSPARENT)
        box.addView(bar, LinearLayout.LayoutParams(dp(3), LinearLayout.LayoutParams.MATCH_PARENT))
        val t = TextView(ctx)
        t.text = if (open) item.text else ClipMockPlan.oneLine(item.text)
        t.textSize = fs(13.5f)
        t.setTextColor(ink2)
        t.maxLines = if (open) ClipMockPlan.OPEN_MAX_LINES else 1
        t.ellipsize = TextUtils.TruncateAt.END
        t.setPadding(dp(9), dp(if (open) 9 else 0), dp(6), dp(if (open) 9 else 0))
        t.gravity = Gravity.CENTER_VERTICAL
        box.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        try {   // Aa = this clip has a stored format; tap = paste the OTHER way (not in select mode)
            if (!selOn && store.hasFmt(item.text)) {   // v0.53.1 (CM2): small outlined mock tag (the tap box is as high as the row)
                val ft = ClipMock.fmtTag(ctx, pf)
                ft.setOnClickListener { try { host.feedback(ft) } catch (_: Throwable) { }; pasteItem(item, true) }
                val flp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, if (open) dp(26) else LinearLayout.LayoutParams.MATCH_PARENT)
                flp.setMargins(dp(2), if (open) dp(5) else 0, dp(4), 0)
                ft.layoutParams = flp
                box.addView(ft)
            }
        } catch (_: Throwable) { }
        try {   // age only, dim (pref clip_count OFF = no age)
            if (JabraPrefs(ctx).bool("clip_count", true)) {
                val age = com.mgb.keyboard.clip.ClipAge.shortAge(store.madeOf(item.text), System.currentTimeMillis())
                if (age.isNotEmpty()) {
                    val m = TextView(ctx)
                    m.text = age
                    m.textSize = 11f
                    m.setTextColor(hush())
                    m.setPadding(dp(4), dp(if (open) 9 else 0), dp(4), 0)
                    m.gravity = Gravity.CENTER_VERTICAL
                    box.addView(m, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, if (open) LinearLayout.LayoutParams.WRAP_CONTENT else LinearLayout.LayoutParams.MATCH_PARENT))
                }
            }
        } catch (_: Throwable) { }
        if (ClipMockPlan.expandable(item.text)) {
            val ar = TextView(ctx)
            ar.text = if (open) "\u25B4" else "\u25BE"
            ar.textSize = 13f
            ar.setTextColor(hush())
            ar.gravity = Gravity.CENTER
            ar.contentDescription = if (open) "Show less" else "Show more"
            ar.isClickable = true
            ar.setOnClickListener { try { host.feedback(ar) } catch (_: Throwable) { }; if (open) openClips.remove(item.text) else openClips.add(item.text); refresh() }
            box.addView(ar, LinearLayout.LayoutParams(dp(30), if (open) dp(34) else LinearLayout.LayoutParams.MATCH_PARENT))
        }
        val w = ClipMock.swipeRow(ctx, pf, box, if (item.pinned) "Unpin" else "Pin", selOn, clipHoldMs(),
            { if (sel.isOn) { sel.toggle(item); confirmDel = false; refresh() } else pasteItem(item, false) },
            { sel.enterWith(item); confirmDel = false; refresh() },
            { store.togglePin(item); refresh() },
            { keepUndo(listOf(item)); store.remove(item); msg = ClipCopy.deleted(1); refresh() },
            { try { host.feedback(box) } catch (_: Throwable) { } })
        w.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.setMargins(dp(3), dp(2), dp(3), dp(1)) }
        return w
    }

    /** v0.16.16 (E2): hold time (ms) from pref clip_hold (level, default 1.5 s); 0 = old system long-click. Broken value = default. */
    private fun clipHoldMs(): Int = try { com.mgb.keyboard.typing.HoldSelect.msForLevel(JabraPrefs(ctx).int("clip_hold", com.mgb.keyboard.typing.HoldSelect.LEVEL_DEFAULT)) } catch (_: Throwable) { 0 }

    private fun itemRowBase(item: ClipList.Item): View {
        val lv = try { JabraPrefs(ctx).int("clip_lines", 0).coerceIn(0, 2) } catch (_: Throwable) { 0 }   // v0.2.5.1: 0/1/2 = 2/4/6 lines
        val rowH = 44 + 22 * lv
        val r = row()
        r.gravity = Gravity.CENTER_VERTICAL
        val selOn = sel.isOn
        val picked = selOn && sel.isSelected(item)
        val prefix = if (selOn) (if (picked) "☑ " else "☐ ") + (if (item.pinned) "★ " else "") else (if (item.pinned && !JabraPrefs(ctx).bool("icons_on", true)) "★ " else "")   // v0.2.5.3: check box + pinned tag in select mode
        val act: () -> Unit = {   // v0.16.16 (E2): shared by the click and the hold listener
            if (sel.isOn) {   // v0.2.5.3: tap toggles, NO paste
                sel.toggle(item); confirmDel = false; refresh()
            } else {
                pasteItem(item, false)   // v0.17.37 (M4); v0.17.41 (M8): plain or with format (ClipPasteMode)
            }
        }
        val t = btn(prefix + Glyphs.KEEP + item.text.replace("\n", " ⏎ "), 1f, rowH) { act() }   // v0.17.68 (S1): KEEP = clip text is never turned into an icon
        if (picked) t.setBackgroundColor(try { JabraTokens.rowPicked(pf) } catch (_: Throwable) { Color.parseColor("#CFE0F5") })
        val held = ClipHold.attach(t, clipHoldMs(), selOn, { sel.enterWith(item); confirmDel = false; refresh() }, { act() }, { try { host.feedback(t) } catch (_: Throwable) { } })   // v0.16.16 (E2): hold = select mode
        if (!held) t.setOnLongClickListener {   // v0.2.5.3: long-press = select mode with this clip selected (old way, used when clip_hold = System)
            if (sel.isOn) false else { sel.enterWith(item); confirmDel = false; refresh(); true }
        }
        ClipHold.wire(r, clipHoldMs(), selOn, { sel.enterWith(item); confirmDel = false; refresh() }, { act() }, { try { host.feedback(r) } catch (_: Throwable) { } })   // v0.18.6: tag / count / gaps of the classic row tap like the text (the text button keeps its own handling)
        t.textSize = 13f
        t.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        t.maxLines = 2 + 2 * lv
        t.ellipsize = TextUtils.TruncateAt.END
        t.setPadding(dp(8), 0, dp(8), 0)
        r.addView(t)
        try { ClipLook.tag(ctx, pf, item.text)?.let { tg -> tg.setPadding(dp(5), dp(1), dp(5), dp(1)); r.addView(tg) } } catch (_: Throwable) { }   // v0.10.24 (C1): kind tag (Link / Email / Code / Phone / Bangla)
        try {   // v0.17.41 (M8): Aa tag = this clip has a stored format; tap = paste the OTHER way (not in select mode)
            if (!selOn && store.hasFmt(item.text)) ClipLook.fmtTag(ctx, pf)?.let { ft ->
                ft.setOnClickListener { try { host.feedback(ft) } catch (_: Throwable) { }; pasteItem(item, true) }
                val flp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                flp.setMargins(dp(3), 0, dp(3), 0)
                ft.layoutParams = flp
                r.addView(ft)
            }
        } catch (_: Throwable) { }
        try {   // v0.4.3: char count (small grey) between the text and the star
            val inf = infoLine(item)
            if (inf.isNotEmpty()) {
                val ct = TextView(ctx)
                ct.text = inf
                ct.textSize = 10f
                ct.setTextColor(hush())
                ct.setPadding(dp(2), 0, dp(2), 0)
                r.addView(ct)
            }
        } catch (_: Throwable) { }
        if (selOn) return r   // v0.2.5.3: no star / x while selecting
        r.addView(JabraIcons.putThemed(ctx, btn(if (item.pinned) "★" else "☆", 0f, rowH) { store.togglePin(item); refresh() }.also { it.minimumWidth = dp(44); it.minWidth = dp(44) }, if (item.pinned) "pinned" else "pin"))   // v0.0.7
        r.addView(JabraIcons.putThemed(ctx, btn("✕", 0f, rowH) { keepUndo(listOf(item)); store.remove(item); if (nl()) msg = ClipCopy.deleted(1); refresh() }.also { it.minimumWidth = dp(44); it.minWidth = dp(44) }, "close"))   // v0.0.7
        return r
    }
}
