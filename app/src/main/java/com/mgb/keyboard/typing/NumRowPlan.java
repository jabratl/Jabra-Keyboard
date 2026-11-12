package com.mgb.keyboard.typing;

import java.util.List;

/**
 * v0.16.7 (plan X, step X7): number row options. Pure Java, no Android (tools/NumRowPlanTest.java).
 *  nr_hpct  height 70..150 %, 100 = exactly today (key height, or key height + 12 dp when "Large number row" is ON)
 *  nr_mode  0 = digits as today (default), 1 = digits + a symbol on long-press (! @ # $ % ^ &amp; * ( )), 2 = your own characters (nr_chars)
 *  nr_chars up to 10 different visible characters; empty / nothing usable = the digits 1234567890
 * Mode 0 changes nothing (the old code path runs); "Bangla digits" (numrow_bn) only affects mode 0.
 */
public final class NumRowPlan {
    private NumRowPlan() { }

    public static final int H_MIN = 70, H_MAX = 150, H_STEP = 5, H_DEF = 100;
    public static final int MODE_DIGITS = 0, MODE_SYMBOLS = 1, MODE_CUSTOM = 2;
    public static final int MAX_CHARS = 10;
    public static final String DIGITS = "1234567890";
    public static final String SYMBOLS = "!@#$%^&*()";
    public static final int LARGE_EXTRA_DP = 12;

    public static int sanitizePct(int pct) { return pct >= H_MIN && pct <= H_MAX ? pct : H_DEF; }
    public static int sanitizeMode(int mode) { return mode >= MODE_DIGITS && mode <= MODE_CUSTOM ? mode : MODE_DIGITS; }

    /** True when the old code path must run unchanged (height 100 % and mode 0). */
    public static boolean isDefault(int pct, int mode) { return sanitizePct(pct) == H_DEF && sanitizeMode(mode) == MODE_DIGITS; }

    /** Row height in dp: (key height + 12 when Large) x percent. 100 % = the old height. */
    public static int heightDp(int keyDp, boolean large, int pct) {
        return Math.round((keyDp + (large ? LARGE_EXTRA_DP : 0)) * sanitizePct(pct) / 100f);
    }

    /** Text to save in nr_chars: up to 10 different visible characters, no spaces. */
    public static String sanitizeChars(String s) { return LangOrder.customRowText(s); }

    /** The labels (= typed text) of the keys. Mode 0 / 1 = 10 digits. Mode 2 = the characters (1..10), empty = 10 digits. Never empty. */
    public static String[] labels(int mode, String chars) {
        if (sanitizeMode(mode) == MODE_CUSTOM) {
            List<String> l = LangOrder.customRow(chars);
            if (!l.isEmpty()) return l.toArray(new String[0]);
        }
        String[] d = new String[10];
        for (int i = 0; i < 10; i++) d[i] = String.valueOf(DIGITS.charAt(i));
        return d;
    }

    /** Long-press text per key (same length as labels()): mode 1 = the symbols, otherwise "" (nothing). */
    public static String[] longPress(int mode, String chars) {
        String[] lab = labels(mode, chars);
        String[] out = new String[lab.length];
        for (int i = 0; i < out.length; i++) out[i] = (sanitizeMode(mode) == MODE_SYMBOLS && i < SYMBOLS.length()) ? String.valueOf(SYMBOLS.charAt(i)) : "";
        return out;
    }
}
