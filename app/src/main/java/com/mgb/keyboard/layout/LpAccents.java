package com.mgb.keyboard.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * v0.17.46 (P3) pure data (no Android): long-press OPTIONS the default English layout can add when the user turns them on.
 * accents(c) = accent letters of one letter key (shown in the slide-to-pick bar / grid, lower case only); comma() / period() = symbol options of the , and . keys.
 * Tested by tools/LpAccentsTest.java.
 */
public final class LpAccents {
    private LpAccents() { }

    public static List<String> accents(char c) {
        String s;
        switch (Character.toLowerCase(c)) {
            case 'a': s = "\u00E0 \u00E1 \u00E2 \u00E4 \u00E3 \u00E5 \u0101"; break;
            case 'e': s = "\u00E8 \u00E9 \u00EA \u00EB \u0113 \u0117 \u0119"; break;
            case 'i': s = "\u00EC \u00ED \u00EE \u00EF \u012B \u012F"; break;
            case 'o': s = "\u00F2 \u00F3 \u00F4 \u00F6 \u00F5 \u00F8 \u014D"; break;
            case 'u': s = "\u00F9 \u00FA \u00FB \u00FC \u016B"; break;
            case 'c': s = "\u00E7 \u0107 \u010D"; break;
            case 'n': s = "\u00F1 \u0144"; break;
            case 's': s = "\u015B \u0161 \u00DF"; break;
            case 'y': s = "\u00FD \u00FF"; break;
            case 'z': s = "\u017E \u017A \u017C"; break;
            case 'l': s = "\u0142"; break;
            default: return new ArrayList<String>();
        }
        return new ArrayList<String>(Arrays.asList(s.split(" ")));
    }

    /** Options after the comma's own @emoji: & % + " - : ' @ */
    public static List<String> comma() { return new ArrayList<String>(Arrays.asList("&", "%", "+", "\"", "-", ":", "'", "@")); }

    /** Options after the period's own ? : ! ; / ( ) # , */
    public static List<String> period() { return new ArrayList<String>(Arrays.asList("!", ";", "/", "(", ")", "#", ",")); }
}
