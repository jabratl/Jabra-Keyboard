package com.mgb.keyboard.clip;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * v0.0.6: clipboard history logic. Pure Java (no Android) so it can be tested on a computer.
 * Newest first. Pinned items are never removed automatically. Text only.
 * Saved format (one string): lines "<0|1>,<url-encoded text>".
 */
public final class ClipList {
    public static final int MAX_UNPINNED = 40;
    public static final int MAX_PINNED = 30;
    public static final int MAX_LEN = 2000;

    public static final class Item {
        public final String text;
        public boolean pinned;
        Item(String text, boolean pinned) { this.text = text; this.pinned = pinned; }
    }

    private final ArrayList<Item> items = new ArrayList<Item>();

    /** Adds a copied text (moves it to the front if it is already there). Returns true if the list changed. */
    public boolean add(String t) {
        if (t == null || t.trim().length() == 0 || t.length() > MAX_LEN) return false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).text.equals(t)) {
                if (i == 0) return false;
                Item it = items.remove(i);
                items.add(0, it);
                return true;
            }
        }
        items.add(0, new Item(t, false));
        int unpinned = 0;
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).pinned && ++unpinned > MAX_UNPINNED) { items.remove(i); i--; }
        }
        return true;
    }

    /** Items to show: pinned first, then the rest, both newest first; filtered by query (case-insensitive) if not empty. */
    public List<Item> view(String query) {
        String q = query == null ? "" : query.toLowerCase();
        ArrayList<Item> out = new ArrayList<Item>();
        for (int pass = 0; pass < 2; pass++) {
            for (Item it : items) {
                if (it.pinned != (pass == 0)) continue;
                if (q.length() == 0 || it.text.toLowerCase().contains(q)) out.add(it);
            }
        }
        return out;
    }

    /** Pin / unpin. Returns false if the pin limit is reached. */
    public boolean togglePin(Item it) {
        if (it == null || !items.contains(it)) return false;
        if (!it.pinned) {
            int n = 0;
            for (Item x : items) if (x.pinned) n++;
            if (n >= MAX_PINNED) return false;
        }
        it.pinned = !it.pinned;
        return true;
    }

    public boolean remove(Item it) { return items.remove(it); }

    /** Removes everything that is not pinned. Returns how many were removed. */
    public int clearUnpinned() {
        int n = 0;
        for (int i = items.size() - 1; i >= 0; i--) if (!items.get(i).pinned) { items.remove(i); n++; }
        return n;
    }

    public int size() { return items.size(); }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        try {
            for (Item it : items) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(it.pinned ? '1' : '0').append(',').append(URLEncoder.encode(it.text, "UTF-8"));
            }
        } catch (Exception e) { /* never happens for UTF-8 */ }
        return sb.toString();
    }

    /** Tolerant: a bad line is skipped, never throws. */
    public static ClipList parse(String s) {
        ClipList c = new ClipList();
        if (s == null || s.length() == 0) return c;
        for (String line : s.split("\n")) {
            try {
                int comma = line.indexOf(',');
                if (comma != 1) continue;
                String text = URLDecoder.decode(line.substring(2), "UTF-8");
                if (text.trim().length() == 0 || text.length() > MAX_LEN) continue;
                c.items.add(new Item(text, line.charAt(0) == '1'));   // keeps saved order (newest first)
            } catch (Exception e) { /* skip bad line */ }
        }
        return c;
    }
}
