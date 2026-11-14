package com.mgb.keyboard.typing;

/**
 * v0.18.1: pure rules for "hold C = copy, hold V = paste, hold X = cut" (pref hold_edit, default OFF = nothing changes).
 * Smart: your own "Long-press override" for that letter wins. v0.18.2: Copy / Cut / Paste also work in password fields (user request); the passwordField argument is kept but ignored. No Android classes.
 */
public final class HoldEditPlan {
    private HoldEditPlan() { }

    public static final String PREF = "hold_edit";

    /** "copy" / "paste" / "cut" for the letter, or null = the key keeps its normal long-press. */
    public static String actionFor(char c, boolean on, String custom, boolean passwordField) {
        if (!on) return null;
        char k = Character.toLowerCase(c);
        String a;
        if (k == 'c') a = "copy";
        else if (k == 'v') a = "paste";
        else if (k == 'x') a = "cut";
        else return null;
        try { if (LongPressHints.customFor(k, custom == null ? "" : custom) != null) return null; } catch (Throwable e) { }
        return a;
    }

    public static String label(String action) {
        if ("copy".equals(action)) return "Copy";
        if ("paste".equals(action)) return "Paste";
        if ("cut".equals(action)) return "Cut";
        return "";
    }
}
