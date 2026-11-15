package com.mgb.keyboard

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.hist.HistDays
import com.mgb.keyboard.hist.HistInfo
import com.mgb.keyboard.hist.HistPause
import com.mgb.keyboard.hist.HistUi
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.hist.HistPolicy

/**
 * v0.20.0 (H1) "Typing history" card inside Data & Backup: ON / OFF (default OFF, a confirm dialog the first time), keep time 1 / 3 / 7 / 30 days,
 * two optional skips (email / URL / number fields, incognito fields), Pause, and "Clear typing history now" (two taps).
 * Texts come from hist/HistInfo.java (tested on a computer). v0.21.0 (H2): the button "Open typing history" opens the history PAGE (HistPage.kt: days, search, copy, delete, export).
 * Called inside try/catch from DataPage: any problem = the card is simply missing.
 */
object HistCard {
    /** v0.23.0 (H3): the new look (hero status card, switches, segmented choices, pause picker). Any problem = the classic button list below. */
    fun build(ctx: Context): View = try { buildModern(ctx) } catch (_: Throwable) { buildClassic(ctx) }

    private fun buildModern(ctx: Context): View {
        val prefs = JabraPrefs(ctx)
        fun dp(v: Int) = AppUi.dp(ctx, v)
        val text = AppUi.text(ctx)
        val dim = AppUi.dim(ctx)
        val root = LinearLayout(ctx); root.orientation = LinearLayout.VERTICAL
        root.setPadding(0, dp(AppTokens.S1), 0, dp(AppTokens.S1))
        val body = LinearLayout(ctx); body.orientation = LinearLayout.VERTICAL
        val msg = TextView(ctx); msg.setPadding(0, dp(AppTokens.S2), 0, dp(AppTokens.S2)); msg.setTextColor(dim); msg.textSize = AppTokens.T_BODY.toFloat()
        lateinit var fill: () -> Unit

        fun lp(top: Int = 0): LinearLayout.LayoutParams {
            val l = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            l.topMargin = dp(top); return l
        }
        fun tv(t: String, size: Int, color: Int, bold: Boolean = false): TextView {
            val v = TextView(ctx); v.text = t; v.textSize = size.toFloat(); v.setTextColor(color)
            if (bold) v.setTypeface(null, Typeface.BOLD)
            return v
        }

        fill = {
            body.removeAllViews()
            HistStore.ensure(ctx)
            HistStore.pruneNow()
            val on = prefs.bool("hist_on", false)
            val paused = HistStore.isPaused(prefs)
            val lv = HistPolicy.level(prefs.int("hist_days_lv", HistPolicy.DEF_LEVEL))
            val n = HistStore.count()
            val now = System.currentTimeMillis()

            // ---- hero card: dot + state + details
            val hero = LinearLayout(ctx); hero.orientation = LinearLayout.VERTICAL
            hero.background = AppUi.card(ctx, AppTokens.R_CARD)
            hero.setPadding(dp(AppTokens.S4), dp(AppTokens.S3), dp(AppTokens.S4), dp(AppTokens.S3))
            val top = LinearLayout(ctx); top.orientation = LinearLayout.HORIZONTAL; top.gravity = Gravity.CENTER_VERTICAL
            val st = HistUi.state(on, paused)
            val dot = View(ctx)
            val dg = GradientDrawable(); dg.shape = GradientDrawable.OVAL
            dg.setColor(when (st) { 1 -> AppUi.ok(ctx); 2 -> AppUi.warn(ctx); else -> dim })
            dot.background = dg
            top.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)))
            val ht = tv(HistUi.heroTitle(on, paused), AppTokens.T_TITLE, text, true)
            ht.setPadding(dp(AppTokens.S2), 0, 0, 0)
            try { ht.isAccessibilityHeading = true } catch (_: Throwable) { }
            top.addView(ht)
            hero.addView(top)
            hero.addView(tv(HistUi.heroSub(on, lv, n), AppTokens.T_BODY, dim).also { it.setPadding(0, dp(AppTokens.S1), 0, 0) })
            val pl = HistPause.line(prefs.bool("hist_pause", false), HistStore.pauseUntil(prefs), now)
            if (on && pl.isNotEmpty()) hero.addView(tv(pl, AppTokens.T_BODY, AppUi.warn(ctx)).also { it.setPadding(0, dp(AppTokens.S1), 0, 0) })
            body.addView(hero, lp())

            // ---- main button (accent)
            val open = Button(ctx)
            open.isAllCaps = false
            open.text = HistDays.openLabel(n)
            open.textSize = AppTokens.T_ROW.toFloat()
            open.setTextColor(AppUi.onAccent(ctx))
            val ob = GradientDrawable(); ob.setColor(AppUi.accent(ctx)); ob.cornerRadius = dp(AppTokens.R_BTN).toFloat()
            open.background = ob
            open.minimumHeight = dp(AppTokens.TOUCH)
            open.setOnClickListener { try { HistPage.open(ctx) { try { fill() } catch (_: Throwable) { } } } catch (_: Throwable) { } }
            body.addView(open, lp(AppTokens.S3))

            // ---- saving
            body.addView(AppUi.sectionHeader(ctx, "Saving"))
            body.addView(AppRows.switchRow(ctx, "Save my typing", "Saves what you type with Jabra (time, app, text) on this phone only. Passwords and pasted text are never saved.",
                { prefs.bool("hist_on", false) }) { v ->
                if (!v) { prefs.setBool("hist_on", false); msg.text = "Typing history is OFF. Nothing new is saved."; fill() }
                else if (prefs.bool("hist_ack", false)) { prefs.setBool("hist_on", true); msg.text = "Typing history is ON."; fill() }
                else {
                    try {   // first time: explain, then ON only after "Turn ON"
                        val dlg = AlertDialog.Builder(ctx).setTitle(HistInfo.confirmTitle()).setMessage(HistInfo.confirmBody())
                            .setPositiveButton("Turn ON") { _, _ -> prefs.setBool("hist_ack", true); prefs.setBool("hist_on", true); msg.text = "Typing history is ON."; fill() }
                            .setNegativeButton("Cancel") { _, _ -> fill() }
                            .create()
                        dlg.setOnCancelListener { fill() }
                        dlg.show()
                    } catch (_: Throwable) { msg.text = "Could not show the confirm window, so typing history stays OFF."; fill() }
                }
            }.view)
            body.addView(AppRows.choiceRow(ctx, "Keep for", "Older entries are deleted every time the keyboard or this app opens.",
                { HistPolicy.NAMES }, { HistPolicy.level(prefs.int("hist_days_lv", HistPolicy.DEF_LEVEL)) }) { i ->
                prefs.setInt("hist_days_lv", i); HistStore.pruneNow()
                msg.text = "Entries older than " + HistPolicy.NAMES[i] + " are removed now and every time the keyboard opens."; fill()
            }.view)
            body.addView(AppRows.switchRow(ctx, "Skip email, URL and number fields", "Email, web address, number and phone fields are not saved.",
                { prefs.bool("hist_skip_sens", true) }) { v -> prefs.setBool("hist_skip_sens", v); fill() }.view)
            body.addView(AppRows.switchRow(ctx, "Skip incognito fields", "Private / incognito fields (apps that ask keyboards not to learn) are not saved.",
                { prefs.bool("hist_skip_incog", true) }) { v -> prefs.setBool("hist_skip_incog", v); fill() }.view)

            // ---- pause
            body.addView(AppUi.sectionHeader(ctx, HistUi.pauseTitle()))
            body.addView(AppRows.choiceRow(ctx, "Pause length", HistUi.pauseHelp(), { HistPause.NAMES }, { HistStore.pauseLevel(prefs) }) { i ->
                prefs.setInt("hist_pause_lv", i); fill()
            }.view)
            val pb = Button(ctx)
            AppRows.styleAction(ctx, pb)
            pb.isAllCaps = false
            pb.text = if (paused) HistUi.resumeNow() else HistUi.pauseNow(HistPause.name(HistStore.pauseLevel(prefs)))
            pb.isEnabled = on
            pb.setOnClickListener { try { HistStore.pauseTap(ctx, prefs); fill() } catch (_: Throwable) { } }
            body.addView(pb)
            body.addView(AppRows.switchRow(ctx, HistUi.pauseUntilOff(), "Stays paused until you switch this off (the old Pause switch).",
                { prefs.bool("hist_pause", false) }) { v -> prefs.setBool("hist_pause", v); prefs.setStr("hist_pause_until", "0"); fill() }.view)
            body.addView(tv("Tip: Edit toolbar can add a small \"H\" key to the keyboard. Tap it to pick 15 min / 1 hour / 1 day, tap again to resume.", AppTokens.T_CAPTION, dim).also { it.setPadding(0, dp(AppTokens.S1), 0, 0) })

            // ---- clear
            body.addView(AppUi.sectionHeader(ctx, "Clear"))
            val cb = AppRows.confirmAction(ctx, HistInfo.clearLabel(false, n), HistInfo.clearLabel(true, n)) {
                val r = HistStore.clearAll(ctx); msg.text = HistInfo.clearDone(r); fill()
            }
            cb.isEnabled = n > 0
            body.addView(cb)
            body.addView(tv("Saved as plain text in the app's private storage on this phone. It is not part of Backup.", AppTokens.T_CAPTION, dim).also { it.setPadding(0, dp(AppTokens.S2), 0, 0) })
        }
        root.addView(body)
        root.addView(msg)
        fill()
        try { if (!HistStore.isLoaded()) HistStore.whenLoaded { try { fill() } catch (_: Throwable) { } } } catch (_: Throwable) { }
        return root
    }

    private fun buildClassic(ctx: Context): View {
        val prefs = JabraPrefs(ctx)
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val root = LinearLayout(ctx); root.orientation = LinearLayout.VERTICAL
        root.setPadding(0, dp(6), 0, dp(6))
        val body = LinearLayout(ctx); body.orientation = LinearLayout.VERTICAL
        val msg = TextView(ctx); msg.setPadding(0, dp(4), 0, dp(4))
        var armed = false

        fun line(t: String): TextView { val v = TextView(ctx); v.text = t; v.setPadding(0, dp(2), 0, dp(2)); return v }
        fun onOff(b: Boolean) = if (b) "ON" else "OFF"
        fun btn(label: String, on: (Button) -> Unit): Button {
            val b = Button(ctx); b.text = label; b.isAllCaps = false
            b.setOnClickListener { try { on(b) } catch (_: Throwable) { } }
            return b
        }

        fun fill() {
            body.removeAllViews()
            HistStore.ensure(ctx)
            HistStore.pruneNow()
            val on = prefs.bool("hist_on", false)
            val paused = HistStore.isPaused(prefs)   // v0.23.0 (H3): the switch or the 1-hour pause
            val lv = HistPolicy.level(prefs.int("hist_days_lv", HistPolicy.DEF_LEVEL))
            val n = HistStore.count()
            val st = line(HistInfo.statusLine(on, paused, lv, n)); st.setTypeface(null, Typeface.BOLD)
            body.addView(st)
            try {   // v0.23.0 (H3): how long a timed pause still runs
                val pl = HistPause.line(prefs.bool("hist_pause", false), HistStore.pauseUntil(prefs), System.currentTimeMillis())
                if (on && pl.isNotEmpty()) body.addView(line(pl))
            } catch (_: Throwable) { }
            body.addView(btn(HistDays.openLabel(n)) {   // v0.21.0 (H2): the history page; when it closes the card refreshes (count, status)
                armed = false
                HistPage.open(ctx) { try { fill() } catch (_: Throwable) { } }
            })

            body.addView(btn("Typing history: " + onOff(on)) {
                armed = false
                if (on) {
                    prefs.setBool("hist_on", false)
                    msg.text = "Typing history is OFF. Nothing new is saved."
                    fill()
                } else if (prefs.bool("hist_ack", false)) {
                    prefs.setBool("hist_on", true)
                    msg.text = "Typing history is ON."
                    fill()
                } else {
                    try {   // first time: explain, then ON only after the person taps "Turn ON"
                        AlertDialog.Builder(ctx).setTitle(HistInfo.confirmTitle()).setMessage(HistInfo.confirmBody())
                            .setPositiveButton("Turn ON") { _, _ ->
                                prefs.setBool("hist_ack", true); prefs.setBool("hist_on", true)
                                msg.text = "Typing history is ON."; fill()
                            }
                            .setNegativeButton("Cancel", null).show()
                    } catch (_: Throwable) { msg.text = "Could not show the confirm window, so typing history stays OFF." }
                }
            })
            body.addView(btn(HistInfo.keepLabel(lv)) {
                armed = false
                val nv = (lv + 1) % HistPolicy.DAYS.size
                prefs.setInt("hist_days_lv", nv)
                HistStore.pruneNow()
                msg.text = "Entries older than " + HistPolicy.NAMES[nv] + " are removed now and every time the keyboard opens."
                fill()
            })
            body.addView(btn("Skip email, URL and number fields: " + onOff(prefs.bool("hist_skip_sens", true))) {
                armed = false; prefs.setBool("hist_skip_sens", !prefs.bool("hist_skip_sens", true)); fill()
            })
            body.addView(btn("Skip incognito fields: " + onOff(prefs.bool("hist_skip_incog", true))) {
                armed = false; prefs.setBool("hist_skip_incog", !prefs.bool("hist_skip_incog", true)); fill()
            })
            body.addView(btn("Pause: " + onOff(prefs.bool("hist_pause", false))) {
                armed = false; prefs.setBool("hist_pause", !prefs.bool("hist_pause", false)); prefs.setStr("hist_pause_until", "0"); fill()   // v0.23.0 (H3): a switch change also ends a 1-hour pause
            })
            body.addView(btn(if (paused) "Resume typing history now" else "Pause for 1 hour (resumes by itself)") {   // v0.23.0 (H3)
                armed = false; HistStore.pauseTap(ctx, prefs); fill()
            })
            val cb = btn(HistInfo.clearLabel(armed, n)) {
                if (n <= 0) return@btn
                if (!armed) { armed = true; it.text = HistInfo.clearLabel(true, n) }
                else { armed = false; val r = HistStore.clearAll(ctx); msg.text = HistInfo.clearDone(r); fill() }
            }
            cb.isEnabled = n > 0
            body.addView(cb)
            body.addView(line("Tip: Edit toolbar can add a small \"H\" key to the keyboard: tap = pause / resume typing history."))
            body.addView(line("Passwords and pasted text are never saved. Saved as plain text in the app's private storage on this phone; it is not part of Backup."))
        }
        root.addView(body)
        root.addView(msg)
        fill()
        try { if (!HistStore.isLoaded()) HistStore.whenLoaded { try { fill() } catch (_: Throwable) { } } } catch (_: Throwable) { }
        return root
    }
}
