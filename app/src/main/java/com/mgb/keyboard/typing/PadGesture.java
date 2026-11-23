package com.mgb.keyboard.typing;

/**
 * v0.48.1: pure rules of the cursor pad's trackpad (status line) and of the auto-open debounce.
 * Pure Java (no Android classes), same style as PadState / PanelKeep, so every rule can be tested on a computer.
 * PadPanel (touch events) and MgbIme (auto-open timer) only read the prefs and call these.
 */
public final class PadGesture {
    private PadGesture() { }

    // ---- trackpad speed (pref pad_speed) ----
    public static final int SPEED_DEF = 1;   // Normal = the old fixed 16 dp per step

    /** dp of finger travel per cursor step: 0 Slow / 1 Normal / 2 Fast (anything else = Normal). */
    public static int stepDp(int level) {
        switch (level) {
            case 0: return 24;
            case 2: return 10;
            default: return 16;
        }
    }

    // ---- auto-open debounce (pref pad_delay) ----
    public static final int DELAY_DEF = 2;   // Normal = 180 ms (v0.48.0 value)

    /** ms a selection must stay before the pad auto-opens: 0 Instant / 1 Short / 2 Normal / 3 Long (anything else = Normal). 0 = no waiting. */
    public static long delayMs(int level) {
        switch (level) {
            case 0: return 0L;
            case 1: return 100L;
            case 3: return 300L;
            default: return 180L;
        }
    }

    // ---- taps on the status line ----
    public static final long TAP_GAP_MS = 300L;   // max time between two taps of one double / triple tap

    /**
     * Counts quick clean taps (drags are not taps: call reset() on a drag). tap() returns 1, 2 or 3 = how many
     * taps in a row this one is; after the 3rd tap the count starts again, so a 4th quick tap is a new "1".
     * 2 = select word, 3 = select line.
     */
    public static final class TapCounter {
        private long last = 0L;
        private int count = 0;

        public int tap(long now) {
            if (last != 0L && now - last <= TAP_GAP_MS) count++; else count = 1;
            last = now;
            int c = count;
            if (c >= 3) reset();
            return c;
        }

        public void reset() { last = 0L; count = 0; }
    }

    // ---- drag -> cursor steps ----
    /** Turns finger movement into whole cursor steps. Horizontal wins when it is the larger movement (no both-ways step on a diagonal drag). */
    public static final class Glide {
        private float accX = 0f, accY = 0f;

        public void reset() { accX = 0f; accY = 0f; }

        /** @return {xSteps, ySteps}: positive = right / down, negative = left / up. */
        public int[] feed(float dx, float dy, int step) {
            accX += dx;
            accY += dy;
            int sx = 0, sy = 0;
            if (step <= 0) return new int[] { 0, 0 };
            while (Math.abs(accX) >= step && Math.abs(accX) >= Math.abs(accY)) {
                if (accX > 0) { sx++; accX -= step; } else { sx--; accX += step; }
            }
            while (Math.abs(accY) >= step && Math.abs(accY) > Math.abs(accX)) {
                if (accY > 0) { sy++; accY -= step; } else { sy--; accY += step; }
            }
            return new int[] { sx, sy };
        }
    }
}
