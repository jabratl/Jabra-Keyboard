package com.mgb.keyboard

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.clip.ClipList
import com.mgb.keyboard.typing.PrivacyInfo
import java.io.File

/**
 * v0.10.10 (W4) "Privacy & storage" card inside Data & Backup. Read-only facts + one two-tap button
 * ("Clear clipboard history now", pinned clips stay). Texts come from typing/PrivacyInfo.java (tested on a computer).
 * Sizes are real file sizes (File.length) and the length of the clip text stored in the settings file. No network, no permission.
 * Called inside try/catch from DataPage: any problem = the card is simply missing.
 */
object PrivacyPage {
    fun build(ctx: Context): View {
        val prefs = JabraPrefs(ctx)
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val root = LinearLayout(ctx); root.orientation = LinearLayout.VERTICAL
        root.setPadding(0, dp(10), 0, dp(6))
        val body = LinearLayout(ctx); body.orientation = LinearLayout.VERTICAL
        val msg = TextView(ctx); msg.setPadding(0, dp(4), 0, dp(4))
        var armed = false

        fun line(t: String): TextView { val v = TextView(ctx); v.text = t; v.setPadding(0, dp(2), 0, dp(2)); return v }
        fun head(t: String): TextView { val v = line(t); v.setTypeface(null, Typeface.BOLD); v.textSize = 16f; v.setPadding(0, dp(8), 0, dp(2)); return v }
        fun fileLen(name: String): Long = try { val f = File(ctx.filesDir, name); if (f.exists()) f.length() else -1L } catch (_: Throwable) { -1L }
        fun dirLen(dir: File): Long = try { if (!dir.exists()) -1L else (dir.listFiles() ?: emptyArray()).sumOf { if (it.isDirectory) maxOf(0L, dirLen(it)) else it.length() } } catch (_: Throwable) { -1L }
        fun prefBytes(key: String): Long = try { prefs.str(key, "").toByteArray(Charsets.UTF_8).size.toLong() } catch (_: Throwable) { -1L }

        fun fill() {
            body.removeAllViews()
            body.addView(head("Privacy & storage"))
            for (p in PrivacyInfo.permissionLines()) body.addView(line("\u2022 " + p))
            body.addView(head("Settings now"))
            body.addView(line(PrivacyInfo.clipLine(prefs.bool("clip_on", true), prefs.int("clip_expire", 0), prefs.bool("clip_sens", false))))
            body.addView(line(PrivacyInfo.learnLine(prefs.bool("learn_on", false))))
            body.addView(head("Stored on this phone"))
            val clipBytes = prefBytes("clips_v1") + prefBytes("clip_meta_v1")
            val sizes = longArrayOf(
                fileLen("vault_v1.json"), fileLen("profiles_v1.json"), clipBytes, fileLen("learn_v1.txt"),
                fileLen("emoji_user_v1.txt"), dirLen(File(ctx.filesDir, "backups")),
                try { File(ctx.applicationInfo.dataDir, "shared_prefs/jabra.xml").let { if (it.exists()) it.length() else -1L } } catch (_: Throwable) { -1L })
            val names = arrayOf("Vault", "Profiles", "Clipboard history", "Learned words", "Downloaded emoji list", "Backup copies in the app", "Settings file")
            for (i in names.indices) body.addView(line(PrivacyInfo.storageLine(names[i], sizes[i])))
            body.addView(line("Total: " + PrivacyInfo.bytes(PrivacyInfo.total(sizes))))
            val cl = try { ClipList.parse(prefs.str("clips_v1", "")) } catch (_: Throwable) { ClipList() }
            val unpinned = try { cl.size() - cl.pinnedCount() } catch (_: Throwable) { 0 }
            val cb = Button(ctx); cb.isAllCaps = false; cb.text = PrivacyInfo.clearLabel(armed, unpinned)
            cb.isEnabled = unpinned > 0
            cb.setOnClickListener {
                try {
                    if (!armed) { armed = true; cb.text = PrivacyInfo.clearLabel(true, unpinned) }
                    else {
                        armed = false
                        val n = ClipStore.clearUnpinnedNow(prefs)
                        msg.text = PrivacyInfo.clearDone(n)
                        fill()
                    }
                } catch (_: Throwable) { }
            }
            body.addView(cb)
        }
        root.addView(body)
        root.addView(msg)
        fill()
        return root
    }
}
