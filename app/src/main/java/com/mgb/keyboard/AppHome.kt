package com.mgb.keyboard

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.EdgePlan
import com.mgb.keyboard.typing.HomePlan
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

    fun build(act: SettingsActivity, setupCard: View?, pvLink: PreviewLink, search: AppSearchView?, rowsChanged: () -> List<Pair<String, Boolean>> = { emptyList() }, open: (String) -> Unit): AppHomeView {   // v0.17.79 (U4): rowsChanged = (old group, changed flag) of every row, for the tile badges   // v0.13.0 (M3): search = global search (null = none)
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
        title.textSize = AppTokens.T_HEAD.toFloat()
        title.setTypeface(null, Typeface.BOLD)
        try { title.setTextColor(AppUi.text(act)) } catch (_: Throwable) { }
        try { title.isAccessibilityHeading = true } catch (_: Throwable) { }
        val ver = TextView(act)
        ver.text = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
        ver.textSize = AppTokens.T_CAPTION.toFloat()   // v0.17.64 (R4): quieter version line
        try { ver.setTextColor(AppUi.dim(act)) } catch (_: Throwable) { }
        names.addView(title)
        names.addView(ver)
        head.addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(head)

        // ---- v0.17.79 (U4): sticky search. The input line leaves the scrolling list and stays on top; its results (2nd child of the box) move to the top of the list.
        //      Unknown box shape = old behaviour (whole search view inside the list). Favourites stay right under the search. ----
        var fieldBox: ViewGroup? = null
        var resultsView: View? = null
        try {
            if (search != null) {
                val box = search.view as? ViewGroup
                val res = if (box != null && box.childCount >= 2) box.getChildAt(1) else null
                if (box != null && res != null) { box.removeView(res); fieldBox = box; resultsView = res; col.addView(res) } else col.addView(search.view)
                col.addView(search.favs)   // v0.15.0 (M5): favourites right under the search box
            }
        } catch (_: Throwable) { }

        // ---- v0.17.79 (U4): ONE status card = the setup card / wizard while setup is open, else ONE warning card (crash guard first, then old backup) with an action ----
        val statusBox = LinearLayout(act)
        statusBox.orientation = LinearLayout.VERTICAL
        try { if (setupCard != null) { (setupCard.parent as? ViewGroup)?.removeView(setupCard); statusBox.addView(setupCard) } } catch (_: Throwable) { }
        val warnCard = LinearLayout(act)
        warnCard.orientation = LinearLayout.VERTICAL
        warnCard.visibility = View.GONE
        try { warnCard.background = AppUi.card(act) } catch (_: Throwable) { }
        warnCard.setPadding(dp(AppTokens.S3), dp(AppTokens.S3), dp(AppTokens.S3), dp(AppTokens.S3))
        val wTitle = TextView(act)
        wTitle.textSize = AppTokens.T_TITLE.toFloat()
        wTitle.setTypeface(null, Typeface.BOLD)
        try { wTitle.setTextColor(AppUi.warn(act)); wTitle.isAccessibilityHeading = true } catch (_: Throwable) { }
        val wText = TextView(act)
        wText.textSize = AppTokens.T_BODY.toFloat()
        wText.setPadding(0, dp(AppTokens.S1), 0, 0)
        try { wText.setTextColor(AppUi.text(act)) } catch (_: Throwable) { }
        val wBtn = Button(act)
        wBtn.isAllCaps = false
        wBtn.minHeight = dp(AppTokens.TOUCH)
        wBtn.minimumHeight = dp(AppTokens.TOUCH)
        try { AppRows.styleAction(act, wBtn) } catch (_: Throwable) { }
        warnCard.addView(wTitle)
        warnCard.addView(wText)
        warnCard.addView(wBtn)
        val warnLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        warnLp.setMargins(0, dp(AppTokens.S3), 0, dp(AppTokens.S1))
        statusBox.addView(warnCard, warnLp)
        var statusNow = HomePlan.ST_NONE
        fun refreshStatus() {
            try {
                val pending = setupCard != null && setupCard.visibility == View.VISIBLE
                val g = try { PanelGuard.note(act) } catch (_: Throwable) { "" }
                val b = try { com.mgb.keyboard.typing.HealthCheck.backupNote(prefs.bool("bk_auto", true), prefs.str("bk_last", ""), System.currentTimeMillis()) } catch (_: Throwable) { "" }
                statusNow = HomePlan.status(pending, g, b)
                if (statusNow == HomePlan.ST_GUARD || statusNow == HomePlan.ST_BACKUP) {
                    wTitle.text = HomePlan.statusTitle(statusNow)
                    wText.text = HomePlan.statusText(statusNow, g, b)
                    wBtn.text = HomePlan.statusAction(statusNow)
                    wBtn.contentDescription = wBtn.text
                    warnCard.visibility = View.VISIBLE
                } else warnCard.visibility = View.GONE
            } catch (_: Throwable) { warnCard.visibility = View.GONE }
        }
        wBtn.setOnClickListener {
            try {
                if (statusNow == HomePlan.ST_GUARD) { PanelGuard.reset(act); refreshStatus() }
                else if (statusNow == HomePlan.ST_BACKUP) open(PagePlan.DATA)
            } catch (_: Throwable) { }
        }
        refreshStatus()
        col.addView(statusBox)

        // ---- v0.18 (easy browse): the Settings list comes first, then Customize, then Quick looks, then the test field ----
        col.addView(label(act, "Settings", dp(AppTokens.S4)))
        val tilesBox = LinearLayout(act)
        tilesBox.orientation = LinearLayout.VERTICAL
        col.addView(tilesBox)
        val customBox = LinearLayout(act)
        customBox.orientation = LinearLayout.VERTICAL
        col.addView(customBox)

        // ---- Quick looks ----
        col.addView(label(act, "Quick looks", dp(AppTokens.S4)))
        try {   // v0.17.64 (R4): one line that says what a Quick look does
            val qh = TextView(act)
            qh.text = "One tap sets key size and spacing. You can undo it."
            qh.textSize = AppTokens.T_CAPTION.toFloat()
            qh.setTextColor(AppUi.dim(act))
            qh.setPadding(0, dp(AppTokens.S1), 0, dp(AppTokens.S1))
            col.addView(qh)
        } catch (_: Throwable) { }
        val keys = EdgePlan.PRESET_KEYS
        fun curVals(): IntArray = IntArray(keys.size) { if (EdgePlan.isBool(it)) (if (prefs.bool(keys[it], false)) 1 else 0) else prefs.int(keys[it], -1) }
        fun write(v: IntArray) { for (i in keys.indices) { if (EdgePlan.isBool(i)) prefs.setBool(keys[i], v[i] == 1) else prefs.setInt(keys[i], v[i]) } }

        val note = TextView(act)
        try { note.setTextColor(AppUi.dim(act)) } catch (_: Throwable) { }
        note.textSize = AppTokens.T_CAPTION.toFloat()
        note.setPadding(0, dp(AppTokens.S1), 0, 0)
        val undo = Button(act)
        undo.isAllCaps = false
        undo.text = "Undo last look"
        undo.minHeight = dp(AppTokens.TOUCH)
        undo.minimumHeight = dp(AppTokens.TOUCH)
        fun hasUndo(): Boolean = EdgePlan.decodeUpTo(prefs.str("geo_prev", ""), keys.size) != null
        undo.visibility = if (hasUndo()) View.VISIBLE else View.GONE
        try { AppRows.styleAction(act, undo) } catch (_: Throwable) { }

        val tileSum = ArrayList<Pair<String, TextView>>()
        val tileViews = HashMap<String, View>()   // v0.13.0 (M3)
        val tileBadge = HashMap<String, TextView>()   // v0.17.79 (U4): "3 changed" pill per tile (hidden at 0)
        fun counts(): IntArray = try {   // changed rows per page, same order as PagePlan.ORDER
            val rows = rowsChanged()
            PagePlan.changedByPage(rows.map { it.first }, BooleanArray(rows.size) { rows[it].second })
        } catch (_: Throwable) { IntArray(PagePlan.ORDER.size) }
        fun badgeOf(id: String, c: IntArray): Int { val i = PagePlan.index(id); return if (i in c.indices) c[i] else 0 }
        fun refreshTiles() {
            val c = counts()
            for ((id, tv) in tileSum) {
                try {
                    tv.text = summary(act, prefs, id)
                    val n = badgeOf(id, c)
                    tileBadge[id]?.let { b -> b.text = PagePlan.badge(n); b.visibility = if (n > 0) View.VISIBLE else View.GONE }
                    tileViews[id]?.contentDescription = PagePlan.tileDescription(id, tv.text.toString(), n)   // v0.13.0 (M3): TalkBack reads title + summary (+ changed count, U4)
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
            b.textSize = AppTokens.T_CAPTION.toFloat()
            b.minHeight = dp(AppTokens.TOUCH)
            b.minimumHeight = dp(AppTokens.TOUCH)
            b.minWidth = 0
            b.minimumWidth = 0
            b.contentDescription = "Quick look: " + PagePlan.quickLabel(id)
            try { AppRows.styleAction(act, b) } catch (_: Throwable) { }
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

        col.addView(label(act, "Try the keyboard", dp(AppTokens.S4)))
        val test = EditText(act)
        test.hint = "Type here to test the keyboard"
        try {   // v0.17.64 (R4): same card look as the tiles, 48 dp touch height
            test.background = AppUi.cardBg(act, 12)
            test.setPadding(dp(AppTokens.S3), dp(AppTokens.S3), dp(AppTokens.S3), dp(AppTokens.S3))
            test.minHeight = dp(AppTokens.TOUCH)
            test.setTextColor(AppUi.text(act))
            test.setHintTextColor(AppUi.dim(act))
        } catch (_: Throwable) { }
        col.addView(test)

        // ---- tiles: one list row per page (icon, title, short summary, arrow); v0.15.0 (M5): own order + hidden tiles (PersonalPlan) ----
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
            tileBadge.clear()
            val cnt = counts()
            val ids = PersonalPlan.visible(prefs.str(PersonalPlan.PREF_ORDER, ""), prefs.str(PersonalPlan.PREF_HIDE, ""))
            for (i in ids.indices) {
                val id = ids[i]
                val tile = LinearLayout(act)
                tile.orientation = LinearLayout.HORIZONTAL
                tile.gravity = Gravity.CENTER_VERTICAL
                tile.setPadding(dp(AppTokens.S3), dp(AppTokens.S3), dp(AppTokens.S3), dp(AppTokens.S3))
                tile.minimumHeight = dp(72)
                try { tile.background = AppUi.cardBg(act, 12) } catch (_: Throwable) { }
                tile.isClickable = true
                tile.isFocusable = true
                // icon on a soft accent square
                val chip = FrameLayout(act)
                try {
                    val cg = GradientDrawable()
                    cg.setColor((AppUi.accent(act) and 0x00FFFFFF) or 0x26000000)
                    cg.cornerRadius = dp(10).toFloat()
                    chip.background = cg
                } catch (_: Throwable) { }
                val icon = Button(act)   // JabraIcons draws on a Button; here it is only a picture
                icon.background = null
                icon.minWidth = 0; icon.minimumWidth = 0; icon.minHeight = 0; icon.minimumHeight = 0
                icon.setPadding(0, 0, 0, 0)
                icon.isClickable = false
                icon.isFocusable = false
                try { JabraIcons.apply(icon, PagePlan.icon(id), true, AppUi.accent(act)) } catch (_: Throwable) { }
                icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                val ilp = FrameLayout.LayoutParams(dp(26), dp(26))
                ilp.gravity = Gravity.CENTER
                chip.addView(icon, ilp)
                chip.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                tile.addView(chip, LinearLayout.LayoutParams(dp(44), dp(44)))
                // title + one-line summary
                val texts = LinearLayout(act)
                texts.orientation = LinearLayout.VERTICAL
                texts.setPadding(dp(AppTokens.S3), 0, dp(AppTokens.S2), 0)
                val tt = TextView(act)
                tt.text = PagePlan.title(id)
                tt.textSize = AppTokens.T_TITLE.toFloat()
                tt.setTypeface(null, Typeface.BOLD)
                try { tt.setTextColor(AppUi.text(act)) } catch (_: Throwable) { }
                texts.addView(tt)
                val sm = TextView(act)
                sm.textSize = AppTokens.T_LABEL.toFloat()
                sm.maxLines = 2; sm.ellipsize = android.text.TextUtils.TruncateAt.END   // v0.17.64 (R4): even tile height
                try { sm.setTextColor(AppUi.dim(act)) } catch (_: Throwable) { }
                sm.text = summary(act, prefs, id)
                texts.addView(sm)
                tile.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                // v0.17.79 (U4): changed-count badge (hidden at 0; spoken inside the tile description)
                val nChanged = badgeOf(id, cnt)
                try {
                    val bd = AppUi.pill(act, PagePlan.badge(nChanged), false)
                    bd.textSize = AppTokens.T_CAPTION.toFloat()
                    bd.visibility = if (nChanged > 0) View.VISIBLE else View.GONE
                    bd.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    blp.setMargins(0, 0, dp(AppTokens.S2), 0)
                    tile.addView(bd, blp)
                    tileBadge[id] = bd
                } catch (_: Throwable) { }
                // arrow
                val arrow = TextView(act)
                arrow.text = "\u203A"
                arrow.textSize = AppTokens.T_DISPLAY.toFloat()
                try { arrow.setTextColor(AppUi.dim(act)) } catch (_: Throwable) { }
                arrow.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                tile.addView(arrow)
                tileSum.add(Pair(id, sm))
                tile.contentDescription = PagePlan.tileDescription(id, sm.text.toString(), nChanged)   // v0.13.0 (M3) + U4
                AppA11y.asButton(tile)
                tileViews[id] = tile
                tile.setOnClickListener { try { open(id) } catch (_: Throwable) { } }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, dp(AppTokens.S1), 0, dp(AppTokens.S1))
                tilesBox.addView(tile, lp)
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
            b.textSize = AppTokens.T_CAPTION.toFloat()
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
            hint.textSize = AppTokens.T_CAPTION.toFloat()
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
                name.textSize = AppTokens.T_BODY.toFloat()
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
        try { AppRows.styleAction(act, customBtn) } catch (_: Throwable) { }
        customBtn.setOnClickListener {
            try {
                val open2 = custom.visibility != View.VISIBLE
                if (open2) buildCustom()
                custom.visibility = if (open2) View.VISIBLE else View.GONE
                customBtn.text = if (open2) "Done" else "Customize Home"
            } catch (_: Throwable) { }
        }
        customBox.addView(customBtn)
        customBox.addView(custom)

        try {   // v0.13.0 (M3): lists at the end; everything under the search box is hidden while a search text is typed
            if (search != null) {
                col.addView(search.lists)
                val at = col.indexOfChild(search.view)
                if (at >= 0) search.bindRest((at + 1 until col.childCount).map { col.getChildAt(it) })
                else if (resultsView != null) {   // v0.17.79 (U4): sticky search: the logo header and everything after the results hide while a text is typed
                    val ra = col.indexOfChild(resultsView)
                    if (ra >= 0) search.bindRest(listOf<View>(head) + (ra + 1 until col.childCount).map { col.getChildAt(it) })
                }
            }
        } catch (_: Throwable) { }

        val sv = ScrollView(act)
        sv.isFillViewport = true
        sv.addView(col)
        // v0.17.79 (U4): outer = [sticky search line] + [scrolling list]. No sticky line (no search / unknown shape) = the scroll view alone, as before.
        val outer = LinearLayout(act)
        outer.orientation = LinearLayout.VERTICAL
        try { if (dark) outer.setBackgroundColor(AppUi.bg(act)) } catch (_: Throwable) { }
        val fb = fieldBox
        if (fb != null) {
            fb.setPadding(dp(AppTokens.S4), dp(AppTokens.S2), dp(AppTokens.S4), dp(AppTokens.S1))
            outer.addView(fb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        outer.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return AppHomeView(outer, {
            try { refreshTiles(); refreshStatus(); undo.visibility = if (hasUndo()) View.VISIBLE else View.GONE } catch (_: Throwable) { }
            try { search?.refresh?.invoke() } catch (_: Throwable) { }
        }, { id -> select(id) })
    }

    /** v0.17.64 (R4): Home headings use the same small-caps + divider look as the page sections (AppRows.sectionHeader). Old look = fallback. */
    private fun label(act: SettingsActivity, text: String, topPx: Int): View {
        try { return AppRows.sectionHeader(act, text) } catch (_: Throwable) { }
        val t = TextView(act)
        t.text = text
        t.textSize = AppTokens.T_TITLE.toFloat()
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
            PagePlan.TOOLS -> PagePlan.toolsSummary(p.bool("edit_bar", true), p.bool("fmt_on", true))   // v0.15.3 (N3)
            PagePlan.DATA -> PagePlan.dataSummary(p.bool("bk_auto", true), BuildConfig.VERSION_NAME)
            else -> ""
        }
    } catch (_: Throwable) { "" }
}
