package com.mgb.keyboard.typing;

/**
 * v0.9.7 (plan V2) same keyboard height in every language (pure Java, no Android, tested by tools/HeightLockTest.java).
 * The Probhat extra row (vowels, 38 dp) used to be GONE outside Probhat, so the keyboard grew / shrank on every language switch
 * and the host app re-laid out (the visible "jump"). With the lock ON the row keeps its space (INVISIBLE) as long as Probhat can be
 * chosen at all. The three values are the same numbers as android.view.View VISIBLE / INVISIBLE / GONE; MgbIme maps them by name.
 */
public final class HeightLock {
    private HeightLock() { }

    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;

    /**
     * Visibility of the Probhat extra row.
     * @param lock          pref h_lock (default ON)
     * @param probhatOn     Probhat layout is active right now (Bangla + Probhat + not a Latin-only field)
     * @param probhatAvail  pref lang_probhat_on (Probhat is in the language cycle); when it is OFF nobody can switch to Probhat, nothing can jump
     */
    public static int mode(boolean lock, boolean probhatOn, boolean probhatAvail) {
        if (probhatOn) return VISIBLE;
        if (!lock) return GONE;
        return probhatAvail ? INVISIBLE : GONE;
    }

    /** True when the row takes space (VISIBLE or INVISIBLE), i.e. the keyboard height includes it. */
    public static boolean takesSpace(int mode) { return mode == VISIBLE || mode == INVISIBLE; }

    /** True when switching from state a to state b changes the keyboard height (a jump). */
    public static boolean jumps(boolean lock, boolean availablePb, boolean aOn, boolean bOn) {
        return takesSpace(mode(lock, aOn, availablePb)) != takesSpace(mode(lock, bOn, availablePb));
    }
}
