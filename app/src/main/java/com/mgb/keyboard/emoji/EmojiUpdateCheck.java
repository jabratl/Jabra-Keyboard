package com.mgb.keyboard.emoji;

/**
 * v0.8.5 (E6) decides whether a freshly built emoji list may replace the current one (pure Java, no Android; tools/EmojiUpdateCheckTest.java).
 * The download itself is Kotlin (EmojiUpdater.kt); this class only judges the numbers. Anything doubtful = keep the old list.
 */
public final class EmojiUpdateCheck {
    private EmojiUpdateCheck() { }

    public static final String URL = "https://unicode.org/Public/emoji/latest/emoji-test.txt";
    public static final int MAX_DOWNLOAD = 3 * 1024 * 1024;   // the real emoji-test.txt is about 0.5 MB
    public static final int MIN_COUNT = 1000;                 // a real list has about 1,900
    public static final int MIN_GROUPS = 8;                   // Unicode has 9 (Component is dropped)

    public static final int OK = 0, UP_TO_DATE = 1, TOO_FEW = 2, BAD = 3;

    /** "18.0" -> {18, 0}. Digits and dots only, 1-4 parts, each 1-4 digits. Anything else = null. */
    public static int[] parseVersion(String v) {
        if (v == null) return null;
        String t = v.trim();
        if (t.length() == 0) return null;
        String[] p = t.split("\\.", -1);
        if (p.length > 4) return null;
        int[] out = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            String s = p[i];
            if (s.length() == 0 || s.length() > 4) return null;
            for (int k = 0; k < s.length(); k++) if (s.charAt(k) < '0' || s.charAt(k) > '9') return null;
            out[i] = Integer.parseInt(s);
        }
        return out;
    }

    /** Numeric compare (18.0 vs 17.0, 15.1 vs 15.0, 16 vs 16.0 are equal). A bad / empty version counts as lower than any good one. */
    public static int compareVersions(String a, String b) {
        int[] x = parseVersion(a), y = parseVersion(b);
        if (x == null && y == null) return 0;
        if (x == null) return -1;
        if (y == null) return 1;
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    /**
     * newUni / newCount / newGroups = what EmojiBuild made; curUni / curCount = the list in use now (curCount 0 = unknown).
     * BAD: the new version is unreadable or too few groups. TOO_FEW: fewer than MIN_COUNT, or more than 10% fewer than now.
     * UP_TO_DATE: same or older Unicode version. OK: replace.
     */
    public static int verdict(String newUni, int newCount, int newGroups, String curUni, int curCount) {
        if (parseVersion(newUni) == null) return BAD;
        if (newGroups < MIN_GROUPS) return BAD;
        if (newCount < MIN_COUNT) return TOO_FEW;
        if (curCount > 0 && (long) newCount * 10L < (long) curCount * 9L) return TOO_FEW;
        if (compareVersions(newUni, curUni) <= 0) return UP_TO_DATE;
        return OK;
    }
}
