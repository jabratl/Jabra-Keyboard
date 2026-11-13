package com.mgb.keyboard.layout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * v0.17.20 (L5a) pure rules of the long-press OPTIONS bar (no Android): which texts a key offers, where the bar sits,
 * which cell is under the finger, and how the editor field text maps to KeyDef.lpList. The Kotlin part (LpBar / CustomRows) only draws and calls these.
 */
public final class LpBarPlan {
    private LpBarPlan() { }

    /** A bar is used from this many options; 0 or 1 option = the old single long-press text. */
    public static final int MIN_OPTIONS = 2;
    /** Max chars of the editor field (items are separated by spaces). */
    public static final int MAX_TEXT = 70;

    /** The options of a key: long-press text first, then the list; empty items and duplicates dropped; at most LayoutText.MAX_LPLIST. */
    public static List<String> options(KeyDef k) {
        List<String> out = new ArrayList<String>();
        if (k == null) return out;
        Set<String> seen = new LinkedHashSet<String>();
        if (k.lp != null && k.lp.length() > 0) seen.add(k.lp);
        if (k.lpList != null) for (String s : k.lpList) if (s != null && s.length() > 0) seen.add(s);
        for (String s : seen) { if (out.size() >= LayoutText.MAX_LPLIST) break; out.add(s); }
        return out;
    }

    public static boolean useBar(KeyDef k) { return options(k).size() >= MIN_OPTIONS; }

    /** Cell width in px: at least the key width and 40 dp, shrunk so that all cells fit on the screen. */
    public static int cell(int keyW, float density, int count, int screenW) {
        int c = Math.max(keyW, Math.round(40f * density));
        if (count > 0 && screenW > 0 && (long) c * count > screenW) c = screenW / count;
        return Math.max(c, 1);
    }

    /** Left edge of the bar: the first cell sits above the key, the bar is kept on the screen. */
    public static int left(int keyLeft, int keyW, int cell, int count, int screenW) {
        int total = cell * Math.max(count, 0);
        int l = keyLeft + keyW / 2 - cell / 2;
        if (l + total > screenW) l = screenW - total;
        if (l < 0) l = 0;
        return l;
    }

    /** Cell under the finger (raw screen x); left / right of the bar = first / last cell. */
    public static int indexAt(float rawX, int left, int cell, int count) {
        if (count <= 0 || cell <= 0) return 0;
        int i = (int) Math.floor((rawX - left) / (double) cell);
        return Math.max(0, Math.min(count - 1, i));
    }

    // v0.17.44 (P1): 6 or more options = 2-row grid (Ridmik style). Option 0 sits in the BOTTOM row at the key, the rest fill the top row.
    public static final int GRID_MIN = 6;

    public static int rows(int count) { return count >= GRID_MIN ? 2 : 1; }

    /** Cells per row (the bottom row is always full; the top row may have fewer). */
    public static int perRow(int count) { int r = rows(count); return r <= 1 ? Math.max(count, 0) : (count + r - 1) / r; }

    /** Option index under the finger in a 2-row grid (raw screen x / y). Below the grid = bottom row, above = top row; columns are clamped. */
    public static int indexAtGrid(float rawX, float rawY, int left, int top, int cell, int rowH, int perRow, int count) {
        if (count <= 0 || cell <= 0 || rowH <= 0 || perRow <= 0) return 0;
        int c = Math.max(0, Math.min(perRow - 1, (int) Math.floor((rawX - left) / (double) cell)));
        int r = Math.max(0, Math.min(1, (int) Math.floor((rawY - top) / (double) rowH)));
        int i = r == 1 ? c : perRow + c;
        return Math.max(0, Math.min(count - 1, i));
    }

    /** Text drawn in a cell: an @action shows its name without "@". */
    public static String shown(String item) {
        if (item == null) return "";
        return item.length() > 1 && item.charAt(0) == '@' ? item.substring(1) : item;
    }

    /** Editor field text -> list: split at white space, each item cleaned (R3), empty dropped, at most MAX_LPLIST. */
    public static List<String> parse(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        for (String p : text.trim().split("\\s+")) {
            String t = LayoutText.cleanSwipe(p, LayoutText.MAX_LP);
            if (t.length() > 0 && out.size() < LayoutText.MAX_LPLIST) out.add(t);
        }
        return out;
    }

    public static String join(List<String> l) {
        if (l == null) return "";
        StringBuilder b = new StringBuilder();
        for (String s : l) { if (b.length() > 0) b.append(' '); b.append(s); }
        return b.toString();
    }
}
