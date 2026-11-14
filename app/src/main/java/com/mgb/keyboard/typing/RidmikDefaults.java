package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * v0.17.69 (S2): the Ridmik-like DEFAULT settings, applied ONCE per phone and only to settings the user never stored
 * (so nothing the user chose is overwritten). Pure Java, no Android (tools/RidmikGridTest.java).
 * key_lang = false: no EN / language key (the Ridmik bottom row has none) - works because space_lang = true (swipe the space key to switch).
 * key_emoji = true: emoji key left of the comma (Ridmik). bot_style = 1: Ridmik-like widths (?123 and Enter the same width).
 */
public final class RidmikDefaults {
    private RidmikDefaults() { }

    /** Raise this number to apply a NEW list once more (pref rd_v stores the number already applied). */
    public static final int VERSION = 1;
    public static final String PREF_VERSION = "rd_v";

    /** key, value (Boolean or Integer). */
    public static final Object[][] DEFAULTS = {
        { "space_lang", Boolean.TRUE },
        { "key_lang", Boolean.FALSE },
        { "key_emoji", Boolean.TRUE },
        { "bot_style", Integer.valueOf(1) },
    };

    /** The {key, value} pairs to write now: every default whose key is NOT in [stored]; empty when [applied] >= VERSION. */
    public static List<Object[]> toWrite(int applied, Set<String> stored) {
        List<Object[]> out = new ArrayList<Object[]>();
        if (applied >= VERSION) return out;
        for (Object[] d : DEFAULTS) if (stored == null || !stored.contains((String) d[0])) out.add(d);
        return out;
    }
}
