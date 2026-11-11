package com.mgb.keyboard.typing;

/**
 * v0.6.1 Pure decision: does the auto-capital rule need to ask the app (InputConnection.getCursorCapsMode, a synchronous
 * cross-process call) after this selection update? Safe default = ASK. Only a plain LETTER we just typed skips the call:
 * a letter can never start a new sentence, and the one-shot shift was already ended by ShiftState.typed().
 * Digits, space, punctuation, danda, newline, backspace, paste, cursor jumps, field start = always ask.
 * Fields with the "capitalize all characters" flag always ask (they want a capital after every letter).
 */
public final class AutoCapGate {
    private AutoCapGate() {}

    public static final int E_START = 0;      // new input field
    public static final int E_LETTER = 1;     // we just typed one letter
    public static final int E_DELIM = 2;      // we typed a digit / space / punctuation / other single char
    public static final int E_BACKSPACE = 3;  // we deleted
    public static final int E_JUMP = 4;       // cursor moved by the user / the app / anything we did not type
    public static final int E_OTHER = 5;      // multi-character text (paste, emoji, snippet)

    /** Kind of the text we are about to commit. null / empty = OTHER. One code point that is a letter = LETTER, one other code point = DELIM. */
    public static int kindOf(String s) {
        if (s == null || s.isEmpty()) return E_OTHER;
        int cp = s.codePointAt(0);
        if (s.length() != Character.charCount(cp)) return E_OTHER;
        return Character.isLetter(cp) ? E_LETTER : E_DELIM;
    }

    /** True = call getCursorCapsMode. */
    public static boolean shouldCheck(int event, boolean capChars) {
        if (capChars) return true;
        return event != E_LETTER;
    }
}
