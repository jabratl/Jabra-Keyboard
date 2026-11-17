import com.mgb.keyboard.typing.ClipCompact;

public class ClipCompactTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    static boolean cc2NeverMore() {
        for (int m = 0; m < 32; m++) {
            boolean f = (m & 1) != 0, sel = (m & 2) != 0, u = (m & 4) != 0, msg = (m & 8) != 0, cf = (m & 16) != 0;
            for (int se = 0; se < 2; se++) {
                int off = ClipCompact.rowCount(ClipCompact.rowPlan(false, f, sel, u, msg, cf, se == 1));
                int on = ClipCompact.rowCount(ClipCompact.rowPlan(true, f, sel, u, msg, cf, se == 1));
                if (on > off) return false;
            }
        }
        return true;
    }
    static boolean cc2UndoReachable() {
        for (int m = 0; m < 16; m++) {
            boolean f = (m & 1) != 0, sel = (m & 2) != 0, msg = (m & 4) != 0, cf = (m & 8) != 0;
            int p = ClipCompact.rowPlan(true, f, sel, true, msg, cf, false);
            if (!(ClipCompact.undoInFilter(p) || ClipCompact.hasInfo(p))) return false;
        }
        return true;
    }
    static boolean cc2MsgReachable() {
        for (int m = 0; m < 8; m++) {
            boolean f = (m & 1) != 0, sel = (m & 2) != 0, u = (m & 4) != 0;
            if (!ClipCompact.hasInfo(ClipCompact.rowPlan(true, f, sel, u, true, false, false))) return false;
            if (!ClipCompact.hasInfo(ClipCompact.rowPlan(true, f, sel, u, false, true, false))) return false;
        }
        return true;
    }
    public static void main(String[] a) {
        t("OFF = old height 40", ClipCompact.rowDp(40, false) == 40);
        t("ON 40 -> 34", ClipCompact.rowDp(40, true) == 34);
        t("ON never grows (30 stays 30)", ClipCompact.rowDp(30, true) == 30);
        t("ON old 0 stays 0", ClipCompact.rowDp(0, true) == 0);
        t("ON negative stays", ClipCompact.rowDp(-5, true) == -5);
        t("ON 34 stays 34", ClipCompact.rowDp(34, true) == 34);
        t("ON 20 stays 20 (already lower than floor)", ClipCompact.rowDp(20, true) == 20);
        t("ON >= MIN_DP", ClipCompact.rowDp(40, true) >= 28);
        t("OFF weights ABC 1.0", ClipCompact.weight(ClipCompact.ABC, false, false) == 1.0f);
        t("OFF weights Search 1.6", ClipCompact.weight(ClipCompact.SEARCH, false, false) == 1.6f);
        t("OFF weights Search slim 0.9", ClipCompact.weight(ClipCompact.SEARCH, false, true) == 0.9f);
        t("OFF weights Done 1.3", ClipCompact.weight(ClipCompact.DONE, false, false) == 1.3f);
        t("OFF weights Done slim 0.8", ClipCompact.weight(ClipCompact.DONE, false, true) == 0.8f);
        t("ON ABC narrower than OFF", ClipCompact.weight(ClipCompact.ABC, true, false) < ClipCompact.weight(ClipCompact.ABC, false, false));
        t("ON Search wider than OFF (select mode)", ClipCompact.weight(ClipCompact.SEARCH, true, false) > ClipCompact.weight(ClipCompact.SEARCH, false, false));
        t("ON Done narrower than OFF", ClipCompact.weight(ClipCompact.DONE, true, false) < ClipCompact.weight(ClipCompact.DONE, false, false));
        t("unknown which = 1", ClipCompact.weight(9, true, false) == 1.0f && ClipCompact.weight(-1, false, true) == 1.0f);
        t("weights always > 0", ClipCompact.weight(0, true, true) > 0 && ClipCompact.weight(1, true, true) > 0 && ClipCompact.weight(2, true, true) > 0);
        t("padV OFF 4", ClipCompact.padV(false) == 4);
        t("padV ON 2", ClipCompact.padV(true) == 2);
        t("saved 40 -> 10 dp", ClipCompact.savedDp(40) == 10);
        t("saved never negative for low rows", ClipCompact.savedDp(20) >= 0);
        // touch floor with the user size % (ClipBtn): compact row at 70 % must not go under 28 dp
        t("compact + size 70% floor", com.mgb.keyboard.typing.ClipBtn.heightDp(ClipCompact.rowDp(40, true), 70) >= 28);
        t("compact + size 100% = 34", com.mgb.keyboard.typing.ClipBtn.heightDp(ClipCompact.rowDp(40, true), 100) == 34);
        // ---- v0.34.0 (CC2): rowPlan(compact2, filterShown, selOn, undo, hasMsg, confirm, searching)
        int F = ClipCompact.FILTER_ROW, I = ClipCompact.INFO_ROW, U = ClipCompact.UNDO_IN_FILTER;
        // OFF = v0.33.0 behaviour
        t("CC2 OFF normal = filter + info", ClipCompact.rowPlan(false, true, false, false, false, false, false) == (F | I));
        t("CC2 OFF normal + undo = filter + info (undo stays in the info line)", ClipCompact.rowPlan(false, true, false, true, false, false, false) == (F | I));
        t("CC2 OFF select idle = filter only (old idleSel)", ClipCompact.rowPlan(false, true, true, false, false, false, false) == F);
        t("CC2 OFF select + undo = filter + info", ClipCompact.rowPlan(false, true, true, true, false, false, false) == (F | I));
        t("CC2 OFF select + msg = filter + info", ClipCompact.rowPlan(false, true, true, false, true, false, false) == (F | I));
        t("CC2 OFF select + confirm = filter + info", ClipCompact.rowPlan(false, true, true, false, false, true, false) == (F | I));
        t("CC2 OFF searching = no info", ClipCompact.rowPlan(false, true, false, true, true, true, true) == F);
        t("CC2 OFF never UNDO_IN_FILTER", !ClipCompact.undoInFilter(ClipCompact.rowPlan(false, true, false, true, false, false, false)));
        // ON
        t("CC2 ON normal, nothing to say = filter only (count line gone)", ClipCompact.rowPlan(true, true, false, false, false, false, false) == F);
        t("CC2 ON normal + undo = filter row only, undo in it", ClipCompact.rowPlan(true, true, false, true, false, false, false) == (F | U));
        t("CC2 ON select + undo = filter row only, undo in it", ClipCompact.rowPlan(true, true, true, true, false, false, false) == (F | U));
        t("CC2 ON message keeps its own line", ClipCompact.rowPlan(true, true, false, false, true, false, false) == (F | I));
        t("CC2 ON message + undo = undo in filter, message line", ClipCompact.rowPlan(true, true, false, true, true, false, false) == (F | I | U));
        t("CC2 ON confirm keeps its own line, undo not in filter", ClipCompact.rowPlan(true, true, false, true, false, true, false) == (F | I));
        t("CC2 ON filter row hidden (More tray) + undo = info line fallback", ClipCompact.rowPlan(true, false, true, true, false, false, false) == I);
        t("CC2 ON filter row hidden, nothing to say = no rows", ClipCompact.rowPlan(true, false, true, false, false, false, false) == 0);
        t("CC2 ON searching = no info, no undo in filter", ClipCompact.rowPlan(true, true, false, true, true, true, true) == F);
        t("CC2 ON searching + filter hidden = 0", ClipCompact.rowPlan(true, false, false, true, true, true, true) == 0);
        t("CC2 ON never more rows than OFF (all combos)", cc2NeverMore());
        t("CC2 ON undo never lost (shown in filter or info) (all combos)", cc2UndoReachable());
        t("CC2 ON message / confirm never lost (all combos)", cc2MsgReachable());
        t("CC2 rowCount", ClipCompact.rowCount(F | I) == 2 && ClipCompact.rowCount(F) == 1 && ClipCompact.rowCount(0) == 0 && ClipCompact.rowCount(I | U) == 1);
        t("CC2 helpers", ClipCompact.hasInfo(I) && !ClipCompact.hasInfo(F) && ClipCompact.hasFilter(F) && !ClipCompact.hasFilter(I) && ClipCompact.undoInFilter(U));
        System.out.println("ClipCompactTest " + ok + "/" + (ok + bad));
        if (bad > 0) System.exit(1);
    }
}
