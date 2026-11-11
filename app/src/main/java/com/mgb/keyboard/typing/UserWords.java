package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v0.10.2 (G3.1) the words the user typed (learning), pure Java, no Android, no permission (tested by tools/UserWordsTest.java).
 * Used ONLY when pref learn_on is ON (default OFF). Lives on the phone only (filesDir/learn_v1.txt, written by LearnStore.kt). Never sent anywhere.
 * - learn(): counts a finished word. Rejected: shorter than MIN_WORD, longer than WordIndex.MAX_WORD, digits, symbols, a letter repeated 4+ times in a row.
 *   Latin words are stored in lower case unless a capital sits INSIDE the word (iPhone, McDonald); matchCase() puts the capitals back when suggesting.
 * - lookup(): words that start with the typed prefix that were typed at least MIN_SHOW times (one-off typos never show), most used first, then newest.
 * - merge(): learned words first, then the bundled words (no duplicates, ignoring case), at most [max].
 * - Size is capped at MAX_WORDS: the least used / oldest word is dropped to make room.
 * - File: "#JABRA-LEARN 1" / "#count N" / lines "count TAB word", oldest first. parseOrNull refuses a broken or cut-off file (= start empty, never crash).
 * All public methods are synchronized (the file is read on a background thread, words are learned on the UI thread).
 */
public final class UserWords {
    public static final String MAGIC = "#JABRA-LEARN";
    public static final int FORMAT = 1;
    public static final int MAX_WORDS = 3000;
    public static final int MIN_WORD = 2;
    public static final int MIN_SHOW = 2;
    public static final int MAX_COUNT = 1000000;
    public static final int MAX_BYTES = 512 * 1024;

    private static final class E { String w; int n; long t; }

    private final Map<String, E> map = new HashMap<String, E>();   // key = lower-case word
    private long seq = 0;
    private boolean dirty = false;

    private static String key(String s) { return s.toLowerCase(Locale.ROOT); }

    private static boolean wordChar(char c) {
        if (Character.isLetter(c)) return true;
        int t = Character.getType(c);
        return t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK;
    }

    /** True when [w] may be learned (see class comment). */
    public static boolean acceptable(String w) {
        if (w == null) return false;
        int n = w.length();
        if (n < MIN_WORD || n > WordIndex.MAX_WORD) return false;
        boolean letter = false;
        int run = 1;
        for (int i = 0; i < n; i++) {
            char c = w.charAt(i);
            if (Character.isLetter(c)) letter = true;
            if (!(wordChar(c) || ((c == '\'' || c == '’') && i > 0 && i < n - 1))) return false;
            if (i > 0 && c == w.charAt(i - 1)) { run++; if (run >= 4) return false; } else run = 1;
        }
        return letter;
    }

    /** True when committing [s] ends a word: exactly one char that is a space / newline / punctuation (not a letter, digit, mark or apostrophe). */
    public static boolean isBoundary(String s) {
        if (s == null || s.length() != 1) return false;
        char c = s.charAt(0);
        if (wordChar(c) || Character.isDigit(c) || c == '\'' || c == '\u2019') return false;
        return true;
    }

    /** Stored form: lower case unless a capital sits inside the word. */
    static String stored(String w) {
        for (int i = 1; i < w.length(); i++) if (Character.isUpperCase(w.charAt(i))) return w;
        return key(w);
    }

    /** Counts one use of [w]. Returns true when it was accepted. */
    public synchronized boolean learn(String w) {
        if (!acceptable(w)) return false;
        String k = key(w);
        E e = map.get(k);
        if (e == null) {
            if (map.size() >= MAX_WORDS) dropWeakest();
            e = new E();
            e.w = stored(w);
            e.n = 0;
            map.put(k, e);
        } else if (e.w.equals(key(e.w)) && !stored(w).equals(key(w))) {
            e.w = stored(w);   // first seen plain, now with an inner capital (iPhone): keep the capital form
        }
        if (e.n < MAX_COUNT) e.n++;
        e.t = ++seq;
        dirty = true;
        return true;
    }

    private void dropWeakest() {
        String worst = null;
        E we = null;
        for (Map.Entry<String, E> me : map.entrySet()) {
            E e = me.getValue();
            if (we == null || e.n < we.n || (e.n == we.n && e.t < we.t)) { we = e; worst = me.getKey(); }
        }
        if (worst != null) map.remove(worst);
    }

    public synchronized int size() { return map.size(); }
    public synchronized boolean isDirty() { return dirty; }
    public synchronized void markClean() { dirty = false; }

    /** How many times [w] was typed (0 = unknown). */
    public synchronized int count(String w) {
        if (w == null) return 0;
        E e = map.get(key(w));
        return e == null ? 0 : e.n;
    }

