package com.mgb.keyboard

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.mgb.keyboard.typing.AppTokens

/**
 * v0.63.0 (PROJECT 2): the Project screen in the MAIN APP (same place as "Find & Replace sets"). It edits exactly the same projects as the
 * Project screen inside the keyboard's Replace tool (same prefs, see ProjectStore), but here the phone's normal keyboard is used,
 * so Bangla / any language can be typed and pasted freely.
 *  - Projects: list, + New project, open one.
 *  - One project: Make active (the project Replace > Fix with project uses), Delete (2 taps),
 *    tab Terms: Add box (gaam; geme; gome => game), term list (tap = edit, X = delete with a second tap), Paste list, Whole words, Search.
 *    tab Keep as is: names that are never changed (type, + Clipboard, X, Clear with a second tap).
 *  Everything is saved at once. The keyboard shows the change the next time Replace is opened.
 * Everything is in try / catch: a problem = the page does not open, the keyboard still works.
 */
object ProjectPage {
    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun button(ctx: Context): Button {
        val b = Button(ctx)
        b.isAllCaps = false
        b.text = "Replace projects (terms, keep as is)"
        b.contentDescription = "Open the Replace projects: terms and names to keep"
        AppRows.styleAction(ctx, b)
        b.setOnClickListener { open(ctx) }
        return b
    }

