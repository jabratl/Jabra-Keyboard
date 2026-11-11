package com.mgb.keyboard

import android.content.Context
import android.util.AtomicFile
import com.mgb.keyboard.vault.VaultList
import java.io.File

/**
 * v0.4.4 Personal Text Vault storage (rule P3 / P4 of the plan).
 * One file: filesDir/vault_v1.json, written with AtomicFile (write whole -> rename), NOT in SharedPreferences.
 * Loaded lazily the first time the Vault panel needs it, dropped again with unload() when the panel is left.
 * A damaged file (or one with a newer "ver") is NEVER overwritten: get() returns null and [problem] says why.
 * Never throws. The file is already part of every backup (BackupStore.FILES since v0.4.0).
 */
class VaultStore(private val ctx: Context) {

    private var list: VaultList? = null

    /** Non-empty = the vault could not be read; all writes are refused until it is fixed (restore a backup). */
    var problem: String = ""
        private set

    private fun file(): File = File(ctx.filesDir, NAME)

    /** The vault in memory (loaded on first use), or null when the file is damaged / unreadable. */
    fun get(): VaultList? {
        list?.let { return it }
        try {
            val f = file()
            if (!f.exists()) { list = VaultList(); problem = ""; return list }
            val text = String(AtomicFile(f).readFully(), Charsets.UTF_8)
            if (text.isBlank()) { list = VaultList(); problem = ""; return list }
            val l = VaultList.parse(text)
            if (l == null) {
                problem = "Vault file is damaged or from a newer version. Nothing was changed. Restore a backup in the app (Data & Backup)."
                return null
            }
            list = l
            problem = ""
            return l
        } catch (_: Throwable) {
            problem = "Could not read the Vault file."
            return null
        }
    }

    /** Forget the in-memory copy (next get() reads the file again). */
    fun unload() { list = null }

    /** Save the in-memory vault. Returns "" when saved, else a short message. On failure the memory copy is dropped (screen shows what is really on disk). */
    fun commit(): String {
        val l = list ?: return "Vault not loaded"
        try {
            val af = AtomicFile(file())
            val o = af.startWrite()
            try {
                o.write(l.toJson().toByteArray(Charsets.UTF_8))
                af.finishWrite(o)
                return ""
            } catch (e: Throwable) {
                af.failWrite(o)
            }
        } catch (_: Throwable) { }
        list = null
        return "NOT saved: could not write the Vault file"
    }

    companion object {
        const val NAME = "vault_v1.json"

        /** Number of entries in the file for the Data & Backup page: 0 = none / no file, -1 = file damaged. */
        fun countOnDisk(ctx: Context): Int = try {
            val f = File(ctx.filesDir, NAME)
            if (!f.exists()) 0 else {
                val text = String(AtomicFile(f).readFully(), Charsets.UTF_8)
                if (text.isBlank()) 0 else (VaultList.parse(text)?.size() ?: -1)
            }
        } catch (_: Throwable) { -1 }
    }
}
