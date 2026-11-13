package com.mgb.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.layout.KeyDef
import com.mgb.keyboard.layout.LayoutCheck
import com.mgb.keyboard.layout.LayoutDef
import com.mgb.keyboard.layout.LayoutHistory
import com.mgb.keyboard.layout.LayoutOps
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.LayoutEditPlan

/**
 * v0.17.8 (K6a) the keyboard layout editor, page 1: see the layout, tap a key (selected = accent outline), edit its label, tap output,
 * shift output, long-press text and width. Every change goes through LayoutOps + LayoutStore.save (the safety gate S4) at once.
 * Shown as a full-screen dialog from App > Layout & size > "Edit layout" (no new page route, no manifest change). The app screen may use EditText.
 * v0.17.9 (K6b): structure buttons (move, merge, split, empty / fill, add, delete, row tools) + Undo / Redo (LayoutHistory; every undo is saved through the same gate).
 *  swipes / warnings = K7; number row / symbols / action keys = K8.
 */
class LayoutEditorPage(private val act: Activity, private val prefs: JabraPrefs, private val onClose: () -> Unit) {
    private class Field(val box: LinearLayout, val et: EditText, val read: (KeyDef) -> String, val op: (LayoutDef, String, String) -> LayoutDef)

    private var model: LayoutDef? = null
    private var tab = 0
    private var selected = ""
    private var loading = false
    private var pending: Runnable? = null
    private val h = Handler(Looper.getMainLooper())
    private val fields = mutableListOf<Field>()
    private val tabBtns = mutableListOf<Button>()
    private var view: LayoutEditView? = null
    private var shiftBtn: Button? = null
    private var title: TextView? = null
    private var note: TextView? = null
    private var weightTv: TextView? = null
    private var minus: Button? = null
    private var plus: Button? = null
    private var hint: TextView? = null
    private var hist: LayoutHistory? = null          // v0.17.9
    private var undoBtn: Button? = null
    private var redoBtn: Button? = null
    private var emptyBtn: Button? = null
    private var rowTv: TextView? = null
    private var heightTv: TextView? = null
    private var lastEt: EditText? = null              // v0.17.10: the field the picker writes into
    private var warnTitle: TextView? = null
    private var warnBox: LinearLayout? = null
    private var chipTitle: TextView? = null
    private var chipBox: LinearLayout? = null
    private var layoutsNote: TextView? = null          // v0.17.13 (K9)
    private var after = ""                           // message for the note after a structure change (or the reason of a refusal)

    private fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()

    fun show() { try { build() } catch (_: Throwable) { } }

    private fun txt(s: String, sp: Float, col: Int, bold: Boolean = false): TextView {
        val t = TextView(act)
        t.text = s; t.textSize = sp; t.setTextColor(col)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun btn(s: String, onClick: () -> Unit): Button {
        val b = Button(act)
        b.text = s; b.isAllCaps = false
        b.minHeight = dp(AppTokens.TOUCH); b.minimumHeight = dp(AppTokens.TOUCH)
        b.setOnClickListener { try { onClick() } catch (_: Throwable) { } }
        return b
    }

    private fun mini(s: String, onClick: () -> Unit): Button {
        val b = btn(s, onClick)
        b.textSize = 12f
        b.setPadding(dp(4), 0, dp(4), 0)
        b.minWidth = 0; b.minimumWidth = 0
        return b
    }

    private fun addRowOf(card: LinearLayout, vararg bs: Button) {
        val r = LinearLayout(act)
        r.orientation = LinearLayout.HORIZONTAL
        for (b in bs) r.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(r)
    }

    private fun build() {
        val dlg = Dialog(act, android.R.style.Theme_DeviceDefault_NoActionBar)
        try { dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) } catch (_: Throwable) { }
        val ink = AppUi.text(act); val dim = AppUi.dim(act)
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(AppUi.bg(act))

        val bar = LinearLayout(act)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(AppTokens.S4), dp(AppTokens.S1), dp(AppTokens.S2), dp(AppTokens.S1))
        val head = txt("Edit keyboard layout", 20f, ink, true)
        try { head.isAccessibilityHeading = true } catch (_: Throwable) { }
        bar.addView(head, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(btn("Close") { dlg.dismiss() })
        root.addView(bar)

        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(AppTokens.S4), dp(AppTokens.S1), dp(AppTokens.S4), dp(24))

        val m0 = try { LayoutStore.load(act, prefs) } catch (_: Throwable) { null }
        model = m0
        hist = if (m0 != null) LayoutHistory(m0) else null
        if (m0 == null) {   // damaged layout: say so, offer the one-tap repair (the classic keyboard keeps running)
            col.addView(txt("The saved layout cannot be read (" + LayoutStore.lastProblem + "). The classic keyboard is used.", 14f, AppUi.warn(act)))
            col.addView(btn("Rebuild layout from current settings") {
                if (LayoutStore.rebuildFromSettings(act, prefs)) { dlg.dismiss(); LayoutEditorPage(act, prefs, onClose).show() }
            })
            finish(dlg, root, col)
            return
        }

