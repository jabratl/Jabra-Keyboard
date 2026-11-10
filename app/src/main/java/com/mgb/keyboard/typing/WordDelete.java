package com.mgb.keyboard.typing;

/** v0.3.2 word-delete rule (pure Java, no Android). How many UTF-16 chars to remove before the cursor to delete one word. */
public final class WordDelete {
    private WordDelete() { }

    /** Hold-backspace: after this many repeats the key deletes whole words. */
    public static final int FAST_AFTER = 22;
    /** Ask the field for this many chars before the cursor. */
    public static final int WINDOW = 64;

    /**
     * before = text just before the cursor (may be null / empty). Skips spaces / tabs first, then one run of non-space chars.
     * A line break stops the rule (deleting it counts as the word if nothing else is there). Returns 0 for nothing to delete.
     */
    public static int count(CharSequence before) {
        if (before == null || before.length() == 0) return 0;
        int n = before.length();
        int i = n;
        while (i > 0 && isBlank(before.charAt(i - 1))) i--;
        if (i == n && i > 0 && before.charAt(i - 1) == '\n') return 1;   // only a line break before the cursor
        if (i > 0 && before.charAt(i - 1) == '\n') return n - i;          // spaces right after a line break: keep the break
        while (i > 0 && !isBlank(before.charAt(i - 1)) && before.charAt(i - 1) != '\n') i--;
        // do not cut a surrogate pair in half at the start of the window
        if (i == 0 && n > 0 && Character.isLowSurrogate(before.charAt(0))) i = 1;
        return n - i;
    }

    private static boolean isBlank(char c) { return c == ' ' || c == '\t'; }
}
