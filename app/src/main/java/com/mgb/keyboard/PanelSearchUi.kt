package com.mgb.keyboard

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.PanelLayout
import com.mgb.keyboard.typing.PanelSearchData
import com.mgb.keyboard.typing.PanelTiles

/**
 * v0.16.11 (X11 part 2b): in-keyboard settings search for the NEW gear panel and the NEW More page (panel_ui = 1).
 * No EditText inside the IME window: same trick as the clipboard search = a tiny letters keyboard drawn inside the panel; its keys feed [query].
 * Layout added to [root]: [Search button row] [content = the tile grid, hidden while searching] [search box = query line + results + letters, shown while searching].
 * Results come from PanelSearchData (max PanelTiles.SEARCH_MAX); tap a result = host.showPanel(route) (the panel that holds the setting).
 * Never throws: on any problem the content is simply added without a search row.
 */
object PanelSearchUi {
    private const val KEY_H = 34     // dp, same as the clipboard search keys
    private const val LIST_H = 96    // dp, results list (scrolls)

    fun install(ctx: Context, p: JabraPrefs, host: JabraHost?, root: LinearLayout, content: View) {
        try {
            var searching = false
            var query = ""
            var rebuild: () -> Unit = { }
            val fb: (View) -> Unit = { v -> try { host?.feedback(v) } catch (_: Throwable) { } }
            val box = LinearLayout(ctx)
            box.orientation = LinearLayout.VERTICAL
            box.visibility = View.GONE
            val top = UiKit.row(ctx)
            val toggle = UiKit.btn(ctx, p, "🔍 Search settings", 1f, PanelLayout.MIN_TOUCH, JabraTokens.T_BTN, fb) {
                searching = !searching
                query = ""
                content.visibility = if (searching) View.GONE else View.VISIBLE
                box.visibility = if (searching) View.VISIBLE else View.GONE
                (top.getChildAt(0) as? Button)?.text = if (searching) "✕ Search" else "🔍 Search settings"
                rebuild()
            }
            top.addView(toggle)
            val ink = JabraTokens.panelText(p)

            fun keyRow(letters: String, extra: (LinearLayout) -> Unit = { }): LinearLayout {
                val r = UiKit.row(ctx)
                letters.forEach { ch -> r.addView(UiKit.btn(ctx, p, ch.toString(), 1f, KEY_H, JabraTokens.T_BTN, fb) { query += ch; rebuild() }) }
                extra(r)
                return r
            }

            rebuild = {
                try {
                    box.removeAllViews()
                    if (searching) {
                        val q = TextView(ctx)
                        q.text = query + "▏"
                        q.textSize = 15f
                        q.setTextColor(ink)
                        q.setPadding(JabraTokens.dp(ctx, 8), JabraTokens.dp(ctx, 2), JabraTokens.dp(ctx, 8), JabraTokens.dp(ctx, 2))
                        box.addView(q)
                        val hits = PanelSearchData.search(query)
                        if (hits.isEmpty()) {
                            box.addView(UiKit.noteText(ctx, p, if (PanelTiles.isSearching(query)) "No match" else "Type a setting name, e.g. height"))
                        } else {
                            val list = LinearLayout(ctx)
                            list.orientation = LinearLayout.VERTICAL
                            for (h in hits) {
                                val route = h.route
                                val line = UiKit.row(ctx)
                                line.addView(UiKit.btn(ctx, p, h.title + "   ›   " + h.group, 1f, PanelLayout.MIN_TOUCH, JabraTokens.T_BTN, fb) { host?.showPanel(route) })
                                list.addView(line)
                            }
                            val sv = ScrollView(ctx)
                            sv.addView(list)
                            box.addView(sv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, JabraTokens.dp(ctx, LIST_H)))
                        }
                        box.addView(keyRow("qwertyuiop"))
                        box.addView(keyRow("asdfghjkl") { r ->
                            r.addView(UiKit.btn(ctx, p, "␣", 1f, KEY_H, JabraTokens.T_BTN, fb) { query += " "; rebuild() })
                        })
                        box.addView(keyRow("zxcvbnm") { r ->
                            r.addView(UiKit.btn(ctx, p, "⌫", 1.5f, KEY_H, JabraTokens.T_BTN, fb) { if (query.isNotEmpty()) query = query.dropLast(1); rebuild() })
                        })
                    }
                } catch (_: Throwable) { }
            }

            root.addView(top)
            root.addView(content)
            root.addView(box)
        } catch (_: Throwable) {
            try { if (content.parent == null) root.addView(content) } catch (_: Throwable) { }
        }
    }
}
