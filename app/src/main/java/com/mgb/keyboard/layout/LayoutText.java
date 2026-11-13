package com.mgb.keyboard.layout;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * v0.17.3 (K3) text sanitiser + limits for the layout model (pure Java, no Android).
 * R2: limits count Unicode CODE POINTS (a Bangla conjunct, ZWJ / ZWNJ, emoji are never cut in half).
 * R3: control chars removed (only \n and \t stay), unpaired surrogates removed, "@id" swipe / long-press values
 *     must be on the whitelist (unknown @id = "" = ignored, never typed).
 */
public final class LayoutText {
    private LayoutText() { }

    public static final int MAX_OUT = 16, MAX_LABEL = 6, MAX_LP = 16, MAX_COMBO = 24, MAX_ID = 16, MAX_LPLIST = 16;   // v0.17.46 (P3): 8 -> 16 (2-row grid of 8)

    /** ACTION key ids (kind ACTION). Same functions the bottom row / toolbar already call. */
    public static final Set<String> ACTIONS = new HashSet<String>(Arrays.asList(
            "shift", "backspace", "enter", "space", "sym", "lang", "emoji", "globe", "voice", "tab", "clip",
            "comma", "period", "undo", "redo", "combo"));

    /** R3 whitelist of "@actions" usable as swipe / long-press value. */
    public static final Set<String> AT_ACTIONS = new HashSet<String>(Arrays.asList(
            "@tab", "@undo", "@redo", "@paste", "@copy", "@cut", "@selall", "@emoji", "@clip", "@lang", "@caps",
            "@sym", "@left", "@right", "@home", "@end", "@del_word"));

    /** Valid layer ids. s1-s3 = symbol pages, nr = number row, bot = bottom row (shared rows). */
    public static final Set<String> LAYERS = new HashSet<String>(Arrays.asList(
            "en", "av", "pb", "s1", "s2", "s3", "nr", "bot", "bot_av", "bot_pb"));

    /** v0.17.19 (L3) shared-row layer ids: number row, the bottom row and the optional own bottom rows of Avro / Probhat. */
    public static boolean isShareId(String s) { return "nr".equals(s) || isBottomId(s); }

    /** v0.17.19 (L3) true for "bot", "bot_av", "bot_pb". */
    public static boolean isBottomId(String s) { return "bot".equals(s) || "bot_av".equals(s) || "bot_pb".equals(s); }

    /** v0.17.19 (L3) the bottom-row layer a letter layer OWNS when it has its own bottom row (en always uses "bot"). */
    public static String botLayerOf(String letterLayer) {
        if ("av".equals(letterLayer)) return "bot_av";
        if ("pb".equals(letterLayer)) return "bot_pb";
        return "bot";
    }

    public static int cp(String s) { return s == null ? 0 : s.codePointCount(0, s.length()); }

    /** Cleans a free text field: no control chars (except \n \t), no unpaired surrogates, at most max code points. */
    public static String clean(String s, int max) {
        if (s == null || s.length() == 0 || max <= 0) return "";
        StringBuilder b = new StringBuilder(Math.min(s.length(), max * 2));
        int n = 0;
        for (int i = 0; i < s.length() && n < max; ) {
            int c = s.codePointAt(i);
            i += Character.charCount(c);
            if (c >= 0xD800 && c <= 0xDFFF) continue;          // unpaired surrogate half
            if (c == 0xFFFE || c == 0xFFFF) continue;
            if (Character.isISOControl(c) && c != '\n' && c != '\t') continue;
            b.appendCodePoint(c);
            n++;
        }
        return b.toString();
    }

    /** Swipe / long-press value: text, or an "@action" from the whitelist (anything else starting with "@" = ""). */
    public static String cleanSwipe(String s, int max) {
        String t = clean(s, max);
        if (t.length() > 1 && t.charAt(0) == '@') return AT_ACTIONS.contains(t) ? t : "";
        return t;
    }

    /** Lower-case ASCII word of letters, digits and "_" only, at most max chars. */
    public static String cleanWord(String s, int max) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < max; i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') b.append(c);
        }
        return b.toString();
    }

    /** Key id: letters, digits, "_" (any case), at most MAX_ID chars. */
    public static String cleanId(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < MAX_ID; i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') b.append(c);
        }
        return b.toString();
    }

    /** True when every char is a Latin letter (so shift = upper case). */
    public static boolean isLatinLetters(String s) {
        if (s == null || s.length() == 0) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x250 || !Character.isLetter(c)) return false;
        }
        return true;
    }
}
