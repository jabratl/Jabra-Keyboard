package com.mgb.keyboard.probhat;

import java.util.HashMap;

/**
 * v0.0.4: Probhat-style FIXED Bangla layout (own table, one key = one Bangla letter).
 * Pure Java, no Android. To fix a wrong key, edit ONLY the T table below (key, normal, shift).
 * NOTE: table written from the standard Probhat layout as remembered; NOT verified against the
 * official chart. Independent vowels (আ ই ঈ উ ঊ এ ঐ) + ঁ ঃ ৎ + ZWNJ live in EXTRAS (extra row shown in Probhat mode).
 */
public final class ProbhatLayout {
    private ProbhatLayout() {}

    private static final String NUKTA = "়";           // ় (makes ড়, ঢ়, য়)

    /** {keyLetter(lowercase), normal output, shift output} */
    private static final String[][] T = {
        {"q", "ঙ", "ং"},
        {"w", "য", "য" + NUKTA},
        {"e", "ড", "ঢ"},
        {"r", "প", "ফ"},
        {"t", "ট", "ঠ"},
        {"y", "চ", "ছ"},
        {"u", "জ", "ঝ"},
        {"i", "হ", "ঞ"},
        {"o", "গ", "ঘ"},
        {"p", "ড" + NUKTA, "ঢ" + NUKTA},
        {"a", "ৃ", "ঋ"},
        {"s", "ু", "ূ"},
        {"d", "ি", "ী"},
        {"f", "া", "অ"},
        {"g", "্", "।"},
        {"h", "ব", "ভ"},
        {"j", "ক", "খ"},
        {"k", "ত", "থ"},
        {"l", "দ", "ধ"},
        {"z", "ো", "ৌ"},
        {"x", "ও", "ঔ"},
        {"c", "ে", "ৈ"},
        {"v", "র", "ল"},
        {"b", "ন", "ণ"},
        {"n", "স", "ষ"},
        {"m", "ম", "শ"},
    };

    /** Extra row (Probhat mode only). */
    public static final String[] EXTRAS = {
        "আ", "ই", "ঈ", "উ", "ঊ", "এ", "ঐ", "ঁ", "ঃ", "ৎ", "‌"
    };

    private static final HashMap<Character, String> M = new HashMap<Character, String>();
    static {
        for (String[] r : T) {
            char k = r[0].charAt(0);
            M.put(k, r[1]);
            M.put(Character.toUpperCase(k), r[2]);
        }
    }

    /** Bangla text for a pressed letter key (upper-case letter = shift). null if the key is not in the table. */
    public static String map(char key) {
        return M.get(key);
    }

    /** Text to draw on the key. Combining signs get a dotted circle in front so they are visible. */
    public static String label(char key) {
        String s = M.get(key);
        return s == null ? String.valueOf(key) : show(s);
    }

    public static String extraLabel(String s) {
        if (s == null || s.length() == 0) return "";
        if (s.charAt(0) == '‌') return "ZWNJ";
        return show(s);
    }

    private static String show(String s) {
        int t = Character.getType(s.charAt(0));
        if (t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK) return "◌" + s;
        return s;
    }
}
