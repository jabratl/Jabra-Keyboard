package com.mgb.keyboard.typing;

/**
 * v0.17.36 (M3): pure rule for the cursor pad buttons "Top" / "Bottom" (jump to the start / end of the WHOLE text).
 * Select OFF: the cursor goes to 0 / len (no selection).
 * Select ON : TOP selects from the current position to the start = (0, selEnd); BOTTOM selects from the current
 *             position to the end = (selStart, len). With nothing selected selStart == selEnd == cursor, with an existing
 *             selection the far end stays. Result is always (start, end) with start <= end, clamped to 0..len.
 * No Android classes (unit tested on a computer).
 */
public final class PadJump {
    private PadJump() { }

    public static int[] jump(int len, int selStart, int selEnd, boolean top, boolean selectOn) {
        int n = Math.max(0, len);
        int a = clamp(Math.min(selStart, selEnd), n);
        int b = clamp(Math.max(selStart, selEnd), n);
        if (!selectOn) { int p = top ? 0 : n; return new int[] { p, p }; }
        return top ? new int[] { 0, b } : new int[] { a, n };
    }

    /** v0.45.1: hop size (chars asked per step) and the most steps for padHop in MgbIme.kt (400 x 30000 = 12 million chars). */
    public static final int HOP_CHARS = 30000, HOP_MAX = 400;

    /** v0.45.1: selection (start <= end) in the WHOLE text, from a text window that starts at offset. null = the app does not say (any negative value). */
    public static int[] span(int offset, int s, int e) {
        if (offset < 0 || s < 0 || e < 0) return null;
        return new int[] { offset + Math.min(s, e), offset + Math.max(s, e) };
    }

    private static int clamp(int v, int n) { return v < 0 ? 0 : (v > n ? n : v); }
}
