package com.mgb.keyboard.typing;

import com.mgb.keyboard.clip.ClipKind;
import com.mgb.keyboard.clip.ClipList;
import java.util.ArrayList;
import java.util.List;

/**
 * v0.16.9 (plan X, step X9): the filter chips of the NEW clipboard panel (panel_ui = 1): All | Pinned | Text | Links | Numbers.
 * Pure Java, no Android (tools/ClipFilterTest.java). Nothing is stored (the chip lives only while the panel is open), nothing is changed in the clips.
 * Rules: Pinned = pinned flag. Links = ClipKind.LINK. Numbers = only digits (ASCII or Bangla) and number marks (+ - . , ( ) / : % and currency signs),
 * at least one digit (prices, phone numbers, one-time codes). Text = everything that is neither a link nor a number (an e-mail address is Text).
 * So Links / Numbers / Text never overlap and together hold every clip. Never throws; unknown chip = All.
 */
public final class ClipFilter {
    private ClipFilter() { }

    public static final int ALL = 0, PINNED = 1, TEXT = 2, LINKS = 3, NUMBERS = 4;
    public static final int COUNT = 5;
    public static final String[] LABELS = { "All", "Pinned", "Text", "Links", "Numbers" };

    /** Unknown / out of range chip = ALL. */
    public static int sanitize(int chip) { return chip >= 0 && chip < COUNT ? chip : ALL; }

    public static String label(int chip) { return LABELS[sanitize(chip)]; }

    private static boolean isDigit(char c) { return (c >= '0' && c <= '9') || (c >= 0x09E6 && c <= 0x09EF); }

    private static boolean isMark(char c) {
        return Character.isWhitespace(c) || "+-.,()/:%$\u20AC\u00A3\u00A5\u20B9\u09F3".indexOf(c) >= 0;
    }

    /** True for text made only of digits and number marks with at least one digit ("1,250.50", "+880 1711-000000", "482913"). */
    public static boolean isNumber(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty() || t.length() > ClipKind.MAX_LOOK) return false;
        boolean digit = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (isDigit(c)) digit = true;
            else if (!isMark(c)) return false;
        }
        return digit;
    }

    public static boolean isLink(String text) {
        try { return ClipKind.kind(text) == ClipKind.LINK; } catch (Throwable e) { return false; }
    }

    /** Does a clip with this [text] / [pinned] flag belong to [chip]? null text = false for every chip except ALL (which is true). */
    public static boolean matches(String text, boolean pinned, int chip) {
        switch (sanitize(chip)) {
            case PINNED: return pinned;
            case LINKS: return text != null && isLink(text);
            case NUMBERS: return text != null && isNumber(text);
            case TEXT: return text != null && !isLink(text) && !isNumber(text);
            default: return true;
        }
    }

    public static boolean matches(ClipList.Item it, int chip) {
        if (it == null) return false;
        return matches(it.text, it.pinned, chip);
    }

    /** New list with the items of [chip], same order (null items are dropped, null list = empty). */
    public static List<ClipList.Item> filter(List<ClipList.Item> items, int chip) {
        ArrayList<ClipList.Item> out = new ArrayList<ClipList.Item>();
        if (items == null) return out;
        for (ClipList.Item it : items) if (it != null && matches(it, chip)) out.add(it);
        return out;
    }

    /** How many of [items] belong to [chip]. */
    public static int count(List<ClipList.Item> items, int chip) {
        int n = 0;
        if (items == null) return 0;
        for (ClipList.Item it : items) if (it != null && matches(it, chip)) n++;
        return n;
    }

    /** Text of the empty list: search = "No match"; All = the old hint; any other chip = a short hint for that chip. */
    public static String emptyText(int chip, boolean searching) {
        if (searching) return "No match";
        switch (sanitize(chip)) {
            case PINNED: return "No pinned clips yet. Tap the star on a clip to pin it.";
            case TEXT: return "No text clips here.";
            case LINKS: return "No links copied yet.";
            case NUMBERS: return "No numbers copied yet.";
            default: return "Nothing copied yet. Copy some text and it will show here.";
        }
    }

    /** Bottom bar summary in select mode. */
    public static String selectedText(int n) { return n <= 0 ? "Tap clips to select" : n + " selected"; }
}
