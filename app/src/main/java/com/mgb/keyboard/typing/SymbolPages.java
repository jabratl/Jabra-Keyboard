package com.mgb.keyboard.typing;

/**
 * v0.9.5 symbols panel pages (pure Java, no Android, tested by tools/SymbolPagesTest.java).
 * Page 0 and page 1 are EXACTLY the old two pages of JabraPanels.symbols() (v0.9.4). Page 2 is new: math / Greek / arrows.
 * Every page = 3 rows of single characters (BMP only, one char = one key). Row limits keep the keys readable at 360 dp:
 * row 1 max 10 keys, row 2 max 10 keys, row 3 max 7 keys (the row has the page key on the left and backspace on the right).
 * Page order: 0 -> 1 -> 2 -> 0. Source uses \\u escapes only, so file encoding can never change a symbol.
 */
public final class SymbolPages {
    private SymbolPages() { }

    public static final int COUNT = 3;
    public static final int[] ROW_MAX = {10, 10, 7};

    private static final String[][] PAGES = {
        {   // page 0 = old page 0 (v0.9.4), character for character
            "1234567890",
            "@#\u09F3%&-+()/",                                              // @ # taka % & - + ( ) /
            "*\"':;!?"
        },
        {   // page 1 = old page 1 (v0.9.4), character for character
            "~`|\u2022\u00B0^=_{}",                                        // ~ ` | bullet degree ^ = _ { }
            "\\[]<>\u00A3\u20AC\u00A5\u20B9$",                            // \ [ ] < > pound euro yen rupee $
            "\u2026\u00A9\u00AE\u2122\u00A7"                                // ellipsis copyright registered trademark section
        },
        {   // page 2 = NEW (v0.9.5): math / Greek / arrows. Shares no character with page 0 or 1.
            "\u03C0\u221A\u00F7\u00D7\u00B6\u0394\u00B1\u2260\u2248\u221E",   // pi root divide times pilcrow Delta plus-minus not-equal approx infinity
            "\u2264\u2265\u2190\u2192\u2191\u2193\u2194\u2211\u222B",          // <= >= arrows (left right up down both) sum integral
            "\u03B1\u03B2\u03B3\u03B8\u03BB\u03BC\u03A9"                         // alpha beta gamma theta lambda mu Omega
        }
    };

    /** Bad index (negative / too big) = page 0. */
    public static int norm(int p) { return (p < 0 || p >= COUNT) ? 0 : p; }

    /** Page after p in the cycle 0 -> 1 -> 2 -> 0. Bad input = page 1 (the page after 0). */
    public static int next(int p) { return (norm(p) + 1) % COUNT; }

    /** The 3 rows of page p (a fresh copy). */
    public static String[] page(int p) {
        String[] src = PAGES[norm(p)];
        return new String[] { src[0], src[1], src[2] };
    }

    /** Text of the left key of row 3 on page p: it shows the page you get when you tap it (old labels kept: "=<" on page 0, "?123" back on the last page). */
    public static String switchLabel(int p) {
        switch (norm(p)) {
            case 0: return "=<";
            case 1: return "\u03C0\u221A";
            default: return "?123";
        }
    }
}
