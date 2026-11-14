package com.mgb.keyboard.clip;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v0.18.4: "Recently deleted" clips (the Undo list of the clipboard). Pure Java, no Android (tools/ClipTrashTest.java).
 * Newest first. One delete action (x, Delete, Clear) = one GROUP, so "Undo" brings back the whole last action.
 * The list is limited to [limit] clips (pref clip_undo_n, default 30, 0 = Undo list off) and to MAX_CHARS characters
 * (the oldest go first, the newest entry always stays). The same text is never stored twice.
 * Saved format (one string): lines "group,pinned,atMs,urlencoded text,urlencoded html or ~" ("~" = no stored format; URLEncoder never writes a raw "~").
 * Never throws; null / bad input = nothing happens.
 */
public final class ClipTrash {
    public static final int DEFAULT_LIMIT = 30;
    public static final int MAX_LIMIT = 100;
    /** Total characters (text + html) of all entries. The oldest entries go first; the newest entry is never dropped by this budget. */
    public static final long MAX_CHARS = 200000L;
    /** The choices of the quick row in the clipboard menu (0 = off). The settings page allows every step of 10 from 0 to 100. */
    public static final int[] QUICK = { 0, 10, 30, 50, 100 };

    public static final class Entry {
        public final String text;
        public final boolean pinned;
        public final String html;   // null = plain clip
        public final long at;       // when it was deleted (ms), 0 = unknown
        public final int group;     // same number = deleted in one action
        Entry(String text, boolean pinned, String html, long at, int group) {
            this.text = text; this.pinned = pinned; this.html = html; this.at = at; this.group = group;
        }
    }

    private final ArrayList<Entry> items = new ArrayList<Entry>();
    private int nextGroup = 1;

    /** Out of range (negative or above MAX_LIMIT) = DEFAULT_LIMIT. 0 = off. */
    public static int sanitize(int n) { return (n < 0 || n > MAX_LIMIT) ? DEFAULT_LIMIT : n; }

    public int size() { return items.size(); }

    public boolean isEmpty() { return items.isEmpty(); }

    /** Copy of the list, newest first. */
    public List<Entry> view() { return Collections.unmodifiableList(new ArrayList<Entry>(items)); }

    private static long weight(Entry e) { return e.text.length() + (e.html == null ? 0 : e.html.length()); }

    /**
     * Stores one delete action. [texts] / [pinned] / [htmls] are parallel lists ([pinned] and [htmls] may be null or shorter).
     * Empty texts are skipped. An older entry with the same text is replaced. Returns how many clips were stored (0 when [limit] is 0).
     */
    public int push(List<String> texts, List<Boolean> pinned, List<String> htmls, long now, int limit) {
        limit = sanitize(limit);
        if (limit == 0) { items.clear(); return 0; }
        if (texts == null) return 0;
        ArrayList<Entry> grp = new ArrayList<Entry>();
        int g = nextGroup;
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            if (t == null || t.trim().length() == 0) continue;
            boolean dup = false;
            for (Entry e : grp) if (e.text.equals(t)) { dup = true; break; }
            if (dup) continue;
            boolean p = pinned != null && i < pinned.size() && pinned.get(i) != null && pinned.get(i).booleanValue();
            String h = (htmls != null && i < htmls.size()) ? htmls.get(i) : null;
            if (h != null && h.length() == 0) h = null;
            grp.add(new Entry(t, p, h, now, g));
        }
        if (grp.isEmpty()) return 0;
        nextGroup = g + 1;
        for (int i = items.size() - 1; i >= 0; i--) {
            for (Entry e : grp) if (items.get(i).text.equals(e.text)) { items.remove(i); break; }
        }
        items.addAll(0, grp);
        trim(limit);
        int kept = 0;
        for (Entry e : items) if (e.group == g) kept++;
        return kept;
    }

    /** Cuts the list to [limit] clips (0 = empty) and to MAX_CHARS characters, oldest first. Returns how many were removed. */
    public int trim(int limit) {
        limit = sanitize(limit);
        int before = items.size();
        if (limit == 0) { items.clear(); return before; }
        while (items.size() > limit) items.remove(items.size() - 1);
        long total = 0;
        for (Entry e : items) total += weight(e);
        while (total > MAX_CHARS && items.size() > 1) { total -= weight(items.get(items.size() - 1)); items.remove(items.size() - 1); }
        return before - items.size();
    }

    /** Removes entries deleted more than [ttlMs] ago (ttlMs <= 0 = nothing). Entries with unknown time stay. Returns how many were removed. */
    public int expire(long now, long ttlMs) {
        if (ttlMs <= 0L || now <= 0L) return 0;
        int n = 0;
        for (int i = items.size() - 1; i >= 0; i--) {
            Entry e = items.get(i);
            if (e.at > 0L && now - e.at > ttlMs) { items.remove(i); n++; }
        }
        return n;
    }

    /** The entries of the newest delete action (what "Undo" brings back). Empty list when there is none. */
    public List<Entry> newestGroup() {
        ArrayList<Entry> out = new ArrayList<Entry>();
        if (items.isEmpty()) return out;
        int g = items.get(0).group;
        for (Entry e : items) if (e.group == g) out.add(e);
        return out;
    }

    /** Same as newestGroup() and removes them from the list. */
    public List<Entry> popNewestGroup() {
        List<Entry> out = newestGroup();
        removeAll(out);
        return out;
    }

    public boolean remove(Entry e) { return e != null && items.remove(e); }

    /** Removes these entries (same objects as view() returned). Returns how many were removed. */
    public int removeAll(List<Entry> sel) {
        if (sel == null) return 0;
        int n = 0;
        for (Entry e : sel) if (e != null && items.remove(e)) n++;
        return n;
    }

    public void clear() { items.clear(); }

    // ---- save / load ----

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return ""; }
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : items) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.group).append(',').append(e.pinned ? '1' : '0').append(',').append(e.at).append(',')
              .append(enc(e.text)).append(',').append(e.html == null ? "~" : enc(e.html));
        }
        return sb.toString();
    }

    /** Tolerant: a bad line is skipped, never throws. */
    public static ClipTrash parse(String s) {
        ClipTrash c = new ClipTrash();
        if (s == null || s.length() == 0) return c;
        int maxG = 0;
        for (String line : s.split("\n")) {
            try {
                String[] p = line.split(",", -1);
                if (p.length != 5) continue;
                int g = Integer.parseInt(p[0]);
                boolean pin = "1".equals(p[1]);
                long at = Long.parseLong(p[2]);
                String text = URLDecoder.decode(p[3], "UTF-8");
                if (text.trim().length() == 0) continue;
                String html = "~".equals(p[4]) ? null : URLDecoder.decode(p[4], "UTF-8");
                if (html != null && html.length() == 0) html = null;
                c.items.add(new Entry(text, pin, html, at, g));
                if (g > maxG) maxG = g;
            } catch (Exception e) { /* skip bad line */ }
        }
        c.nextGroup = maxG + 1;
        return c;
    }
}
