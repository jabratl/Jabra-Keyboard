package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * v0.38.0 (P1): pure helpers for the new PASTE keys of the small keyboards (clip Edit screen, Find & Replace typing view).
 * No Android, never throws. Tested by tools/QuickPasteTest.java.
 * Why: the small keyboards had no paste, so long things (emails, links, Bangla words, templates) had to be typed key by key.
 */
public final class QuickPaste {
    private QuickPaste() { }

    /** Quick-insert chips of the clip Edit screen (typing these key by key is slow). */
    public static final String[] QUICK = { ".com", "www.", "https://", "@gmail.com" };

    public static final int LIST_MAX = 30;

    /** Clipboard text made safe for a text box: null = "", CR LF and CR become LF, NUL removed. */
    public static String normalize(String s) {
        if (s == null || s.length() == 0) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\r') {
                if (i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
                sb.append('\n');
            } else if (c != '\u0000') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** What really goes in: normalize(), then cut to at most room characters (never inside a surrogate pair). room <= 0 = "". */
    public static String fit(String clip, int room) {
        String t = normalize(clip);
        if (room <= 0 || t.length() == 0) return "";
        if (t.length() <= room) return t;
        int end = room;
        if (end > 0 && end < t.length() && Character.isHighSurrogate(t.charAt(end - 1)) && Character.isLowSurrogate(t.charAt(end))) end--;
        return t.substring(0, end);
    }

    /** True when fit() had to cut something off. */
    public static boolean wasCut(String clip, int room) {
        return normalize(clip).length() > Math.max(room, 0) && fit(clip, room).length() > 0;
    }

    /** One-line label for a list button: line breaks and tabs shown as marks, long text cut with an ellipsis (surrogate-safe). */
    public static String oneLine(String t, int max) {
        String s = normalize(t).replace("\n", "\u23CE").replace("\t", "\u21E5");
        if (max < 2) max = 2;
        if (s.length() <= max) return s;
        int end = max - 1;
        if (end > 0 && Character.isHighSurrogate(s.charAt(end - 1)) && Character.isLowSurrogate(s.charAt(end))) end--;
        return s.substring(0, end) + "\u2026";
    }

    /**
     * Texts for the picker: the latest copy first (if it has text), then the others in the given order.
     * Blank texts and repeats are dropped, at most max kept.
     */
    public static List<String> pickList(String latest, List<String> others, int max) {
        ArrayList<String> out = new ArrayList<String>();
        HashSet<String> seen = new HashSet<String>();
        if (max <= 0) return out;
        String l = normalize(latest);
        if (l.trim().length() > 0) { out.add(l); seen.add(l); }
        if (others != null) {
            for (String o : others) {
                if (out.size() >= max) break;
                String n = normalize(o);
                if (n.trim().length() == 0 || seen.contains(n)) continue;
                seen.add(n);
                out.add(n);
            }
        }
        while (out.size() > max) out.remove(out.size() - 1);
        return out;
    }

    /** Short message after a paste. */
    public static String doneText(int inserted, boolean cut) {
        return "Pasted " + inserted + (cut ? " (cut to fit)" : "");
    }

    /** Message when nothing could be pasted. */
    public static String noneText(String clip, int room) {
        if (normalize(clip).length() == 0) return "Clipboard is empty";
        return "No room left (max " + (room <= 0 ? 0 : room) + " more)";
    }
}
