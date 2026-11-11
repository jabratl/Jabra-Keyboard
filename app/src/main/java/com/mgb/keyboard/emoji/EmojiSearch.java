package com.mgb.keyboard.emoji;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v0.8.3 (E4) keyword search over the emoji data (pure Java, no Android; tested by tools/EmojiSearchTest.java).
 * Words = words of the emoji NAME + the SUBGROUP name (English, lower case). Every query word must be the start (prefix) of
 * some word of the emoji (AND). Order: exact name word first, then name prefix, then subgroup only, then a one-letter-shorter match (smile -> smiling); same score = file order.
 * Emoji with ok[i] == false (the phone cannot draw them, E3) are never returned. Built ONCE on the background thread.
 */
public final class EmojiSearch {
    public static final int MAX_QUERY = 30;

    private final EmojiDb db;
    private final String[][] nameWords;
    private final String[][] subWords;

    public EmojiSearch(EmojiDb db) {
        this.db = db;
        int n = db.size();
        nameWords = new String[n][];
        subWords = new String[n][];
        for (int i = 0; i < n; i++) {
            nameWords[i] = words(db.name(i));
            subWords[i] = words(db.subgroup(i));
        }
    }

    /** Lower case words made of letters / digits (everything else separates). */
    public static String[] words(String s) {
        List<String> out = new ArrayList<String>();
        if (s != null) {
            String low = s.toLowerCase(Locale.ROOT);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < low.length(); i++) {
                char c = low.charAt(i);
                if (Character.isLetterOrDigit(c)) sb.append(c);
                else if (sb.length() > 0) { out.add(sb.toString()); sb.setLength(0); }
            }
            if (sb.length() > 0) out.add(sb.toString());
        }
        return out.toArray(new String[0]);
    }

    /** Item indexes that match, best first, at most max. Empty query / no words = empty list. ok may be null (= all drawable). */
    public List<Integer> search(String query, boolean[] ok, int max) {
        List<Integer> out = new ArrayList<Integer>();
        if (query == null || max <= 0) return out;
        if (query.length() > MAX_QUERY) query = query.substring(0, MAX_QUERY);
        String[] q = words(query);
        if (q.length == 0) return out;
        int n = db.size();
        int[] score = new int[n];
        int[] hit = new int[n];
        int cnt = 0;
        for (int i = 0; i < n; i++) {
            if (ok != null && i < ok.length && !ok[i]) continue;
            int total = 0;
            boolean all = true;
            for (int k = 0; k < q.length && all; k++) {
                int best = bestScore(q[k], nameWords[i], subWords[i]);
                if (best < 0) all = false; else total += best;
            }
            if (all) { score[i] = total; hit[cnt++] = i; }
        }
        // stable: score 0 first, then 1, ... (file order inside a score)
        int maxScore = q.length * 3;
        for (int s = 0; s <= maxScore && out.size() < max; s++) {
            for (int j = 0; j < cnt && out.size() < max; j++) if (score[hit[j]] == s) out.add(hit[j]);
        }
        return out;
    }

    private static int bestScore(String w, String[] name, String[] sub) {
        int best = -1;
        for (String t : name) {
            if (t.equals(w)) return 0;
            if (t.startsWith(w)) best = 1;
        }
        if (best >= 0) return best;
        for (String t : sub) if (t.startsWith(w)) return 2;
        // weak match: "smile" / "smiley" / "hearts" also find "smiling" / "heart" (drop one ending letter, 4+ letters)
        int L = w.length();
        if (L >= 4) {
            char last = w.charAt(L - 1);
            if (last == 'e' || last == 's' || last == 'y') {
                String stem = w.substring(0, L - 1);
                for (String t : name) if (t.startsWith(stem)) return 3;
            }
        }
        return -1;
    }
}
