package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v0.10.23 (W9) the pure rules of the "selection quick actions" in the suggestion row (no Android; tested by tools/QuickActionPlanTest.java).
 * When text is selected (pref qa_on, default OFF) the 3 strip slots show up to 3 one-tap actions instead of word suggestions:
 *   Copy (always) | Case (only when the text has upper / lower letters) | Clean (only when cleaning would change it) | "N words" (fills a free slot; tap = full counts).
 * Case cycles on every tap: UPPER > lower > Title > UPPER ... (the selection stays selected so the next tap goes on).
 * Never in password fields (the caller checks blocked) and never for a selection longer than MAX_SEL chars.
 */
public final class QuickActionPlan {
    private QuickActionPlan() { }

    public static final int MAX_SEL = 20000;
    public static final String COPY = "copy", CASE = "case", CLEAN = "clean", COUNT = "count";

    /** True when the chips should show: pref ON, not blocked, a real selection (end > start) that is not too long. */
    public static boolean active(boolean qaOn, boolean blocked, int selStart, int selEnd) {
        if (!qaOn || blocked) return false;
        int len = Math.abs(selEnd) - Math.abs(selStart);
        if (selEnd < 0 || selStart < 0) return false;
        return selEnd > selStart && len <= MAX_SEL;
    }

    static boolean hasCased(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c) || Character.isLowerCase(c)) return true;
        }
        return false;
    }

    static boolean cleanable(String s) {
        return !TextTools.clean(s, true, true, true).equals(s);
    }

    /** Action ids for the selected text, in slot order, 0..3 of them (empty = show normal suggestions). */
    public static List<String> ids(String sel) {
        List<String> out = new ArrayList<String>(3);
        if (sel == null || sel.isEmpty() || sel.length() > MAX_SEL) return out;
        if (sel.trim().isEmpty()) return out;
        out.add(COPY);
        if (hasCased(sel)) out.add(CASE);
        if (cleanable(sel)) out.add(CLEAN);
        if (out.size() < 3) out.add(COUNT);
        return out;
    }

    /** Text on the chip. Count shows the word count ("12 words"). */
    public static String label(String id, String sel) {
        if (COPY.equals(id)) return "Copy";
        if (CASE.equals(id)) return "Case";
        if (CLEAN.equals(id)) return "Clean";
        if (COUNT.equals(id)) {
            int w = TextTools.counts(sel == null ? "" : sel)[0];
            return w + (w == 1 ? " word" : " words");
        }
        return "";
    }

    /** Next case mode for the Case chip: UPPER > lower > Title > UPPER. */
    public static int nextCase(String sel) {
        if (sel == null) return TextTools.UPPER;
        if (sel.equals(sel.toUpperCase(Locale.ROOT))) return TextTools.LOWER;
        if (sel.equals(sel.toLowerCase(Locale.ROOT))) return TextTools.TITLE;
        return TextTools.UPPER;
    }

    /** Replacement text for Case / Clean, null for Copy / Count / unknown (those do not change the text). */
    public static String apply(String id, String sel) {
        if (sel == null || sel.isEmpty()) return null;
        if (CASE.equals(id)) return TextTools.caseConvert(sel, nextCase(sel));
        if (CLEAN.equals(id)) return TextTools.clean(sel, true, true, true);
        return null;
    }
}
