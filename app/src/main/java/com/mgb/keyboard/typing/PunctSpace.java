package com.mgb.keyboard.typing;

/**
 * v0.17.34 (M1): pure rule for "Space before , . ! ?" (pref sp_punct).
 * True = delete the ONE space before the cursor because a punctuation mark is typed right after it.
 * Two cases:
 *  - afterPick: the space was added by a tapped suggestion (old v0.10.6 rule: any text ending in a space, punct set , . ! ? ; : ) danda).
 *  - byKeyboardSpace: the space was typed with the space key (v0.17.34): only , . ! ? danda, only when the text
 *    ends with EXACTLY one space and the char before it is a letter / digit / Bangla sign / closing quote or bracket.
 * No Android classes (unit tested on a computer).
 */
public final class PunctSpace {
    private PunctSpace() { }

    /** Old set (after a tapped suggestion). */
    public static final String PICK_SET = ",.!?;:)\u0964";
    /** New set (after a space typed with the space key). */
    public static final String KEY_SET = ",.!?\u0964";

    public static boolean shouldRemove(String before, char typed, boolean byKeyboardSpace, boolean afterPick) {
        if (before == null || before.isEmpty() || before.charAt(before.length() - 1) != ' ') return false;
        if (afterPick && PICK_SET.indexOf(typed) >= 0) return true;
        if (!byKeyboardSpace || KEY_SET.indexOf(typed) < 0) return false;
        if (before.length() < 2) return false;
        return wordEnd(before.charAt(before.length() - 2));
    }

    static boolean wordEnd(char c) {
        if (Character.isLetterOrDigit(c)) return true;
        int t = Character.getType(c);
        if (t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK) return true;   // Bangla vowel signs, hasanta
        return ")]}\"'\u201D\u2019".indexOf(c) >= 0;
    }
}
