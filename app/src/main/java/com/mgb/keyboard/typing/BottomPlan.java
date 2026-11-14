package com.mgb.keyboard.typing;

/**
 * v0.10.27 (P2 of the "Ridmik feel" plan): bottom row widths + Probhat home row gap. Pure Java, no Android.
 * Prefs (all default = old look): bot_style (0 old / 1 Ridmik-like), pb_home_gap (bool, Probhat home row half-key gap), key_dots (bool, "..." marks).
 * Ridmik bottom row (measured): [?123 1.55][globe 1][comma 1][space 3.3][period 1][emoji 1][enter 1.55]: ?123 and Enter have the SAME width.
 * Old Jabra: ?123 1.2, EN key 1.3, space 4, Enter 1.4.
 */
public final class BottomPlan {
    private BottomPlan() { }

    public static final int OLD = 0, RIDMIK = 1;
    /** Sum of all weights of the old bottom row (with Emoji key shown): keeps the row as wide as before. */
    public static final float TOTAL = 10.6f;
    public static final float SPACE_OLD = 4f, SPACE_MIN = 2f, SPACE_MAX = 4.5f;

    /** v0.17.1 (K1): with the "?" / danda key hidden the space bar may grow by one key width (old style: 4 -> 5, Ridmik style: cap 4.5 -> 5.5). */
    public static final float SPACE_MAX_NO_QM = 5.5f;
    public static float spaceMax(boolean qmShown) { return qmShown ? SPACE_MAX : SPACE_MAX_NO_QM; }
    public static float spaceOld(boolean qmShown) { return qmShown ? SPACE_OLD : SPACE_OLD + 1f; }

    public static int style(int v) { return v == RIDMIK ? RIDMIK : OLD; }
    public static boolean ridmik(int v) { return v == RIDMIK; }

    public static float symWeight(int v) { return ridmik(v) ? 1.55f : 1.2f; }
    public static float enterWeight(int v) { return ridmik(v) ? 1.55f : 1.4f; }
    public static float langWeight(int v) { return ridmik(v) ? 1f : 1.3f; }

    /** Space weight: old = 4. Ridmik-like = what is left of TOTAL after all other visible keys (othersSum), kept in 2..4.5. */
    public static float spaceWeight(int v, float othersSum) { return spaceWeight(v, othersSum, true); }

    /** v0.17.1 (K1): same, with the "?" key shown (true = exactly the old numbers) or hidden (false = space takes the freed width, other keys keep their width). */
    public static float spaceWeight(int v, float othersSum, boolean qmShown) {
        if (!ridmik(v)) return spaceOld(qmShown);
        float w = TOTAL - othersSum;
        float max = spaceMax(qmShown);
        return w < SPACE_MIN ? SPACE_MIN : (w > max ? max : w);
    }

    /** Probhat home row with the gap: 9 letters + the extra key (weight 0.5) at the right, a half-key gap at the left. Needs row_gap ON too. */
    public static boolean pbGap(boolean pb, boolean gapOn, boolean rowGapOn) { return pb && gapOn && rowGapOn; }
    public static float pbKeyWeight(boolean pb, boolean gapOn, boolean rowGapOn) { return pbGap(pb, gapOn, rowGapOn) ? 0.5f : 1f; }
    /** Left free space visible: English / Avro always (old rule), Probhat only with pbGap. */
    public static boolean leftGapShown(boolean pb, boolean gapOn, boolean rowGapOn) { return rowGapOn; }   // v0.17.69 (S2): Ridmik home row has no extra key any more: Probhat gets the half-key gap on BOTH sides like English / Avro
    /** Right free space visible: English / Avro only (in Probhat the extra key sits there). */
    public static boolean rightGapShown(boolean pb, boolean rowGapOn) { return rowGapOn; }   // v0.17.69 (S2)
}
