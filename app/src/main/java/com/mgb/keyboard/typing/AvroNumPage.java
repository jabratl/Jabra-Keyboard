package com.mgb.keyboard.typing;

/**
 * v0.17.55 (M3) the Ridmik Avro number / sign page for Bangla (phonetic) mode (pure Java, no Android, tested by tools/AvroNumPageTest.java).
 * Row 0: Bengali digits 1-0 (hold = the English digit). Row 1: @ # TAKA % &amp; * - + ( )  (hold: TAKA = $, * = star, - = _, ( = &lt;, ) = &gt;).
 * Row 2 (between the page key and Backspace): ! " ' : ; / ?  (hold: : = degree). The page key shows "=\&lt;" and opens the next symbol page (SymbolPages 1, 2).
 * Each key = {label, typed text, hold text ("" = none)}. Source uses \\u escapes only.
 */
public final class AvroNumPage {
    private AvroNumPage() { }

    public static final String SWITCH_LABEL = "=\\<";
    public static final int ROWS = 3;

    private static final String[][][] KEYS = {
        {   // Bengali digits one to nine, zero (U+09E7 .. U+09EF, U+09E6)
            {"\u09E7", "\u09E7", "1"}, {"\u09E8", "\u09E8", "2"}, {"\u09E9", "\u09E9", "3"}, {"\u09EA", "\u09EA", "4"}, {"\u09EB", "\u09EB", "5"},
            {"\u09EC", "\u09EC", "6"}, {"\u09ED", "\u09ED", "7"}, {"\u09EE", "\u09EE", "8"}, {"\u09EF", "\u09EF", "9"}, {"\u09E6", "\u09E6", "0"}
        },
        {
            {"@", "@", ""}, {"#", "#", ""}, {"\u09F3", "\u09F3", "$"}, {"%", "%", ""}, {"&", "&", ""},
            {"*", "*", "\u2605"}, {"-", "-", "_"}, {"+", "+", ""}, {"(", "(", "<"}, {")", ")", ">"}
        },
        {
            {"!", "!", ""}, {"\"", "\"", ""}, {"'", "'", ""}, {":", ":", "\u00B0"}, {";", ";", ""}, {"/", "/", ""}, {"?", "?", ""}
        }
    };

    /** Keys of row r (0 .. 2) as a fresh copy; bad r = empty. Each key = {label, typed, hold or ""}. */
    public static String[][] row(int r) {
        if (r < 0 || r >= ROWS) return new String[0][];
        String[][] src = KEYS[r];
        String[][] out = new String[src.length][];
        for (int i = 0; i < src.length; i++) out[i] = new String[] { src[i][0], src[i][1], src[i][2] };
        return out;
    }

    /** True = show this page instead of the classic symbol page 0: setting ON, phonetic Bangla, and the first symbol page (page keys still reach pages 1 and 2). */
    public static boolean active(boolean settingOn, boolean banglaPhonetic, int symPage) {
        return settingOn && banglaPhonetic && SymbolPages.norm(symPage) == 0;
    }
}
