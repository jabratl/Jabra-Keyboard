package com.mgb.keyboard.typing;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * v0.10.0 (G1) offline word index for suggestions (pure Java, no Android, no permission; tested by tools/WordIndexTest.java).
 * NO UI and NOT used by the keyboard yet (G2 = strip UI). Reads a bundled word list (assets/words/en.txt, bn.txt): plain UTF-8, LF lines:
 *   #JABRA-WORDS 1     magic + format (unknown format = refused)
 *   #lang en           language tag (informational)
 *   #count N           number of word lines (must match, so a cut-off file is refused)
 *   word               one word per line, MOST FREQUENT FIRST (line order = rank 0, 1, 2 ...)
 * Lines starting with # are ignored; blank lines ignored. A word is a line without spaces, 1..MAX_WORD chars. Duplicates (ignoring case) keep the first (better) rank.
 * Lookup = prefix match, Latin is case-insensitive, Bangla exact; result in rank order (frequency). ANY problem when reading = parseOrNull gives null (feature simply stays off).
 * Nothing the user types is stored here (learning = G3, needs its own ASK).
 */
public final class WordIndex {
    public static final int FORMAT = 1;
    public static final String MAGIC = "#JABRA-WORDS";
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    public static final int MAX_WORDS = 200000;
    public static final int MAX_WORD = 40;

    private final String lang;
    private final String[] words;     // rank order
    private final String[] keys;      // lower-case keys, sorted
    private final int[] rankOf;       // rank of keys[i]

    private WordIndex(String lang, String[] words, String[] keys, int[] rankOf) {
        this.lang = lang; this.words = words; this.keys = keys; this.rankOf = rankOf;
    }

    public String lang() { return lang; }
    public int size() { return words.length; }
    /** Word at rank [r] (0 = most frequent), "" when out of range. */
    public String word(int r) { return r >= 0 && r < words.length ? words[r] : ""; }

    private static String key(String s) { return s.toLowerCase(Locale.ROOT); }

    /** Rank of [w] (ignoring case), -1 when unknown. */
    public int rank(String w) {
        if (w == null || w.isEmpty()) return -1;
        String k = key(w);
        int i = lower(k);
        return (i < keys.length && keys[i].equals(k)) ? rankOf[i] : -1;
    }

    public boolean contains(String w) { return rank(w) >= 0; }

    /** First index in keys that is >= k. */
    private int lower(String k) {
        int lo = 0, hi = keys.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (keys[mid].compareTo(k) < 0) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    /** Up to [max] words that start with [prefix], most frequent first (the exact word is included when it is a word). Empty prefix or max <= 0 = empty list. */
    public List<String> lookup(String prefix, int max) { return lookup(prefix, max, false); }

    /** Same, [skipExact] = leave out the word that equals the prefix (nothing to suggest for a finished word). */
    public List<String> lookup(String prefix, int max, boolean skipExact) {
        List<String> out = new ArrayList<String>();
        if (prefix == null || prefix.isEmpty() || max <= 0 || words.length == 0) return out;
        String k = key(prefix);
        int i = lower(k);
        int n = 0;
        int[] r = new int[16];
        for (int j = i; j < keys.length && keys[j].startsWith(k); j++) {
            if (skipExact && keys[j].equals(k)) continue;
            if (n == r.length) r = Arrays.copyOf(r, n * 2);
            r[n++] = rankOf[j];
        }
        if (n == 0) return out;
        Arrays.sort(r, 0, n);
        for (int j = 0; j < n && j < max; j++) out.add(words[r[j]]);
        return out;
    }

    /** Gives a suggestion the same capital style as what the user typed: "He" > "Hello", "HE" > "HELLO", "he" > as stored. Bangla / no letters = unchanged. */
    public static String matchCase(String typed, String word) {
        if (typed == null || typed.isEmpty() || word == null || word.isEmpty()) return word;
        boolean anyLower = false, anyUpper = false;
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (Character.isUpperCase(c)) anyUpper = true; else if (Character.isLowerCase(c)) anyLower = true;
        }
        if (!anyUpper) return word;
        if (!anyLower && typed.length() > 1) return word.toUpperCase(Locale.ROOT);
        if (Character.isUpperCase(typed.charAt(0))) return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
        return word;
    }

    // ---- reading ----

    public static WordIndex parseOrNull(InputStream in) {
        if (in == null) return null;
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > MAX_BYTES) return null;
            }
            return parseOrNull(new String(bo.toByteArray(), StandardCharsets.UTF_8));
        } catch (Throwable t) {
            return null;
        }
    }

    public static WordIndex parseOrNull(String text) {
        try {
            if (text == null) return null;
            if (text.length() > MAX_BYTES) return null;
            if (text.startsWith("﻿")) text = text.substring(1);
            String[] lines = text.split("\n", -1);
            int fmt = -1;
            String lang = "";
            int count = -1;
            List<String> list = new ArrayList<String>();
            Set<String> seen = new HashSet<String>();
            for (int li = 0; li < lines.length; li++) {
                String l = lines[li];
                if (l.endsWith("\r")) l = l.substring(0, l.length() - 1);
                if (l.isEmpty()) continue;
                if (l.charAt(0) == '#') {
                    if (l.startsWith(MAGIC)) {
                        String r = l.substring(MAGIC.length()).trim();
                        try { fmt = Integer.parseInt(r); } catch (NumberFormatException e) { return null; }
                    } else if (l.startsWith("#lang")) {
                        lang = l.substring(5).trim();
                    } else if (l.startsWith("#count")) {
                        try { count = Integer.parseInt(l.substring(6).trim()); } catch (NumberFormatException e) { return null; }
                    }
                    continue;
                }
                if (fmt != FORMAT) return null;            // magic must come first
                String w = l.trim();
                if (w.isEmpty()) continue;
                if (w.length() > MAX_WORD) return null;
                for (int i = 0; i < w.length(); i++) if (Character.isWhitespace(w.charAt(i))) return null;
                if (list.size() >= MAX_WORDS) return null;
                list.add(w);
            }
            if (fmt != FORMAT) return null;
            if (count < 0 || count != list.size()) return null;
            // dedupe (ignoring case), keep the first rank
            List<String> uniq = new ArrayList<String>(list.size());
            for (String w : list) if (seen.add(key(w))) uniq.add(w);
            String[] words = uniq.toArray(new String[0]);
            Integer[] idx = new Integer[words.length];
            final String[] lk = new String[words.length];
            for (int i = 0; i < words.length; i++) { idx[i] = i; lk[i] = key(words[i]); }
            Arrays.sort(idx, (a, b) -> lk[a].compareTo(lk[b]));
            String[] keys = new String[words.length];
            int[] rankOf = new int[words.length];
            for (int i = 0; i < idx.length; i++) { keys[i] = lk[idx[i]]; rankOf[i] = idx[i]; }
            return new WordIndex(lang, words, keys, rankOf);
        } catch (Throwable t) {
            return null;
        }
    }
}