    /** Small card for Home (under the Find & Replace card). Any problem = null (no card). */
    fun homeCard(ctx: Context): View? = try {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S3))
        card.minimumHeight = dp(ctx, 56)
        try { card.background = AppUi.cardBg(ctx, 12) } catch (_: Throwable) { }
        card.isClickable = true
        card.isFocusable = true
        val t = TextView(ctx)
        t.text = "Replace projects"
        t.textSize = AppTokens.T_TITLE.toFloat()
        t.setTypeface(null, Typeface.BOLD)
        try { t.setTextColor(AppUi.text(ctx)) } catch (_: Throwable) { }
        card.addView(t)
        val s = TextView(ctx)
        s.text = "Terms per project (wrong => right) and names to keep. Used by Replace > Fix with project"
        s.textSize = AppTokens.T_LABEL.toFloat()
        try { s.setTextColor(AppUi.dim(ctx)) } catch (_: Throwable) { }
        card.addView(s)
        card.contentDescription = "Replace projects. Opens the projects page."
        card.setOnClickListener { open(ctx) }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(ctx, AppTokens.S2)
        card.layoutParams = lp
        card
    } catch (_: Throwable) { null }

    fun open(ctx: Context) { try { show(ctx) } catch (_: Throwable) { } }

    private fun show(ctx: Context) {
        val text = AppUi.text(ctx)
        val dim = AppUi.dim(ctx)
        val warn = AppUi.warn(ctx)
        val prefs = JabraPrefs(ctx)
        val dlg = Dialog(ctx, android.R.style.Theme_Material_NoActionBar)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(AppUi.bg(ctx))
        root.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2), dp(ctx, AppTokens.S3), 0)

        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(ctx)
        title.text = "Replace projects"
        title.textSize = AppTokens.T_HEAD.toFloat()
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(text)
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = Button(ctx)
        close.isAllCaps = false
        close.text = "Close"
        close.minimumHeight = dp(ctx, AppTokens.TOUCH)
        AppRows.styleAction(ctx, close)
        close.setOnClickListener { try { dlg.dismiss() } catch (_: Throwable) { } }
        bar.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(bar)

        val scroll = ScrollView(ctx)
        val body = LinearLayout(ctx)
        body.orientation = LinearLayout.VERTICAL
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun label(t: String, color: Int): TextView {
            val v = TextView(ctx)
            v.text = t
            v.textSize = AppTokens.T_BODY.toFloat()
            v.setTextColor(color)
            v.setPadding(0, dp(ctx, AppTokens.S1), 0, dp(ctx, AppTokens.S1))
            return v
        }
        fun act(t: String, onClick: () -> Unit): Button {
            val b = Button(ctx)
            b.isAllCaps = false
            b.text = t
            b.minimumHeight = dp(ctx, AppTokens.TOUCH)
            AppRows.styleAction(ctx, b)
            b.setOnClickListener { try { onClick() } catch (_: Throwable) { } }
            return b
        }
        fun toast(t: String) { try { Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show() } catch (_: Throwable) { } }
        fun box(hint: String, value: String, multi: Boolean): EditText {
            val e = EditText(ctx)
            e.hint = hint
            e.setText(value)
            e.setTextColor(text)
            e.setHintTextColor(dim)
            e.inputType = if (multi) (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) else (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            return e
        }
        fun clipText(): String = try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
        } catch (_: Throwable) { "" }

        var showList: () -> Unit = { }
        var showProject: (String, Int, String, String) -> Unit = { _, _, _, _ -> }   // name, tab (1 Terms / 2 Keep as is), term being edited (Rule.find), text of the Add box

        // ---------------------------------------------------------------- one project
        showProject = { name, tab, editFind, boxText ->
            body.removeAllViews()
            val isActive = ProjectStore.current(prefs).equals(name, true)
            body.addView(act("\u2190 All projects") { showList() })
            val head = label(name + (if (isActive) "   \u00B7 active in Replace" else ""), text)
            head.textSize = AppTokens.T_TITLE.toFloat()
            head.setTypeface(null, Typeface.BOLD)
            body.addView(head)

            val top = LinearLayout(ctx)
            top.orientation = LinearLayout.HORIZONTAL
            top.addView(act(if (isActive) "\u2713 Active (tap: turn off)" else "Make active") {
                ProjectStore.setCurrent(prefs, if (isActive) "" else name)
                showProject(name, tab, editFind, boxText)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            var armed = false
            val del = act("Delete project") { }
            del.setOnClickListener {
                try {
                    if (!armed) { armed = true; del.text = "Sure? Delete"; return@setOnClickListener }
                    toast(ProjectStore.delete(prefs, name))
                    showList()
                } catch (_: Throwable) { }
            }
            top.addView(del, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(top)

            val rows = ProjectStore.rows(prefs, name)
            val keep = ProjectStore.keep(prefs, name)
            val tabs = LinearLayout(ctx)
            tabs.orientation = LinearLayout.HORIZONTAL
            tabs.addView(act((if (tab == 1) "\u25CF " else "") + "Terms  " + rows.size) { showProject(name, 1, "", "") }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            tabs.addView(act((if (tab == 2) "\u25CF " else "") + "Keep as is  " + keep.size) { showProject(name, 2, "", "") }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(tabs)

            if (tab == 1) {
                // ---- Terms
                body.addView(label("One line per term: wrong forms, then => and the right form. Separate several wrong forms with ; or |", dim))
                val lineBox = box("gaam; geme; gome => game", boxText, false)
                body.addView(lineBox)
                val msg = label("", dim)
                val addRow = LinearLayout(ctx)
                addRow.orientation = LinearLayout.HORIZONTAL
                addRow.addView(act(if (editFind.isEmpty()) "+ Add" else "Save") {
                    val r = ProjectStore.addLine(prefs, name, lineBox.text.toString(), editFind)
                    if (r.startsWith("Term")) { toast(r); showProject(name, 1, "", "") } else { msg.text = r; msg.setTextColor(warn) }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (editFind.isNotEmpty()) addRow.addView(act("Cancel") { showProject(name, 1, "", "") }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addRow.addView(act("Paste list") {
                    val r = ProjectStore.pasteLines(prefs, name, clipText())
                    toast(r)
                    showProject(name, 1, "", "")
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                body.addView(addRow)
                body.addView(msg)
                body.addView(act((if (ProjectStore.wholeWord(prefs)) "\u2713 " else "") + "Whole words only (game is not fixed inside gamer)") {
                    ProjectStore.setWholeWord(prefs, !ProjectStore.wholeWord(prefs))
                    showProject(name, 1, editFind, lineBox.text.toString())
                })

                val search = box("Search terms", "", false)
                body.addView(search)
                val listBox = LinearLayout(ctx)
                listBox.orientation = LinearLayout.VERTICAL
                body.addView(listBox)
                var delAsk = ""
                fun fill() {
                    listBox.removeAllViews()
                    val q = search.text.toString().trim().lowercase()
                    val shown = rows.filter { r -> q.isEmpty() || (r.formsText() + " " + r.to).lowercase().indexOf(q) >= 0 }
                    if (shown.isEmpty()) listBox.addView(label(if (q.isNotEmpty()) "No match." else "No terms yet. Type a term above and tap + Add.", dim))
                    for (r in shown) {
                        val row = LinearLayout(ctx)
                        row.orientation = LinearLayout.HORIZONTAL
                        row.gravity = Gravity.CENTER_VERTICAL
                        val tv = act(r.to + "   \u2190 " + r.formsText()) {
                            showProject(name, 1, r.rule.find, r.formsText() + " => " + r.to)
                        }
                        tv.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        val key = r.key()
                        val x = act(if (delAsk == key) "Delete?" else "\u2715") { }
                        x.setOnClickListener {
                            try {
                                if (delAsk == key) { delAsk = ""; toast(ProjectStore.deleteTerm(prefs, name, r.rule.find)); showProject(name, 1, "", "") }
                                else { delAsk = key; fill() }
                            } catch (_: Throwable) { }
                        }
                        row.addView(x, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                        listBox.addView(row)
                    }
                    if (shown.isNotEmpty()) listBox.addView(label("Tap a term to edit it. \u2715 deletes (tap twice).", dim))
                }
                search.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { }
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { }
                    override fun afterTextChanged(s: Editable?) { delAsk = ""; fill() }
                })
                fill()
            } else {
                // ---- Keep as is
                body.addView(label("Names Replace never changes, even when a term matches part of them.", dim))
                val nameBox = box("Name to keep as is (one per line is fine)", "", true)
                body.addView(nameBox)
                val kRow = LinearLayout(ctx)
                kRow.orientation = LinearLayout.HORIZONTAL
                kRow.addView(act("+ Add") {
                    val r = ProjectStore.addKeep(prefs, name, nameBox.text.toString())
                    toast(r); showProject(name, 2, "", "")
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                kRow.addView(act("+ Clipboard") {
                    val r = ProjectStore.addKeep(prefs, name, clipText())
                    toast(r); showProject(name, 2, "", "")
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (keep.isNotEmpty()) {
                    var armed2 = false
                    val clr = act("Clear") { }
                    clr.setOnClickListener {
                        try {
                            if (!armed2) { armed2 = true; clr.text = "Sure? Clear all"; return@setOnClickListener }
                            ProjectStore.clearKeep(prefs, name)
                            toast("Keep as is cleared"); showProject(name, 2, "", "")
                        } catch (_: Throwable) { }
                    }
                    kRow.addView(clr, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
                body.addView(kRow)
                if (keep.isEmpty()) body.addView(label("Nothing kept yet.", dim))
                for (k in keep) {
                    body.addView(act(k + "   \u2715") {
                        ProjectStore.removeKeep(prefs, name, k)
                        showProject(name, 2, "", "")
                    })
                }
            }
        }

        // ---------------------------------------------------------------- list of projects
        showList = {
            body.removeAllViews()
            body.addView(label("A project holds the terms (wrong => right) and the names to keep for one book. In the keyboard: Replace > Project, then Fix with project. The active project is the one Replace uses.", dim))
            val list = ProjectStore.infos(prefs)
            body.addView(label("My projects (" + list.size + ")", text))
            if (list.isEmpty()) body.addView(label("None yet. Type a name below and tap + Create.", dim))
            for (n in list) {
                val s = (if (n.active) "\u25CF " else "\u25CB ") + n.name + "  \u00B7  " + n.terms + (if (n.terms == 1) " term" else " terms") + (if (n.kept > 0) "  \u00B7  " + n.kept + " kept" else "")
                body.addView(act(s) { showProject(n.name, 1, "", "") })
            }
            val nb = box("New project name", "", false)
            body.addView(nb)
            val msg = label("", dim)
            body.addView(act("+ Create") {
                val r = ProjectStore.create(prefs, nb.text.toString())
                if (r.startsWith("Created") || r.startsWith("That project")) {
                    toast(r)
                    val nm = ProjectStore.current(prefs)
                    if (nm.isNotEmpty()) showProject(nm, 1, "", "") else showList()
                } else { msg.text = r; msg.setTextColor(warn) }
            })
            body.addView(msg)
            if (list.isNotEmpty() && ProjectStore.current(prefs).isNotEmpty()) {
                body.addView(act("No active project") { ProjectStore.setCurrent(prefs, ""); showList() })
            }
        }
        showList()

        dlg.setContentView(root)
        try { dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) } catch (_: Throwable) { }
        dlg.show()
    }
}
