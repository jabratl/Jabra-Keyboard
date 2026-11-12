package com.mgb.keyboard.typing;

/**
 * v0.10.11 (D2.1) PanelSpec: the ONE spacing / height / text-size scale of the keyboard panels (pure Java, no Android).
 * Values are the ones the panels already use today (handoff section 4), written down once so UiKit and the panels
 * stop each keeping their own numbers. Mirrors JabraTokens (S1..S4, GAP, T_*): if one changes, change both.
 * Nothing here is stored, no pref, no permission. Delete this file = nothing else breaks (UiKit keeps its own copy of the numbers as fallback).
 */
public final class PanelSpec {
    private PanelSpec() { }

    // spacing scale (dp)
    public static final int GAP = 2;    // margin around panel buttons
    public static final int S1 = 4;
    public static final int S2 = 8;
    public static final int S3 = 12;
    public static final int S4 = 16;

    // control heights (dp)
    public static final int H_BTN = 40;   // normal panel button / header
    public static final int H_TAB = 38;   // tab row (Text tools)
    public static final int H_EDIT = 36;  // edit-bar keys
    public static final int H_OPT = 32;   // option row
    public static final int H_CHIP = 30;  // choice chip

    // text sizes (sp)
    public static final float T_NOTE = 11f;
    public static final float T_BTN = 12f;
    public static final float T_BODY = 14f;
    public static final float T_HEAD = 16f;

    /** Smallest touch target we allow for any tappable panel control (dp). */
    public static final int MIN_TOUCH = 30;

    private static final int[] SPACING = { GAP, S1, S2, S3, S4 };
    private static final int[] HEIGHTS = { H_CHIP, H_OPT, H_EDIT, H_TAB, H_BTN };

    /** dp -> px with the same truncation the panels use today ((v * density).toInt()). Bad density (<= 0 or NaN) = 3.0. */
    public static int px(int dp, float density) {
        float d = (density > 0f && !Float.isNaN(density) && !Float.isInfinite(density)) ? density : 3f;
        return (int) (dp * d);
    }

    public static boolean isSpacing(int dp) {
        for (int v : SPACING) if (v == dp) return true;
        return false;
    }

    public static boolean isHeight(int dp) {
        for (int v : HEIGHTS) if (v == dp) return true;
        return false;
    }

    /** Nearest value of the spacing scale; a tie goes to the larger one; below 0 = 0 spacing is not on the scale, so GAP. */
    public static int snapSpacing(int dp) {
        int best = SPACING[0];
        int bestDiff = Math.abs(dp - best);
        for (int v : SPACING) {
            int d = Math.abs(dp - v);
            if (d < bestDiff || (d == bestDiff && v > best)) { best = v; bestDiff = d; }
        }
        return best;
    }

    /** Nearest control height of the scale (tie = larger). */
    public static int snapHeight(int dp) {
        int best = HEIGHTS[0];
        int bestDiff = Math.abs(dp - best);
        for (int v : HEIGHTS) {
            int d = Math.abs(dp - v);
            if (d < bestDiff || (d == bestDiff && v > best)) { best = v; bestDiff = d; }
        }
        return best;
    }

    /** true when a tappable control of this height (dp) is big enough. */
    public static boolean touchOk(int hDp) { return hDp >= MIN_TOUCH; }

    /** Header: back button width in dp for a label (short labels "<" / "ABC" get 56, longer ones 8dp per char, max 140). */
    public static int backWidthDp(String label) {
        int n = label == null ? 0 : label.length();
        int w = n <= 3 ? 56 : n * 8 + 16;
        return Math.min(140, Math.max(56, w));
    }
}
