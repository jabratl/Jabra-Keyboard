package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.42.3 (M3 of PLAN_RP_MINIMAL_M2-M4): pure model of the pair-list "More" floating menu (slim Find & Replace look).
 * Rows: Paste pairs, Save as Set, Whole word (N) [only when a Check found short words] | Clear (danger, LAST).
 * Clear with 2+ pairs needs two taps: the first tap only asks (keepOpen, label "Sure? Clear"), exactly like clearPairs() today.
 * No Android here (tested by tools/ReplaceMenuTest.java). Never returns null.
 */
public final class ReplaceMore {
    private ReplaceMore() { }

    public static final String PASTE = "paste", SAVE = "save", WORD = "word", CLEAR = "clear";
    public static final int WIDTH_DP = 176;

    public static List<ClipPop.Item> items(int nPairs, int nShort, boolean clearAsk) {
        List<ClipPop.Item> l = new ArrayList<ClipPop.Item>();
        l.add(new ClipPop.Item(PASTE, "Paste pairs", null, 0, false, false, ""));
        l.add(new ClipPop.Item(SAVE, "Save as Set", null, 0, false, false, ""));
        if (nShort > 0) l.add(new ClipPop.Item(WORD, "Whole word (" + nShort + ")", null, 0, false, false, ""));
        boolean two = nPairs >= 2;
        String label = (two && clearAsk) ? "Sure? Clear" : "Clear";
        l.add(new ClipPop.Item(CLEAR, label, null, 1, false, true, "", 1, two && !clearAsk, false));
        return l;
    }
}