        if (prefs.int("kb_custom", 0) != 1) {   // the layout works only while the switch is On
            val off = txt("The custom keyboard is OFF, so the keyboard does not use this layout yet.", 12f, AppUi.warn(act))
            col.addView(off)
            col.addView(btn("Turn the custom keyboard ON") { prefs.setInt("kb_custom", 1); off.text = "Custom keyboard is ON. Reopen the keyboard to see your layout." })
        }

        layoutsCard(dlg, col, ink, dim)   // v0.17.13 (K9)

        // layer tabs
        val tabRow = LinearLayout(act)
        tabRow.orientation = LinearLayout.HORIZONTAL
        val titles = LayoutEditPlan.editTitles()
        for (i in titles.indices) {
            val b = btn(titles[i]) { selectTab(i) }
            tabBtns.add(b)
            b.minWidth = 0; b.minimumWidth = 0
            tabRow.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val tabScroll = HorizontalScrollView(act)   // v0.17.12 (K8): 8 tabs scroll sideways
        tabScroll.isHorizontalScrollBarEnabled = false
        tabScroll.addView(tabRow)
        col.addView(tabScroll)

        val sb = btn("Shift: off") { val v = view ?: return@btn; v.shift = !v.shift; shiftBtn?.text = if (v.shift) "Shift: on" else "Shift: off" }
        shiftBtn = sb
        col.addView(sb)

        val v = LayoutEditView(act)
        view = v
        v.model = m0
        v.onSelect = { id -> selectKey(id) }
        col.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        hint = txt("Tap a key to select it. Orange outline = warning. Changes are saved at once. Reopen the keyboard to see them.", 12f, dim)
        hint?.setPadding(0, dp(AppTokens.S1), 0, dp(AppTokens.S2))
        col.addView(hint)

        // v0.17.10 (K7) warnings (never block saving) + missing-letter chips
        val wc = LinearLayout(act)
        wc.orientation = LinearLayout.VERTICAL
        wc.setPadding(dp(AppTokens.S3), dp(AppTokens.S2), dp(AppTokens.S3), dp(AppTokens.S2))
        try { wc.background = AppUi.cardBg(act, 12) } catch (_: Throwable) { }
        val wt = txt("Warnings: none", 14f, ink, true)
        warnTitle = wt
        wc.addView(wt)
        val wb = LinearLayout(act)
        wb.orientation = LinearLayout.VERTICAL
        warnBox = wb
        wc.addView(wb)
        val ct = txt("", 12f, dim)
        chipTitle = ct
        wc.addView(ct)
        val cs = HorizontalScrollView(act)
        val cb = LinearLayout(act)
        cb.orientation = LinearLayout.HORIZONTAL
        chipBox = cb
        cs.addView(cb)
        wc.addView(cs)
        col.addView(wc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // v0.17.9 (K6b) structure card: undo / redo, move, merge, split, empty / fill, add, delete, row tools
        val st = LinearLayout(act)
        st.orientation = LinearLayout.VERTICAL
        st.setPadding(dp(AppTokens.S3), dp(AppTokens.S3), dp(AppTokens.S3), dp(AppTokens.S3))
        try { st.background = AppUi.cardBg(act, 12) } catch (_: Throwable) { }
        st.addView(txt("Keys (the selected key)", 14f, ink, true))
        val ub = mini("Undo") { undoRedo(false) }
        val rb = mini("Redo") { undoRedo(true) }
        undoBtn = ub; redoBtn = rb
        addRowOf(st, ub, rb)
        addRowOf(st, mini("Move left") { structOp("move left") { m -> LayoutOps.moveLeft(m, selected) } },
            mini("Move right") { structOp("move right") { m -> LayoutOps.moveRight(m, selected) } },
            mini("Move up") { structOp("move up") { m -> LayoutOps.moveUp(m, selected) } },
            mini("Move down") { structOp("move down") { m -> LayoutOps.moveDown(m, selected) } })
        val eb = mini("Make empty") { toggleEmpty() }
        emptyBtn = eb
        addRowOf(st, mini("Merge next") { structOp("merge") { m ->
                val r = LayoutOps.merge(m, selected)
                after = if (r !== m && LayoutOps.mergeMovesSecond(m, selected)) "Merged. The second key's letter is now the long-press of the first key." else ""
                r } },
            mini("Split") { structOp("split") { m -> LayoutOps.split(m, selected) } },
            eb,
            mini("Delete") { structOp("delete key") { m -> LayoutOps.delete(m, selected) } })
        addRowOf(st, mini("Add before") { structOp("add key before") { m -> LayoutOps.insertBefore(m, selected) } },
            mini("Add after") { structOp("add key after") { m -> LayoutOps.insertAfter(m, selected) } },
            mini("Copy style to next") { structOp("copy style") { m ->
                val n = LayoutEditPlan.neighbourId(m, LayoutEditPlan.editTabId(tab), selected, 1)
                if (n.isEmpty()) m else LayoutOps.copyKeyStyle(m, selected, n) } })
        addRowOf(st, mini("Function key (Tab, emoji, ABC, ...)") { pickAction() })   // v0.17.12 (K8)
        val rt = txt("", 12f, dim)
        rt.setPadding(0, dp(AppTokens.S2), 0, 0)
        rowTv = rt
        st.addView(rt)
        val hr = LinearLayout(act)
        hr.orientation = LinearLayout.HORIZONTAL
        hr.gravity = Gravity.CENTER_VERTICAL
        hr.addView(txt("Row height", 14f, ink), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val hm = mini("-") { stepRowHeight(-1) }
        hm.contentDescription = "Row lower"
        hr.addView(hm, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))
        val ht = txt("100%", 16f, ink, true)
        ht.gravity = Gravity.CENTER
        ht.minWidth = dp(64)
        heightTv = ht
        hr.addView(ht)
        val hp = mini("+") { stepRowHeight(1) }
        hp.contentDescription = "Row taller"
        hr.addView(hp, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))
        st.addView(hr)
        addRowOf(st, mini("Add row below") { structOp("add row") { m -> val r = ownRowOrNote(m); if (r < 0) m else LayoutOps.addRow(m, LayoutEditPlan.editTabId(tab), r) } },
            mini("Duplicate row") { structOp("duplicate row") { m -> val r = ownRowOrNote(m); if (r < 0) m else LayoutOps.duplicateRow(m, LayoutEditPlan.editTabId(tab), r) } },
            mini("Delete row") { structOp("delete row") { m -> val r = ownRowOrNote(m); if (r < 0) m else LayoutOps.deleteRow(m, LayoutEditPlan.editTabId(tab), r) } })
        addRowOf(st, mini("Row up") { structOp("row up") { m -> val r = ownRowOrNote(m); if (r < 0) m else LayoutOps.swapRows(m, LayoutEditPlan.editTabId(tab), r, r - 1) } },
            mini("Row down") { structOp("row down") { m -> val r = ownRowOrNote(m); if (r < 0) m else LayoutOps.swapRows(m, LayoutEditPlan.editTabId(tab), r, r + 1) } },
            mini("Same width") { structOp("same width in row") { m -> val o = LayoutEditPlan.owner(m, selected); if (o == null) m else LayoutOps.autoBalanceRow(m, o[0], o[1].toInt()) } },
            mini("Width to row") { structOp("this width for the row") { m -> LayoutOps.applyWidthToRow(m, selected) } })
        col.addView(st, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // inspector
        val card = LinearLayout(act)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(AppTokens.S3), dp(AppTokens.S3), dp(AppTokens.S3), dp(AppTokens.S3))
        try { card.background = AppUi.cardBg(act, 12) } catch (_: Throwable) { }
        title = txt("", 14f, ink, true)
        card.addView(title)
        addField(card, "Label (shown on the key)", LayoutEditPlan.limit(LayoutEditPlan.F_LABEL), { k -> k.label[0] }, { m, id, t -> LayoutOps.setLabel(m, id, 0, t) })
        addField(card, "Tap output", LayoutEditPlan.limit(LayoutEditPlan.F_OUT), { k -> k.out[0] }, { m, id, t -> LayoutOps.setOut(m, id, 0, t) })
        addField(card, "Shift output (empty = capital letter)", LayoutEditPlan.limit(LayoutEditPlan.F_SHIFT), { k -> k.out[1] }, { m, id, t -> LayoutOps.setOut(m, id, 1, t) })
        addField(card, "Long-press text", LayoutEditPlan.limit(LayoutEditPlan.F_LP), { k -> k.lp }, { m, id, t -> LayoutOps.setLp(m, id, t) })

        for (i in 0..3) {
            addField(card, LayoutEditPlan.SWIPE_NAMES[i] + " (text or @action)", LayoutEditPlan.limit(LayoutEditPlan.F_SWIPE), { k -> k.sw[i] }, { m, id, t -> LayoutOps.setSwipe(m, id, i, t) })
        }
        card.addView(btn("Pick from list (symbols, Bangla, taka, actions)") { pick() })

        val wr = LinearLayout(act)
        wr.orientation = LinearLayout.HORIZONTAL
        wr.gravity = Gravity.CENTER_VERTICAL
        wr.addView(txt("Width", 14f, ink), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val mi = btn("-") { stepWidth(-1) }
        mi.contentDescription = "Narrower"
        minus = mi
        wr.addView(mi)
        val wv = txt("1", 16f, ink, true)
        wv.gravity = Gravity.CENTER
        wv.minWidth = dp(56)
        weightTv = wv
        wr.addView(wv)
        val pl = btn("+") { stepWidth(1) }
        pl.contentDescription = "Wider"
        plus = pl
        wr.addView(pl)
        card.addView(wr)

        note = txt("", 12f, dim)
        card.addView(note)
        col.addView(card)

        finish(dlg, root, col)
        dlg.setOnDismissListener { try { flushPending() } catch (_: Throwable) { }; try { onClose() } catch (_: Throwable) { } }
        selectTab(0)
    }


    // ---------- v0.17.13 (K9): saved layouts, export / import, reset ----------
    private fun reopen(dlg: Dialog) { dlg.dismiss(); LayoutEditorPage(act, prefs, onClose).show() }

    private fun askName(title: String, initial: String, onOk: (String) -> Unit) {
        val et = EditText(act)
        et.setText(initial); et.setSelection(et.text.length)
        et.setSingleLine(true)
        et.filters = arrayOf(InputFilter.LengthFilter(com.mgb.keyboard.layout.LayoutBook.MAX_NAME))
        AlertDialog.Builder(act).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun sure(title: String, msg: String, yes: String, onYes: () -> Unit) {
        AlertDialog.Builder(act).setTitle(title).setMessage(msg)
            .setPositiveButton(yes) { _, _ -> onYes() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun savedNames(): Array<String> = LayoutStore.book(act).names().toTypedArray()

    private fun pickSaved(title: String, onPick: (String) -> Unit) {
        val names = savedNames()
        if (names.isEmpty()) { say("No saved layouts yet. Use Save as first.", true); return }
        AlertDialog.Builder(act).setTitle(title).setItems(names) { _, i -> onPick(names[i]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun say(msg: String, warn: Boolean) {
        layoutsNote?.setTextColor(if (warn) AppUi.warn(act) else AppUi.dim(act))
        layoutsNote?.text = msg
    }

    private fun layoutsStatus() {
        val b = LayoutStore.book(act)
        val a = b.active()
        layoutsNote?.setTextColor(AppUi.dim(act))
        layoutsNote?.text = (if (a.isEmpty()) "Active layout: not saved under a name" else "Active layout: " + a) + " (" + b.size() + " saved)"
    }

    private fun layoutsCard(dlg: Dialog, col: LinearLayout, ink: Int, dim: Int) {
        val card = LinearLayout(act)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(AppTokens.S3), dp(AppTokens.S2), dp(AppTokens.S3), dp(AppTokens.S2))
        try { card.background = AppUi.cardBg(act, 12) } catch (_: Throwable) { }
        card.addView(txt("Saved layouts", 14f, ink, true))
        val nt = txt("", 12f, dim)
        layoutsNote = nt
        card.addView(nt)
        addRowOf(card,
            mini("Save as...") {
                flushPending()
                askName("Save current layout as", LayoutStore.book(act).let { b -> if (b.active().isNotEmpty()) b.active() else b.uniqueName("My layout") }) { nm ->
                    val b = LayoutStore.book(act)
                    val doSave = {
                        val r = LayoutStore.saveCurrentAs(act, nm)
                        if (r.isEmpty()) layoutsStatus() else say("Not saved: $r", true)
                    }
                    if (b.has(nm)) sure("Replace \"" + nm + "\"?", "A saved layout with this name exists. It will be replaced by the current layout.", "Replace") { doSave() } else doSave()
                }
            },
            mini("Switch...") {
                flushPending()
                pickSaved("Switch to") { nm ->
                    val go = {
                        val r = LayoutStore.switchTo(act, nm)
                        if (r.isEmpty()) reopen(dlg) else say("Not switched: $r", true)
                    }
                    if (LayoutStore.hasUnsavedChanges(act)) sure("Switch to \"" + nm + "\"?", "Your current layout is not saved under its name, so switching replaces it. Use Save as first to keep it.", "Switch") { go() } else go()
                }
            },
            mini("Rename...") {
                pickSaved("Rename which layout?") { old ->
                    askName("New name for \"" + old + "\"", old) { nn ->
                        val r = LayoutStore.renameSaved(act, old, nn)
                        if (r.isEmpty()) layoutsStatus() else say("Not renamed: $r", true)
                    }
                }
            })
        addRowOf(card,
            mini("Duplicate...") {
                pickSaved("Duplicate which layout?") { nm ->
                    val r = LayoutStore.duplicateSaved(act, nm)
                    if (r.isEmpty()) layoutsStatus() else say("Not duplicated: $r", true)
                }
            },
            mini("Delete...") {
                pickSaved("Delete which layout?") { nm ->
                    sure("Delete \"" + nm + "\"?", "The saved copy is removed. Your active layout stays as it is.", "Delete") {
                        val r = LayoutStore.deleteSaved(act, nm)
                        if (r.isEmpty()) layoutsStatus() else say("Not deleted: $r", true)
                    }
                }
            })
        addRowOf(card,
            mini("Export (copy)") {
                flushPending()
                val t = LayoutStore.exportText(act)
                if (t == null) say("Nothing to export: the layout is not valid.", true) else try {
                    val cm = act.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Jabra layout", t))
                    say("Layout copied (" + t.length + " characters). Paste it into a note to keep it.", false)
                } catch (_: Throwable) { say("Could not copy.", true) }
            },
            mini("Import (paste)") {
                flushPending()
                val txtIn = try {
                    val cm = act.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(act)?.toString()
                } catch (_: Throwable) { null }
                if (txtIn.isNullOrBlank()) { say("The clipboard has no text. Copy an exported layout first.", true); return@mini }
                val chk = com.mgb.keyboard.layout.LayoutGuard.gate(txtIn.trim())
                if (!chk.ok()) { say("Not imported: not a valid layout (" + chk.reason + ").", true); return@mini }
                askName("Import as", LayoutStore.book(act).uniqueName("Imported")) { nm ->
                    val b = LayoutStore.book(act)
                    val doIt = {
                        val r = LayoutStore.importText(act, txtIn, nm)
                        if (r.isEmpty()) { layoutsStatus(); layoutsNote?.append(". Imported as \"" + nm + "\". Use Switch to use it.") } else say("Not imported: $r", true)
                    }
                    if (b.has(nm)) sure("Replace \"" + nm + "\"?", "A saved layout with this name exists. It will be replaced by the imported one.", "Replace") { doIt() } else doIt()
                }
            },
            mini("Reset to default") {
                sure("Reset the layout?", "The active layout becomes today's classic keyboard again (built from your settings). Saved layouts stay.", "Reset") {
                    if (LayoutStore.rebuildFromSettings(act, prefs)) reopen(dlg) else say("Could not reset.", true)
                }
            })
        col.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layoutsStatus()
    }

    private fun finish(dlg: Dialog, root: LinearLayout, col: LinearLayout) {
        val sv = ScrollView(act)
        sv.isFillViewport = true
        sv.addView(col)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        dlg.setContentView(root)
        dlg.show()
        try { dlg.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } catch (_: Throwable) { }
    }

    private fun addField(card: LinearLayout, caption: String, maxLen: Int, read: (KeyDef) -> String, op: (LayoutDef, String, String) -> LayoutDef) {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.addView(txt(caption, 12f, AppUi.dim(act)))
        val et = EditText(act)
        et.setTextColor(AppUi.text(act))
        et.setHintTextColor(AppUi.dim(act))
        et.isSingleLine = true
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        et.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(maxLen * 2))
        et.contentDescription = caption
        et.setOnFocusChangeListener { _, f -> if (f) lastEt = et }
        box.addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val f = Field(box, et, read, op)
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { }
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { }
            override fun afterTextChanged(s: Editable?) {
                if (loading) return
                pending?.let { h.removeCallbacks(it) }
                val r = Runnable { pending = null; apply(f) }
                pending = r
                h.postDelayed(r, 350L)   // saved 350 ms after the last key press
            }
        })
        fields.add(f)
        card.addView(box)
    }

    private fun flushPending() {
        val r = pending ?: return
        h.removeCallbacks(r)
        pending = null
        r.run()
    }

    private fun apply(f: Field) {
        val m = model ?: return
        val id = selected
        if (id.isEmpty()) return
        commit(f.op(m, id, f.et.text.toString()), true, "edit text")
    }

    /** nm === model means the operation was refused (nothing changed). Saves through the safety gate; a refused save keeps the old model. */
    private fun commit(nm: LayoutDef, keepText: Boolean, label: String = "edit text"): Boolean {
        val old = model ?: return false
        if (nm === old) { if (!keepText) fillInspector(); return false }
        val r = LayoutStore.save(act, nm)
        if (r.isEmpty()) {
            model = nm
            view?.model = nm
            hist?.commit(nm, label)
            refreshWarnings()
            updateTitleAndWidth()
            note?.setTextColor(AppUi.dim(act)); note?.text = "Saved"
            return true
        }
        note?.setTextColor(AppUi.warn(act)); note?.text = "Not saved: $r"
        fillInspector()
        return false
    }

    /** v0.17.9: one structure change. Refused (same model) = a note, nothing changes. Saved through the safety gate; the selection stays on the key or moves to the key now at its place. */
    private fun structOp(label: String, f: (LayoutDef) -> LayoutDef) {
        flushPending()
        val m = model ?: return
        val layer = LayoutEditPlan.editTabId(tab)
        if (selected.isEmpty() || LayoutEditPlan.findKey(m, layer, selected) == null) return
        val loc = LayoutEditPlan.locate(m, layer, selected)
        after = ""
        val nm = f(m)
        if (nm === m) {
            note?.setTextColor(AppUi.warn(act))
            note?.text = if (after.isNotEmpty()) after else LayoutEditPlan.refusedText(label)
            after = ""
            return
        }
        if (commit(nm, true, label)) {
            selected = LayoutEditPlan.keepOrNear(nm, layer, selected, loc)
            view?.selectedId = selected
            if (after.isEmpty()) after = "Done: $label"
            fillInspector()
        } else after = ""
    }

    /** Row index of the selected key in the OWN rows of this tab, or -1 + a note when it sits in a shared row (number row / bottom row). */
    private fun ownRowOrNote(m: LayoutDef): Int {
        val r = LayoutEditPlan.ownRow(m, LayoutEditPlan.editTabId(tab), selected)
        if (r < 0) after = "This row is shared by all language tabs: only its height can be changed here."
        return r
    }

    private fun stepRowHeight(dir: Int) {
        flushPending()
        val m = model ?: return
        val o = LayoutEditPlan.owner(m, selected) ?: return
        val row = o[1].toInt()
        val cur = m.layer(o[0])?.rows?.get(row)?.heightPct ?: return
        commit(LayoutOps.setRowHeight(m, o[0], row, LayoutEditPlan.nextHeight(cur, dir)), true, "row height")
    }

    private fun toggleEmpty() {
        flushPending()
        val k = LayoutEditPlan.findKey(model, LayoutEditPlan.editTabId(tab), selected) ?: return
        if (k.kind == KeyDef.SPACER) askFill() else structOp("make empty") { m -> LayoutOps.makeSpacer(m, selected) }
    }

    private fun askFill() {
        val et = EditText(act)
        et.isSingleLine = true
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        et.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(LayoutEditPlan.limit(LayoutEditPlan.F_OUT) * 2))
        AlertDialog.Builder(act).setTitle("Letter for this empty spot").setView(et)
            .setPositiveButton("Fill") { _, _ ->
                val t = et.text.toString()
                structOp("fill") { m -> if (t.isEmpty()) m else LayoutOps.fill(m, selected, t) }
            }
            .setNegativeButton("Cancel", null).show()
    }

    /** v0.17.10: the picker. Writes the chosen text into the field that was focused last (@actions only into swipe / long-press fields). */
    /** v0.17.12 (K8): turns the selected key into a function key (Tab, emoji, ABC, space, ...). One undo step; refused = note. */
    private fun pickAction() {
        flushPending()
        val m = model ?: return
        val layer = LayoutEditPlan.editTabId(tab)
        if (selected.isEmpty() || LayoutEditPlan.findKey(m, layer, selected) == null) {
            note?.setTextColor(AppUi.warn(act)); note?.text = "Tap a key first, then Function key."
            return
        }
        val ids = LayoutEditPlan.actionIds(layer)
        val names = Array(ids.size) { LayoutEditPlan.actionTitle(ids[it], layer) }
        AlertDialog.Builder(act).setTitle("Function key").setItems(names) { _, i ->
            structOp("function key") { mm -> LayoutOps.setAction(mm, selected, ids[i]) }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun pick() {
        flushPending()
        val et = lastEt
        val fi = fields.indexOfFirst { it.et === et }
        if (et == null || fi < 0 || et.visibility != android.view.View.VISIBLE || LayoutEditPlan.findKey(model, LayoutEditPlan.editTabId(tab), selected) == null) {
            note?.setTextColor(AppUi.warn(act)); note?.text = "Tap a text field first, then Pick from list."
            return
        }
        val titles = LayoutEditPlan.pickTitles()
        AlertDialog.Builder(act).setTitle("Pick").setItems(titles) { _, g ->
            if (g == 7 && fi < 3) {   // fields 0-2 = label / tap / shift: an @action there would be plain text
                note?.setTextColor(AppUi.warn(act)); note?.text = "Actions work only in a swipe or long-press field."
                return@setItems
            }
            val items = LayoutEditPlan.pickItems(g)
            val names = Array(items.size) { LayoutEditPlan.pickName(items[it]) }
            AlertDialog.Builder(act).setTitle(titles[g]).setItems(names) { _, i ->
                et.setText(items[i]); et.setSelection(et.text.length)
            }.setNegativeButton("Cancel", null).show()
        }.setNegativeButton("Cancel", null).show()
    }

    /** v0.17.10: warning list of this tab (tap = select the key, Fix = safe auto-fix as ONE undo step) + chips of letters no key types. */
    private fun refreshWarnings() {
        val m = model ?: return
        val layer = LayoutEditPlan.editTabId(tab)
        val all = try {
            val dm = act.resources.displayMetrics
            val langs = 1 + (if (prefs.bool("lang_avro_on", true)) 1 else 0) + (if (prefs.bool("lang_probhat_on", true)) 1 else 0)
            LayoutCheck.check(m, LayoutCheck.Env(dm.widthPixels / dm.density, langs))
        } catch (_: Throwable) { emptyList<LayoutCheck.Warn>() }
        val ids = HashSet<String>()
        for (l in arrayOf(layer, "nr", "bot").distinct()) ids.addAll(LayoutEditPlan.warnKeyIds(all, l, LayoutCheck.LOW))
        view?.warnIds = ids
        val mine = ArrayList<LayoutCheck.Warn>()
        for (l in arrayOf(layer, "nr", "bot").distinct()) mine.addAll(LayoutEditPlan.warnsOf(all, l))
        warnTitle?.text = if (mine.isEmpty()) "Warnings: none" else "Warnings (" + mine.size + ") - they never block saving"
        val box = warnBox ?: return
        box.removeAllViews()
        for (w in mine.take(LayoutEditPlan.MAX_WARN_SHOWN)) {
            val r = LinearLayout(act)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            val t = txt(LayoutEditPlan.warnLine(w), 12f, if (w.severity >= LayoutCheck.HIGH) AppUi.warn(act) else AppUi.text(act))
            t.setPadding(0, dp(AppTokens.S1), 0, dp(AppTokens.S1))
            t.setOnClickListener {
                val id = if (w.keyIds.isEmpty()) "" else w.keyIds[0]
                if (id.isNotEmpty() && LayoutEditPlan.findKey(model, layer, id) != null) { selected = id; view?.selectedId = id; fillInspector() }
            }
            r.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (w.fixable) r.addView(mini("Fix") { fixWarning(w) }, LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT))
            box.addView(r)
        }
        if (mine.size > LayoutEditPlan.MAX_WARN_SHOWN) box.addView(txt("... and " + (mine.size - LayoutEditPlan.MAX_WARN_SHOWN) + " more", 12f, AppUi.dim(act)))
        val chips = LayoutEditPlan.coverageItems(all, layer)
        chipTitle?.text = if (chips.isEmpty()) "" else "Letters no key types (tap one = put it into the first empty spot):"
        val cb = chipBox ?: return
        cb.removeAllViews()
        for (c in chips) cb.addView(mini(LayoutEditPlan.pickName(c)) { fillChip(c) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun fixWarning(w: LayoutCheck.Warn) {
        flushPending()
        val m = model ?: return
        val layer = LayoutEditPlan.editTabId(tab)
        val nm = try { LayoutCheck.autoFix(m, w) } catch (_: Throwable) { m }
        if (nm === m) { note?.setTextColor(AppUi.warn(act)); note?.text = "No safe automatic fix for this one."; return }
        val loc = LayoutEditPlan.locate(m, layer, selected)
        if (commit(nm, true, "fix " + w.code)) {
            selected = LayoutEditPlan.keepOrNear(nm, layer, selected, loc)
            view?.selectedId = selected
            after = "Fixed (one Undo step)."
            fillInspector()
        }
    }

    private fun fillChip(item: String) {
        flushPending()
        val m = model ?: return
        val layer = LayoutEditPlan.editTabId(tab)
        val id = LayoutEditPlan.firstSpacerId(m, layer)
        if (id.isEmpty()) { note?.setTextColor(AppUi.warn(act)); note?.text = "No empty spot: select a key and tap Make empty first."; return }
        if (commit(LayoutOps.fill(m, id, item), true, "put " + item)) {
            selected = id
            view?.selectedId = id
            after = "Put it into the empty spot."
            fillInspector()
        }
    }

    private fun undoRedo(redo: Boolean) {
        flushPending()
        val h = hist ?: return
        val layer = LayoutEditPlan.editTabId(tab)
        val loc = LayoutEditPlan.locate(model, layer, selected)
        val label = if (redo) h.redoLabel() else h.undoLabel()
        val m = (if (redo) h.redo() else h.undo()) ?: return
        val r = LayoutStore.save(act, m)
        if (r.isNotEmpty()) {   // the old state cannot be saved any more: stay where we were
            if (redo) h.undo() else h.redo()
            note?.setTextColor(AppUi.warn(act)); note?.text = "Not saved: $r"
            updateTitleAndWidth()
            return
        }
        model = m
        view?.model = m
        selected = LayoutEditPlan.keepOrNear(m, layer, selected, loc)
        view?.selectedId = selected
        refreshWarnings()
        after = (if (redo) "Redone" else "Undone") + (if (label.isEmpty()) "" else ": $label")
        fillInspector()
    }

    private fun stepWidth(dir: Int) {
        flushPending()
        val m = model ?: return
        val k = LayoutEditPlan.findKey(m, LayoutEditPlan.editTabId(tab), selected) ?: return
        commit(LayoutOps.setWeight(m, selected, LayoutEditPlan.nextWeight(k.weight, dir)), true, "width")
    }

    private fun selectTab(i: Int) {
        flushPending()
        tab = i.coerceIn(0, LayoutEditPlan.EDIT_TABS.size - 1)
        val dark = AppUi.isDark(act)
        for (j in tabBtns.indices) {
            val on = j == tab
            tabBtns[j].backgroundTintList = if (on) ColorStateList.valueOf(AppUi.accent(act)) else null
            tabBtns[j].setTextColor(if (on) (if (dark) Color.BLACK else Color.WHITE) else AppUi.text(act))
        }
        val id = LayoutEditPlan.editTabId(tab)
        view?.layer = id
        val first = LayoutEditPlan.firstCharId(model, id)
        selected = first
        view?.selectedId = first
        refreshWarnings()
        fillInspector()
    }

    private fun selectKey(id: String) {
        flushPending()
        selected = id
        fillInspector()
    }

    private fun updateTitleAndWidth() {
        val m = model
        val layer = LayoutEditPlan.editTabId(tab)
        val k = LayoutEditPlan.findKey(m, layer, selected)
        title?.text = if (k == null) "No key selected" else "Selected: " + LayoutEditPlan.describe(m, layer, selected, false)
        weightTv?.text = if (k == null) "-" else LayoutEditPlan.weightText(k.weight)
        val hs = hist
        val cu = hs != null && hs.canUndo(); val cr = hs != null && hs.canRedo()
        undoBtn?.text = LayoutEditPlan.undoText(cu, hs?.undoLabel() ?: ""); undoBtn?.isEnabled = cu
        redoBtn?.text = LayoutEditPlan.redoText(cr, hs?.redoLabel() ?: ""); redoBtn?.isEnabled = cr
        emptyBtn?.text = if (k?.kind == KeyDef.SPACER) "Fill" else "Make empty"
        rowTv?.text = if (k == null) "" else LayoutEditPlan.rowSummary(m, layer, selected)
        val o = LayoutEditPlan.owner(m, selected)
        heightTv?.text = if (m == null || o == null) "-" else ((m.layer(o[0])?.rows?.get(o[1].toInt())?.heightPct ?: 100).toString() + "%")
    }

    /** Puts the selected key into the inspector (hides the fields the key kind does not have). */
    private fun fillInspector() {
        val layer = LayoutEditPlan.editTabId(tab)
        val k = LayoutEditPlan.findKey(model, layer, selected)
        val mask = if (k == null) 0 else LayoutEditPlan.fields(k.kind)
        loading = true
        try {
            val charMask = intArrayOf(LayoutEditPlan.F_LABEL, LayoutEditPlan.F_OUT, LayoutEditPlan.F_SHIFT, LayoutEditPlan.F_LP, LayoutEditPlan.F_SWIPE, LayoutEditPlan.F_SWIPE, LayoutEditPlan.F_SWIPE, LayoutEditPlan.F_SWIPE)
            for (i in fields.indices) {
                val f = fields[i]
                val show = k != null && LayoutEditPlan.has(mask, charMask[i])
                f.box.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
                if (show && k != null) f.et.setText(f.read(k)) else f.et.setText("")
            }
        } finally { loading = false }
        val widthOn = k != null && LayoutEditPlan.has(mask, LayoutEditPlan.F_WIDTH)
        minus?.isEnabled = widthOn; plus?.isEnabled = widthOn
        updateTitleAndWidth()
        note?.setTextColor(AppUi.dim(act))
        note?.text = when {
            k == null -> ""
            after.isNotEmpty() -> after
            k.kind == KeyDef.SPACER -> "Empty spot: it draws nothing and types nothing. Use Fill to put a letter in it, or Merge / Split / Move."
            k.kind == KeyDef.ACTION -> "Function key (" + k.action + "): width, move, merge, delete work; use Function key to change what it does."
            else -> ""
        }
        after = ""
        view?.selectedId = selected
    }
}
