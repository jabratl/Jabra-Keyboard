package com.mgb.keyboard.layout;

import java.util.List;

/**
 * v0.17.14 (K9b) ready-made layouts the user can start from (pure Java, no Android, tested by tools/LayoutPresetsTest.java).
 * Every preset is today's default layout ({@link LayoutDefaults}) plus a few small, safe changes made on a copy.
 * build(i) returns null on any problem (the app then shows "preset not available"); it never throws.
 * Applying a preset = "Save as" a NEW named layout (LayoutBook.uniqueName); an existing layout is never overwritten.
 * Only action ids and @actions that the keyboard really runs are used (ActionPlan); the tests check this.
 */
public final class LayoutPresets {
    private LayoutPresets() { }

    public static final int COUNT = 6;   // v0.17.23 (L5c): + Ridmik symbols
    private static final String[] TITLES = { "Classic (today)", "Ridmik-like + Tab", "Coder", "Big keys", "Bangla fast", "Ridmik symbols" };
    private static final String[] NOTES = {
            "Exactly today's keyboard (default settings).",
            "Ridmik-style bottom row (wide ?123 and Enter) and a Tab key next to the comma.",
            "No number row, Tab key, swipe DOWN on a / z / x / c / v = Select all / Undo / Cut / Copy / Paste.",
            "All rows 25 % taller, easier to hit.",
            "Bangla (fixed) gets one extra row: danda, taka and the Bangla digits 0-9.",
            "Symbols page 1 like Ridmik: Tab ~ \\ < > _ = [ ] / @ # taka % & * - + ( ) / ! \" ' : ; / ?. Letters unchanged." };   // v0.17.23 (L5c)

    public static String title(int i) { return i >= 0 && i < COUNT ? TITLES[i] : ""; }

    public static String note(int i) { return i >= 0 && i < COUNT ? NOTES[i] : ""; }

