package com.mgb.keyboard

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.HomePlan
import com.mgb.keyboard.typing.PagePlan

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
            if (id != PagePlan.HOME) { lastPage = id; attachTop(id) }
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

        val sv = ScrollView(act)
        sv.isFillViewport = true
        sv.addView(col)
        scrolls[id] = sv   // v0.13.0 (M3): for reveal()
        outer.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return outer
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
