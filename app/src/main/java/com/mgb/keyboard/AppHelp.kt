package com.mgb.keyboard

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.HelpPlan

/**
 * v0.17.78 (U3): the Android side of HelpPlan = "smart hints" for settings rows. AppRows asks make() for the (i) icon + the help text of a row;
 * SettingsGroups calls bind() once per row (key known there): flagged rows open by themselves the first time, long texts get a short form.
 * Pref app_tips: 0 Smart (default) / 1 Always (old look) / 2 Off. Nothing here throws; any problem = the plain help text under the row (old behaviour).
 */
object AppHelp {
    const val TAG_INFO = "u3_info"
    const val TAG_HELP = "u3_help"

    class Parts(val info: View?, val text: TextView?)

    private fun mode(c: Context): Int = try { HelpPlan.sanitize(JabraPrefs(c).int(HelpPlan.PREF_TIPS, HelpPlan.SMART)) } catch (_: Throwable) { HelpPlan.SMART }

    /** (i) icon + help text for [help] (title = spoken name). Off / empty help = both null. */
    fun make(c: Context, title: String, help: String): Parts {
        if (help.isEmpty()) return Parts(null, null)
        val m = mode(c)
        if (!HelpPlan.helpExists(m)) return Parts(null, null)
        val t = TextView(c)
        t.text = help
        t.textSize = AppTokens.T_CAPTION.toFloat()
        t.setTextColor(AppUi.dim(c))
        t.setPadding(0, 0, 0, AppUi.dp(c, AppTokens.S2))
        t.tag = TAG_HELP
        if (!HelpPlan.showIcon(m)) return Parts(null, t)   // Always: the old look, text under the row
        t.visibility = if (HelpPlan.startsOpen(m)) View.VISIBLE else View.GONE
        val b = Button(c)
        try {
            b.isAllCaps = false
            b.background = null
            b.stateListAnimator = null
            b.minWidth = 0; b.minimumWidth = 0
            b.minHeight = AppUi.dp(c, AppTokens.TOUCH); b.minimumHeight = AppUi.dp(c, AppTokens.TOUCH)
            b.setPadding(0, 0, 0, 0)
            if (!JabraIcons.apply(b, "info", true, AppUi.dim(c))) b.text = "i"
            b.contentDescription = "Help: " + title
            b.tag = TAG_INFO
            b.setOnClickListener { toggle(c, t) }
        } catch (_: Throwable) { return Parts(null, t.also { it.visibility = View.VISIBLE }) }
        return Parts(b, t)
    }

    /** Width of the (i) button (the 48 dp touch target). */
    fun infoLp(c: Context): ViewGroup.LayoutParams {
        val lp = android.widget.LinearLayout.LayoutParams(AppUi.dp(c, AppTokens.TOUCH), AppUi.dp(c, AppTokens.TOUCH))
        lp.gravity = Gravity.CENTER_VERTICAL
        return lp
    }

    private fun toggle(c: Context, t: TextView) {
        try {
            if (t.visibility == View.VISIBLE) t.visibility = View.GONE else { t.visibility = View.VISIBLE; AppMotion.fadeIn(t, AppTokens.FADE_MS, false) }
        } catch (_: Throwable) { try { t.visibility = View.VISIBLE } catch (_: Throwable) { } }
    }

    /** Call once per row with its pref [key]: short form for long texts (tap = full text), and the first-view auto-open of flagged rows. */
    fun bind(c: Context, key: String, root: View) {
        try {
            val t = root.findViewWithTag<TextView>(TAG_HELP) ?: return
            val full = t.text.toString()
            if (HelpPlan.isShortened(key, full)) {
                val short = HelpPlan.shortOf(key, full)
                var showingFull = false
                t.text = short
                t.setOnClickListener { try { showingFull = !showingFull; t.text = if (showingFull) full else short } catch (_: Throwable) { } }
            }
            if (root.findViewWithTag<View>(TAG_INFO) == null) return   // Always / no icon: nothing opens by itself
            val prefs = JabraPrefs(c)
            val seen = prefs.str(HelpPlan.PREF_SEEN, "")
            if (HelpPlan.autoOpen(mode(c), key, seen, false)) {
                t.visibility = View.VISIBLE
                prefs.setStr(HelpPlan.PREF_SEEN, HelpPlan.addSeen(seen, key))
            }
        } catch (_: Throwable) { }
    }

    /** Open the help of a row (search hit / favourite jump). Does nothing in Always / Off. */
    fun openIn(root: View?) {
        try {
            if (root == null) return
            if (root.findViewWithTag<View>(TAG_INFO) == null) return
            root.findViewWithTag<TextView>(TAG_HELP)?.visibility = View.VISIBLE
        } catch (_: Throwable) { }
    }
}
