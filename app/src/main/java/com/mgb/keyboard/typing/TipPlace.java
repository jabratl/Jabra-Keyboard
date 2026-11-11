package com.mgb.keyboard.typing;

/**
 * v0.8.8 toolbar name tip (pure Java, no Android, tested by tools/TipPlaceTest.java).
 * Hold a toolbar icon HOLD_MS -> a small dim bubble with the item name floats above it (pref tb_tip, default ON).
 * This class only holds the numbers: delay, text rule and where the bubble goes (always fully on screen).
 */
public final class TipPlace {
    private TipPlace() { }

    public static final long HOLD_MS = 300L;       // user request: 0.3 s
    public static final int ALPHA_FILL = 0xE0;     // dim, slightly see-through bubble (about 88 %)
    public static final int MAX_CHARS = 28;        // longest title is "Mode (Phone / PC)" (17); longer text is cut

    /** The text to show, or "" when there is nothing to show (null / blank). Long text is cut with "...". */
    public static String text(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.isEmpty()) return "";
        return t.length() <= MAX_CHARS ? t : t.substring(0, MAX_CHARS - 3) + "...";
    }

    /** true = the tip may be shown (setting ON and there is text). */
    public static boolean shouldShow(boolean enabled, String raw) {
        return enabled && !text(raw).isEmpty();
    }

    /** Left edge of the bubble: centred over the key, kept inside 0..screenW. */
    public static int x(int keyLeft, int keyW, int tipW, int screenW) {
        int x = keyLeft + keyW / 2 - tipW / 2;
        int max = screenW - tipW;
        if (max < 0) max = 0;
        return x < 0 ? 0 : (x > max ? max : x);
    }

    /** Top edge of the bubble: above the key with a gap; never above the screen top. */
    public static int y(int keyTop, int tipH, int gapPx) {
        int y = keyTop - tipH - gapPx;
        return y < 0 ? 0 : y;
    }
}
