package com.mgb.keyboard

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.mgb.keyboard.hist.HistDays
import com.mgb.keyboard.hist.HistInfo
import com.mgb.keyboard.hist.HistLog
import com.mgb.keyboard.hist.HistPolicy
import com.mgb.keyboard.typing.AppTokens
import java.util.TimeZone

/**
 * v0.21.0 (H2) the "Typing history" PAGE: a full-screen page (same pattern as PracticePage) opened from the button in the Data & Backup card.
 * Days (Today, Yesterday, dates), newest first; each row = time, app, text. Search box (text or app name; the box is typed in Jabra's own screen,
 * which is never recorded). Tap = copy to the clipboard (it then also lands in clipboard history like any copy). Hold = delete ONE entry, with a short Undo bar.
 * "Export" = share sheet, "Save" = Downloads / chosen folder (both through ClipExporter: no new permission). "Clear all" = two taps.
 * Data = HistStore.entries(); rules + texts = hist/HistDays.java (tested on a computer). App name = PackageManager label when Android lets us see the
 * package, else a cleaned package name (no QUERY_ALL_PACKAGES). Everything is in try / catch: a problem = the page does not open, the card still works.
 */
object HistPage {
    private const val PAGE = 150          // rows drawn at once; "Show more" adds this many
    private const val UNDO_MS = 6000L
    private const val SEARCH_MS = 250L

