import com.mgb.keyboard.typing.ReplaceSlimPlan;

/** v0.42.2: tests of the pure slim-look rules (typing/ReplaceSlimPlan.java). */
public class ReplaceSlimPlanTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] x) {
        // normal step row
        ReplaceSlimPlan.Step s = ReplaceSlimPlan.step(4, 4, 0, false, false, false, false, false, 0, false);
        t("normal: nav + rest + one", s.nav && s.rest && s.one && s.navEnabled);
        t("normal: counter 1 / 4", s.counter.equals("1 / 4"));
        t("normal: label + enabled", s.allLabel.equals("Replace all") && s.allEnabled);
        s = ReplaceSlimPlan.step(4, 4, 3, false, false, false, false, false, 0, false);
        t("counter follows step", s.counter.equals("4 / 4"));
        s = ReplaceSlimPlan.step(4, 4, 99, false, false, false, false, false, 0, false);
        t("step too big is clamped", s.counter.equals("4 / 4"));
        s = ReplaceSlimPlan.step(4, 4, -5, false, false, false, false, false, 0, false);
        t("step negative is clamped", s.counter.equals("1 / 4"));
        s = ReplaceSlimPlan.step(1, 1, 0, false, false, false, false, false, 0, false);
        t("one match: 1 / 1", s.counter.equals("1 / 1") && s.allEnabled);
        s = ReplaceSlimPlan.step(3, 10, 1, false, false, false, false, false, 0, false);
        t("count bigger than shown hits", s.counter.equals("2 / 10"));
        // no hits
        s = ReplaceSlimPlan.step(0, 0, 0, false, false, false, false, false, 0, false);
        t("no hits: shown but disabled, counter 0", s.nav && s.rest && s.one && !s.navEnabled && s.counter.equals("0") && !s.allEnabled);
        // error
        s = ReplaceSlimPlan.step(2, 2, 0, true, false, false, false, false, 0, false);
        t("error: disabled, counter 0, all disabled", !s.navEnabled && s.counter.equals("0") && !s.allEnabled);
        // blank find
        s = ReplaceSlimPlan.step(0, 0, 0, false, true, false, false, true, 0, false);
        t("blank: all disabled", !s.allEnabled && s.nav);
        // ask
        s = ReplaceSlimPlan.step(4, 4, 0, false, false, false, false, false, 0, true);
        t("ask: Sure? label", s.allLabel.equals("Sure? Replace all") && s.allEnabled);
        // pair mode with empty Find
        s = ReplaceSlimPlan.step(0, 0, 0, false, true, false, true, true, 3, false);
        t("pairs + empty Find: only Replace all (3 pairs)", !s.nav && !s.rest && !s.one && s.allLabel.equals("Replace all (3 pairs)") && s.allEnabled);
        s = ReplaceSlimPlan.step(0, 0, 0, false, true, false, true, true, 1, false);
        t("1 pair singular", s.allLabel.equals("Replace all (1 pair)"));
        s = ReplaceSlimPlan.step(0, 0, 0, false, true, false, true, true, 2, true);
        t("pairs + ask", s.allLabel.equals("Sure? Replace all (2 pairs)"));
        s = ReplaceSlimPlan.step(0, 0, 0, false, true, false, true, true, 0, false);
        t("0 pairs: disabled", !s.allEnabled && !s.nav);
        s = ReplaceSlimPlan.step(0, 0, 0, false, true, false, true, true, -4, false);
        t("negative pairs = 0, no crash", !s.allEnabled && s.allLabel.equals("Replace all (0 pairs)"));
        // pair mode WITH Find typed: normal row
        s = ReplaceSlimPlan.step(2, 2, 0, false, false, false, true, false, 3, false);
        t("pairs + Find typed: normal row", s.nav && s.rest && s.one && s.allEnabled);
        // loaded set
        s = ReplaceSlimPlan.step(0, 5, 0, false, false, true, false, true, 0, false);
        t("set loaded: only Replace all", !s.nav && !s.rest && !s.one && s.allLabel.equals("Replace all") && s.allEnabled);
        s = ReplaceSlimPlan.step(0, 0, 0, true, false, true, false, true, 0, true);
        t("set loaded + error + ask", !s.allEnabled && s.allLabel.equals("Sure? Replace all"));

        // note line
        ReplaceSlimPlan.Note n = ReplaceSlimPlan.note("", "whole field ", 120, false, "", false);
        t("default note = scope text, tappable", n.left.equals("whole field: 120 characters") && n.leftIsScope);
        t("no undo / paste back = Copy", n.rightKind == ReplaceSlimPlan.R_COPY && n.rightLabel.equals("Copy"));
        n = ReplaceSlimPlan.note("Replaced 4", "selection ", 9, true, "Undo (2)", true);
        t("message wins, not scope", n.left.equals("Replaced 4") && !n.leftIsScope);
        t("undo beats paste back", n.rightKind == ReplaceSlimPlan.R_UNDO && n.rightLabel.equals("Undo (2)"));
        n = ReplaceSlimPlan.note("", "from cursor ", 5, false, "x", true);
        t("paste back beats copy", n.rightKind == ReplaceSlimPlan.R_PASTE_BACK && n.rightLabel.equals("Paste back"));
        n = ReplaceSlimPlan.note(null, null, -3, true, null, false);
        t("null-safe: no crash, Undo default label, chars >= 0", n.left.equals(": 0 characters") && n.rightLabel.equals("Undo"));
        String longMsg = "Replaced 4 and this is a very long message that must stay untouched by the plan " + "x".repeat(200);
        n = ReplaceSlimPlan.note(longMsg, "whole field ", 1, false, "", false);
        t("long message untouched", n.left.equals(longMsg));

        // pair button
        t("pair mode + Find typed = Pair", ReplaceSlimPlan.pairButton(true, false, false, -1).equals("Pair"));
        t("pair mode + editing = Update", ReplaceSlimPlan.pairButton(true, false, false, 2).equals("Update"));
        t("pair mode + Find empty = Pair (edit index is reset)", ReplaceSlimPlan.pairButton(true, false, true, 2).equals("Pair"));
        t("no list + Find typed = Pair", ReplaceSlimPlan.pairButton(false, false, false, -1).equals("Pair"));
        t("no list + Find typed + editing = Update", ReplaceSlimPlan.pairButton(false, false, false, 0).equals("Update"));
        t("no list + Find empty = +Paste", ReplaceSlimPlan.pairButton(false, false, true, -1).equals("+Paste"));
        t("set loaded = nothing", ReplaceSlimPlan.pairButton(false, true, true, -1).equals("") && ReplaceSlimPlan.pairButton(false, true, false, -1).equals(""));

        System.out.println("ReplaceSlimPlanTest ok=" + ok + " bad=" + bad);
        if (bad > 0) { System.out.println("FAIL: " + bad + " failed"); System.exit(1); }
    }
}
