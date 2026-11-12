package com.mgb.keyboard

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.EdgePlan
import com.mgb.keyboard.typing.PagePlan
import com.mgb.keyboard.typing.PersonalPlan
import com.mgb.keyboard.typing.SizePct

/** v0.11.0 (M1): the Home view and a refresh() that redraws the tile summaries (called when Home is shown again).
 *  v0.13.0 (M3): select(id) marks the tile of the open page (two panes); refresh() also rebuilds the lists of the global search. */
class AppHomeView(val view: View, val refresh: () -> Unit, val select: (String) -> Unit)

/**
 * v0.11.0 (M1 of the "App UI" plan): new Home = header, ONE setup card, test field, Quick looks, 6 tiles (2 columns).
 * The live preview card is pinned by SettingsActivity above the page area (Home, Look & Theme, Layout & Size).
 * Quick looks write the SAME prefs as EdgePlan.PRESET_KEYS and keep the old values in "geo_prev" (same undo as [Ridmik-style spacing]).
 * Never throws out of build(): the caller falls back to the old screen.
 */
object AppHome {

    fun build(act: SettingsActivity, setupCard: View?, pvLink: PreviewLink, search: AppSearchView?, open: (String) -> Unit): AppHomeView {   // v0.13.0 (M3): search = global search (null = none)
        val prefs = JabraPrefs(act)
        val d = act.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).toInt()
        val dark = try { AppUi.isDark(act) } catch (_: Throwable) { false }

        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(AppTokens.S4), dp(AppTokens.S3), dp(AppTokens.S4), dp(24))

        // ---- header: logo + name + version ----
        val head = LinearLayout(act)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        try {
            val logo = ImageView(act)
            logo.setImageResource(R.drawable.jabra_logo)
            logo.contentDescription = "Jabra Keyboard logo"
            head.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))
        } catch (_: Throwable) { }
        val names = LinearLayout(act)
        names.orientation = LinearLayout.VERTICAL
        names.setPadding(dp(AppTokens.S3), 0, 0, 0)
        val title = TextView(act)
        title.text = "Jabra Keyboard"
        title.textSize = 22f
        title.setTypeface(null, Typeface.BOLD)
        try { title.setTextColor(AppUi.text(act)) } catch (_: Throwable) { }
        try { title.isAccessibilityHeading = true } catch (_: Throwable) { }
        val ver = TextView(act)
        ver.text = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
        try { ver.setTextColor(AppUi.dim(act)) } catch (_: Throwable) { }
        names.addView(title)
        names.addView(ver)
        head.addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(head)

        try { if (search != null) { col.addView(search.view); col.addView(search.favs) } } catch (_: Throwable) { }   // v0.15.0 (M5): favourites right under the search box   // v0.13.0 (M3): ONE search for all settings, right under the header

        try {   // warning when the crash guard switched a panel off / backup is old (same text as the old screen)
            val bn = com.mgb.keyboard.typing.HealthCheck.backupNote(prefs.bool("bk_auto", true), prefs.str("bk_last", ""), System.currentTimeMillis())
            val w = listOf(PanelGuard.note(act), bn).filter { it.isNotEmpty() }.joinToString("\n")
            if (w.isNotEmpty()) { val wt = TextView(act); wt.text = w; wt.setTextColor(AppUi.warn(act)); wt.setPadding(0, dp(AppTokens.S2), 0, 0); col.addView(wt) }
        } catch (_: Throwable) { }

        try { if (setupCard != null) { (setupCard.parent as? ViewGroup)?.removeView(setupCard); col.addView(setupCard) } } catch (_: Throwable) { }   // ONE setup card (the 2 duplicate buttons are not in the new screen)

        val test = EditText(act)
        test.hint = "Type here to test the keyboard"
        col.addView(test)

        // ---- Quick looks ----
        col.addView(label(act, "Quick looks", dp(AppTokens.S3)))
        val keys = EdgePlan.PRESET_KEYS
        fun curVals(): IntArray = IntArray(keys.size) { if (EdgePlan.isBool(it)) (if (prefs.bool(keys[it], false)) 1 else 0) else prefs.int(keys[it], -1) }
        fun write(v: IntArray) { for (i in keys.indices) { if (EdgePlan.isBool(i)) prefs.setBool(keys[i], v[i] == 1) else prefs.setInt(keys[i], v[i]) } }

        val note = TextView(act)
        try { note.setTextColor(AppUi.dim(act)) } catch (_: Throwable) { }
        note.textSize = 12f
        note.setPadding(0, dp(AppTokens.S1), 0, 0)
        val undo = Button(act)
        undo.isAllCaps = false
        undo.text = "Undo last look"
        undo.minHeight = dp(AppTokens.TOUCH)
        undo.minimumHeight = dp(AppTokens.TOUCH)
        fun hasUndo(): Boolean = EdgePlan.decodeUpTo(prefs.str("geo_prev", ""), keys.size) != null
        undo.visibility = if (hasUndo()) View.VISIBLE else View.GONE

        val tileSum = ArrayList<Pair<String, TextView>>()
        val tileViews = HashMap<String, View>()   // v0.13.0 (M3)
        fun refreshTiles() {
            for ((id, tv) in tileSum) {
                try {
                    tv.text = summary(act, prefs, id)
                    tileViews[id]?.contentDescription = PagePlan.title(id) + ". " + tv.text   // v0.13.0 (M3): TalkBack reads title + summary
                } catch (_: Throwable) { }
            }
        }
        fun redrawAll() {
            try { pvLink.changed?.invoke() } catch (_: Throwable) { }
            try { pvLink.refreshRows?.invoke() } catch (_: Throwable) { }
            refreshTiles()
        }

        val chips = LinearLayout(act)
        chips.orientation = LinearLayout.HORIZONTAL
        for (id in PagePlan.QUICK) {
            val b = Button(act)
            b.isAllCaps = false
            b.text = PagePlan.quickLabel(id)
            b.textSize = 12f
            b.minHeight = dp(AppTokens.TOUCH)
            b.minimumHeight = dp(AppTokens.TOUCH)
            b.minWidth = 0
            b.minimumWidth = 0
            b.contentDescription = "Quick look: " + PagePlan.quickLabel(id)
            b.setOnClickListener {
                try {
                    val old = curVals()
                    val nv = PagePlan.quickValues(id, old) ?: return@setOnClickListener
                    prefs.setStr("geo_prev", EdgePlan.encode(old))
                    write(nv)
                    if (id == PagePlan.QL_RIDMIK) prefs.setBool("row_gap", true)
                    note.text = "Applied: " + PagePlan.quickLabel(id) + ". The keyboard uses it when it opens next time."
                    undo.visibility = View.VISIBLE
                    redrawAll()
                } catch (_: Throwable) { }
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(2), 0, dp(2), 0)
            chips.addView(b, lp)
        }
        col.addView(chips)
        undo.setOnClickListener {
            try {
                val old = EdgePlan.decodeUpTo(prefs.str("geo_prev", ""), keys.size)
                if (old == null) { note.text = "Nothing to undo"; undo.visibility = View.GONE; return@setOnClickListener }
                write(old)
                prefs.setStr("geo_prev", "")
                note.text = "Undone"
                undo.visibility = View.GONE
                redrawAll()
            } catch (_: Throwable) { }
        }
        col.addView(undo)
        col.addView(note)

        // ---- tiles: 2 columns; v0.15.0 (M5): own order + hidden tiles (PersonalPlan) ----
        col.addView(label(act, "Settings", dp(AppTokens.S4)))
        val tilesBox = LinearLayout(act)
        tilesBox.orientation = LinearLayout.VERTICAL
        col.addView(tilesBox)
        var selectedId = ""

        fun select(id: String) {   // v0.13.0 (M3): two panes: the tile of the open page gets an accent frame
            selectedId = id
            for ((tid, tv) in tileViews) {
                try {
                    if (tid == id) {
                        val g = GradientDrawable()
                        g.setColor(com.mgb.keyboard.typing.AppPalette.card(dark))
                        g.cornerRadius = dp(AppTokens.S3).toFloat()
                        g.setStroke(dp(2), AppUi.accent(act))
                        tv.background = g
                    } else tv.background = AppUi.cardBg(act, 12)
                } catch (_: Throwable) { }
            }
        }

        fun buildTiles() {
            tilesBox.removeAllViews()
            tileSum.clear()
            tileViews.clear()
            val ids = PersonalPlan.visible(prefs.str(PersonalPlan.PREF_ORDER, ""), prefs.str(PersonalPlan.PREF_HIDE, ""))
            var rowBox: LinearLayout? = null
            for (i in ids.indices) {
                val id = ids[i]
                if (i % 2 == 0) {
                    rowBox = LinearLayout(act)
                    rowBox.orientation = LinearLayout.HORIZONTAL
                    tilesBox.addView(rowBox)
                }
                val tile = LinearLayout(act)
                tile.orientation = LinearLayout.VERTICAL
                tile.setPadding(dp(AppTokens.S3), dp(AppTokens.S3), dp(AppTokens.S3), dp(AppTokens.S3))
                tile.minimumHeight = dp(104)
                try { tile.background = AppUi.cardBg(act, 12) } catch (_: Throwable) { }
                tile.isClickable = true
                tile.isFocusable = true
                val icon = Button(act)   // JabraIcons draws on a Button; here it is only a picture
                icon.background = null
                icon.minWidth = 0; icon.minimumWidth = 0; icon.minHeight = 0; icon.minimumHeight = 0
                icon.setPadding(0, 0, 0, 0)
                icon.isClickable = false
                icon.isFocusable = false
                try { JabraIcons.apply(icon, PagePlan.icon(id), true, AppUi.accent(act)) } catch (_: Throwable) { }
                icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                tile.addView(icon, LinearLayout.LayoutParams(dp(32), dp(32)))
                val tt = TextView(act)
                tt.text = PagePlan.title(id)
                tt.textSize = 15f
                tt.setTypeface(null, Typeface.BOLD)
                try { tt.setTextColor(AppUi.text(act)) } catch (_: Throwable) { }
                tt.setPadding(0, dp(AppTokens.S2), 0, 0)
                tile.addView(tt)
                val sm = TextView(act)
                sm.textSize = 12f
                try { sm.setTextColor(AppUi.dim(act)) } catch (_: Throwable) { }
                sm.text = summary(act, prefs, id)
                tile.addView(sm)
                tileSum.add(Pair(id, sm))
                tile.contentDescription = PagePlan.title(id) + ". " + sm.text   // v0.13.0 (M3)
                AppA11y.asButton(tile)
                tileViews[id] = tile
                tile.setOnClickListener { try { open(id) } catch (_: Throwable) { } }
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(dp(AppTokens.S1), dp(AppTokens.S1), dp(AppTokens.S1), dp(AppTokens.S1))
                rowBox?.addView(tile, lp)
            }
            if (selectedId.isNotEmpty()) select(selectedId)
        }
        buildTiles()

        // ---- v0.15.0 (M5): [Customize Home] = move / hide tiles (Data & About can never be hidden) ----
        val custom = LinearLayout(act)
        custom.orientation = LinearLayout.VERTICAL
        custom.visibility = View.GONE
        fun mini(label: String, desc: String, on: Boolean, click: () -> Unit): Button {
            val b = Button(act)
            b.isAllCaps = false
            b.text = label
            b.textSize = 12f
            b.minWidth = 0; b.minimumWidth = 0
            b.minHeight = dp(AppTokens.TOUCH); b.minimumHeight = dp(AppTokens.TOUCH)
            b.contentDescription = desc
            b.isEnabled = on
            b.setOnClickListener { try { click() } catch (_: Throwable) { } }
            return b
        }
        fun buildCustom() {
            custom.removeAllViews()
            val rawO = prefs.str(PersonalPlan.PREF_ORDER, "")
            val rawH = prefs.str(PersonalPlan.PREF_HIDE, "")
            val full = PersonalPlan.order(rawO)
            val hid = PersonalPlan.hidden(rawH)
            val hint = TextView(act)
            hint.text = "Move tiles with the arrows. A hidden tile still opens from the search."
            hint.textSize = 12f
            try { hint.setTextColor(AppUi.dim(act)) } catch (_: Throwable) { }
            hint.setPadding(0, dp(AppTokens.S1), 0, dp(AppTokens.S1))
            custom.addView(hint)
            for (id in full) {
                val isHid = hid.contains(id)
                val line = LinearLayout(act)
                line.orientation = LinearLayout.HORIZONTAL
                line.gravity = Gravity.CENTER_VERTICAL
                val name = TextView(act)
                name.text = PagePlan.title(id) + (if (isHid) " (hidden)" else "")
                name.textSize = 14f
                try { name.setTextColor(if (isHid) AppUi.dim(act) else AppUi.text(act)) } catch (_: Throwable) { }
                line.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                line.addView(mini("\u25B2", "Move " + PagePlan.title(id) + " up", id != full.first()) {
                    prefs.setStr(PersonalPlan.PREF_ORDER, PersonalPlan.move(rawO, id, -1)); buildTiles(); buildCustom()
                })
                line.addView(mini("\u25BC", "Move " + PagePlan.title(id) + " down", id != full.last()) {
                    prefs.setStr(PersonalPlan.PREF_ORDER, PersonalPlan.move(rawO, id, 1)); buildTiles(); buildCustom()
                })
                line.addView(mini(if (isHid) "Show" else "Hide", (if (isHid) "Show " else "Hide ") + PagePlan.title(id) + " on Home", PersonalPlan.canHide(id)) {
                    prefs.setStr(PersonalPlan.PREF_HIDE, PersonalPlan.toggleHide(rawH, id)); buildTiles(); buildCustom()
                })
                custom.addView(line)
            }
            if (PersonalPlan.customised(rawO, rawH)) {
                custom.addView(mini("Reset Home tiles", "Reset the order and show all tiles", true) {
                    prefs.setStr(PersonalPlan.PREF_ORDER, ""); prefs.setStr(PersonalPlan.PREF_HIDE, ""); buildTiles(); buildCustom()
                })
            }
        }
        val customBtn = Button(act)
        customBtn.isAllCaps = false
        customBtn.text = "Customize Home"
        customBtn.minHeight = dp(AppTokens.TOUCH)
        customBtn.minimumHeight = dp(AppTokens.TOUCH)
        customBtn.setOnClickListener {
            try {
                val open2 = custom.visibility != View.VISIBLE
                if (open2) buildCustom()
                custom.visibility = if (open2) View.VISIBLE else View.GONE
                customBtn.text = if (open2) "Done" else "Customize Home"
            } catch (_: Throwable) { }
        }
        col.addView(customBtn)
        col.addView(custom)

        try {   // v0.13.0 (M3): lists at the end; everything under the search box is hidden while a search text is typed
            if (search != null) {
                col.addView(search.lists)
                val at = col.indexOfChild(search.view)
                if (at >= 0) search.bindRest((at + 1 until col.childCount).map { col.getChildAt(it) })
            }
        } catch (_: Throwable) { }

        val sv = ScrollView(act)
        sv.isFillViewport = true
        sv.addView(col)
        try { if (dark) sv.setBackgroundColor(AppUi.bg(act)) } catch (_: Throwable) { }
        return AppHomeView(sv, {
            try { refreshTiles(); undo.visibility = if (hasUndo()) View.VISIBLE else View.GONE } catch (_: Throwable) { }
            try { search?.refresh?.invoke() } catch (_: Throwable) { }
        }, { id -> select(id) })
    }

    private fun label(act: SettingsActivity, text: String, topPx: Int): TextView {
        val t = TextView(act)
        t.text = text
        t.textSize = 16f
        t.setTypeface(null, Typeface.BOLD)
        try { t.setTextColor(AppUi.text(act)) } catch (_: Throwable) { }
        t.setPadding(0, topPx, 0, (4 * act.resources.displayMetrics.density).toInt())
        try { t.isAccessibilityHeading = true } catch (_: Throwable) { }
        return t
    }

    /** One short line per tile, read from the same prefs and defaults as the rows. */
    private fun summary(act: SettingsActivity, p: JabraPrefs, id: String): String = try {
        when (id) {
            PagePlan.LAYOUT -> {
                val v = p.int("kh_pct_p", -1)
                val h = if (SizePct.heightValid(v)) v else SizePct.pctFromDp(p.keyHeightDp(false))
                PagePlan.layoutSummary(h, EdgePlan.radiusDp(p.int("key_radius", -1)))
            }
            PagePlan.LOOK -> PagePlan.lookSummary(JabraThemes.cur(p).name, p.int("look", 0) == 1)
            PagePlan.TYPING -> PagePlan.typingSummary(p.str("bn_layout", "avro") == "probhat", p.bool("space_lang", false))
            PagePlan.KEYS -> PagePlan.keysSummary(p.bool("haptic", true), p.bool("sound", false), p.bool("key_popup", false))
            PagePlan.CLIP -> PagePlan.clipSummary(p.bool("clip_on", true), p.bool("edit_bar", true))
            PagePlan.DATA -> PagePlan.dataSummary(p.bool("bk_auto", true), BuildConfig.VERSION_NAME)
            else -> ""
        }
    } catch (_: Throwable) { "" }
}
