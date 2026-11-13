package com.mgb.keyboard.typing;

/**
 * v0.17.31 (UI7): what the key bubble shows while a key is HELD and its long-press text has just been typed
 * (e.g. hold "m" -> "?"). Pure Java, no Android (tools/HoldPreviewTest.java).
 * label() = the short text for the bubble, or null = show no bubble (empty text, blank text, or an action text that starts with "@").
 * Space / Enter / Tab get a visible symbol, a long text is cut to MAX characters + "..." (code-point aware, so Bangla / emoji are not split).
 */
public final class HoldPreview {
    private HoldPreview() { }

    /** Longest text shown in full (in characters, not UTF-16 units). */
    public static final int MAX = 4;

    public static final String SPACE = "\u2423", ENTER = "\u21B5", TAB = "\u21E5", DOTS = "\u2026";

    public static String label(String text) {
        if (text == null || text.isEmpty()) return null;
        if (text.charAt(0) == '@') return null;      // "@paste" etc. = an action, not typed text
        if (text.equals(" ")) return SPACE;
        String t = text.replace("\r\n", "\n").replace("\n", ENTER).replace("\t", TAB);
        if (t.trim().isEmpty()) return null;          // other blank text: nothing to show
        int n = t.codePointCount(0, t.length());
        if (n <= MAX) return t;
        int end = t.offsetByCodePoints(0, MAX - 1);
        return t.substring(0, end) + DOTS;
    }
}
