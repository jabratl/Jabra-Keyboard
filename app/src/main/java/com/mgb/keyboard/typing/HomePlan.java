package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * v0.13.0 (M3 of the "App UI" plan): pure rules for the new Home. No Android classes.
 *  - DEFAULT_APP_UI: which app screen opens when pref app_ui is not set (0 = old, 1 = new). v0.14.1: now 1 (phone checklist Z71-Z74 green).
 *  - Panes: width >= 600 dp = two panes (Home on the left, the open page on the right). Landscape phones are usually wider than 600 dp too.
 *  - Global search: every row of every page, grouped by page (order of PagePlan.ORDER), best title match first.
 *  - Lists on Home: recently changed (pref set_recent) and changed-from-default.
 */
public final class HomePlan {
    private HomePlan() { }

    /** v0.14.1: flipped to 1 (new screen) after the phone checklist Z71-Z74 was green. Back = change this ONE number to 0. A saved pref app_ui (0 or 1) always wins. */
    public static final int DEFAULT_APP_UI = 1;

    // ---- panes ----
    public static final int PANE_MIN_DP = 600, SHORT_DP = 480, LEFT_MIN_DP = 300, LEFT_MAX_DP = 380, RIGHT_MIN_DP = 300;
    public static final int PV_PIN = 0, PV_INLINE = 1;

    public static boolean twoPane(int widthDp) { return widthDp >= PANE_MIN_DP; }

    /** Width of the left pane (Home) in dp: 38% of the screen, between 300 and 380, and the right pane keeps at least 300. */
    public static int leftPaneDp(int widthDp) {
        int v = Math.round(widthDp * 0.38f);
        v = Math.max(LEFT_MIN_DP, Math.min(LEFT_MAX_DP, v));
        int room = widthDp - RIGHT_MIN_DP;
        if (room < v) v = Math.max(0, room);
        return v;
    }

    /** One pane: the preview card is pinned above the pages (as in M1). Two panes on a short screen (landscape phone): the preview scrolls inside the page, so the rows keep room. */
    public static int previewMode(boolean two, int heightDp) { return (two && heightDp < SHORT_DP) ? PV_INLINE : PV_PIN; }

    /** First page shown in the right pane (two panes have no Home page). */
    public static String firstPage(String saved) {
        String s = PagePlan.sanitize(saved);
        return s.isEmpty() ? PagePlan.LAYOUT : s;
    }

    // ---- search ----
    public static final int MAX_RESULTS = 40;

    /** Results of one page. [page] is a PagePlan id. */
    public static final class Group {
        public final String page;
        public final List<SettingsIndex.Entry> items = new ArrayList<>();
        Group(String page) { this.page = page; }
    }

    public static String pageOf(SettingsIndex.Entry e) { return e == null ? PagePlan.DATA : PagePlan.pageOfGroup(e.group); }

    private static int rank(SettingsIndex.Entry e, String firstWord) {
        String t = e.title.toLowerCase(java.util.Locale.ROOT);
        if (firstWord.isEmpty()) return 2;
        if (t.startsWith(firstWord)) return 0;
        if (t.contains(firstWord)) return 1;
        return 2;
    }

    /**
     * Rows that match [query] (same word rule as the page search), grouped by page in tile order, best title match first, original order kept
     * inside the same rank. Empty query = empty result. At most MAX_RESULTS rows in total; pages without a hit are left out.
     */
    public static List<Group> searchByPage(List<SettingsIndex.Entry> all, String query) {
        List<Group> out = new ArrayList<>();
        if (all == null || query == null || query.trim().isEmpty()) return out;
        List<SettingsIndex.Entry> hits = SettingsIndex.search(all, query);
        String q = query.trim().toLowerCase(java.util.Locale.ROOT);
        String first = q.split("\\s+")[0];
        int left = MAX_RESULTS;
        for (String page : PagePlan.ORDER) {
            if (left <= 0) break;
            Group g = new Group(page);
            for (int r = 0; r <= 2; r++) {
                for (SettingsIndex.Entry e : hits) {
                    if (left <= 0) break;
                    if (!page.equals(pageOf(e)) || rank(e, first) != r) continue;
                    g.items.add(e);
                    left--;
                }
            }
            if (!g.items.isEmpty()) out.add(g);
        }
        return out;
    }

    public static int count(List<Group> groups) {
        int n = 0;
        if (groups != null) for (Group g : groups) n += g.items.size();
        return n;
    }

    /** One line above the results. */
    public static String resultLine(int n, String query) {
        String q = query == null ? "" : query.trim();
        if (n <= 0) return "No setting found for \u201C" + q + "\u201D. Try another word.";
        return n == 1 ? "1 setting" : n + " settings";
    }

    /** "Vibrate  ·  ON" style label for screen readers and rows. Empty value = title only. */
    public static String withValue(String title, String value) {
        String t = title == null ? "" : title;
        if (value == null || value.isEmpty()) return t;
        return t + ", " + value;
    }

    // ---- lists on Home ----
    /** Keys of pref set_recent that still exist as rows (newest first). */
    public static List<String> recentKeys(String csv, Collection<String> existing) {
        List<String> out = new ArrayList<>();
        if (existing == null) return out;
        for (String k : SettingsIndex.parseRecent(csv)) if (existing.contains(k)) out.add(k);
        return out;
    }

    /** Keys whose [changed] flag is true, in the given (page) order. Lists of different length: only the common part is used. */
    public static List<String> changedKeys(List<String> keys, boolean[] changed) {
        List<String> out = new ArrayList<>();
        if (keys == null || changed == null) return out;
        int n = Math.min(keys.size(), changed.length);
        for (int i = 0; i < n; i++) if (changed[i]) out.add(keys.get(i));
        return out;
    }

    public static String changedTitle(int n, boolean open) { return (open ? "\u25BC  " : "\u25B6  ") + "Changed from default (" + Math.max(0, n) + ")"; }

    /** Scroll position that shows a row with a little room above it. */
    public static int revealY(int rowTopPx, int padPx) { return Math.max(0, rowTopPx - Math.max(0, padPx)); }

    // ---- v0.17.79 (U4): ONE status card on Home. Priority: finish setup > crash guard > old backup > nothing. ----
    public static final int ST_NONE = 0, ST_SETUP = 1, ST_GUARD = 2, ST_BACKUP = 3;

    /** Which single status Home shows. setupPending wins (nothing else matters before the keyboard is on); a non-empty note means "has a warning". */
    public static int status(boolean setupPending, String guardNote, String backupNote) {
        if (setupPending) return ST_SETUP;
        if (guardNote != null && !guardNote.isEmpty()) return ST_GUARD;
        if (backupNote != null && !backupNote.isEmpty()) return ST_BACKUP;
        return ST_NONE;
    }

    public static String statusTitle(int st) {
        if (st == ST_SETUP) return "Finish setup";
        if (st == ST_GUARD) return "A keyboard panel is switched off";
        if (st == ST_BACKUP) return "Backup needs attention";
        return "";
    }

    /** Button text of the warning card ("" = no button). */
    public static String statusAction(int st) {
        if (st == ST_GUARD) return "Switch it on again";
        if (st == ST_BACKUP) return "Open Data & Backup";
        return "";
    }

    public static String statusText(int st, String guardNote, String backupNote) {
        if (st == ST_GUARD) return guardNote == null ? "" : guardNote;
        if (st == ST_BACKUP) return backupNote == null ? "" : backupNote;
        return "";
    }
}
