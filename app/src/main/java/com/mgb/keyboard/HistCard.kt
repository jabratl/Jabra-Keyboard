package com.mgb.keyboard

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.hist.HistDays
import com.mgb.keyboard.hist.HistInfo
import com.mgb.keyboard.hist.HistPolicy

/**
 * v0.20.0 (H1) "Typing history" card inside Data & Backup: ON / OFF (default OFF, a confirm dialog the first time), keep time 1 / 3 / 7 / 30 days,
 * two optional skips (email / URL / number fields, incognito fields), Pause, and "Clear typing history now" (two taps).
 * Texts come from hist/HistInfo.java (tested on a computer). v0.21.0 (H2): the button "Open typing history" opens the history PAGE (HistPage.kt: days, search, copy, delete, export).
 * Called inside try/catch from DataPage: any problem = the card is simply missing.
 */
object HistCard {
    fun build(ctx: Context): View {
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
            val paused = prefs.bool("hist_pause", false)
            val lv = HistPolicy.level(prefs.int("hist_days_lv", HistPolicy.DEF_LEVEL))
            val n = HistStore.count()
            val st = line(HistInfo.statusLine(on, paused, lv, n)); st.setTypeface(null, Typeface.BOLD)
            body.addView(st)
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
            body.addView(btn("Pause: " + onOff(paused)) {
                armed = false; prefs.setBool("hist_pause", !prefs.bool("hist_pause", false)); fill()
            })
            val cb = btn(HistInfo.clearLabel(armed, n)) {
                if (n <= 0) return@btn
                if (!armed) { armed = true; it.text = HistInfo.clearLabel(true, n) }
                else { armed = false; val r = HistStore.clearAll(ctx); msg.text = HistInfo.clearDone(r); fill() }
            }
            cb.isEnabled = n > 0
            body.addView(cb)
            body.addView(line("Passwords and pasted text are never saved. Saved as plain text in the app's private storage on this phone; it is not part of Backup."))
        }
        root.addView(body)
        root.addView(msg)
        fill()
        try { if (!HistStore.isLoaded()) HistStore.whenLoaded { try { fill() } catch (_: Throwable) { } } } catch (_: Throwable) { }
        return root
    }
}
