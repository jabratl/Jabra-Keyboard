package com.mgb.keyboard.hist;

/**
 * v0.23.0 (H3) typing history PAUSE rules (pure Java, tested by tools/HistPauseTest / HistPart3Test).
 * Two ways to be paused: the old switch (pref hist_pause = until you turn it off) and a TIMED pause (pref hist_pause_until = time in ms).
 * The length is a PICKER: 15 min / 1 hour / 1 day (pref hist_pause_lv 0..2, default 1 hour = the last one used). When the time is over,
 * saving starts again by itself, so a pause is not forgotten. Keyboard key: saving -> choose a length; paused (any kind) -> resume.
 */
public final class HistPause {
    private HistPause() { }

    public static final long MIN_MS = 60000L, HOUR_MS = 3600000L, DAY_MS = 86400000L;
    public static final long[] DURS = { 15 * MIN_MS, HOUR_MS, DAY_MS };
    public static final String[] NAMES = { "15 min", "1 hour", "1 day" };
    public static final int DEF_LEVEL = 1;

    public static int level(int raw) { return (raw < 0 || raw >= DURS.length) ? DEF_LEVEL : raw; }
    public static long ms(int rawLevel) { return DURS[level(rawLevel)]; }
    public static String name(int rawLevel) { return NAMES[level(rawLevel)]; }

    public static boolean paused(boolean flag, long until, long now) { return flag || until > now; }

    /** Only the timed pause is running (the old switch is OFF). */
    public static boolean timed(boolean flag, long until, long now) { return !flag && until > now; }

    /** New value of hist_pause_until after a tap: paused -> 0 (resume); saving -> now + the chosen length. */
    public static long tapUntil(boolean flag, long until, long now, int level) { return paused(flag, until, now) ? 0L : now + ms(level); }

    /** "42 min left" / "1 h 20 min left" / "1 day 3 h left" (never negative, rounded up to the minute). */
    public static String left(long until, long now) {
        long ms = until - now;
        if (ms <= 0) return "0 min left";
        long min = (ms + MIN_MS - 1) / MIN_MS;
        if (min < 1) min = 1;
        if (min < 60) return min + " min left";
        long h = min / 60, m = min % 60;
        if (h < 24) return h + " h" + (m > 0 ? " " + m + " min" : "") + " left";
        long d = h / 24, hh = h % 24;
        return d + (d == 1 ? " day" : " days") + (hh > 0 ? " " + hh + " h" : "") + " left";
    }

    /** One line for the card / page. Empty when not paused. */
    public static String line(boolean flag, long until, long now) {
        if (flag) return "Paused until you resume.";
        if (until > now) return "Paused: " + left(until, now) + ", then saving starts again by itself.";
        return "";
    }

    /** Key label on the keyboard: on / paused / off. */
    public static String keyLabel(boolean on, boolean paused) { return !on ? "H\u25CB" : paused ? "H\u23F8" : "H\u25CF"; }

    /** Text of the small bubble after a tap or hold on the key. [reason] = HistPolicy.* of the open field. */
    public static String tip(boolean on, boolean flag, long until, long now, int reason) {
        if (!on) return "Typing history is OFF (turn it on in Data & Backup)";
        if (flag) return "History paused. Tap to resume";
        if (until > now) return "History paused, " + left(until, now) + ". Tap to resume";
        if (reason == HistPolicy.OK || reason == HistPolicy.PAUSED) return "History is saving. Tap to pause";
        return HistPolicy.reasonText(reason) + ". Tap to pause";
    }

    /** Title of the length picker bubble on the keyboard. */
    public static String pickTitle() { return "Pause typing history for"; }
}
