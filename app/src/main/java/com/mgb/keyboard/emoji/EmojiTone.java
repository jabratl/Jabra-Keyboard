package com.mgb.keyboard.emoji;

import java.util.Arrays;

/**
 * v0.8.0 (E1) skin tone helper (pure Java, no Android). The data file stores ONLY the base emoji; a variant is made here.
 * Rule (checked by EmojiBuild against every variant of the real Unicode list): put the tone modifier (U+1F3FB..U+1F3FF)
 * right after EVERY "modifier base" code point of the base emoji, and drop a U+FE0F that directly follows that code point.
 * The modifier-base list comes from the data file header (#mods), so a new Unicode version needs no code change.
 */
public final class EmojiTone {
    private EmojiTone() { }

    public static final int COUNT = 5;                 // light, medium-light, medium, medium-dark, dark
    public static final int FIRST = 0x1F3FB;
    public static final int LAST = 0x1F3FF;

    public static boolean isTone(int cp) { return cp >= FIRST && cp <= LAST; }

    /** The tone swatch itself (index 0..4) as a string, for the tone picker. Bad index = "". */
    public static String swatch(int tone) {
        if (tone < 0 || tone >= COUNT) return "";
        return new String(Character.toChars(FIRST + tone));
    }

    /**
     * Variant of base for tone 0..4. mods = SORTED array of modifier-base code points. Returns base unchanged when the tone
     * index is bad, mods is null / empty, or the base has no modifier-base code point.
     */
    public static String apply(String base, int tone, int[] mods) {
        if (base == null || mods == null || mods.length == 0 || tone < 0 || tone >= COUNT) return base;
        StringBuilder sb = new StringBuilder(base.length() + 4);
        int n = base.length();
        int i = 0;
        boolean changed = false;
        while (i < n) {
            int cp = base.codePointAt(i);
            i += Character.charCount(cp);
            sb.appendCodePoint(cp);
            if (Arrays.binarySearch(mods, cp) >= 0 && !zwjBothSides(base, i - Character.charCount(cp), i)) {   // v0.8.0.1
                if (i < n && base.charAt(i) == '️') i++;
                sb.appendCodePoint(FIRST + tone);
                changed = true;
            }
        }
        return changed ? sb.toString() : base;
    }

    /** v0.8.0.1: true when the code point at [start, end) sits BETWEEN two ZWJ (U+200D), like the handshake in "people holding hands". Unicode gives it no tone. */
    private static boolean zwjBothSides(String s, int start, int end) {
        if (start <= 0 || end >= s.length()) return false;
        return s.charAt(start - 1) == '\u200D' && s.charAt(end) == '\u200D';
    }
}
