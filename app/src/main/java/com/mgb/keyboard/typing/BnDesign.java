package com.mgb.keyboard.typing;

/**
 * v0.17.61 (R1): which look the Bangla FIXED (Probhat) keyboard uses. Pref bn_design: 0 = Ridmik (Probhat) look (default), 1 = the keyboard's original design.
 * Pure Java, no Android (tools/BnDesignTest.java). Ridmik look = no dotted circle on sign keys, shift letters as small hints, Bengali number / sign page.
 * Bangla (phonetic) and English never change with this setting.
 */
public final class BnDesign {
    private BnDesign() { }

    public static final int RIDMIK = 0, ORIGINAL = 1;
    /** Setting names (index = pref value). Bangla text as \\u escapes: PA VIRAMA RA BHA AA TA = prabhat. */
    public static final String[] NAMES = { "Ridmik (\u09AA\u09CD\u09B0\u09AD\u09BE\u09A4) look", "Keyboard's original design" };

    public static int sanitize(int v) { return v == ORIGINAL ? ORIGINAL : RIDMIK; }

    public static boolean ridmik(int v) { return sanitize(v) == RIDMIK; }
}
