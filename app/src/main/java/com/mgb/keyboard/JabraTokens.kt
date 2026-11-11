package com.mgb.keyboard

import android.content.Context
import android.graphics.Color

/**
 * v0.6.3 DESIGN TOKENS (S4): one place for the panel look (sizes, spacing, text sizes, colours).
 * Colour functions read the CURRENT keyboard theme (JabraThemes), so a panel that uses them follows the theme.
 * Theme 0 "Classic" returns EXACTLY the values the panels use today (section 4 of the handoff):
 *   panel bg #DDDDDD, text/icons #222222, notes #555555, button = system button, card #F7F7F7 / #C8C8C8.
 * Nothing calls this file yet (S5-S8 migrate the panels one by one). Every function has try/catch + Classic fallback, never throws.
 * Plain fixed values (no pref, no storage, no permission). Delete this file + UiKit.kt = back to v0.6.2 behaviour.
 */
object JabraTokens {

    // ---- radii (dp) ----
    const val R_SM = 9        // Jabra look key radius
    const val R_MD = 12
    const val R_LG = 16       // popup bubble
    const val R_CARD = 10     // clipboard card radius today (ClipCards.kt)

    // ---- spacing (dp) ----
    const val S1 = 4
    const val S2 = 8
    const val S3 = 12
    const val S4 = 16
    const val GAP = 2         // margin around panel buttons today

    // ---- text sizes (sp) ----
    const val T_NOTE = 11f
    const val T_BTN = 12f
    const val T_BODY = 14f
    const val T_HEAD = 16f
    const val T_TITLE = 22f   // app-screen title only

    // ---- fixed accents (same in every theme) ----
    val ACCENT: Int = 0xFF2271B1.toInt()        // caps lock blue
    val ACCENT_SOFT: Int = 0xFF9CC3E6.toInt()   // one-shot shift blue
    val WARN: Int = 0xFFD84315.toInt()
    val DANGER: Int = 0xFFAA0000.toInt()
    val ON_ACCENT_SOFT: Int = 0xFF1B1B1B.toInt()   // dark text that stays readable on ACCENT_SOFT in every theme

    private val CLASSIC_BG: Int = 0xFFDDDDDD.toInt()
    private val CLASSIC_TEXT: Int = 0xFF222222.toInt()
    private val CLASSIC_NOTE: Int = 0xFF555555.toInt()
    private val CLASSIC_CARD: Int = 0xFFF7F7F7.toInt()
    private val CLASSIC_CARD_PICKED: Int = 0xFFDCE9FA.toInt()
    private val CLASSIC_STROKE: Int = 0xFFC8C8C8.toInt()
    private val CLASSIC_STROKE_PICKED: Int = 0xFF2F6FD0.toInt()
    private val CLASSIC_STROKE_PINNED: Int = 0xFF7A9CC6.toInt()

    fun dp(ctx: Context, v: Int): Int = try { (v * ctx.resources.displayMetrics.density).toInt() } catch (_: Throwable) { v * 3 }

    fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or ((a and 0xFF) shl 24)

    /** true only for theme 0 (Classic): callers can keep the old colours for it. */
    fun isClassic(p: JabraPrefs): Boolean = try { JabraThemes.index(p) == 0 } catch (_: Throwable) { true }

    /** true when the theme background is dark (same rule as JabraThemes.noteColor). */
    fun isDark(p: JabraPrefs): Boolean = try { Color.luminance(JabraThemes.cur(p).bg) < 0.4f } catch (_: Throwable) { false }

    // ---- colours that follow the theme ----
    fun panelBg(p: JabraPrefs): Int = try { JabraThemes.cur(p).bg } catch (_: Throwable) { CLASSIC_BG }
    fun panelText(p: JabraPrefs): Int = try { JabraThemes.iconColor(p) } catch (_: Throwable) { CLASSIC_TEXT }
    fun note(p: JabraPrefs): Int = try { JabraThemes.noteColor(p) } catch (_: Throwable) { CLASSIC_NOTE }
    /** Button fill, or null = leave the system button as it is (Classic). */
    fun btnFill(p: JabraPrefs): Int? = try { JabraThemes.cur(p).key } catch (_: Throwable) { null }
    /** Button text colour, or null = leave the system default (Classic). */
    fun btnText(p: JabraPrefs): Int? = try { JabraThemes.cur(p).text } catch (_: Throwable) { null }
    fun funcFill(p: JabraPrefs): Int = try { JabraThemes.funcFill(p) } catch (_: Throwable) { CLASSIC_BG }

    // ---- cards (clipboard cards, later vault / tools) ----
    fun cardFill(p: JabraPrefs, picked: Boolean): Int {
        if (isClassic(p)) return if (picked) CLASSIC_CARD_PICKED else CLASSIC_CARD
        return if (picked) withAlpha(ACCENT, 0x66) else (btnFill(p) ?: CLASSIC_CARD)
    }
    fun cardStroke(p: JabraPrefs, picked: Boolean, pinned: Boolean): Int {
        if (isClassic(p)) return if (picked) CLASSIC_STROKE_PICKED else if (pinned) CLASSIC_STROKE_PINNED else CLASSIC_STROKE
        return if (picked) ACCENT else if (pinned) ACCENT_SOFT else withAlpha(panelText(p), 0x40)
    }
    /** Main text on a card (Classic = black as today). */
    fun cardText(p: JabraPrefs): Int = if (isClassic(p)) Color.BLACK else (btnText(p) ?: panelText(p))
    /** v0.6.4: secondary text / headers (Classic = Color.DKGRAY as the clipboard uses today). */
    fun dimText(p: JabraPrefs): Int = try { if (isClassic(p)) Color.DKGRAY else note(p) } catch (_: Throwable) { Color.DKGRAY }
    /** v0.6.4: highlight of a ticked list row (Classic = #CFE0F5 as today). */
    fun rowPicked(p: JabraPrefs): Int = try { if (isClassic(p)) 0xFFCFE0F5.toInt() else withAlpha(ACCENT, 0x66) } catch (_: Throwable) { 0xFFCFE0F5.toInt() }
    /** Small grey info text on a card (Classic = grey as today). */
    fun cardNote(p: JabraPrefs): Int = if (isClassic(p)) Color.GRAY else withAlpha(cardText(p), 0xA6)   // v0.6.6: relative to the card text (the card is key-coloured, so the panel note colour could be too faint, e.g. Ocean)
    /** v0.6.6: darker secondary text on a card / list row (Classic = Color.DKGRAY as today). */
    fun onCardDim(p: JabraPrefs): Int = if (isClassic(p)) Color.DKGRAY else withAlpha(cardText(p), 0xCC)
}
