package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * v0.35.1 (CC3-B): pure model of the clipboard popover menu (More in select mode + the 3-dot header menu).
 * No Android here, so it is tested on the computer (tools/ClipPopTest.java). The popover window itself is ClipPopover.kt.
 * Every action of the OLD trays has an id here (nothing may get lost): the Kotlin side maps each id to the SAME old handler.
 */
public final class ClipPop {
    private ClipPop() { }

    public static final String UNDO = "undo";
    public static final String CLEAR = "clear";
    public static final int GUARD_MS = 250;

    // v0.36.5: smaller popover (was width 200 / row 40 / text 14 / icon 18 = the 3-dot menu was ~470 dp tall, nearly the whole screen).
    public static final int WIDTH_DP = 168;
    public static final int ROW_DP = 34;
    public static final int TEXT_SP = 13;
    public static final int VALUE_SP = 12;
    public static final int ICON_DP = 16;
    public static final int PAD_V_DP = 3;
    public static final int PAD_L_DP = 12;
    public static final int PAD_R_DP = 10;

    public static final class Item {
        public final String id, label, icon, value;
        public final int group;
        public final boolean ticked, danger;
        Item(String id, String label, String icon, int group, boolean ticked, boolean danger, String value) {
            this.id = id; this.label = label; this.icon = icon; this.group = group; this.ticked = ticked; this.danger = danger; this.value = value;
        }
    }

    /** Select-mode More: [All, None] [Edit, Replace, Merge, Unpin] [Paste 1 line, To Vault (only if it exists), Export]. */
    public static List<Item> moreItems(boolean hasVault) {
        List<Item> l = new ArrayList<>();
        l.add(new Item("all", "All", "all", 0, false, false, ""));
        l.add(new Item("none", "None", "selnone", 0, false, false, ""));
        l.add(new Item("edit", "Edit", "edit", 1, false, false, ""));
        l.add(new Item("replace", "Replace", null, 1, false, false, ""));
        l.add(new Item("merge", "Merge", null, 1, false, false, ""));
        l.add(new Item("unpin", "Unpin", "pin", 1, false, false, ""));
        l.add(new Item("paste1", "Paste 1 line", "paste", 2, false, false, ""));
        if (hasVault) l.add(new Item("vault", "To Vault", "vault", 2, false, false, ""));
        l.add(new Item("export", "Export", null, 2, false, false, ""));
        return l;
    }

    private static final String[] SORT = { "Newest", "Oldest", "A-Z", "Used" };

    /** Header 3-dot menu. Sort + View only when the dropdown pills are OFF (same rule as the old tray). Clear is last + danger. */
    public static List<Item> headerItems(int sortLv, boolean cardsOn, boolean pillOn, int undoLim, int deletedN) {
        List<Item> l = new ArrayList<>();
        int s = Math.max(0, Math.min(3, sortLv));
        int g = 0;
        if (!pillOn) {
            for (int i = 0; i < 4; i++) l.add(new Item("sort" + i, SORT[i], "sort", 0, i == s, false, ""));
            l.add(new Item("view0", "List", null, 1, !cardsOn, false, ""));
            l.add(new Item("view1", "Cards", null, 1, cardsOn, false, ""));
            g = 2;
        }
        l.add(new Item("manage", "Manage", "checkon", g, false, false, ""));
        if (undoLim > 0) l.add(new Item("deleted", deletedN > 0 ? "Deleted " + deletedN : "Deleted", "trash", g, false, false, ""));
        l.add(new Item("exportall", "Export all", null, g, false, false, ""));
        l.add(new Item(UNDO, "Undo kept", "undo", g + 1, false, false, undoLim <= 0 ? "Off" : String.valueOf(undoLim)));
        l.add(new Item(CLEAR, "Clear", "trash", g + 2, false, true, ""));
        return l;
    }

    /** Next Undo-limit in the cycle Off / 10 / 30 / 50 / 100 (a custom value like 20 stays in the cycle, sorted). */
    public static int nextUndo(int cur, int[] quick) {
        List<Integer> v = new ArrayList<>();
        for (int q : quick) v.add(q);
        if (!v.contains(cur)) v.add(cur);
        java.util.Collections.sort(v);
        int i = v.indexOf(cur);
        return v.get((i + 1) % v.size());
    }

    /** Popover height in dp: the content, but never above the free space (minus 8) and never below one row. */
    public static int maxHeightDp(int spaceDp, int contentDp, int rowDp) {
        int max = spaceDp - 8;
        if (max < rowDp) max = rowDp;
        return Math.min(contentDp, max);
    }

    /** true = opening is allowed. false = the same tap that just closed the popover (within GUARD_MS) must not re-open it. */
    public static boolean reopenGuard(long now, long dismissedAt) {
        return dismissedAt <= 0 || now - dismissedAt > GUARD_MS || now < dismissedAt;
    }

    public static List<String> ids(List<Item> l) {
        List<String> r = new ArrayList<>();
        for (Item i : l) r.add(i.id);
        return r;
    }

    public static List<String> oldMoreIds() { return Arrays.asList("all", "none", "edit", "replace", "merge", "unpin", "paste1", "vault", "export"); }
    public static List<String> oldHeaderIds() { return Arrays.asList("manage", "deleted", "exportall", "undo", "clear"); }
}
