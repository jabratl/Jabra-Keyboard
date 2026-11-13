package com.mgb.keyboard.typing;

import com.mgb.keyboard.probhat.ProbhatLayout;

/**
 * v0.17.61 (R1): the small hint on a Bangla FIXED (Probhat) letter key = the letter the key types with Shift (Ridmik Probhat look: "d" shows its shift letter).
 * Hold a key = types that letter. Pure Java (tools/ProbhatHintsTest.java). The extra keys inside the rows (e-kar, o-kar, ...) are not letter keys and get no hint here.
 */
public final class ProbhatHints {
    private ProbhatHints() { }

    /** The hint of letter key c (case ignored), or null = no hint (not a letter key). */
    public static String hintFor(char c) {
        char k = Character.toLowerCase(c);
        if (k < 'a' || k > 'z') return null;
        String s = ProbhatLayout.map(Character.toUpperCase(k));
        return s == null || s.isEmpty() ? null : s;
    }

    /** True = Probhat hints on: design is Ridmik, Bangla fixed is the active language, not a Latin-only field. */
    public static boolean active(boolean ridmikDesign, boolean banglaMode, boolean probhat, boolean latinOnly) {
        return ridmikDesign && banglaMode && probhat && !latinOnly;
    }
}
