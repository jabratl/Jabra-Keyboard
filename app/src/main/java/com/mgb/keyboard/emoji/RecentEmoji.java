package com.mgb.keyboard.emoji;

import java.util.ArrayList;
import java.util.List;

/** v0.1.0 recently used emoji (pure Java). Stored as ONE string, emoji separated by a single space, newest first. */
public final class RecentEmoji {
    private RecentEmoji() { }

    public static final int MAX = 32;

    public static List<String> parse(String stored) {
        List<String> out = new ArrayList<String>();
        if (stored == null) return out;
        for (String s : stored.split(" ")) {
            if (s.length() > 0 && !out.contains(s)) out.add(s);
            if (out.size() >= MAX) break;
        }
        return out;
    }

    /** New stored string after the user used emoji e: moves it to the front, no duplicates, max MAX items. */
    public static String add(String stored, String e) {
        if (e == null || e.length() == 0 || e.indexOf(' ') >= 0) return stored == null ? "" : stored;
        List<String> l = parse(stored);
        l.remove(e);
        l.add(0, e);
        while (l.size() > MAX) l.remove(l.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append(' '); sb.append(l.get(i)); }
        return sb.toString();
    }
}
