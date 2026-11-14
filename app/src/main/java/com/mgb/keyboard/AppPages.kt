package com.mgb.keyboard

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.HomePlan
import com.mgb.keyboard.typing.PagePlan
import com.mgb.keyboard.typing.SectionPlan

/**
 * v0.11.0 (M1 of the "App UI" plan): SettingsGroups.build(ctx, link, sink) hands its group bodies to this sink instead of building the accordion.
 * The Row registry (search / recent / undo / reset group) stays alive inside SettingsGroups; M1 only MOVES the views.
 *   bodies = group name -> the group body (rows, always open)   top = intro + search + undo + recent   clearSearch = empties the search box
 */
class PageSink {
    val bodies = LinkedHashMap<String, View>()
    var top: View? = null
    var clearSearch: (() -> Unit)? = null
    val rows = ArrayList<SinkRow>()   // v0.13.0 (M3): every row of every page, for the global search on Home (filled by SettingsGroups.addRowNew)
}

/** v0.13.0 (M3): one row as the global search sees it. [view] = the row on its page (scroll target + flash), [changed] = dot rule, [value] = short value text ("ON", "120%"). */
class SinkRow(val key: String, val title: String, val group: String, val help: String, val view: View,
              val changed: () -> Boolean, val value: () -> String)

/**
 * v0.11.0 (M1): page container. One FrameLayout shows either Home or one page (title bar + scroll body). Same Activity, no manifest change.
 * Any problem while opening a page = Home is shown again. System Back / the title bar button return to Home.
 * [about] builds the About section (update / emoji / app theme / old-screen switch) for the "Data & About" page.
 * [onShown] is called after every switch (the Activity pins or hides the preview card and refreshes the tile summaries).
 */
