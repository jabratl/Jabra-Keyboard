package com.mgb.keyboard

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.TypingLevels

/**
 * v0.1.7 settings page in 5 groups (Ridmik-like), shown on the launcher screen (SettingsActivity).
 * It only reads / writes the SAME pref keys and defaults as the in-keyboard panels (gear, More settings, Typing options),
 * so both places always agree. Nothing is renamed. Built in code; build() is called inside try/catch by the hook,
 * so on any problem the launcher screen looks exactly like v0.1.6.
 * Changes are read by the keyboard the next time it opens (or after "ABC (apply)" inside the keyboard).
 */
object SettingsGroups {

    fun build(ctx: Context): View {
        val prefs = JabraPrefs(ctx)
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL

        val intro = TextView(ctx)
        intro.text = "Keyboard settings (same settings as inside the keyboard). Changes apply when the keyboard opens next time."
        intro.setPadding(0, dp(12), 0, dp(4))
        root.addView(intro)

        fun onOff(b: Boolean) = if (b) "ON" else "OFF"

        fun btn(label: String, onClick: (Button) -> Unit): Button {
            val b = Button(ctx)
            b.text = label
            b.isAllCaps = false
            b.setOnClickListener { try { onClick(b) } catch (_: Throwable) { } }
            return b
        }

        fun toggle(box: LinearLayout, title: String, key: String, def: Boolean) {
            val b = btn("") { v ->
                val nv = !prefs.bool(key, def)
                prefs.setBool(key, nv)
                (v as Button).text = title + ": " + onOff(nv)
            }
            b.text = title + ": " + onOff(prefs.bool(key, def))
            box.addView(b)
        }

        fun level(box: LinearLayout, title: String, key: String, def: Int, names: Array<String>) {
            fun cur() = prefs.int(key, def).coerceIn(0, names.size - 1)
            val b = btn("") { v ->
                val nv = (cur() + 1) % names.size
                prefs.setInt(key, nv)
                (v as Button).text = title + ": " + names[nv]
            }
            b.text = title + ": " + names[cur()]
            box.addView(b)
        }

        fun cycle(box: LinearLayout, title: String, names: () -> Array<String>, get: () -> Int, set: (Int) -> Unit) {
            val b = btn("") { v ->
                val n = names()
                val nv = (get() + 1) % n.size
                set(nv)
                (v as Button).text = title + ": " + n[nv.coerceIn(0, n.size - 1)]
            }
            val n0 = names()
            b.text = title + ": " + n0[get().coerceIn(0, n0.size - 1)]
            box.addView(b)
        }

        fun group(title: String, fill: (LinearLayout) -> Unit) {
            val body = LinearLayout(ctx)
            body.orientation = LinearLayout.VERTICAL
            body.visibility = View.GONE
            try { fill(body) } catch (_: Throwable) { }
            val head = btn("\u25B6  " + title) { v ->
                val open = body.visibility != View.VISIBLE
                body.visibility = if (open) View.VISIBLE else View.GONE
                (v as Button).text = (if (open) "\u25BC  " else "\u25B6  ") + title
            }
            root.addView(head)
            root.addView(body)
        }

        group("Languages & layouts") { g ->
            cycle(g, "Bangla layout", { arrayOf("Avro (phonetic)", "Probhat") },
                { if (prefs.str("bn_layout", "avro") == "probhat") 1 else 0 },
                { prefs.setStr("bn_layout", if (it == 1) "probhat" else "avro") })
            toggle(g, "Number row", "numrow", true)
            toggle(g, "Auto numpad (number / phone fields)", "numpad_auto", true)
            cycle(g, "Mode", { arrayOf("Phone", "PC") }, { if (prefs.bool("pc_mode", false)) 1 else 0 }, { prefs.setBool("pc_mode", it == 1) })
            toggle(g, "Mode key", "mode_key", true)
            toggle(g, "Remember mode", "pc_remember", true)
        }

        group("Typing") { g ->
            toggle(g, "Hold backspace", "bs_repeat", true)
            toggle(g, "Auto caps", "autocap", true)
            toggle(g, "Enter action", "enter_action", true)
            toggle(g, "Double-space period", "ds_period", false)
            toggle(g, "Double-space tab", "ds_tab", false)
            toggle(g, "Long-press characters", "hint_on", true)
        }

        group("Look") { g ->
            cycle(g, "Theme", { Array(JabraThemes.ALL.size) { i -> JabraThemes.ALL[i].name } }, { JabraThemes.index(prefs) }, { prefs.setInt("theme", it) })
            cycle(g, "Look", { arrayOf("Classic", "Jabra") }, { if (prefs.int("look", 0) == 1) 1 else 0 }, { prefs.setInt("look", it) })
            cycle(g, "One-hand", { arrayOf("Off", "Left", "Right") }, { JabraOneHand.mode(prefs) }, { prefs.setInt("onehand", it) })
            toggle(g, "Icons", "icons_on", true)
            toggle(g, "Large number row", "numrow_large", false)
            toggle(g, "Hide long-press hints", "hint_hide", false)
            val note = TextView(ctx)
            note.text = "Key height % and one-hand width %: inside the keyboard (gear > More settings > Size)."
            g.addView(note)
        }

        group("Keys & gestures") { g ->
            toggle(g, "Vibrate", "haptic", true)
            toggle(g, "Sound", "sound", false)
            toggle(g, "Key popup", "key_popup", false)
            toggle(g, "Globe key", "key_globe", false)
            toggle(g, "Voice key", "key_voice", false)
            toggle(g, "Emoji key", "key_emoji", false)
            level(g, "Long-press time", "lp_delay", TypingLevels.LP_DEFAULT, TypingLevels.LP_NAMES)
            level(g, "Cursor speed (space drag)", "cursor_speed", TypingLevels.CS_DEFAULT, TypingLevels.CS_NAMES)
        }

        group("Clipboard & tools") { g ->
            toggle(g, "Edit bar", "edit_bar", true)
            toggle(g, "Clip history", "clip_on", true)
            toggle(g, "Formatter (Aa button)", "fmt_on", true)
            level(g, "Clip lines", "clip_lines", 0, arrayOf("2 lines", "4 lines", "6 lines"))   // v0.2.5.1
            level(g, "Clipboard view", "clip_view", 0, arrayOf("List", "Cards"))   // v0.2.5.2
        }

        return root
    }
}
