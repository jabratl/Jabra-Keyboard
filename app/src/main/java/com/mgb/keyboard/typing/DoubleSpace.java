package com.mgb.keyboard.typing;

/** v0.1.1 double-space rule (pure Java, no Android). Decides what a SECOND space tap should do. */
public final class DoubleSpace {
    private DoubleSpace() { }

    public static final int NONE = 0;    // type a normal space
    public static final int PERIOD = 1;  // replace the first space with ". " (Bangla mode: danda + space)
    public static final int TAB = 2;     // replace the first space with a tab

    public static final int WINDOW_MS = 800;

    /** True for a character that already ends a sentence (no second period after it). */
    static boolean isSentenceEnd(char c) {
        return ".!?,;:\u0964\u0965\u2026".indexOf(c) >= 0;
    }

    /** True for letters, digits, Bangla vowel signs (matras) and closing quotes / brackets: a period may follow. */
    static boolean periodFits(char c) {
        if (Character.isLetterOrDigit(c)) return true;
        int t = Character.getType(c);
        if (t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK) return true;
        return ")]}\"'\u201D\u2019".indexOf(c) >= 0;
    }

    /**
     * periodOn / tabOn : the two settings. lastSpaceAt : time of the previous space tap (0 = none). now : time of this tap.
     * before2 : the (up to) 2 characters before the cursor. Only when they are [non-space char][space] and the previous space
     * was tapped within WINDOW_MS, a double space is recognised. Period wins over tab when both are ON and the period fits.
     */
    public static int decide(boolean periodOn, boolean tabOn, long lastSpaceAt, long now, int windowMs, String before2) {
        if (!periodOn && !tabOn) return NONE;
        if (before2 == null || before2.length() != 2) return NONE;
        if (lastSpaceAt <= 0 || now < lastSpaceAt || now - lastSpaceAt > windowMs) return NONE;
        char a = before2.charAt(0);
        char b = before2.charAt(1);
        if (b != ' ') return NONE;
        if (Character.isWhitespace(a)) return NONE;
        if (periodOn && !isSentenceEnd(a) && periodFits(a)) return PERIOD;
        if (tabOn) return TAB;
        return NONE;
    }
}
