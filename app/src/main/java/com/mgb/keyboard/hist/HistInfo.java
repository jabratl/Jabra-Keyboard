package com.mgb.keyboard.hist;

/** v0.20.0 (H1) typing history - all texts shown in Data & Backup and in the Privacy card (pure Java, tested by tools/HistInfoTest.java). */
public final class HistInfo {
    private HistInfo() { }

    public static String statusLine(boolean on, boolean paused, int level, int count) {
        if (!on) return "Typing history: OFF (nothing is saved)" + (count > 0 ? ". " + count + (count == 1 ? " old entry is still stored; it is removed by the keep time or Clear." : " old entries are still stored; they are removed by the keep time or Clear.") : "");
        String s = "Typing history: ON" + (paused ? " (paused)" : "") + ", kept " + HistPolicy.NAMES[HistPolicy.level(level)] + ", " + count + (count == 1 ? " entry" : " entries");
        return s;
    }

    public static String keepLabel(int level) { return "Keep typing history: " + HistPolicy.NAMES[HistPolicy.level(level)]; }

    public static String clearLabel(boolean armed, int n) {
        if (n <= 0) return "Clear typing history now (nothing to clear)";
        return armed ? "Sure? Delete " + n + (n == 1 ? " entry" : " entries") : "Clear typing history now";
    }

    public static String clearDone(int removed) {
        if (removed <= 0) return "Nothing to clear.";
        return "Cleared " + removed + (removed == 1 ? " entry." : " entries.");
    }

    public static String confirmTitle() { return "Turn on typing history?"; }

    /** Shown the first time the switch is turned ON. */
    public static String confirmBody() {
        return "Jabra will save what you type with this keyboard: the time, the app and the text (Bangla, Avro or English), as one entry per message.\n\n"
            + "\u2022 Saved on this phone only, as plain text inside the app's private storage. Other apps cannot read it, but anyone who unlocks the phone and opens Jabra can.\n"
            + "\u2022 Pasted text and voice typing are not saved.\n"
            + "\u2022 Password fields are never saved. Email, URL and number fields and incognito fields are skipped (you can change this).\n"
            + "\u2022 Entries older than the keep time are deleted every time the keyboard or this app opens. At most 2000 entries are kept.\n"
            + "\u2022 It is not part of Backup.\n\n"
            + "You can pause or turn it off at any time and clear everything with two taps.";
    }

    /** Fixed lines for the Privacy card (always shown, so the rules stay visible even when the feature is OFF). */
    public static String privacyLine(boolean on, boolean paused, int level) {
        if (!on) return "Typing history: OFF (typed text is not saved)";
        return "Typing history: ON" + (paused ? " (paused)" : "") + ", entries deleted after " + HistPolicy.DAYS[HistPolicy.level(level)] + (HistPolicy.DAYS[HistPolicy.level(level)] == 1 ? " day" : " days");
    }

    public static String privacySkipLine(boolean skipSens, boolean skipIncog) {
        return "Typing history never records password fields or pasted text. Email / URL / number fields: " + (skipSens ? "skipped" : "recorded")
            + ". Incognito fields: " + (skipIncog ? "skipped" : "recorded") + ".";
    }
}