    /** Removes one word. True when it existed. */
    public synchronized boolean forget(String w) {
        if (w == null) return false;
        boolean r = map.remove(key(w)) != null;
        if (r) dirty = true;
        return r;
    }

    public synchronized void clear() {
        if (!map.isEmpty()) dirty = true;
        map.clear();
        seq = 0;
    }

    /** Learned words that start with [prefix] (not equal to it when [skipExact]), typed >= MIN_SHOW times, most used first, then newest. */
    public synchronized List<String> lookup(String prefix, int max, boolean skipExact) {
        List<String> out = new ArrayList<String>();
        if (prefix == null || prefix.isEmpty() || max <= 0) return out;
        String k = key(prefix);
        List<E> hit = new ArrayList<E>();
        for (Map.Entry<String, E> me : map.entrySet()) {
            E e = me.getValue();
            if (e.n < MIN_SHOW) continue;
            String mk = me.getKey();
            if (!mk.startsWith(k)) continue;
            if (skipExact && mk.equals(k)) continue;
            hit.add(e);
        }
        Collections.sort(hit, new Comparator<E>() {
            public int compare(E a, E b) {
                if (a.n != b.n) return a.n > b.n ? -1 : 1;
                if (a.t != b.t) return a.t > b.t ? -1 : 1;
                return a.w.compareTo(b.w);
            }
        });
        for (int i = 0; i < hit.size() && out.size() < max; i++) out.add(hit.get(i).w);
        return out;
    }

    /** Learned words first, then [bundled] words that are not already there (ignoring case), at most [max]. Null-safe. */
    public static List<String> merge(List<String> learned, List<String> bundled, int max) {
        List<String> out = new ArrayList<String>();
        if (max <= 0) return out;
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        if (learned != null) for (String w : learned) {
            if (w == null || w.isEmpty() || !seen.add(key(w))) continue;
            out.add(w);
            if (out.size() >= max) return out;
        }
        if (bundled != null) for (String w : bundled) {
            if (w == null || w.isEmpty() || !seen.add(key(w))) continue;
            out.add(w);
            if (out.size() >= max) return out;
        }
        return out;
    }

    // ---- file ----

    /** Text for the file, oldest first (so reading it back keeps the "newest" order). */
    public synchronized String serialize() {
        List<E> all = new ArrayList<E>(map.values());
        Collections.sort(all, new Comparator<E>() {
            public int compare(E a, E b) { return a.t < b.t ? -1 : (a.t > b.t ? 1 : a.w.compareTo(b.w)); }
        });
        StringBuilder sb = new StringBuilder();
        sb.append(MAGIC).append(' ').append(FORMAT).append('\n');
        sb.append("#count ").append(all.size()).append('\n');
        for (E e : all) sb.append(e.n).append('\t').append(e.w).append('\n');
        return sb.toString();
    }

    /** Reads a file text. Any problem (wrong magic, wrong count, bad line, too big) = null. */
    public static UserWords parseOrNull(String text) {
        try {
            if (text == null || text.length() > MAX_BYTES) return null;
            if (text.startsWith("﻿")) text = text.substring(1);
            String[] lines = text.split("\n", -1);
            int fmt = -1, count = -1;
            UserWords u = new UserWords();
            int lineNo = 0;
            for (String raw : lines) {
                String l = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
                if (l.isEmpty()) continue;
                if (l.charAt(0) == '#') {
                    if (l.startsWith(MAGIC)) {
                        try { fmt = Integer.parseInt(l.substring(MAGIC.length()).trim()); } catch (NumberFormatException e) { return null; }
                    } else if (l.startsWith("#count")) {
                        try { count = Integer.parseInt(l.substring(6).trim()); } catch (NumberFormatException e) { return null; }
                    }
                    continue;
                }
                if (fmt != FORMAT) return null;
                int tab = l.indexOf('\t');
                if (tab <= 0) return null;
                int n;
                try { n = Integer.parseInt(l.substring(0, tab)); } catch (NumberFormatException e) { return null; }
                String w = l.substring(tab + 1);
                if (n < 1 || n > MAX_COUNT || !acceptable(w)) return null;
                String k = key(w);
                if (u.map.containsKey(k)) return null;
                if (u.map.size() >= MAX_WORDS) return null;
                E e = new E();
                e.w = w; e.n = n; e.t = ++lineNo;
                u.map.put(k, e);
            }
            if (fmt != FORMAT || count != u.map.size()) return null;
            u.seq = lineNo;
            return u;
        } catch (Throwable t) {
            return null;
        }
    }
}