    fun open(ctx: Context, onClose: () -> Unit) {
        try { show(ctx, onClose) } catch (_: Throwable) { }
    }

    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    private fun show(ctx: Context, onClose: () -> Unit) {
        val text = AppUi.text(ctx)
        val dim = AppUi.dim(ctx)
        val warnC = AppUi.warn(ctx)
        val prefs = JabraPrefs(ctx)
        val tz = TimeZone.getDefault()
        val h = Handler(Looper.getMainLooper())
        val pm = ctx.packageManager
        val names = HashMap<String, String>()
        val labeler = object : HistDays.Labeler {
            override fun label(pkg: String?): String? {
                if (pkg == null || pkg.isEmpty()) return null
                val c = names[pkg]
                if (c != null) return if (c.isEmpty()) null else c
                var s = ""
                try { s = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Throwable) { }
                names[pkg] = s
                return if (s.isEmpty()) null else s
            }
        }

        HistStore.ensure(ctx)
        HistStore.pruneNow()

        val dlg = Dialog(ctx, android.R.style.Theme_Material_NoActionBar)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(AppUi.bg(ctx))
        root.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2), dp(ctx, AppTokens.S3), 0)

        // ---- top bar ----
        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(ctx)
        title.text = "Typing history"
        title.textSize = AppTokens.T_HEAD.toFloat()
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(text)
        try { title.isAccessibilityHeading = true } catch (_: Throwable) { }
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = Button(ctx)
        close.isAllCaps = false
        close.text = "Close"
        close.minimumHeight = dp(ctx, AppTokens.TOUCH)
        AppRows.styleAction(ctx, close)
        close.setOnClickListener { try { dlg.dismiss() } catch (_: Throwable) { } }
        bar.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(bar)

        fun small(color: Int): TextView {
            val t = TextView(ctx)
            t.textSize = AppTokens.T_BODY.toFloat()
            t.setTextColor(color)
            t.setPadding(0, dp(ctx, AppTokens.S1), 0, dp(ctx, AppTokens.S1))
            return t
        }
        val status = small(dim)
        root.addView(status)

        // ---- search ----
        val search = EditText(ctx)
        search.hint = "Search text or app"
        search.setSingleLine(true)
        search.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        search.setTextColor(text)
        search.setHintTextColor(dim)
        search.textSize = AppTokens.T_TITLE.toFloat()
        search.background = AppUi.cardBg(ctx, 8)
        search.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2), dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2))
        search.minimumHeight = dp(ctx, AppTokens.TOUCH)
        search.contentDescription = "Search typing history by text or app name"
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- export row ----
        val acts = LinearLayout(ctx)
        acts.orientation = LinearLayout.HORIZONTAL
        root.addView(acts, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        fun actBtn(label: String, on: () -> Unit): Button {
            val b = Button(ctx)
            b.isAllCaps = false
            b.text = label
            AppRows.styleAction(ctx, b)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(ctx, 2), dp(ctx, AppTokens.S2), dp(ctx, 2), 0)
            b.layoutParams = lp
            b.setOnClickListener { try { on() } catch (_: Throwable) { } }
            acts.addView(b)
            return b
        }

        val summary = small(text)
        summary.setTypeface(null, Typeface.BOLD)
        root.addView(summary)
        val hint = small(dim)
        hint.textSize = AppTokens.T_CAPTION.toFloat()
        hint.text = HistDays.hintLine()
        root.addView(hint)

        // ---- list ----
        val sv = ScrollView(ctx)
        val list = LinearLayout(ctx)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, 0, 0, dp(ctx, AppTokens.S4))
        sv.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // ---- undo bar (hidden until an entry is deleted) ----
        val undoBar = LinearLayout(ctx)
        undoBar.orientation = LinearLayout.HORIZONTAL
        undoBar.gravity = Gravity.CENTER_VERTICAL
        undoBar.background = AppUi.card(ctx, AppTokens.R_CARD)
        undoBar.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S1), dp(ctx, AppTokens.S1), dp(ctx, AppTokens.S1))
        undoBar.visibility = View.GONE
        val undoText = TextView(ctx)
        undoText.textSize = AppTokens.T_BODY.toFloat()
        undoText.setTextColor(text)
        undoText.text = HistDays.deletedToast()
        undoBar.addView(undoText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val undoBtn = Button(ctx)
        undoBtn.isAllCaps = false
        undoBtn.text = HistDays.undoLabel()
        undoBtn.minimumHeight = dp(ctx, AppTokens.TOUCH)
        AppRows.styleAction(ctx, undoBtn)
        undoBtn.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        undoBar.addView(undoBtn)
        val ulp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ulp.setMargins(0, dp(ctx, AppTokens.S1), 0, dp(ctx, AppTokens.S2))
        root.addView(undoBar, ulp)

        // ---- state ----
        var limit = PAGE
        var lastShown = 0
        var pending: HistLog.Entry? = null
        var armed = false
        var clearRef: Button? = null

        fun toast(m: String) { try { Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show() } catch (_: Throwable) { } }
        val hideUndo = Runnable { try { undoBar.visibility = View.GONE; pending = null } catch (_: Throwable) { } }
        val disarm = Runnable { armed = false; try { clearRef?.text = HistInfo.clearLabel(false, HistStore.count()) } catch (_: Throwable) { } }

        var render: () -> Unit = { }

        fun copyEntry(e: HistLog.Entry) {
            try {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Typing history", e.text))
                toast(HistDays.copiedToast())
            } catch (_: Throwable) { toast("Could not copy") }
        }

        fun deleteEntry(e: HistLog.Entry) {
            try {
                if (!HistStore.remove(e.id)) return
                pending = e
                undoText.text = HistDays.deletedToast()
                undoBar.visibility = View.VISIBLE
                h.removeCallbacks(hideUndo)
                h.postDelayed(hideUndo, UNDO_MS)
                render()
            } catch (_: Throwable) { }
        }

        fun row(e: HistLog.Entry): View {
            val card = LinearLayout(ctx)
            card.orientation = LinearLayout.VERTICAL
            card.background = AppUi.cardBg(ctx, AppTokens.R_CARD)
            card.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2), dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2))
            val metaText = HistDays.timeText(e.time, tz) + "  \u00B7  " + HistDays.appName(e.app, labeler)
            val meta = TextView(ctx)
            meta.text = metaText
            meta.textSize = AppTokens.T_CAPTION.toFloat()
            meta.setTextColor(dim)
            card.addView(meta)
            val body = TextView(ctx)
            body.text = e.text
            body.textSize = AppTokens.T_TITLE.toFloat()
            body.setTextColor(text)
            body.maxLines = 8
            body.ellipsize = TextUtils.TruncateAt.END
            body.setPadding(0, dp(ctx, 2), 0, 0)
            card.addView(body)
            card.isClickable = true
            card.isFocusable = true
            card.minimumHeight = dp(ctx, AppTokens.TOUCH)
            card.contentDescription = metaText + ". " + e.text + ". Tap to copy. Hold to delete."
            card.setOnClickListener { copyEntry(e) }
            card.setOnLongClickListener { deleteEntry(e); true }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(ctx, AppTokens.S1)
            card.layoutParams = lp
            return card
        }

        render = {
            try {
                list.removeAllViews()
                val q = search.text.toString()
                val all = HistStore.entries()
                val total = all.size
                val on = prefs.bool("hist_on", false)
                val paused = prefs.bool("hist_pause", false)
                val lv = HistPolicy.level(prefs.int("hist_days_lv", HistPolicy.DEF_LEVEL))
                val days = HistDays.group(all, System.currentTimeMillis(), tz, q, labeler)
                val shown = HistDays.count(days)
                lastShown = shown
                status.text = HistInfo.statusLine(on, paused, lv, total)
                summary.text = HistDays.summaryLine(shown, total, q)
                hint.visibility = if (shown > 0) View.VISIBLE else View.GONE

                if (shown == 0) {
                    val et = TextView(ctx)
                    et.text = HistDays.emptyTitle(on, total, q)
                    et.textSize = AppTokens.T_TITLE.toFloat()
                    et.setTypeface(null, Typeface.BOLD)
                    et.setTextColor(text)
                    et.setPadding(0, dp(ctx, AppTokens.S4), 0, dp(ctx, AppTokens.S1))
                    list.addView(et)
                    val eh = TextView(ctx)
                    eh.text = HistDays.emptyHelp(on, total, q)
                    eh.textSize = AppTokens.T_BODY.toFloat()
                    eh.setTextColor(dim)
                    list.addView(eh)
                    if (!on && total == 0) {
                        val gb = Button(ctx)
                        gb.isAllCaps = false
                        gb.text = "Back to Data & Backup"
                        AppRows.styleAction(ctx, gb)
                        gb.setOnClickListener { try { dlg.dismiss() } catch (_: Throwable) { } }
                        list.addView(gb)
                    }
                } else {
                    var drawn = 0
                    for (d in days) {
                        if (drawn >= limit) break
                        list.addView(AppUi.sectionHeader(ctx, d.label))
                        for (e in d.items) {
                            if (drawn >= limit) break
                            list.addView(row(e))
                            drawn++
                        }
                    }
                    if (shown > drawn) {
                        val more = Button(ctx)
                        more.isAllCaps = false
                        more.text = "Show more (" + (shown - drawn) + " left)"
                        AppRows.styleAction(ctx, more)
                        more.setOnClickListener { try { limit += PAGE; render() } catch (_: Throwable) { } }
                        list.addView(more)
                    }
                }

                if (total > 0) {
                    val cb = Button(ctx)
                    cb.isAllCaps = false
                    cb.text = HistInfo.clearLabel(armed, total)
                    AppRows.styleAction(ctx, cb)
                    cb.setOnClickListener {
                        try {
                            val n = HistStore.count()
                            if (n <= 0) return@setOnClickListener
                            if (!armed) {
                                armed = true
                                cb.text = HistInfo.clearLabel(true, n)
                                h.removeCallbacks(disarm)
                                h.postDelayed(disarm, 3000L)
                            } else {
                                armed = false
                                h.removeCallbacks(disarm)
                                val r = HistStore.clearAll(ctx)
                                pending = null
                                undoBar.visibility = View.GONE
                                toast(HistInfo.clearDone(r))
                                render()
                            }
                        } catch (_: Throwable) { }
                    }
                    clearRef = cb
                    list.addView(cb)
                } else {
                    clearRef = null
                }

                val note = TextView(ctx)
                note.text = "Export makes a plain text file outside the app's private storage (share sheet or Downloads). Delete it yourself when you no longer need it."
                note.textSize = AppTokens.T_CAPTION.toFloat()
                note.setTextColor(dim)
                note.setPadding(0, dp(ctx, AppTokens.S3), 0, 0)
                list.addView(note)
            } catch (_: Throwable) { }
        }

        fun exportNow(save: Boolean) {
            val now = System.currentTimeMillis()
            if (lastShown <= 0) { toast("Nothing to export"); return }
            val body = HistDays.exportText(HistStore.entries(), now, tz, search.text.toString(), labeler)
            val name = HistDays.exportName(now, tz)
            val bytes = body.toByteArray(Charsets.UTF_8)
            if (!save) {
                toast(ClipExporter.share(ctx, name, "text/plain", bytes, "Export typing history"))
            } else {
                Thread {
                    val m = try { ClipExporter.saveToDownloads(ctx, name, "text/plain", bytes) } catch (_: Throwable) { "Could not save the file" }
                    h.post { toast(m) }
                }.start()
            }
        }

        actBtn("Export (share)") { exportNow(false) }
        if (ClipExporter.canSave(ctx)) actBtn("Save to Downloads") { exportNow(true) }

        undoBtn.setOnClickListener {
            try {
                val e = pending
                h.removeCallbacks(hideUndo)
                undoBar.visibility = View.GONE
                pending = null
                if (e != null && HistStore.restore(e)) { toast(HistDays.restoredToast()); render() }
            } catch (_: Throwable) { }
        }

        val doSearch = Runnable { try { limit = PAGE; render() } catch (_: Throwable) { } }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
            override fun afterTextChanged(s: Editable?) { h.removeCallbacks(doSearch); h.postDelayed(doSearch, SEARCH_MS) }
        })

        dlg.setOnDismissListener {
            try { h.removeCallbacksAndMessages(null) } catch (_: Throwable) { }
            try { onClose() } catch (_: Throwable) { }
        }

        render()
        try { if (!HistStore.isLoaded()) HistStore.whenLoaded { try { render() } catch (_: Throwable) { } } } catch (_: Throwable) { }

        dlg.setContentView(root)
        try {
            val w = dlg.window
            w?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            w?.setBackgroundDrawable(ColorDrawable(AppUi.bg(ctx)))
            w?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        } catch (_: Throwable) { }
        dlg.show()
    }
}