class AppPages(private val act: SettingsActivity, private val sink: PageSink, private val home: View,
               private val about: () -> View?, private val twoPane: Boolean, private val oldScreen: () -> Unit,
               private val onShown: (String) -> Unit) {
    // v0.13.0 (M3): twoPane = true: Home lives in the left pane (the Activity places it), this frame is the right pane and never shows Home
    // (show(HOME) opens the last / first page, no Back button). oldScreen = button of the error card ("Use the old app screen").

    val frame = FrameLayout(act)
    var current: String = PagePlan.HOME
        private set
    private val views = HashMap<String, View>()
    private val cols = HashMap<String, LinearLayout>()
    private val scrolls = HashMap<String, ScrollView>()
    private var lastPage: String = ""
    private val forcedAdv = HashSet<View>()               // v0.17.80 (U5a): advanced rows opened on purpose (search hit / favourite) stay visible this session
    private val advButtons = HashMap<String, Button>()    // page id -> "Show advanced" button
    private val HEAD_KEY = 0x4A5B0001   // v0.17.81 (U5b): setTag key "click listener already set" (tag ids must be > 0x00FFFFFF)
    private val jumpBars = HashMap<String, View>()        // page id -> jump bar (chips follow the section visibility)

    /** The column of an opened page (the Activity puts the preview card there on short two-pane screens). null = page not built yet. */
    fun inlineHost(id: String): LinearLayout? = cols[id]

    private fun dp(v: Int): Int = (v * act.resources.displayMetrics.density).toInt()

    fun show(id0: String) {
        var id = PagePlan.sanitize(id0)
        if (twoPane && id == PagePlan.HOME) id = HomePlan.firstPage(lastPage)   // v0.13.0 (M3): the right pane has no Home page
        try {
            val v: View = if (id == PagePlan.HOME) home else (views[id] ?: build(id).also { views[id] = it })
            val moved = id != current   // v0.14.0 (M4): fade only when another page appears
            frame.removeAllViews()
            (v.parent as? ViewGroup)?.removeView(v)
            frame.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            current = id
            if (moved) AppMotion.fadeIn(v)
            if (id != PagePlan.HOME) { lastPage = id; attachTop(id); applyAdvanced(id) }   // v0.17.80 (U5a): changed state may have moved since the last visit
            try { onShown(id) } catch (_: Throwable) { }
        } catch (_: Throwable) {
            try {   // v0.13.0 (M3): could not open this page: a card with [Back] and [Use the old app screen] (was: silently Home)
                frame.removeAllViews()
                val wrap = ScrollView(act)
                wrap.addView(errorCard(id))
                frame.addView(wrap, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                current = id
                try { onShown(id) } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    /** true = Back was used here (a page was closed). false = already on Home (or two panes: no Home page), the system handles Back. */
    fun back(): Boolean {
        if (twoPane || current == PagePlan.HOME) return false
        show(PagePlan.HOME)
        return true
    }

    /**
     * v0.13.0 (M3): opens page [id] and scrolls to [target] (a row view of that page) and flashes it. Any problem = the page is simply open.
     * The scroll waits for the next layout pass (the page was just put into the frame).
     */
    fun reveal(id: String, target: View?) {
        show(id)
        try {
            val t = target ?: return
            val pid = PagePlan.sanitize(id)
            if (!t.isShown) { openSectionOf(pid, t); if (t.visibility != View.VISIBLE) forcedAdv.add(t); applyAdvanced(pid) }   // v0.17.81 (U5b): also opens a closed section   // v0.17.80 (U5a): a search hit / favourite always shows itself
            val sv = scrolls[pid] ?: return
            val col = cols[pid] ?: return
            var done = false
            fun go() {
                if (done) return
                done = true
                try {
                    var y = 0
                    var v: View? = t
                    while (v != null && v !== col) { y += v.top; v = v.parent as? View }
                    if (v === col) sv.smoothScrollTo(0, HomePlan.revealY(y, dp(72)))
                    flash(t)
                    try { AppHelp.openIn(t) } catch (_: Throwable) { }   // v0.17.78 (U3): a search hit / favourite jump opens the help of that row
                } catch (_: Throwable) { }
            }
            sv.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    try { if (sv.viewTreeObserver.isAlive) sv.viewTreeObserver.removeOnGlobalLayoutListener(this) } catch (_: Throwable) { }
                    go()
                }
            })
            sv.requestLayout()
        } catch (_: Throwable) { }
    }

    private fun flash(v: View) {   // soft accent background for about one second, then the old background again
        try {
            val old = v.background
            val g = GradientDrawable()
            g.setColor((AppUi.accent(act) and 0x00FFFFFF) or 0x40000000)
            g.cornerRadius = dp(AppTokens.S2).toFloat()
            v.background = g
            v.postDelayed({ try { v.background = old } catch (_: Throwable) { } }, 1100)
        } catch (_: Throwable) { }
    }

    /** v0.13.0 (M3): error / empty state card. Buttons: Back to Home (one pane only) and the old app screen. */
    private fun errorCard(id: String): View {
        val card = LinearLayout(act)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(AppTokens.S4), dp(AppTokens.S4), dp(AppTokens.S4), dp(AppTokens.S4))
        try { card.background = AppUi.cardBg(act, 12) } catch (_: Throwable) { }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(AppTokens.S4), dp(AppTokens.S4), dp(AppTokens.S4), dp(AppTokens.S4))
        card.layoutParams = lp
        val t = TextView(act)
        t.text = "Could not open this page"
        t.textSize = 16f
        t.setTypeface(null, Typeface.BOLD)
        try { t.setTextColor(AppUi.warn(act)) } catch (_: Throwable) { }
        try { t.isAccessibilityHeading = true } catch (_: Throwable) { }
        card.addView(t)
        val m = TextView(act)
        m.text = "Your settings are safe. You can go back, or use the old app screen."
        try { m.setTextColor(AppUi.text(act)) } catch (_: Throwable) { }
        m.setPadding(0, dp(6), 0, dp(AppTokens.S2))
        card.addView(m)
        if (!twoPane) {
            val b = Button(act)
            b.text = "Back to Home"
            b.isAllCaps = false
            b.minHeight = dp(AppTokens.TOUCH); b.minimumHeight = dp(AppTokens.TOUCH)
            b.setOnClickListener { try { show(PagePlan.HOME) } catch (_: Throwable) { } }
            card.addView(b)
        }
        val o = Button(act)
        o.text = "Use the old app screen"
        o.isAllCaps = false
        o.minHeight = dp(AppTokens.TOUCH); o.minimumHeight = dp(AppTokens.TOUCH)
        o.setOnClickListener { try { oldScreen() } catch (_: Throwable) { } }
        card.addView(o)
        return card
    }

    // search + undo + recent live in ONE view; it moves to the page that is open (search box is emptied first)
    private fun attachTop(id: String) {
        try {
            val top = sink.top ?: return
            try { sink.clearSearch?.invoke() } catch (_: Throwable) { }
            (top.parent as? ViewGroup)?.removeView(top)
            cols[id]?.addView(top, 0)
        } catch (_: Throwable) { }
    }

    private fun build(id: String): View {
        val dark = try { AppUi.isDark(act) } catch (_: Throwable) { false }
        val outer = LinearLayout(act)
        outer.orientation = LinearLayout.VERTICAL
        try { if (dark) outer.setBackgroundColor(AppUi.bg(act)) } catch (_: Throwable) { }

        val bar = LinearLayout(act)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(AppTokens.S2), dp(AppTokens.S1), dp(AppTokens.S4), dp(AppTokens.S1))
        val backBtn = Button(act)
        backBtn.text = "\u2039 Home"
        backBtn.isAllCaps = false
        backBtn.background = null
        backBtn.stateListAnimator = null
        try { backBtn.setTextColor(AppUi.accent(act)) } catch (_: Throwable) { }
        backBtn.minHeight = dp(AppTokens.TOUCH)
        backBtn.minimumHeight = dp(AppTokens.TOUCH)
        backBtn.contentDescription = "Back to home"
        backBtn.setOnClickListener { try { back() } catch (_: Throwable) { } }
        if (!twoPane) bar.addView(backBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(AppTokens.TOUCH)))   // v0.13.0 (M3): no Back button in two panes (Home is always visible)
        val title = TextView(act)
        title.text = PagePlan.title(id)
        title.textSize = 20f
        title.setTypeface(null, Typeface.BOLD)
        try { title.setTextColor(AppUi.text(act)) } catch (_: Throwable) { }
        title.setPadding(if (twoPane) dp(AppTokens.S4) else dp(AppTokens.S2), 0, 0, 0)
        try { title.isAccessibilityHeading = true } catch (_: Throwable) { }
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        outer.addView(bar)
        try {   // thin line under the title bar
            val rule = View(act)
            rule.setBackgroundColor((AppUi.dim(act) and 0x00FFFFFF) or 0x30000000)
            outer.addView(rule, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        } catch (_: Throwable) { }

        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(AppTokens.S4), dp(AppTokens.S1), dp(AppTokens.S4), dp(24))

        val known = PagePlan.groupsOf(id).filter { sink.bodies.containsKey(it) }
        val extra = sink.bodies.keys.filter { PagePlan.pageOfGroup(it) == id && !known.contains(it) }
        val names = known + extra
        for (name in names) {
            val body = sink.bodies[name] ?: continue
            (body.parent as? ViewGroup)?.removeView(body)
            if (names.size > 1 || id == PagePlan.DATA) col.addView(sectionHeader(name))
            col.addView(body)
        }
        if (id == PagePlan.DATA) {
            try { about()?.let { col.addView(sectionHeader("About & updates")); col.addView(it) } } catch (_: Throwable) { }
        }
        if (names.isEmpty()) {   // nothing to show: error card (v0.13.0: with a button to the old screen)
            col.addView(errorCard(id))
        }
        cols[id] = col
        try {   // v0.17.80 (U5a): Typing + Look: "Show advanced settings" button at the top of the page (pref app_adv, default OFF)
            if (SectionPlan.hasAdvanced(id)) {
                val ab = Button(act)
                ab.isAllCaps = false
                ab.minHeight = dp(AppTokens.TOUCH); ab.minimumHeight = dp(AppTokens.TOUCH)
                try { AppRows.styleAction(act, ab) } catch (_: Throwable) { }
                ab.setOnClickListener {
                    try {
                        val p = JabraPrefs(act)
                        p.setBool(SectionPlan.PREF, !p.bool(SectionPlan.PREF, false))
                        forcedAdv.clear()
                        applyAdvanced(id)
                    } catch (_: Throwable) { }
                }
                col.addView(ab, 0)
                advButtons[id] = ab
            }
        } catch (_: Throwable) { }

        val sv = ScrollView(act)
        sv.isFillViewport = true
        sv.addView(col)
        scrolls[id] = sv   // v0.13.0 (M3): for reveal()
        try { jumpBar(col, sv)?.let { outer.addView(it); jumpBars[id] = it } } catch (_: Throwable) { }   // v0.17.65 (R5): fixed "jump to section" bar under the title
        applyAdvanced(id)   // v0.17.80 (U5a): after the jump bar exists, so its chips can follow the sections
        outer.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return outer
    }

    /**
     * v0.17.80 (U5a): hides the advanced rows of a page (SectionPlan) unless app_adv is ON, the row is changed, or it was opened from search / favourites.
     * Then a section whose rows are all hidden hides its header + card, and the jump bar chips + the button text follow. Never throws; a problem = rows stay as they are.
     */
    private fun applyAdvanced(id: String) {
        try {
            val col = cols[id] ?: return
            val prefs = JabraPrefs(act)
            val on = prefs.bool(SectionPlan.PREF, false)
            var hidden = 0
            if (SectionPlan.hasAdvanced(id)) {
                for (r in sink.rows) {
                    if (PagePlan.pageOfGroup(r.group) != id || !SectionPlan.isAdvanced(r.key)) continue
                    val ch = try { r.changed() } catch (_: Throwable) { false }
                    val show = SectionPlan.visible(on, true, ch, forcedAdv.contains(r.view))
                    r.view.visibility = if (show) View.VISIBLE else View.GONE
                    if (!show) hidden++
                }
            }
            val closed = prefs.str(SectionPlan.PREF_CLOSED, "")
            val heads = ArrayList<Pair<String, View>>()
            findHeads(col, heads)
            for ((name, hd) in heads) {
                val parent = hd.parent as? ViewGroup ?: continue
                val bx = parent.getChildAt(parent.indexOfChild(hd) + 1) as? ViewGroup ?: continue
                var any = false
                for (i in 0 until bx.childCount) { val c = bx.getChildAt(i); if (c.tag != "secreset" && c.visibility == View.VISIBLE) any = true }
                if (!any && bx.childCount == 0) continue   // not a row box (nothing to decide)
                val open = SectionPlan.isOpen(closed, SectionPlan.secId(id, name))   // v0.17.81 (U5b): collapsed = card hidden, header stays
                hd.visibility = if (any) View.VISIBLE else View.GONE
                bx.visibility = if (any && open) View.VISIBLE else View.GONE
                for (i in 0 until bx.childCount) { val c = bx.getChildAt(i); if (c.tag == "secreset") c.visibility = if (any) View.VISIBLE else View.GONE }
                try { styleHead(id, name, hd, open) } catch (_: Throwable) { }
            }
            jumpBars[id]?.let { fixChips(it) }
            advButtons[id]?.let { b -> b.text = SectionPlan.buttonText(on, hidden); b.contentDescription = b.text }
        } catch (_: Throwable) { }
    }

    /** v0.17.81 (U5b): section header = tap target with an arrow; the state is saved in pref sec_open (closed sections only). */
    private fun styleHead(page: String, name: String, hd: View, open: Boolean) {
        val tv = (hd as? ViewGroup)?.getChildAt(0) as? TextView
        tv?.text = SectionPlan.headerText(name, open)
        hd.minimumHeight = dp(AppTokens.TOUCH)
        hd.contentDescription = name + ", " + (if (open) "expanded" else "collapsed")
        if (hd.tag != null && hd.getTag(HEAD_KEY) == null) {
            hd.setTag(HEAD_KEY, true)
            hd.isClickable = true
            hd.isFocusable = true
            try { AppA11y.asButton(hd) } catch (_: Throwable) { }
            hd.setOnClickListener {
                try {
                    val p = JabraPrefs(act)
                    val sid = SectionPlan.secId(page, name)
                    p.setStr(SectionPlan.PREF_CLOSED, SectionPlan.setOpen(p.str(SectionPlan.PREF_CLOSED, ""), sid, !SectionPlan.isOpen(p.str(SectionPlan.PREF_CLOSED, ""), sid)))
                    applyAdvanced(page)
                } catch (_: Throwable) { }
            }
        }
    }

    /** v0.17.81 (U5b): a search hit / favourite inside a closed section opens that section first. */
    private fun openSectionOf(page: String, t: View) {
        try {
            val col = cols[page] ?: return
            var v: View? = t
            while (v != null && v !== col) {
                val par = v.parent as? ViewGroup ?: return
                val i = par.indexOfChild(v)
                if (i > 0) {
                    val h = par.getChildAt(i - 1)
                    val tg = h.tag
                    if (tg is String && tg.startsWith("sechead:")) {
                        val p = JabraPrefs(act)
                        val sid = SectionPlan.secId(page, tg.substring(8))
                        if (!SectionPlan.isOpen(p.str(SectionPlan.PREF_CLOSED, ""), sid)) p.setStr(SectionPlan.PREF_CLOSED, SectionPlan.setOpen(p.str(SectionPlan.PREF_CLOSED, ""), sid, true))
                        return
                    }
                }
                v = par
            }
        } catch (_: Throwable) { }
    }

    private fun fixChips(v: View) {   // a chip carries its section header in tag
        val t = v.tag
        if (t is View) v.visibility = if (t.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        if (v is ViewGroup) for (i in 0 until v.childCount) fixChips(v.getChildAt(i))
    }

    private fun findHeads(v: View, out: MutableList<Pair<String, View>>) {
        val t = v.tag
        if (t is String && t.startsWith("sechead:")) out.add(Pair(t.substring(8), v))
        if (v is ViewGroup) for (i in 0 until v.childCount) findHeads(v.getChildAt(i), out)
    }

    /** v0.17.65 (R5): a page with 3+ sections gets one row of small chips (one per section, fixed under the title). Tap = the page scrolls to that section. Never throws; null = no bar. */
    private fun jumpBar(col: LinearLayout, sv: ScrollView): View? {
        val heads = ArrayList<Pair<String, View>>()
        findHeads(col, heads)
        val shown = heads.filter { it.second.visibility == View.VISIBLE }
        if (shown.size < 3) return null
        val dark = try { AppUi.isDark(act) } catch (_: Throwable) { false }
        val hs = HorizontalScrollView(act)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(AppTokens.S4), dp(AppTokens.S1), dp(AppTokens.S4), dp(AppTokens.S1))
        for ((name, hd) in shown) {
            val b = Button(act)
            b.text = name
            b.isAllCaps = false
            b.textSize = 13f
            b.minWidth = 0; b.minimumWidth = 0
            b.minHeight = dp(36); b.minimumHeight = dp(36)
            b.setPadding(dp(AppTokens.S3), 0, dp(AppTokens.S3), 0)
            b.stateListAnimator = null
            try { b.setTextColor(AppUi.text(act)) } catch (_: Throwable) { }
            try {
                val g = GradientDrawable()
                g.setColor(com.mgb.keyboard.typing.AppPalette.card(dark))
                g.cornerRadius = dp(18).toFloat()
                g.setStroke(dp(1), (AppUi.dim(act) and 0x00FFFFFF) or 0x40000000)
                b.background = g
            } catch (_: Throwable) { }
            b.contentDescription = "Jump to " + name
            b.tag = hd   // v0.17.80 (U5a): applyAdvanced hides the chip when its section is hidden
            b.setOnClickListener {
                try {
                    var y = 0
                    var v: View? = hd
                    while (v != null && v !== col) { y += v.top; v = v.parent as? View }
                    sv.smoothScrollTo(0, Math.max(0, y - dp(AppTokens.S2)))
                } catch (_: Throwable) { }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36))
            lp.setMargins(0, 0, dp(AppTokens.S2), 0)
            row.addView(b, lp)
        }
        hs.addView(row)
        return hs
    }

    private fun sectionHeader(text: String): View {
        val t = TextView(act)
        t.text = text
        t.textSize = 16f
        t.setTypeface(null, Typeface.BOLD)
        try { t.setTextColor(AppUi.accent(act)) } catch (_: Throwable) { }
        t.setPadding(0, dp(AppTokens.S4) + dp(AppTokens.S2), 0, dp(AppTokens.S1))
        try { t.isAccessibilityHeading = true } catch (_: Throwable) { }
        return t
    }
}
