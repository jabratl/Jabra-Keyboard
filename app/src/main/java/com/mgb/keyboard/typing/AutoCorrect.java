package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v0.10.3 (G3.2) auto-correct rules (pure Java, no Android, no storage; tested by tools/AutoCorrectTest.java). Used ONLY when pref ac_on is ON (default OFF).
 * fix(typed, words, learned) = the ONE bundled word that is exactly 1 edit away from [typed], or "" (= leave the word alone).
 * Never corrects: words shorter than 3 (3 only for a swap like teh > the) / longer than 20, anything that is not plain letters a-z (Bangla, Avro, digits, apostrophes),
 * a word with a capital inside (acronym / name: NASA, iPhone), a word that is already in the bundled list or was learned, a word whose first letter would change.
 * 1 edit = delete one letter, insert one letter, replace one letter, swap two neighbours. Ambiguous (2+ different candidates) = no correction.
 * The result keeps the capital style the user typed (Teh > The).
 */
public final class AutoCorrect {
    private AutoCorrect() { }

    public static final int MIN_LEN = 3;
    public static final int MAX_LEN = 20;

    /** What is known: bundled words + words the user typed before (learned). */
    public interface Known { boolean has(String lower); }

    public static boolean eligible(String w) {
        if (w == null) return false;
        int n = w.length();
        if (n < MIN_LEN || n > MAX_LEN) return false;
        for (int i = 0; i < n; i++) {
            char c = w.charAt(i);
            boolean lower = c >= 'a' && c <= 'z';
            boolean upper = c >= 'A' && c <= 'Z';
            if (!lower && !upper) return false;
            if (i > 0 && upper) return false;   // capital inside = acronym / name
        }
        return true;
    }

    /** All distinct strings 1 edit from [w] (lower case, first letter unchanged). */
    static List<String> edits(String w) {
        List<String> out = new ArrayList<String>();
        int n = w.length();
        for (int i = 1; i < n; i++) out.add(w.substring(0, i) + w.substring(i + 1));                 // delete
        for (int i = 1; i < n - 1; i++) out.add(w.substring(0, i) + w.charAt(i + 1) + w.charAt(i) + w.substring(i + 2));   // swap neighbours (not the first letter)
        for (int i = 1; i < n; i++) for (char c = 'a'; c <= 'z'; c++) if (c != w.charAt(i)) out.add(w.substring(0, i) + c + w.substring(i + 1));   // replace
        for (int i = 1; i <= n; i++) for (char c = 'a'; c <= 'z'; c++) out.add(w.substring(0, i) + c + w.substring(i));       // insert
        return out;
    }

    /** The correction for [typed] (same capital style), or "" for none. [words] = bundled list, [learned] may be null. */
    public static String fix(String typed, Known words, Known learned) {
        try {
            if (!eligible(typed) || words == null) return "";
            String w = typed.toLowerCase(Locale.ROOT);
            if (words.has(w) || (learned != null && learned.has(w))) return "";
            String found = "";
            boolean swapOnly = w.length() == MIN_LEN;
            int n = w.length();
            for (String c : edits(w)) {
                if (swapOnly && c.length() != n) continue;                 // length 3: only same-length edits
                if (swapOnly && !isSwap(w, c)) continue;                   // ... and only a swap
                if (c.length() < MIN_LEN || !words.has(c)) continue;
                if (found.isEmpty()) found = c; else if (!found.equals(c)) return "";   // ambiguous
            }
            if (found.isEmpty()) return "";
            return WordIndex.matchCase(typed, found);
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean isSwap(String a, String b) {
        if (a.length() != b.length()) return false;
        int d = 0;
        for (int i = 0; i < a.length(); i++) if (a.charAt(i) != b.charAt(i)) d++;
        return d == 2;
    }

    /** True when the text before the cursor ends with the correction + one space (so a backspace may undo it). */
    public static boolean canUndo(String before, String fixed) {
        return before != null && fixed != null && !fixed.isEmpty() && before.endsWith(fixed + " ");
    }
}
