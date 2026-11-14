package com.mgb.keyboard.typing;

/**
 * v0.17.76 (U2): pure rules for the look of a settings row (no Android). Tested by tools/RowPlanTest.java.
 * choiceRow: few + short options = one-tap segmented control; many or long options = a value chip that opens a pick list (so nothing is squeezed or cut).
 */
public final class RowPlan {
    private RowPlan() { }

    public static final int SEG_MAX_OPTIONS = 4, SEG_MAX_TOTAL_CHARS = 30, SEG_MAX_ONE_CHARS = 14;

    /** true = draw as a segmented control; false = value chip + pick list. null / empty = false. */
    public static boolean segmented(String[] names) {
        if (names == null || names.length < 2 || names.length > SEG_MAX_OPTIONS) return false;
        int total = 0;
        for (String n : names) {
            int l = n == null ? 0 : n.length();
            if (l > SEG_MAX_ONE_CHARS) return false;
            total += l;
        }
        return total <= SEG_MAX_TOTAL_CHARS;
    }

    /** Index kept inside the list (bad value -> 0, empty list -> 0). */
    public static int clamp(int i, int n) { return n <= 0 ? 0 : (i < 0 ? 0 : (i >= n ? n - 1 : i)); }

    /** Text read by TalkBack for one option of a segmented control. */
    public static String optionDesc(String name, boolean selected) { return (name == null ? "" : name) + (selected ? ", selected" : ""); }
}