    /** The preset as a layout, or null (unknown index / any problem / the result does not pass the load gate). Never throws. */
    public static LayoutDef build(int i) {
        try {
            if (i < 0 || i >= COUNT) return null;
            LayoutDef m;
            switch (i) {
                case 0: m = LayoutDefaults.build(); break;
                case 1: m = ridmik(); break;
                case 2: m = coder(); break;
                case 3: m = big(); break;
                case 5: m = ridmikSymbols(); break;   // v0.17.23 (L5c)
                default: m = banglaFast(); break;
            }
            if (m == null || LayoutOps.valid(m) != null || LayoutOps.fatal(m).size() > 0) return null;
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    /** The preset as layout text (JKL1 header carries the title), or null. */
    public static String text(int i) {
        try {
            LayoutDef m = build(i);
            String t = m == null ? null : LayoutCodec.encode(m);
            return t == null ? null : LayoutBook.withName(t, title(i));
        } catch (Throwable e) {
            return null;
        }
    }

    // ---------- the presets ----------
    private static LayoutDef ridmik() {
        LayoutDefaults.Options o = new LayoutDefaults.Options();
        o.botStyle = 1;
        return addTab(LayoutDefaults.build(o));
    }

    private static LayoutDef coder() {
        LayoutDefaults.Options o = new LayoutDefaults.Options();
        o.numRow = false;
        LayoutDef m = addTab(LayoutDefaults.build(o));
        String[][] sw = { { "en_a", "@selall" }, { "en_z", "@undo" }, { "en_x", "@cut" }, { "en_c", "@copy" }, { "en_v", "@paste" } };
        for (String[] s : sw) m = LayoutOps.setSwipe(m, s[0], 1, s[1]);   // 1 = down
        return m;
    }

    private static LayoutDef big() {
        LayoutDef m = LayoutDefaults.build().copy();
        for (LayerDef l : m.layers) for (RowDef r : l.rows) {
            int h = Math.round(r.heightPct * 1.25f);
            r.heightPct = Math.max(RowDef.H_MIN, Math.min(RowDef.H_MAX, h));
        }
        return LayoutOps.normalize(m);
    }

    private static LayoutDef banglaFast() {
        LayoutDef m = LayoutDefaults.build().copy();
        LayerDef pb = m.layer("pb");
        if (pb == null || pb.rows.size() < 2) return null;
        String[] signs = { "\u0964", "\u09F3", "\u09E6", "\u09E7", "\u09E8", "\u09E9", "\u09EA", "\u09EB", "\u09EC", "\u09ED", "\u09EE", "\u09EF" };
        RowDef r = new RowDef();
        r.heightPct = 100;
        for (int i = 0; i < signs.length; i++) r.keys.add(KeyDef.ch("pb_f" + (i + 1), signs[i], 1f));
        pb.rows.add(pb.rows.size() - 1, r);   // before the shared bottom row
        return LayoutOps.normalize(m);
    }

    /** v0.17.23 (L5c): symbols page 1 in the Ridmik order (video reference): [Tab ~ \\ < > _ = [ ]] / [@ # taka % & * - + ( )] / [page ! " ' : ; / ? backspace]. Letters, bottom row and pages 2 / 3 stay as in the default. */
    private static LayoutDef ridmikSymbols() {
        LayoutDef m = LayoutDefaults.build().copy();
        LayerDef s1 = m.layer("s1");
        if (s1 == null || s1.rows.size() < 3) return null;
        List<KeyDef> r1 = s1.rows.get(0).keys;
        r1.clear();
        KeyDef tab = KeyDef.act("s1_tab", "tab", 1f);
        tab.label[0] = "Tab";
        r1.add(tab);
        String[] a = { "~", "\\", "<", ">", "_", "=", "[", "]" };
        for (int i = 0; i < a.length; i++) r1.add(KeyDef.ch("s1_r1_" + (i + 2), a[i], 1f));
        String[] b = { "@", "#", "\u09F3", "%", "&", "*", "-", "+", "(", ")" };
        if (!setChars(s1.rows.get(1).keys, b)) return null;
        String[] c = { "!", "\"", "'", ":", ";", "/", "?" };
        List<KeyDef> r3 = s1.rows.get(2).keys;
        int n = 0;
        for (KeyDef k : r3) if (k.kind == KeyDef.CHAR) n++;
        if (n != c.length) return null;
        int j = 0;
        for (KeyDef k : r3) if (k.kind == KeyDef.CHAR) { k.out[0] = c[j]; k.label[0] = ""; j++; }
        return LayoutOps.normalize(m);
    }

    /** Sets the output of the CHAR keys of a row in order (false when the count differs). */
    private static boolean setChars(List<KeyDef> keys, String[] outs) {
        int n = 0;
        for (KeyDef k : keys) if (k.kind == KeyDef.CHAR) n++;
        if (n != outs.length) return false;
        int j = 0;
        for (KeyDef k : keys) if (k.kind == KeyDef.CHAR) { k.out[0] = outs[j]; k.label[0] = ""; j++; }
        return true;
    }

    /** A Tab key (action "tab") before the comma of the shared bottom row; the space bar gives up the same width (never below 2). */
    private static LayoutDef addTab(LayoutDef base) {
        LayoutDef m = base.copy();
        LayerDef bot = m.layer("bot");
        if (bot == null || bot.rows.size() == 0) return null;
        List<KeyDef> keys = bot.rows.get(0).keys;
        int at = -1;
        KeyDef space = null;
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).id.equals("b_comma")) at = i;
            if (keys.get(i).id.equals("b_space")) space = keys.get(i);
        }
        if (at < 0 || space == null) return null;
        KeyDef tab = KeyDef.act("b_tab", "tab", 1f);
        tab.label[0] = "Tab";
        keys.add(at, tab);
        space.weight = Math.max(2f, Math.round((space.weight - 1f) * 100f) / 100f);
        return LayoutOps.normalize(m);
    }
}
