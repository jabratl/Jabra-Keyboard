package com.mgb.keyboard

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.RecentPairs

/**
 * v0.26.0 (R3, step 3f) card "Recent Find & Replace" inside Data & Backup: ONE switch (default OFF) + "Clear recent pairs" (two taps).
 * ON = the Find / With texts of every Replace are stored as plain text on this phone (last 10 pairs, pref rpr_list). OFF = the list is deleted.
 * The prefs rpr_on / rpr_list are never part of Backup / Restore (BackupPlan.isSkippedPref). Texts come from typing/RecentPairs.java (tested).
 * Called inside try/catch from DataPage: any problem = the classic card below, and if that fails too the card is simply missing.
 */
object RecentCard {
    fun build(ctx: Context): View = try { buildModern(ctx) } catch (_: Throwable) { buildClassic(ctx) }

    private fun count(prefs: JabraPrefs): Int = try { RecentPairs.parse(prefs.str(RecentPairs.PREF_LIST, "")).size } catch (_: Throwable) { 0 }

    /** OFF also deletes the stored pairs (nothing stays on the phone silently). */
    private fun setOn(prefs: JabraPrefs, on: Boolean) {
        prefs.setBool(RecentPairs.PREF_ON, on)
        if (!on) prefs.setStr(RecentPairs.PREF_LIST, "")
    }

    private fun buildModern(ctx: Context): View {
        val prefs = JabraPrefs(ctx)
        fun dp(v: Int) = AppUi.dp(ctx, v)
        val root = LinearLayout(ctx); root.orientation = LinearLayout.VERTICAL
        root.setPadding(0, dp(AppTokens.S1), 0, dp(AppTokens.S1))
        val body = LinearLayout(ctx); body.orientation = LinearLayout.VERTICAL
        val msg = TextView(ctx); msg.setPadding(0, dp(AppTokens.S2), 0, dp(AppTokens.S2)); msg.setTextColor(AppUi.dim(ctx)); msg.textSize = AppTokens.T_BODY.toFloat()
        lateinit var fill: () -> Unit
        fill = {
            body.removeAllViews()
            val on = prefs.bool(RecentPairs.PREF_ON, false)
            val n = count(prefs)
            val st = TextView(ctx)
            st.text = RecentPairs.statusLine(on, n)
            st.textSize = AppTokens.T_BODY.toFloat()
            st.setTextColor(AppUi.text(ctx))
            st.setPadding(0, dp(AppTokens.S2), 0, dp(AppTokens.S1))
            body.addView(st)
            body.addView(AppRows.switchRow(ctx, "Remember my Find / With pairs", RecentPairs.switchHelp(),
                { prefs.bool(RecentPairs.PREF_ON, false) }) { v ->
                setOn(prefs, v)
                msg.text = if (v) "Recent pairs is ON. Find / With texts are now stored on this phone." else "Recent pairs is OFF. Stored pairs were deleted."
                fill()
            }.view)
            val cb = AppRows.confirmAction(ctx, "Clear recent pairs", "Tap again to clear") {
                prefs.setStr(RecentPairs.PREF_LIST, ""); msg.text = "Recent pairs cleared."; fill()
            }
            cb.isEnabled = n > 0
            body.addView(cb)
            val cap = TextView(ctx)
            cap.text = "Used by the Find & Replace panel (toolbar key \"Rp\" > Recent). Not part of Backup."
            cap.textSize = AppTokens.T_CAPTION.toFloat()
            cap.setTextColor(AppUi.dim(ctx))
            cap.setPadding(0, dp(AppTokens.S2), 0, 0)
            body.addView(cap)
        }
        root.addView(body)
        root.addView(msg)
        fill()
        return root
    }

    private fun buildClassic(ctx: Context): View {
        val prefs = JabraPrefs(ctx)
        val root = LinearLayout(ctx); root.orientation = LinearLayout.VERTICAL
        val info = TextView(ctx)
        val sw = Button(ctx); sw.isAllCaps = false
        val clr = Button(ctx); clr.isAllCaps = false; clr.text = "Clear recent pairs"
        fun paint() {
            val on = prefs.bool(RecentPairs.PREF_ON, false)
            val n = count(prefs)
            sw.text = "Recent Find / With pairs: " + (if (on) "ON" else "OFF")
            info.text = RecentPairs.statusLine(on, n) + "\n" + RecentPairs.switchHelp()
            clr.isEnabled = n > 0
        }
        sw.setOnClickListener { try { setOn(prefs, !prefs.bool(RecentPairs.PREF_ON, false)); paint() } catch (_: Throwable) { } }
        clr.setOnClickListener { try { prefs.setStr(RecentPairs.PREF_LIST, ""); paint() } catch (_: Throwable) { } }
        paint()
        root.addView(info); root.addView(sw); root.addView(clr)
        return root
    }
}
