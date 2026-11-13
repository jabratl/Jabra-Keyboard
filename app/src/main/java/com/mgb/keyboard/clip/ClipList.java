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
    /** v0.16.15 (E3): texts COPIED in other apps are kept up to this length (the Add / Edit buttons still use MAX_LEN). */
    public static final int MAX_CAPTURE = 20000;
    /** v0.16.15 (E3): total characters of all UNPINNED clips; the oldest unpinned clips go first. Pinned clips are never touched. */
    public static final int MAX_UNPINNED_CHARS = 150000;

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


    /**
     * v0.16.15 (E3): like add(), but for text copied in other apps: up to MAX_CAPTURE characters, and the
     * oldest unpinned clips are dropped when all unpinned clips together pass MAX_UNPINNED_CHARS.
     * The newest clip is never dropped by the character budget. add() is unchanged (MAX_LEN).
     */
    public boolean addCaptured(String t) {
        if (t == null || t.trim().length() == 0 || t.length() > MAX_CAPTURE) return false;
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
        long chars = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).pinned) continue;
            if (++unpinned > MAX_UNPINNED) { items.remove(i); i--; } else chars += items.get(i).text.length();
        }
        for (int i = items.size() - 1; i > 0 && chars > MAX_UNPINNED_CHARS; i--) {
            Item x = items.get(i);
            if (!x.pinned) { chars -= x.text.length(); items.remove(i); }
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

    // ---- v0.2.5.1: new methods only (old ones above are untouched) ----

    private List<Item> group(String query, boolean pinned) {
        String q = query == null ? "" : query.toLowerCase();
        ArrayList<Item> out = new ArrayList<Item>();
        for (Item it : items) {
            if (it.pinned != pinned) continue;
            if (q.length() == 0 || it.text.toLowerCase().contains(q)) out.add(it);
        }
        return out;
    }

    /** Pinned items only, newest first, same search rule as view(). */
    public List<Item> viewPinned(String query) { return group(query, true); }

    /** Not-pinned items only, newest first, same search rule as view(). */
    public List<Item> viewRecent(String query) { return group(query, false); }

    public int pinnedCount() {
        int n = 0;
        for (Item x : items) if (x.pinned) n++;
        return n;
    }

    /** Pins (or unpins) many items. Pinning stops at MAX_PINNED. Returns how many really changed. */
    public int setPinned(List<Item> sel, boolean pin) {
        if (sel == null) return 0;
        int changed = 0;
        int n = pinnedCount();
        for (Item it : sel) {
            if (it == null || it.pinned == pin || !items.contains(it)) continue;
            if (pin) {
                if (n >= MAX_PINNED) break;
                it.pinned = true; n++; changed++;
            } else {
                it.pinned = false; n--; changed++;
            }
        }
        return changed;
    }

    /** Removes many items. Returns how many were removed. */
    public int removeAll(List<Item> sel) {
        if (sel == null) return 0;
        int n = 0;
        for (Item it : sel) if (it != null && items.remove(it)) n++;
        return n;
    }

    /**
     * v0.4.3.1: replaces the text of ONE clip, same position, same pinned flag.
     * 0 = ok (also when the text is unchanged), 1 = empty, 2 = too long, 3 = another clip already has this text, 4 = clip not found.
     */
    public int replace(Item it, String text) {
        int idx = items.indexOf(it);
        if (idx < 0) return 4;
        int c = check(text);
        if (c != 0) return c;
        for (int i = 0; i < items.size(); i++) if (i != idx && items.get(i).text.equals(text)) return 3;
        if (items.get(idx).text.equals(text)) return 0;
        items.set(idx, new Item(text, it.pinned));
        return 0;
    }

    /** Can this text be saved as a clip? 0 = ok, 1 = empty, 2 = longer than MAX_LEN. */
    public static int check(String t) {
        if (t == null || t.trim().length() == 0) return 1;
        if (t.length() > MAX_LEN) return 2;
        return 0;
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
                if (text.trim().length() == 0 || text.length() > MAX_CAPTURE) continue;   // v0.16.15 (E3): was MAX_LEN, long copied clips must survive a restart
                c.items.add(new Item(text, line.charAt(0) == '1'));   // keeps saved order (newest first)
            } catch (Exception e) { /* skip bad line */ }
        }
        return c;
    }
}
