package com.mgb.keyboard.typing;

import java.util.Locale;

/**
 * v0.36.1: Shift tap on SELECTED text cycles the case: small letters -> First Letter Of Each Word Big -> ALL BIG -> all small -> ...
 * Pure Java (tools/SelCaseTest.java). next() returns the SAME text when nothing can change (no letters with case, e.g. Bangla / digits):
 * the caller then does the normal Shift tap.
 */
public final class SelCase {
    private SelCase() { }

    public static String lower(String t) { return t.toLowerCase(Locale.ROOT); }
    public static String upper(String t) { return t.toUpperCase(Locale.ROOT); }

    /** First letter of every word big, the rest small (a word starts after a space / new line / tab). */
    public static String title(String t) {
        StringBuilder sb = new StringBuilder(t.length());
        boolean start = true;
        int i = 0;
        while (i < t.length()) {
            int cp = t.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) { start = true; sb.appendCodePoint(cp); continue; }
            sb.appendCodePoint(start ? Character.toTitleCase(cp) : Character.toLowerCase(cp));
            start = false;
        }
        return sb.toString();
    }

    public static String next(String t) {
        if (t == null || t.isEmpty()) return t;
        if (t.equals(upper(t))) return lower(t);   // ALL BIG -> all small
        if (t.equals(title(t))) return upper(t);   // First Big -> ALL BIG
        return title(t);                           // small / mixed -> First Big
    }
}
