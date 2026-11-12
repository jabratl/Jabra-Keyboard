package com.mgb.keyboard.typing;

/**
 * v0.10.26 (P1 of the "Ridmik feel" plan): keyboard spacing rules. Pure Java, no Android.
 * Three NEW prefs (all unset = -1 = exactly the v0.10.25 look):
 *   edge_dp  = space between the screen edge and the first key, in dp (the keyboard side padding). Old fixed value: 2.
 *   row_vgap = up-down space between key rows, in dp. Unset = same as Key gap (old behaviour).
 *   fn_gap   = extra space between Shift / Backspace and the letters next to them, in dp. Unset = 0 (old behaviour).
 * Ridmik reference (measured on screenshots, 95% height there = 120% height in Jabra): key gap about 4 dp, edge about 3 dp,
 * Shift / Backspace gap about 8 dp (= key gap + 4), bottom row symmetrical (that part comes in P2).
 */
public final class EdgePlan {
    private EdgePlan() { }

    public static final int UNSET = -1;
    public static final int E_MIN = 0, E_MAX = 8, E_DEF = 2;
    public static final int V_MIN = 0, V_MAX = 8;
    public static final int F_MIN = 0, F_MAX = 8, F_DEF = 0;
    /** Old weight of the Shift and Backspace keys. */
    public static final float FN_BASE = 1.5f;
    public static final float FN_MIN = 1.1f;

    public static final String[] PRESET_KEYS = { "kh_pct_p", "kh_pct_l", "key_gap", "edge_dp", "row_vgap", "fn_gap" };
    /** Ridmik-style values, same order as PRESET_KEYS. 120 / 100 = the height the user already matched by hand. */
    public static final int[] RIDMIK = { 120, 100, 2, 1, 2, 4 };

    public static boolean edgeValid(int v) { return v >= E_MIN && v <= E_MAX; }
    public static boolean vValid(int v) { return v >= V_MIN && v <= V_MAX; }
    public static boolean fnValid(int v) { return v >= F_MIN && v <= F_MAX; }

    /** Side padding of the keyboard in dp; unset / invalid -> 2 (old). */
    public static int edgeDp(int v) { return edgeValid(v) ? v : E_DEF; }

    /** Up-down key margin in dp; unset / invalid -> the key gap (old: same margin on all four sides). */
    public static int vGapDp(int v, int keyGapDp) { return vValid(v) ? v : keyGapDp; }

    /** Extra margin beside Shift / Backspace in dp; unset / invalid -> 0 (old). */
    public static int fnGapDp(int v) { return fnValid(v) ? v : F_DEF; }

    /**
     * Weight of Shift / Backspace. With 0 extra gap = 1.5 (old). With an extra gap the weight is lowered a little so the
     * letters of this row keep the SAME width as the row above (the gap is taken from the two big keys).
     */
    public static float fnWeight(int fnGap, float screenDp, int edgeDp) {
        if (fnGap <= 0) return FN_BASE;
        float unit = (screenDp - 2f * edgeDp) / 10f;
        if (unit < 10f) return FN_BASE;
        float w = FN_BASE - fnGap / unit;
        return w < FN_MIN ? FN_MIN : w;
    }

    /** "120,100,2,1,2,4" (the old values, -1 = was unset) so one tap can undo the preset. */
    public static String encode(int[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) { if (i > 0) sb.append(','); sb.append(v[i]); }
        return sb.toString();
    }

    /** Back from encode(); wrong text or wrong count -> null (nothing is restored). */
    public static int[] decode(String s, int n) {
        if (s == null || s.length() == 0) return null;
        String[] p = s.split(",", -1);
        if (p.length != n) return null;
        int[] out = new int[n];
        try { for (int i = 0; i < n; i++) out[i] = Integer.parseInt(p[i].trim()); } catch (NumberFormatException e) { return null; }
        return out;
    }
}
