package com.mgb.keyboard.clip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.4.3 pure Java: small side table for clips (when made, when last pasted, how many pastes).
 * clips_v1 is NOT touched. Saved format (pref clip_meta_v1): lines "key,made,used,count"; key = hash of the text + length.
 * Also: sort modes and the auto-delete rule (unpinned only, never a timer: caller runs it when the panel opens / a clip is added).
 */
public final class ClipMeta {
    public static final int SORT_NEWEST = 0, SORT_OLDEST = 1, SORT_AZ = 2, SORT_USED = 3;
    public static final String[] SORT_NAMES = { "Newest", "Oldest", "A-Z", "Most used" };
    public static final String[] EXPIRE_NAMES = { "Never", "1 hour", "1 day", "7 days" };

    private static final class M { long made, used; int count; }

    private final Map<String, M> map = new LinkedHashMap<String, M>();

    public static String key(String text) {
        String t = text == null ? "" : text;
        return Integer.toHexString(t.hashCode()) + "x" + t.length();
    }

    public static long ttlMs(int level) {
        switch (level) {
            case 1: return 3600000L;
            case 2: return 86400000L;
            case 3: return 7L * 86400000L;
            default: return 0L;
        }
    }

    public void made(String text, long now) {
        String k = key(text);
        if (!map.containsKey(k)) { M m = new M(); m.made = now; map.put(k, m); }
    }

    public void used(String text, long now) {
        String k = key(text);
        M m = map.get(k);
        if (m == null) { m = new M(); m.made = now; map.put(k, m); }
        m.used = now;
        m.count++;
    }

    /** v0.4.3.1: an edited clip keeps its made / used / count under the new text. */
    public void rename(String oldText, String newText) {
        String ko = key(oldText), kn = key(newText);
        if (ko.equals(kn)) return;
        M m = map.remove(ko);
        if (m != null) map.put(kn, m);
    }

    public int count(String text) { M m = map.get(key(text)); return m == null ? 0 : m.count; }
    public long madeAt(String text) { M m = map.get(key(text)); return m == null ? 0L : m.made; }
    public long usedAt(String text) { M m = map.get(key(text)); return m == null ? 0L : m.used; }
    public int size() { return map.size(); }

    /** Drops entries of clips that no longer exist. Returns true if something was dropped. */
    public boolean prune(List<ClipList.Item> items) {
        HashSet<String> keep = new HashSet<String>();
        if (items != null) for (ClipList.Item it : items) keep.add(key(it.text));
        boolean ch = false;
        for (java.util.Iterator<String> i = map.keySet().iterator(); i.hasNext();) {
            if (!keep.contains(i.next())) { i.remove(); ch = true; }
        }
        return ch;
    }

    /** Sort a copy. NEWEST = list order as given (the list is already newest first). */
    public List<ClipList.Item> sort(List<ClipList.Item> in, int mode) {
        ArrayList<ClipList.Item> out = new ArrayList<ClipList.Item>(in == null ? new ArrayList<ClipList.Item>() : in);
        if (mode == SORT_OLDEST) {
            Collections.reverse(out);
        } else if (mode == SORT_AZ) {
            Collections.sort(out, new Comparator<ClipList.Item>() {
                public int compare(ClipList.Item a, ClipList.Item b) { return a.text.toLowerCase().compareTo(b.text.toLowerCase()); }
            });
        } else if (mode == SORT_USED) {
            Collections.sort(out, new Comparator<ClipList.Item>() {   // stable: equal counts keep newest first
                public int compare(ClipList.Item a, ClipList.Item b) { return count(b.text) - count(a.text); }
            });
        }
        return out;
    }

    /**
     * Unpinned clips older than ttl (by the later of made / used). Clips with no record get "made = now" (so turning
     * Auto-delete on never wipes old history at once) and are not returned. ttl <= 0 = nothing.
     */
    public List<ClipList.Item> expired(List<ClipList.Item> items, long ttl, long now) {
        ArrayList<ClipList.Item> out = new ArrayList<ClipList.Item>();
        if (ttl <= 0 || items == null) return out;
        for (ClipList.Item it : items) {
            if (it.pinned) continue;
            M m = map.get(key(it.text));
            if (m == null) { made(it.text, now); continue; }
            long last = Math.max(m.made, m.used);
            if (now - last > ttl) out.add(it);
        }
        return out;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, M> e : map.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.getKey()).append(',').append(e.getValue().made).append(',').append(e.getValue().used).append(',').append(e.getValue().count);
        }
        return sb.toString();
    }

    public static ClipMeta parse(String s) {
        ClipMeta c = new ClipMeta();
        if (s == null) return c;
        for (String line : s.split("\n")) {
            String[] p = line.split(",");
            if (p.length != 4 || p[0].isEmpty()) continue;
            try {
                M m = new M();
                m.made = Long.parseLong(p[1]); m.used = Long.parseLong(p[2]); m.count = Integer.parseInt(p[3]);
                if (m.count < 0) continue;
                c.map.put(p[0], m);
            } catch (NumberFormatException ex) { }
        }
        return c;
    }
}
