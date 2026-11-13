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

    private static int clamp(int v, int n) { return v < 0 ? 0 : (v > n ? n : v); }
}
