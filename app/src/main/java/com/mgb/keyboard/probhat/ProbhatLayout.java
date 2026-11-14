package com.mgb.keyboard.probhat;

import java.util.HashMap;

/**
 * v0.0.4: Probhat-style FIXED Bangla layout (own table, one key = one Bangla letter).
 * Pure Java, no Android. To fix a wrong key, edit ONLY the T table below (key, normal, shift).
 * NOTE: table written from the standard Probhat layout as remembered; NOT verified against the
 * official chart. v0.9.10.3: no vowel row any more; vowels are on the shift layer, the rest on the EXTRA_ROW keys.
 */
public final class ProbhatLayout {
    private ProbhatLayout() {}

    private static final String NUKTA = "়";           // ় (makes ড়, ঢ়, য়)

    /** {keyLetter(lowercase), normal output, shift output}. v0.9.10.3: 3-row Probhat (same rows as the older design, no vowel row). */
    private static final String[][] T = {
        // row 1 (q..p; the two keys after p are EXTRA_ROW1)
        {"q", "দ", "ধ"},
        {"w", "ূ", "ঊ"},
        {"e", "ী", "ঈ"},
        {"r", "র", "ড়"},
        {"t", "ট", "ঠ"},
        {"y", "এ", "ঐ"},
        {"u", "ু", "উ"},
        {"i", "ি", "ই"},
        {"o", "ও", "ঔ"},
        {"p", "প", "ফ"},
        // row 2 (a..l; one more key after l is EXTRA_ROW2)
        {"a", "া", "অ"},
        {"s", "স", "ষ"},
        {"d", "ড", "ঢ"},
        {"f", "ত", "থ"},
        {"g", "গ", "ঘ"},
        {"h", "হ", "ঃ"},
        {"j", "জ", "ঝ"},
        {"k", "ক", "খ"},
        {"l", "ল", "ং"},
        // row 3 (z..m; two more keys after m are EXTRA_ROW3)
        {"z", "য়", "য"},
        {"x", "শ", "ঢ়"},
        {"c", "চ", "ছ"},
        {"v", "আ", "ঋ"},
        {"b", "ব", "ভ"},
        {"n", "ন", "ণ"},
        {"m", "ম", "ঙ"},
    };

    /** Extra keys that sit INSIDE the 3 letter rows in Probhat mode (GONE in English / Avro). {normal, shift}. */
    public static final String[][] EXTRA_ROW1 = { {"ে", "ৈ"}, {"ো", "ৌ"} };
    /** v0.17.69 (S2): Ridmik grid = home row has NO extra key (9 letters only). The ri-kar moved to row 3. */
    public static final String[][] EXTRA_ROW2 = { };
    /** v0.17.69 (S2): Ridmik row 3 = z..m + [ৃ] [্ / ঁ]. Shift of the ri-kar key shows the same sign (as in Ridmik). */
    public static final String[][] EXTRA_ROW3 = { {"ৃ", "ৃ"}, {"্", "ঁ"} };

    /**
     * v0.17.69 (S2): letters that Ridmik shows on NO key face. They stay typeable by a hidden swipe-up on the extra key:
     * {key's normal output, swipe-up text}. ZWNJ is not on a key any more (the "Z W" key was not Ridmik).
     */
    public static final String[][] EXTRA_SWIPE_UP = { {"ৃ", "ঞ"}, {"্", "ৎ"} };

    /** Every output of the extra keys (faces + hidden swipe texts), for tests / coverage. */
    public static final String[] EXTRAS = {
        "ে", "ৈ", "ো", "ৌ", "ৃ", "্", "ঁ", "ঞ", "ৎ"
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

    /** v0.17.61 (R1): Ridmik look: a sign key shows the bare sign (ZWNJ in front, no dotted circle). Other keys = same as label(). */
    public static String labelBare(char key) {
        String s = M.get(key);
        return s == null ? String.valueOf(key) : showBare(s);
    }

    public static String extraLabelBare(String s) {
        if (s == null || s.length() == 0) return "";
        if (s.charAt(0) == '\u200c') return "ZWNJ";
        return showBare(s);
    }

    private static String showBare(String s) {
        int t = Character.getType(s.charAt(0));
        if (t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK) return "\u200c" + s;
        return s;
    }

    private static String show(String s) {
        int t = Character.getType(s.charAt(0));
        if (t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK) return "◌" + s;
        return s;
    }
}
