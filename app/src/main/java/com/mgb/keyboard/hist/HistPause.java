package com.mgb.keyboard.hist;

/**
 * v0.22.0 (H3) typing history PAUSE rules (pure Java, tested by tools/HistPauseTest.java).
 * Two ways to be paused: the old switch (pref hist_pause = until you turn it off) and a TIMED pause (pref hist_pause_until = time in ms,
 * 1 hour from the tap; then saving starts again by itself, so a pause is not forgotten). One tap on the keyboard key:
 * paused (any kind) -> resume; saving -> pause for 1 hour.
 */
public final class HistPause {
    private HistPause() { }

    public static final long HOUR_MS = 3600000L;

    public static boolean paused(boolean flag, long until, long now) { return flag || until > now; }

    /** Only the timed pause is running (the old switch is OFF). */
    public static boolean timed(boolean flag, long until, long now) { return !flag && until > now; }

    /** New value of hist_pause_until after one tap on the keyboard key. */
    public static long tapUntil(boolean flag, long until, long now) { return paused(flag, until, now) ? 0L : now + HOUR_MS; }

    /** "42 min left" / "1 min left" / "less than 1 min left" (never negative). */
    public static String left(long until, long now) {
        long ms = until - now;
        if (ms <= 0) return "0 min left";
        long min = (ms + 59999L) / 60000L;
        if (min < 1) min = 1;
        return min + " min left";
    }

    /** One line for the card / page. Empty when not paused. */
    public static String line(boolean flag, long until, long now) {
        if (flag) return "Paused until you resume.";
        if (until > now) return "Paused for 1 hour: " + left(until, now) + ", then saving starts again by itself.";
        return "";
    }

    /** Key label on the keyboard: on / paused / off. */
    public static String keyLabel(boolean on, boolean paused) { return !on ? "H\u25CB" : paused ? "H\u23F8" : "H\u25CF"; }

    /** Text of the small bubble after a tap or hold on the key. [reason] = HistPolicy.* of the open field. */
    public static String tip(boolean on, boolean flag, long until, long now, int reason) {
        if (!on) return "Typing history is OFF (turn it on in Data & Backup)";
        if (flag) return "History paused. Tap to resume";
        if (until > now) return "History paused, " + left(until, now) + ". Tap to resume";
        if (reason == HistPolicy.OK || reason == HistPolicy.PAUSED) return "History is saving. Tap to pause 1 hour";
        return HistPolicy.reasonText(reason) + ". Tap to pause 1 hour";
    }
}
