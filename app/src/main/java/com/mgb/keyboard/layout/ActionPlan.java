package com.mgb.keyboard.layout;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * v0.17.7 (K5b) which ACTION key ids and which swipe / long-press @actions the keyboard (MgbIme / CustomRows) really runs.
 * Pure Java. The Kotlin code and these sets must agree: tools/ActionPlanTest.java checks them against LayoutText (a whitelist entry nobody handles = red).
 * "combo" (PC-mode Ctrl / Alt keys) is NOT built yet.
 */
public final class ActionPlan {
    private ActionPlan() { }

    public static final Set<String> ACTION_DONE = new HashSet<String>(Arrays.asList(
            "shift", "backspace", "enter", "space", "sym", "lang", "emoji", "globe", "voice", "tab", "clip", "comma", "period", "undo", "redo"));

    public static final Set<String> AT_DONE = new HashSet<String>(Arrays.asList(
            "@tab", "@undo", "@redo", "@paste", "@copy", "@cut", "@selall", "@emoji", "@clip", "@lang", "@caps",
            "@sym", "@left", "@right", "@home", "@end", "@del_word"));

    public static boolean actionHandled(String id) { return id != null && ACTION_DONE.contains(id); }

    public static boolean atHandled(String id) { return id != null && AT_DONE.contains(id); }
}
