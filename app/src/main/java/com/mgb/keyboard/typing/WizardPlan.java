package com.mgb.keyboard.typing;

/**
 * v0.13.0 (M3): pure rules of the first-run setup wizard (3 steps: Enable, Choose, Test). No Android classes.
 * Prefs: wiz_on (the wizard was shown while setup was not finished), wiz_done (the user tapped Done).
 * A phone that is already set up and never saw the wizard does NOT get it (wanted = false).
 */
public final class WizardPlan {
    private WizardPlan() { }

    public static final int ENABLE = 0, CHOOSE = 1, TEST = 2, DONE = 3;
    public static final String[] NAMES = { "Enable", "Choose", "Test" };

    /** Step from the two system checks and the "Done" flag. */
    public static int step(boolean enabled, boolean selected, boolean done) {
        if (done) return DONE;
        if (!enabled) return ENABLE;
        if (!selected) return CHOOSE;
        return TEST;
    }

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
        return "Setup: step " + (step + 1) + " of 3";
    }

    public static String text(int step) {
        switch (step) {
            case ENABLE: return "Turn on Jabra Keyboard in the phone's keyboard list.";
            case CHOOSE: return "Now choose Jabra Keyboard as the keyboard you type with.";
            case TEST: return "Tap the box below and type a few words. If the Jabra keyboard shows, you are done.";
            default: return "";
        }
    }

    public static String button(int step) {
        switch (step) {
            case ENABLE: return "Open keyboard settings";
            case CHOOSE: return "Choose Jabra Keyboard";
            case TEST: return "Done";
            default: return "";
        }
    }
}
