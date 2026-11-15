package com.mgb.keyboard.hist;

/** v0.23.0 (H3) texts for the redesigned typing history card and page (pure Java, tested by tools/HistPart3Test.java). */
public final class HistUi {
    private HistUi() { }

    public static String heroTitle(boolean on, boolean paused) { return !on ? "Typing history is OFF" : paused ? "Typing history is paused" : "Typing history is ON"; }

    /** state: 0 = off, 1 = saving, 2 = paused (the page / card colour the dot with it). */
    public static int state(boolean on, boolean paused) { return !on ? 0 : paused ? 2 : 1; }

    public static String heroSub(boolean on, int level, int count) {
        String n = count + (count == 1 ? " entry" : " entries");
        if (!on) return count > 0 ? n + " still stored. They go when the keep time ends or you clear them." : "Nothing is saved. Turn it on below.";
        return n + " \u00B7 kept " + HistPolicy.NAMES[HistPolicy.level(level)];
    }

    public static String pauseTitle() { return "Pause"; }
    public static String pauseHelp() { return "Stop saving for a while. It starts again by itself when the time is over."; }
    public static String pauseNow(String lengthName) { return "Pause for " + lengthName; }
    public static String resumeNow() { return "Resume now"; }
    public static String pauseUntilOff() { return "Pause until I turn it off"; }
    public static String bannerResume() { return "Resume"; }

    /** First letter (capital) for the round app badge; "?" when empty. */
    public static String badge(String appName) {
        if (appName == null) return "?";
        String s = appName.trim();
        if (s.isEmpty()) return "?";
        int cp = s.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(cp)));
    }
}
