package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.util.AtomicFile
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.AppProfileMap
import com.mgb.keyboard.typing.ProfileCodec
import com.mgb.keyboard.typing.ProfileCodec.Profile
import java.io.File

/**
 * v0.5.1 WORKSPACE / LAYOUT PROFILES storage (same pattern as VaultStore).
 * One file filesDir/profiles_v1.json written with AtomicFile; it is already part of every backup (BackupStore.FILES since v0.4.0).
 * A damaged file (or a newer "ver") is NEVER overwritten: get() returns null and [problem] says why. Never throws.
 */
class ProfileStore(private val ctx: Context) {

    private var list: List<Profile>? = null

    var problem: String = ""
        private set

    private fun file(): File = File(ctx.filesDir, NAME)
    private fun sp() = ctx.getSharedPreferences("jabra", Context.MODE_PRIVATE)

    /** The saved profiles (loaded on first use), or null when the file is damaged / unreadable. */
    fun get(): List<Profile>? {
        list?.let { return it }
        try {
            val f = file()
            if (!f.exists()) { list = emptyList(); problem = ""; return list }
            val text = String(AtomicFile(f).readFully(), Charsets.UTF_8)
            if (text.isBlank()) { list = emptyList(); problem = ""; return list }
            val l = ProfileCodec.parse(text)
            if (l == null) { problem = "Profiles file is damaged or from a newer version. Nothing was changed. Restore a backup in the app (Data & Backup)."; return null }
            list = l; problem = ""
            return l
        } catch (_: Throwable) { problem = "Could not read the Profiles file."; return null }
    }

    fun unload() { list = null }

    /** Save a new list. "" = saved, else a short message. On failure the memory copy is dropped. */
    fun commit(l: List<Profile>): String {
        try {
            val af = AtomicFile(file())
            val o = af.startWrite()
            try {
                o.write(ProfileCodec.toJson(l).toByteArray(Charsets.UTF_8))
                af.finishWrite(o)
                list = l
                return ""
            } catch (e: Throwable) { af.failWrite(o) }
        } catch (_: Throwable) { }
        list = null
        return "NOT saved: could not write the Profiles file"
    }

    /** Snapshot of the current whitelisted settings. */
    fun captureNow(name: String): Profile? = try { ProfileCodec.capture(name, sp().all) } catch (_: Throwable) { null }

    /** Apply = ONE prefs edit batch (only whitelisted keys; null = remove the key = default). */
    fun apply(p: Profile): Boolean = try {
        val ed = sp().edit()
        for ((k, v) in p.values) {
            if (!ProfileCodec.allowed(k)) continue
            when (v) {
                null -> ed.remove(k)
                is Boolean -> ed.putBoolean(k, v)
                is Int -> ed.putInt(k, v)
                is String -> ed.putString(k, v)
                else -> { }
            }
        }
        ed.apply()
        true
    } catch (_: Throwable) { false }

    companion object { const val NAME = "profiles_v1.json" }
}

/*
 * v0.5.1 PROFILES panel (route showPanel("profiles"), toolbar item "profiles", OFF until the user adds it).
 * Tap a profile = apply it (one batch) + back to the letters. Ready-made profiles (not stored) come first, your own saved ones below.
 * Save current = snapshot of today's settings; the NAME is the text in the field (type it first), empty = "Profile N".
 * Ren = new name from the field text. Del = two taps. Undo = go back to the settings before the last apply.
 * Same grey look as the other panels. build() returns null on any problem -> the keyboard shows the letters.
 */
class ProfilePanel(private val ctx: Context, private val host: JabraHost, private val store: ProfileStore) {

    private var msg = ""
    private var confirmDel = -1
    private var undoSnap: Profile? = null

    fun reset() { msg = ""; confirmDel = -1; undoSnap = null; store.unload() }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
    private fun refresh() { host.showPanel("profiles") }

    private val pf: JabraPrefs by lazy { JabraPrefs(ctx) }   // v0.6.7

