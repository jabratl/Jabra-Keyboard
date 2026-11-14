package com.mgb.keyboard.typing;

/**
 * v0.11.0 (M1 of the "App UI" plan): pure rules for the new app screen. No Android classes.
 * Home + 6 pages. Every old settings group goes to exactly one page (an unknown group name goes to the LAST page, so no setting is lost).
 * Also: tile summaries ("Height 120%, radius 6") and the 3 Quick looks (value tables for the same prefs as EdgePlan.PRESET_KEYS).
 */
public final class PagePlan {
    private PagePlan() { }

    public static final String HOME = "";
    public static final String TYPING = "typing", LOOK = "look", LAYOUT = "layout", KEYS = "keys", CLIP = "clip", DATA = "data", TOOLS = "tools";   // v0.15.3 (N3): TOOLS added at the END so saved ids / tile_order stay valid

    /** Tile order on Home. */
    public static final String[] ORDER = { TYPING, LOOK, LAYOUT, KEYS, CLIP, DATA, TOOLS };
    public static final String[] TITLES = { "Typing", "Look & Theme", "Layout & Size", "Keys & Gestures", "Clipboard & Tools", "Data & About", "Tools & Panels" };
    /** Icon names that exist in JabraIcons.NAMES. */
    public static final String[] ICONS = { "sym", "theme", "size", "pad", "clip", "gear", "tt" };

    /** Old group names (SettingsGroups) per page, same order as ORDER. Languages rows go into Typing. */
    private static final String[][] GROUPS = {
        { "Languages & layouts", "Typing" },
        { "Look" },
        { "Layout & size" },
        { "Keys & gestures" },
        { "Clipboard & tools" },
        { "Data & Backup" },
        { "Tools & Panels" },   // v0.15.3 (N3)
    };

    public static int index(String id) {
        if (id == null) return -1;
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i].equals(id)) return i;
        return -1;
    }

    /** Unknown id -> HOME (so a wrong saved page never opens a broken screen). */
    public static String sanitize(String id) { return index(id) >= 0 ? id : HOME; }

    public static String title(String id) { int i = index(id); return i < 0 ? "Jabra Keyboard" : TITLES[i]; }
    public static String icon(String id) { int i = index(id); return i < 0 ? "gear" : ICONS[i]; }

    public static String[] groupsOf(String id) { int i = index(id); return i < 0 ? new String[0] : GROUPS[i].clone(); }

    /** Page that shows an old group. Unknown name -> last page (DATA). */
    public static String pageOfGroup(String group) {
        if (group != null) for (int i = 0; i < GROUPS.length; i++) for (String g : GROUPS[i]) if (g.equals(group)) return ORDER[i];
        return DATA;
    }

    /** The live preview card stays pinned on Home and on the two looks / sizes pages only. */
    public static boolean pinPreview(String id) { return HOME.equals(sanitize(id)) || LOOK.equals(id) || LAYOUT.equals(id); }

    // ---- tile summaries (one short line, from plain values the caller reads from prefs) ----
    private static String onOff(boolean b) { return b ? "on" : "off"; }

    public static String layoutSummary(int heightPct, int radiusDp) { return "Height " + heightPct + "%, radius " + radiusDp; }
    public static String lookSummary(String themeName, boolean jabraLook) { return (themeName == null || themeName.isEmpty() ? "Classic" : themeName) + ", " + (jabraLook ? "Jabra look" : "Classic look"); }
    public static String typingSummary(boolean probhat, boolean spaceSwipe) { return LangNames.summary(probhat) + ", space swipe " + onOff(spaceSwipe); }
    public static String keysSummary(boolean vibrate, boolean sound, boolean popup) { return "Vibrate " + onOff(vibrate) + ", sound " + onOff(sound) + ", popup " + onOff(popup); }
    public static String clipSummary(boolean history, boolean editBar) { return "History " + onOff(history) + ", edit bar " + onOff(editBar); }
    public static String toolsSummary(boolean editBar, boolean formatter) { return "Edit bar " + onOff(editBar) + ", formatter " + onOff(formatter); }   // v0.15.3 (N3)
    public static String dataSummary(boolean autoBackup, String version) { return "Auto backup " + onOff(autoBackup) + ", v" + (version == null ? "?" : version); }

    // ---- Quick looks: tables over EdgePlan.PRESET_KEYS ----
    public static final String QL_RIDMIK = "ridmik", QL_COMPACT = "compact", QL_LARGE = "large";
    public static final String[] QUICK = { QL_RIDMIK, QL_COMPACT, QL_LARGE };
    public static final String[] QUICK_LABELS = { "Ridmik-style", "Compact", "Large keys" };

    public static String quickLabel(String id) {
        for (int i = 0; i < QUICK.length; i++) if (QUICK[i].equals(id)) return QUICK_LABELS[i];
        return "";
    }

    /**
     * New values for the keys of EdgePlan.PRESET_KEYS. [cur] has the same length (-1 = unset). Ridmik = the whole preset.
     * Compact / Large change only height (portrait, landscape), key gap and edge; everything else stays as it is.
     * Wrong length or unknown id -> null (nothing is written).
     */
    public static int[] quickValues(String id, int[] cur) {
        int n = EdgePlan.PRESET_KEYS.length;
        if (id == null || cur == null || cur.length != n) return null;
        int[] out = cur.clone();
        if (QL_RIDMIK.equals(id)) return EdgePlan.RIDMIK.clone();
        if (QL_COMPACT.equals(id)) { out[0] = 90; out[1] = 85; out[2] = 2; out[3] = 1; return out; }
        if (QL_LARGE.equals(id)) { out[0] = 135; out[1] = 115; out[2] = 3; out[3] = 2; return out; }
        return null;
    }

    // ---- v0.17.79 (U4): changed-count badge on the Home tiles ----
    /** Number of changed rows per page, same order as ORDER. [groups] = old group name of each row, [changed] = its changed flag (shorter list wins; null = zeros). */
    public static int[] changedByPage(java.util.List<String> groups, boolean[] changed) {
        int[] out = new int[ORDER.length];
        if (groups == null || changed == null) return out;
        int n = Math.min(groups.size(), changed.length);
        for (int i = 0; i < n; i++) if (changed[i]) out[index(pageOfGroup(groups.get(i)))]++;
        return out;
    }

    /** Badge text: "" when nothing changed (badge hidden), else "3 changed". */
    public static String badge(int n) { return n <= 0 ? "" : n + " changed"; }

    /** Screen reader text of a tile: title, summary, changed count. */
    public static String tileDescription(String id, String summary, int changed) {
        String s = title(id) + ". " + (summary == null ? "" : summary);
        return changed > 0 ? s + ", " + changed + (changed == 1 ? " setting changed" : " settings changed") : s;
    }
}
