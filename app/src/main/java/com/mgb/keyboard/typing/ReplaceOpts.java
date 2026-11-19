package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.42.1 (M1 of PLAN_RP_MINIMAL): pure model of the Find & Replace "Options" floating menu. No Android here (tested by tools/ReplaceOptsTest.java).
 * Rows (like docs/Replace_Tool_Minimal_Mock.html): Match: Match case (Aa), Smart | Text: Keep case, List a|b|c, \n as new line | Never change (N) >.
 * Smart / List / \n are Text + Word only (Regex mode hides them, same as the old chip rows). Tick rows keep the menu open; "Never change" closes it and opens the page.
 * The Kotlin side (ReplaceSlim.kt + ClipPopover) maps each id to the SAME old state change, then calls the same optChanged().
 */
public final class ReplaceOpts {
    private ReplaceOpts() { }

    public static final String AA = "aa", SMART = "smart", KEEP = "keep", LIST = "list", NL = "nl", NEVER = "never", HELP = "help";
    public static final int WIDTH_DP = 224;

    /** The menu rows for these options. protectCount = size of the never-change list. */
    public static List<ClipPop.Item> items(ReplaceEngine.Opt o, int protectCount) {
        ReplaceEngine.Opt a = o == null ? new ReplaceEngine.Opt() : o;
        boolean plain = a.mode != ReplaceEngine.MODE_REGEX;
        List<ClipPop.Item> l = new ArrayList<ClipPop.Item>();
        l.add(new ClipPop.Item("h_match", "Match", null, 0, false, false, "", 1, false, true));
        l.add(tick(AA, "Match case (Aa)", 0, a.matchCase));
        if (plain) l.add(tick(SMART, "Smart (spaces, accents)", 0, a.smart));
        l.add(new ClipPop.Item("h_text", "Text", null, 1, false, false, "", 1, false, true));
        l.add(tick(KEEP, "Keep case", 1, a.keepCase));
        if (plain) {
            l.add(tick(LIST, "List  a|b|c", 1, a.list));
            l.add(tick(NL, "\\n as new line", 1, a.escapes));
        }
        l.add(new ClipPop.Item(NEVER, "Never change", null, 2, false, false, Math.max(0, protectCount) + " \u203A"));
        l.add(new ClipPop.Item(HELP, "Help", null, 3, false, false, "\u203A"));   // v0.42.4 (M4): the "?" of the slim top bar lives here
        return l;
    }

    private static ClipPop.Item tick(String id, String label, int group, boolean on) {
        return new ClipPop.Item(id, label, null, group, on, false, "", 1, true, false, true);   // v0.42.4: boxTick
    }

    /** The small number on the Options button: how many of the shown options are ON, +1 when the never-change list is not empty. */
    public static int activeCount(ReplaceEngine.Opt o, int protectCount) {
        ReplaceEngine.Opt a = o == null ? new ReplaceEngine.Opt() : o;
        int n = (a.matchCase ? 1 : 0) + (a.keepCase ? 1 : 0);
        if (a.mode != ReplaceEngine.MODE_REGEX) n += (a.smart ? 1 : 0) + (a.list ? 1 : 0) + (a.escapes ? 1 : 0);
        if (protectCount > 0) n++;
        return n;
    }

    /** Applies a tick row to the options (same rules as the old chips: Keep ON turns Match case OFF). false = not a tick id. */
    public static boolean toggle(ReplaceEngine.Opt o, String id) {
        if (o == null || id == null) return false;
        if (AA.equals(id)) { o.matchCase = !o.matchCase; return true; }
        if (SMART.equals(id)) { o.smart = !o.smart; return true; }
        if (KEEP.equals(id)) { o.keepCase = !o.keepCase; if (o.keepCase) o.matchCase = false; return true; }
        if (LIST.equals(id)) { o.list = !o.list; return true; }
        if (NL.equals(id)) { o.escapes = !o.escapes; return true; }
        return false;
    }

    /** Text of the Options button. */
    public static String buttonLabel(ReplaceEngine.Opt o, int protectCount) {
        int n = activeCount(o, protectCount);
        return n > 0 ? "Options \u00B7 " + n : "Options";
    }
}