    // v0.6.7 theme: buttons = UiKit.btn (12sp, follows theme / Jabra look / Borderless), old body kept as btnOld() = automatic fallback
    private fun btn(label: String, weight: Float, hDp: Int, onClick: (Button) -> Unit): Button {
        try { return UiKit.btn(ctx, pf, label, weight, hDp, 12f, { v -> host.feedback(v) }, onClick) } catch (_: Throwable) { }
        return btnOld(label, weight, hDp, onClick)
    }

    private fun paintBg(v: View) { try { JabraThemes.paint(pf, v) } catch (_: Throwable) { v.setBackgroundColor(Color.parseColor("#DDDDDD")) } }   // v0.6.7
    private fun dim(): Int = try { JabraTokens.dimText(pf) } catch (_: Throwable) { Color.DKGRAY }   // v0.6.7

    private fun btnOld(label: String, weight: Float, hDp: Int, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.textSize = 12f
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(if (weight > 0f) 0 else LinearLayout.LayoutParams.WRAP_CONTENT, dp(hDp), if (weight > 0f) weight else 0f)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    /** v0.6.7: error text: #AA0000 as before, a lighter red on dark themes (dark red is unreadable there). */
    private fun errColor(): Int = try { if (JabraTokens.isDark(pf)) Color.parseColor("#FF8A80") else Color.parseColor("#AA0000") } catch (_: Throwable) { Color.parseColor("#AA0000") }

    private fun row(): LinearLayout { val r = LinearLayout(ctx); r.orientation = LinearLayout.HORIZONTAL; return r }

    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun applyProfile(p: Profile) {
        val before = store.captureNow("Before")
        if (store.apply(p)) { undoSnap = before; host.showLetters() } else { msg = "Could not apply"; refresh() }
    }

    // v0.10.18 (W6.1): app -> profile map in pref ap_json (read / written here only; switching on focus comes in W6.2). Every call is try/catch.
    private fun appMap(): AppProfileMap = try { AppProfileMap.parse(pf.str("ap_json", "")) } catch (_: Throwable) { AppProfileMap.empty() }
    private fun mapApp(pkg: String, profileName: String) {
        try {
            val nm = appMap().put(pkg, profileName)
            if (nm == null) msg = "Cannot map (max " + AppProfileMap.MAX_APPS + " apps)"
            else { pf.setStr("ap_json", nm.toJson()); msg = "$pkg > $profileName" }
        } catch (_: Throwable) { msg = "Could not save the app mapping" }
        confirmDel = -1; refresh()
    }

    private fun make(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)   // v0.6.7
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        val saved = store.get()
        val top = row()
        top.addView(btn("ABC", 1f, 38) { host.showLetters() })
        top.addView(btn("Save current", 2f, 38) {
            val l = store.get()
            if (l == null) { msg = store.problem }
            else {
                var name = ProfileCodec.cleanName(host.fieldText().lineSequence().firstOrNull() ?: "")
                if (name.isEmpty()) { var n = l.size + 1; while (ProfileCodec.indexOf(l, "Profile $n") >= 0) n++; name = "Profile $n" }
                val snap = store.captureNow(name)
                val nl = if (snap == null) null else ProfileCodec.put(l, snap)
                if (nl == null) msg = "Cannot save (max " + ProfileCodec.MAX_PROFILES + " profiles)"
                else { val e = store.commit(nl); msg = if (e.isEmpty()) "Saved: $name" else e }
            }
            confirmDel = -1; refresh()
        })
        if (undoSnap != null) top.addView(btn("↶ Undo apply", 1.6f, 38) {
            val s = undoSnap
            if (s != null && store.apply(s)) { undoSnap = null; host.showLetters() } else { msg = "Could not undo"; refresh() }
        })
        root.addView(top)

        val note = TextView(ctx)
        note.textSize = 11f
        note.setTextColor(dim())   // v0.6.7
        note.setPadding(dp(4), dp(2), dp(4), dp(2))
        note.text = if (msg.isNotEmpty()) msg else "Tap a profile = apply. Save current: type a name in the field first (empty = Profile N)."
        root.addView(note)

        val app = try { host.currentApp() } catch (_: Throwable) { "" }   // v0.10.18 (W6.1)
        for (s in ProfileCodec.starters()) {
            val r = row()
            r.addView(btn("● " + s.name + "  (ready)", 1f, 38) { applyProfile(s) })
            if (app.isNotEmpty()) r.addView(btn("> App", 0.35f, 38) { mapApp(app, s.name) })   // v0.10.18
            root.addView(r)
        }

        if (saved == null) {
            val bad = TextView(ctx); bad.textSize = 12f; bad.setTextColor(errColor())   // v0.6.7; bad.setPadding(dp(4), dp(4), dp(4), dp(4)); bad.text = store.problem
            root.addView(bad)
        } else {
            for ((i, p) in saved.withIndex()) {
                val r = row()
                r.addView(btn(p.name, 3f, 38) { applyProfile(p) })
                if (app.isNotEmpty()) r.addView(btn("> App", 1f, 38) { mapApp(app, p.name) })   // v0.10.18 (W6.1)
                r.addView(btn("Ren", 1f, 38) {
                    val nn = ProfileCodec.cleanName(host.fieldText().lineSequence().firstOrNull() ?: "")
                    val nl = if (nn.isEmpty()) null else ProfileCodec.rename(saved, i, nn)
                    msg = if (nn.isEmpty()) "Type the new name in the field first"
                          else if (nl == null) "Name already used"
                          else { val e = store.commit(nl); if (e.isEmpty()) "Renamed to $nn" else e }
                    confirmDel = -1; refresh()
                })
                r.addView(btn(if (confirmDel == i) "Sure?" else "Del", 1f, 38) {
                    if (confirmDel != i) { confirmDel = i; refresh() }
                    else { val e = store.commit(ProfileCodec.remove(saved, i)); msg = if (e.isEmpty()) "Deleted" else e; confirmDel = -1; refresh() }
                })
                root.addView(r)
            }
        }
        // v0.10.18 (W6.1): list of app > profile mappings (remove row each). Mappings to a deleted profile are shown with "(missing)" and ignored later.
        try {
            val am = appMap()
            val pkgs = am.packages()
            if (app.isNotEmpty() || pkgs.isNotEmpty()) {
                val hd = TextView(ctx); hd.textSize = 11f; hd.setTextColor(dim()); hd.setPadding(dp(4), dp(6), dp(4), dp(2))
                hd.text = (if (app.isNotEmpty()) "Open app: $app. Tap \"> App\" on a profile to use it for this app.\n" else "") +
                    "Apps with a profile (" + pkgs.size + "). Switching by app is not active yet (next version)."
                root.addView(hd)
                val names = ArrayList<String>()
                for (s in ProfileCodec.starters()) names.add(s.name)
                if (saved != null) for (p in saved) names.add(p.name)
                for (pk in pkgs) {
                    val pn = am.profileOf(pk) ?: ""
                    val miss = names.none { it.equals(pn, ignoreCase = true) }
                    val r = row()
                    val lb = TextView(ctx); lb.textSize = 11f; lb.setTextColor(if (miss) errColor() else dim()); lb.setPadding(dp(4), 0, dp(4), 0)
                    lb.gravity = android.view.Gravity.CENTER_VERTICAL
                    lb.text = pk + "\n> " + pn + (if (miss) " (missing)" else "")
                    lb.layoutParams = LinearLayout.LayoutParams(0, dp(38), 3f)
                    r.addView(lb)
                    r.addView(btn("Remove", 1f, 38) {
                        try { pf.setStr("ap_json", appMap().remove(pk).toJson()); msg = "Removed: $pk" } catch (_: Throwable) { msg = "Could not remove" }
                        refresh()
                    })
                    root.addView(r)
                }
            }
        } catch (_: Throwable) { }
        val bottom = row()
        bottom.addView(btn("ABC", 1f, 38) { host.showLetters() })
        root.addView(bottom)
        return root
    }
}
