import com.mgb.keyboard.layout.*;

/** v0.17.7 (K5b) the keyboard handles every whitelisted @action and every ACTION key id (except "combo"). */
public class ActionPlanTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] a) {
        for (String s : LayoutText.AT_ACTIONS) t("at handled " + s, ActionPlan.atHandled(s));
        for (String s : ActionPlan.AT_DONE) t("at in whitelist " + s, LayoutText.AT_ACTIONS.contains(s));
        for (String s : LayoutText.ACTIONS) {
            if (s.equals("combo")) t("combo not built yet", !ActionPlan.actionHandled(s));
            else t("action handled " + s, ActionPlan.actionHandled(s));
        }
        for (String s : ActionPlan.ACTION_DONE) t("action in list " + s, LayoutText.ACTIONS.contains(s));
        t("at count", ActionPlan.AT_DONE.size() == LayoutText.AT_ACTIONS.size());
        t("unknown at", !ActionPlan.atHandled("@nope"));
        t("unknown action", !ActionPlan.actionHandled("nope"));
        t("null at", !ActionPlan.atHandled(null));
        t("null action", !ActionPlan.actionHandled(null));
        t("empty at", !ActionPlan.atHandled(""));
        t("at without @", !ActionPlan.atHandled("tab"));
        System.out.println("ActionPlanTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
