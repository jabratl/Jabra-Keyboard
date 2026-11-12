package com.mgb.keyboard

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
}

/**
 * v0.11.0 (M1): page container. One FrameLayout shows either Home or one page (title bar + scroll body). Same Activity, no manifest change.
 * Any problem while opening a page = Home is shown again. System Back / the title bar button return to Home.
 * [about] builds the About section (update / emoji / app theme / old-screen switch) for the "Data & About" page.
 * [onShown] is called after every switch (the Activity pins or hides the preview card and refreshes the tile summaries).
 */
class AppPages(private val act: SettingsActivity, private val sink: PageSink, private val home: View,
               private val about: () -> View?, private val onShown: (String) -> Unit) {

    val frame = FrameLayout(act)
    var current: String = PagePlan.HOME
        private set
    private val views = HashMap<String, View>()
    private val cols = HashMap<String, LinearLayout>()

    private fun dp(v: Int): Int = (v * act.resources.displayMetrics.density).toInt()

    fun show(id0: String) {
        val id = PagePlan.sanitize(id0)
        try {
            val v: View = if (id == PagePlan.HOME) home else (views[id] ?: build(id).also { views[id] = it })
            frame.removeAllViews()
            (v.parent as? ViewGroup)?.removeView(v)
            frame.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            current = id
            if (id != PagePlan.HOME) attachTop(id)
            try { onShown(id) } catch (_: Throwable) { }
        } catch (_: Throwable) {
            try {   // could not open this page: show Home
                frame.removeAllViews()
                (home.parent as? ViewGroup)?.removeView(home)
                frame.addView(home, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                current = PagePlan.HOME
                try { onShown(PagePlan.HOME) } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    /** true = Back was used here (a page was closed). false = already on Home, the system handles Back. */
    fun back(): Boolean {
        if (current == PagePlan.HOME) return false
        show(PagePlan.HOME)
        return true
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
        bar.setPadding(dp(8), dp(4), dp(16), dp(4))
        val backBtn = Button(act)
        backBtn.text = "← Back"
        backBtn.isAllCaps = false
        backBtn.minHeight = dp(48)
        backBtn.minimumHeight = dp(48)
        backBtn.contentDescription = "Back to home"
        backBtn.setOnClickListener { try { back() } catch (_: Throwable) { } }
        bar.addView(backBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        val title = TextView(act)
        title.text = PagePlan.title(id)
        title.textSize = 20f
        title.setTypeface(null, Typeface.BOLD)
        try { title.setTextColor(AppUi.text(act)) } catch (_: Throwable) { }
        title.setPadding(dp(8), 0, 0, 0)
        try { title.isAccessibilityHeading = true } catch (_: Throwable) { }
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        outer.addView(bar)

        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(4), dp(16), dp(24))

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
        if (names.isEmpty()) {   // nothing to show: say so, the Back button still works
            val t = TextView(act)
            t.text = "Could not open this page. Use Back."
            try { t.setTextColor(AppUi.warn(act)) } catch (_: Throwable) { }
            col.addView(t)
        }
        cols[id] = col

        val sv = ScrollView(act)
        sv.isFillViewport = true
        sv.addView(col)
        outer.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return outer
    }

    private fun sectionHeader(text: String): View {
        val t = TextView(act)
        t.text = text
        t.textSize = 16f
        t.setTypeface(null, Typeface.BOLD)
        try { t.setTextColor(AppUi.accent(act)) } catch (_: Throwable) { }
        t.setPadding(0, dp(16), 0, dp(4))
        try { t.isAccessibilityHeading = true } catch (_: Throwable) { }
        return t
    }
}
