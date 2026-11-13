package com.mgb.keyboard.clip;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v0.17.39 (M6): remembers the rich-text format (html) of copied clips. Pure Java, no Android (tools/ClipFmtTest.java).
 * key = the clip text (same key as clip_meta_v1), value = the html that was copied with it. Oldest first.
 * Caps: one html max MAX_ONE chars (bigger = not stored), whole store max MAX_TOTAL chars (oldest dropped first).
 * Never throws. Data only: nothing in the app reads it for pasting yet (M7).
 */
public final class ClipFmt {
    public static final int MAX_ONE = 100000, MAX_TOTAL = 1500000;

    private final LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
    private long total = 0;

    private static long cost(String k, String h) { return (long) k.length() + h.length(); }

    /** Stores html for key (newer replaces older). html null / blank / too big = the key loses any stored format. Returns true when something changed. */
    public boolean put(String key, String html) {
        if (key == null || key.length() == 0) return false;
        if (html == null || html.trim().length() == 0 || html.length() > MAX_ONE) return remove(key);
        String old = map.get(key);
        if (old != null && old.equals(html)) return false;   // same format again: nothing to do (order kept)
        if (old != null) { map.remove(key); total -= cost(key, old); }
        map.put(key, html);
        total += cost(key, html);
        while (total > MAX_TOTAL && map.size() > 1) {
            Iterator<Map.Entry<String, String>> it = map.entrySet().iterator();
            Map.Entry<String, String> e = it.next();
            total -= cost(e.getKey(), e.getValue());
            it.remove();
        }
        return true;
    }

    public String get(String key) { return key == null ? null : map.get(key); }
    public boolean has(String key) { return key != null && map.containsKey(key); }
    public int size() { return map.size(); }
    public long totalChars() { return total; }

    public boolean remove(String key) {
        if (key == null) return false;
        String old = map.remove(key);
        if (old == null) return false;
        total -= cost(key, old);
        return true;
    }

    /** Drops every key that is not in keep (null keep = drop all). Returns true when something was removed. */
    public boolean prune(Collection<String> keep) {
        HashSet<String> k = keep == null ? new HashSet<String>() : new HashSet<String>(keep);
        boolean changed = false;
        Iterator<Map.Entry<String, String>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> e = it.next();
            if (!k.contains(e.getKey())) { total -= cost(e.getKey(), e.getValue()); it.remove(); changed = true; }
        }
        return changed;
    }

    public void clear() { map.clear(); total = 0; }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') b.append("\\\\"); else if (c == '\n') b.append("\\n"); else if (c == '\r') b.append("\\r"); else if (c == '\t') b.append("\\t"); else b.append(c);
        }
        return b.toString();
    }

    private static String unesc(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                if (n == 'n') b.append('\n'); else if (n == 'r') b.append('\r'); else if (n == 't') b.append('\t'); else b.append(n);
            } else b.append(c);
        }
        return b.toString();
    }

    /** One line per entry: key TAB html, both escaped (backslash, newline, CR, tab). Oldest first. */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(esc(e.getKey())).append('\t').append(esc(e.getValue()));
        }
        return sb.toString();
    }

    /** Broken lines are skipped; caps are applied again. Never null. */
    public static ClipFmt parse(String s) {
        ClipFmt f = new ClipFmt();
        if (s == null || s.length() == 0) return f;
        try {
            int pos = 0, n = s.length();
            while (pos <= n) {
                int nl = s.indexOf('\n', pos);
                if (nl < 0) nl = n;
                String line = s.substring(pos, nl);
                int tab = line.indexOf('\t');
                if (tab > 0 && tab < line.length() - 1) f.put(unesc(line.substring(0, tab)), unesc(line.substring(tab + 1)));
                pos = nl + 1;
            }
        } catch (Throwable t) { /* keep what was read */ }
        return f;
    }
}
