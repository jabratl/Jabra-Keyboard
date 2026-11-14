package com.mgb.keyboard.typing;

/**
 * v0.13.0 (M3): pure rules of the first-run setup wizard. No Android classes.
 * v0.17.84 (U8): 5 steps = Enable, Choose, Language, Theme, Test (was 3). Language / Theme can be skipped; Enable / Choose have a retry + failure text.
 * Prefs: wiz_on (the wizard was shown while setup was not finished), wiz_done (the user tapped Done), wiz_lang / wiz_theme (U8: that step was answered or skipped).
 * A phone that is already set up and never saw the wizard does NOT get it (wanted = false).
 */
public final class WizardPlan {
    private WizardPlan() { }

    public static final int ENABLE = 0, CHOOSE = 1, LANGUAGE = 2, THEME = 3, TEST = 4, DONE = 5;   // v0.17.84 (U8): LANGUAGE + THEME added before TEST
    public static final String[] NAMES = { "Enable", "Choose", "Language", "Theme", "Test" };

    /** Step from the two system checks, the two "answered / skipped" flags and the "Done" flag. */
    public static int step(boolean enabled, boolean selected, boolean langDone, boolean themeDone, boolean done) {   // v0.17.84 (U8)
        if (done) return DONE;
        if (!enabled) return ENABLE;
        if (!selected) return CHOOSE;
        if (!langDone) return LANGUAGE;
        if (!themeDone) return THEME;
        return TEST;
    }

    /** Old 3-argument form (v0.13.0): Language and Theme counted as answered. */
    public static int step(boolean enabled, boolean selected, boolean done) { return step(enabled, selected, true, true, done); }

    /** Show the wizard instead of the plain setup card? Done = never. Setup not finished = yes. Finished but the wizard was already running = keep it until Done. */
    public static boolean wanted(boolean done, boolean started, boolean enabled, boolean selected) {
        if (done) return false;
        if (!(enabled && selected)) return true;
        return started;
    }

    public static String progress(int step) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NAMES.length; i++) {
            if (i > 0) sb.append("   ");
            sb.append(i < step ? "\u2713" : (i == step ? "\u25CF" : "\u25CB")).append(' ').append(NAMES[i]);
        }
        return sb.toString();
    }

    public static String title(int step) {
        if (step < 0 || step > TEST) return "Setup finished";
        return "Setup: step " + (step + 1) + " of " + NAMES.length;   // v0.17.84 (U8)
    }

    public static String text(int step) {
        switch (step) {
            case ENABLE: return "Turn on Jabra Keyboard in the phone's keyboard list.";
            case CHOOSE: return "Now choose Jabra Keyboard as the keyboard you type with.";
            case LANGUAGE: return "How do you type Bangla? You can change this later in Typing settings.";   // v0.17.84 (U8)
            case THEME: return "Pick a look for the keyboard. You can change it later in Look settings.";     // v0.17.84 (U8)
            case TEST: return "Tap the box below and type a few words. If the Jabra keyboard shows, you are done.";
            default: return "";
        }
    }

    public static String button(int step) {
        switch (step) {
            case ENABLE: return "Open keyboard settings";
            case CHOOSE: return "Choose Jabra Keyboard";
            case LANGUAGE: case THEME: return "Next";   // v0.17.84 (U8)
            case TEST: return "Done";
            default: return "";
        }
    }

    // ---- v0.17.84 (U8): retry / failure states, skip, and the two new choices ----
    /** [tried] = the person already tapped the button of this step once and the step is still open. */
    public static String text(int step, boolean tried) {
        if (tried && step == ENABLE) return "Jabra Keyboard is still OFF. Open the list, switch Jabra Keyboard ON, then come back here.";
        if (tried && step == CHOOSE) return "Jabra Keyboard is not chosen yet. Pick it in the small window. No window? Tap the button again.";
        return text(step);
    }

    public static String button(int step, boolean tried) {
        if (tried && (step == ENABLE || step == CHOOSE)) return "Try again";
        return button(step);
    }

    /** The phone refused to open the system screen (the button threw). */
    public static String failText(int step) {
        if (step == ENABLE) return "This phone did not open the keyboard list. Open the phone Settings, search for \u201CKeyboard\u201D and switch Jabra Keyboard ON.";
        if (step == CHOOSE) return "This phone did not open the keyboard chooser. Open the phone Settings, search for \u201CKeyboard\u201D and choose Jabra Keyboard as the default.";
        return "";
    }

    /** Small second button; empty = none. Language and Theme can be skipped (nothing is changed). Test: choose again if the keyboard does not show. */
    public static String secondary(int step) {
        if (step == LANGUAGE || step == THEME) return "Skip";
        if (step == TEST) return "Keyboard not showing? Choose again";
        return "";
    }

    /** Bangla layout pref (bn_layout) <-> choice index: 0 = Avro (default), 1 = Probhat. Anything else = Avro. */
    public static int layoutIndex(String pref) { return "probhat".equals(pref) ? 1 : 0; }
    public static String layoutValue(int index) { return index == 1 ? "probhat" : "avro"; }

    /** Next theme index when the person taps the theme chip. size <= 0 = 0; a bad current index starts again at 0. */
    public static int nextTheme(int cur, int size) {
        if (size <= 0) return 0;
        if (cur < 0 || cur >= size) return 0;
        return (cur + 1) % size;
    }
}
