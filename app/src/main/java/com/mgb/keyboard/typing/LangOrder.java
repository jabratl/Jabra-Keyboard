package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.5.0 language key rules (pure Java, no Android classes, tested by tools/LangOrderTest.java).
 *
 * State of the keyboard = (bangla, probhat): EN = bangla false; Avro = bangla true + probhat false; Probhat = bangla true + probhat true.
 * (probhat is remembered while EN is shown = "last used Bangla layout".)
 * DEFAULT settings (tapMode 0, fav 0, both layouts on) = exactly the old behaviour:
 *   tap = EN <-> last used Bangla layout, long-press = Avro <-> Probhat (and Bangla on).
 * Results are int[]{bangla(0/1), probhat(0/1)}.
 */
public final class LangOrder {

    private LangOrder() { }

    /** tapMode: 0 = EN <-> Bangla (default), 1 = cycle EN > Avro > Probhat, 2 = cycle EN > Probhat > Avro (hidden layouts are skipped). */
    public static final String[] TAP_NAMES = { "EN <-> Bangla", "Cycle EN > Avro > Probhat", "Cycle EN > Probhat > Avro" };
    /** fav: which Bangla layout comes first when you tap from EN: 0 = the last used one (default), 1 = Avro, 2 = Probhat. */
    public static final String[] FAV_NAMES = { "Last used", "Avro", "Probhat" };

    public static final int MAX_CUSTOM = 10;

    private static int[] st(boolean bangla, boolean probhat) { return new int[] { bangla ? 1 : 0, probhat ? 1 : 0 }; }

    private static boolean on(boolean probhat, boolean avroOn, boolean probhatOn) { return probhat ? probhatOn : avroOn; }

    /** New state after a TAP on the language key. */
    public static int[] tap(boolean bangla, boolean probhat, boolean avroOn, boolean probhatOn, int tapMode, int fav) {
        if (tapMode == 1 || tapMode == 2) {
            // sequence: EN, then the enabled layouts in the chosen order
            List<int[]> seq = new ArrayList<int[]>();
            seq.add(st(false, probhat));
            boolean first = tapMode == 1 ? false : true;   // probhat flag of the first Bangla layout
            for (int i = 0; i < 2; i++) {
                boolean p = i == 0 ? first : !first;
                if (on(p, avroOn, probhatOn)) seq.add(st(true, p));
            }
            int idx = -1;
            if (!bangla) idx = 0;
            else for (int i = 1; i < seq.size(); i++) if ((seq.get(i)[1] == 1) == probhat) idx = i;
            int[] nx = seq.get((idx + 1) % seq.size());   // idx -1 (active layout hidden) -> EN
            return nx[0] == 0 ? st(false, probhat) : nx;
        }
        if (bangla) return st(false, probhat);                 // Bangla -> EN (layout is remembered)
        boolean target = probhat;                              // EN -> Bangla
        if (fav == 1 && avroOn) target = false;
        else if (fav == 2 && probhatOn) target = true;
        if (!on(target, avroOn, probhatOn)) target = !target;  // last used layout is switched off: use the other one
        if (!on(target, avroOn, probhatOn)) return st(false, probhat);   // both off: stay EN
        return st(true, target);
    }

    /** New state after a LONG-PRESS on the language key: Avro <-> Probhat, Bangla on. */
    public static int[] longPress(boolean bangla, boolean probhat, boolean avroOn, boolean probhatOn) {
        boolean other = !probhat;
        if (on(other, avroOn, probhatOn)) return st(true, other);
        if (on(probhat, avroOn, probhatOn)) return st(true, probhat);
        return st(false, probhat);
    }

    /** Custom character row: up to MAX_CUSTOM different visible characters (code points) from [s]; whitespace / control dropped. */
    public static List<String> customRow(String s) {
        List<String> out = new ArrayList<String>();
        if (s == null) return out;
        for (int i = 0; i < s.length() && out.size() < MAX_CUSTOM; ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isISOControl(cp)) continue;
            String ch = new String(Character.toChars(cp));
            if (!out.contains(ch)) out.add(ch);
        }
        return out;
    }

    /** The text to save in pref xrow_chars for [s] ("" = no extra row). */
    public static String customRowText(String s) {
        StringBuilder sb = new StringBuilder();
        for (String c : customRow(s)) sb.append(c);
        return sb.toString();
    }
}
