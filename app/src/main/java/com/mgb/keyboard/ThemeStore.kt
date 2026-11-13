package com.mgb.keyboard

import com.mgb.keyboard.typing.ThemeBook

/**
 * v0.17.24 (L4): the named custom themes (typing/ThemeBook). Stored as ONE text in the main prefs file "jabra", key themes_v1.
 * Because it is a normal pref, the daily / manual backup and the restore already carry it (no backup code changed); a damaged text
 * simply decodes to fewer / no themes (ThemeBook.decode never throws and re-checks every theme). ct_json (the ONE active custom theme, slot 21) is untouched.
 */
object ThemeStore {
    private const val KEY = "themes_v1"

    fun book(prefs: JabraPrefs): ThemeBook = try { ThemeBook.decode(prefs.str(KEY, "")) } catch (_: Throwable) { ThemeBook.empty() }

    fun put(prefs: JabraPrefs, b: ThemeBook) { try { prefs.setStr(KEY, b.encode()) } catch (_: Throwable) { } }
}
