package com.mgb.keyboard.typing;

/**
 * v0.10.10 (W4) pure helpers for the clipboard "code guard" (pref clip_sens, default OFF).
 * A clip that LOOKS LIKE a one-time code (SMS / login code) is removed from the history after CODE_TTL_MS (2 minutes).
 * Only the clip text is looked at, on the phone, nothing is stored or sent. Normal sentences, words and long texts are never touched.
 * looksLikeCode(text):
 *   - leading / trailing spaces and line breaks are ignored; any space or line break INSIDE = not a code
 *   - 4 to 8 digits only (English, Bangla or any other Unicode decimal digits) = code
 *   - mixed code: 4 to 10 characters, only ASCII letters + digits, at least 2 digits and at least 1 letter = code (A1B2C3, X7K9Q2)
 *   - everything else = not a code (a plain word, a year inside a sentence, a phone number with + or -, a url ...)
 */
public final class ClipSensitive {
    private ClipSensitive() { }

    public static final long CODE_TTL_MS = 2L * 60L * 1000L;

    private static boolean isDigit(int cp) { return Character.getType(cp) == Character.DECIMAL_DIGIT_NUMBER; }

    private static boolean isAsciiLetter(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'); }

    private static boolean isAsciiDigit(char c) { return c >= '0' && c <= '9'; }

    public static boolean looksLikeCode(String text) {
        if (text == null) return false;
        String s = text.trim();
        int n = s.length();
        if (n < 4 || n > 10) return false;
        // digits only (4..8). codePoints: Bangla digits are single chars, still counted safely
        boolean allDigits = true;
        int digits = 0;
        for (int i = 0; i < n; ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (isDigit(cp)) digits++; else { allDigits = false; break; }
        }
        if (allDigits) return digits >= 4 && digits <= 8;
        // mixed ASCII code (4..10)
        int d = 0, l = 0;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (isAsciiDigit(c)) d++;
            else if (isAsciiLetter(c)) l++;
            else return false;
        }
        return d >= 2 && l >= 1;
    }

    /**
     * true = this clip must be removed now: not pinned, looks like a code, and older than CODE_TTL_MS.
     * madeAt <= 0 (no record) = never due (the caller records "made = now" first).
     */
    public static boolean isDue(boolean pinned, String text, long madeAt, long now) {
        if (pinned || madeAt <= 0L) return false;
        if (now - madeAt <= CODE_TTL_MS) return false;
        return looksLikeCode(text);
    }
}
