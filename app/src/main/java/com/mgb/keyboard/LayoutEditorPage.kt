package com.mgb.keyboard

import android.app.Activity
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.layout.KeyDef
import com.mgb.keyboard.layout.LayoutDef
import com.mgb.keyboard.layout.LayoutOps
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.LayoutEditPlan

/**
 * v0.17.8 (K6a) the keyboard layout editor, page 1: see the layout, tap a key (selected = accent outline), edit its label, tap output,
 * shift output, long-press text and width. Every change goes through LayoutOps + LayoutStore.save (the safety gate S4) at once.
 * Shown as a full-screen dialog from App > Layout & size > "Edit layout" (no new page route, no manifest change). The app screen may use EditText.
 * Structure edits (merge, split, move, add, delete, undo) = K6b; swipes / warnings = K7; number row / symbols / action keys = K8.
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

        // layer tabs
        val tabRow = LinearLayout(act)
        tabRow.orientation = LinearLayout.HORIZONTAL
        val titles = LayoutEditPlan.tabTitles()
        for (i in titles.indices) {
            val b = btn(titles[i]) { selectTab(i) }
            tabBtns.add(b)
            tabRow.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        col.addView(tabRow)

        val sb = btn("Shift: off") { val v = view ?: return@btn; v.shift = !v.shift; shiftBtn?.text = if (v.shift) "Shift: on" else "Shift: off" }
        shiftBtn = sb
        col.addView(sb)

        val v = LayoutEditView(act)
        view = v
        v.model = m0
        v.onSelect = { id -> selectKey(id) }
        col.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        hint = txt("Tap a key to select it. Changes are saved at once. Reopen the keyboard to see them.", 12f, dim)
        hint?.setPadding(0, dp(AppTokens.S1), 0, dp(AppTokens.S2))
        col.addView(hint)

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

        val wr = LinearLayout(act)
        wr.orientation = LinearLayout.HORIZONTAL
        wr.gravity = Gravity.CENTER_VERTICAL
        wr.addView(txt("Width", 14f, ink), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val mi = btn("-") { stepWidth(-1) }
        mi.contentDescription = "Narrower"
        minus = mi
        wr.addView(mi)
        val wt = txt("1", 16f, ink, true)
        wt.gravity = Gravity.CENTER
        wt.minWidth = dp(56)
        weightTv = wt
        wr.addView(wt)
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
        commit(f.op(m, id, f.et.text.toString()), true)
    }

    /** nm === model means the operation was refused (nothing changed). Saves through the safety gate; a refused save keeps the old model. */
    private fun commit(nm: LayoutDef, keepText: Boolean) {
        val old = model ?: return
        if (nm === old) { if (!keepText) fillInspector(); return }
        val r = LayoutStore.save(act, nm)
        if (r.isEmpty()) {
            model = nm
            view?.model = nm
            updateTitleAndWidth()
            note?.setTextColor(AppUi.dim(act)); note?.text = "Saved"
        } else {
            note?.setTextColor(AppUi.warn(act)); note?.text = "Not saved: $r"
            fillInspector()
        }
    }

    private fun stepWidth(dir: Int) {
        flushPending()
        val m = model ?: return
        val k = LayoutEditPlan.findKey(m, LayoutEditPlan.tabId(tab), selected) ?: return
        commit(LayoutOps.setWeight(m, selected, LayoutEditPlan.nextWeight(k.weight, dir)), true)
    }

    private fun selectTab(i: Int) {
        flushPending()
        tab = i.coerceIn(0, LayoutEditPlan.TAB_IDS.size - 1)
        val dark = AppUi.isDark(act)
        for (j in tabBtns.indices) {
            val on = j == tab
            tabBtns[j].backgroundTintList = if (on) ColorStateList.valueOf(AppUi.accent(act)) else null
            tabBtns[j].setTextColor(if (on) (if (dark) Color.BLACK else Color.WHITE) else AppUi.text(act))
        }
        val id = LayoutEditPlan.tabId(tab)
        view?.layer = id
        val first = LayoutEditPlan.firstCharId(model, id)
        selected = first
        view?.selectedId = first
        fillInspector()
    }

    private fun selectKey(id: String) {
        flushPending()
        selected = id
        fillInspector()
    }

    private fun updateTitleAndWidth() {
        val m = model
        val layer = LayoutEditPlan.tabId(tab)
        val k = LayoutEditPlan.findKey(m, layer, selected)
        title?.text = if (k == null) "No key selected" else "Selected: " + LayoutEditPlan.describe(m, layer, selected, false)
        weightTv?.text = if (k == null) "-" else LayoutEditPlan.weightText(k.weight)
    }

    /** Puts the selected key into the inspector (hides the fields the key kind does not have). */
    private fun fillInspector() {
        val layer = LayoutEditPlan.tabId(tab)
        val k = LayoutEditPlan.findKey(model, layer, selected)
        val mask = if (k == null) 0 else LayoutEditPlan.fields(k.kind)
        loading = true
        try {
            val charMask = intArrayOf(LayoutEditPlan.F_LABEL, LayoutEditPlan.F_OUT, LayoutEditPlan.F_SHIFT, LayoutEditPlan.F_LP)
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
            k.kind == KeyDef.SPACER -> "Empty spot: it draws nothing and types nothing. Filling, merging and moving come in the next update."
            k.kind == KeyDef.ACTION -> "Function key (" + k.action + "): only its width can be changed for now."
            else -> ""
        }
        view?.selectedId = selected
    }
}
