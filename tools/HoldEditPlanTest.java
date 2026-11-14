import com.mgb.keyboard.typing.HoldEditPlan;

/** v0.18.1 tests for hold C / V / X = copy / paste / cut (computer only). */
public class HoldEditPlanTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    public static void main(String[] a) {
        t("off = nothing", HoldEditPlan.actionFor('c', false, "", false) == null && HoldEditPlan.actionFor('v', false, "", false) == null);
        t("c copy", "copy".equals(HoldEditPlan.actionFor('c', true, "", false)));
        t("V upper = paste", "paste".equals(HoldEditPlan.actionFor('V', true, "", false)));
        t("x cut", "cut".equals(HoldEditPlan.actionFor('x', true, "", false)));
        t("other letters untouched", HoldEditPlan.actionFor('a', true, "", false) == null && HoldEditPlan.actionFor('z', true, "", false) == null);
        t("digit untouched", HoldEditPlan.actionFor('1', true, "", false) == null);
        t("custom override wins (c)", HoldEditPlan.actionFor('c', true, "c=@", false) == null);
        t("custom for other letter does not block", "copy".equals(HoldEditPlan.actionFor('c', true, "a=@", false)));
        t("null custom ok", "paste".equals(HoldEditPlan.actionFor('v', true, null, false)));
        t("password: copy / cut / paste all work (v0.18.2)", "copy".equals(HoldEditPlan.actionFor('c', true, "", true)) && "cut".equals(HoldEditPlan.actionFor('x', true, "", true)) && "paste".equals(HoldEditPlan.actionFor('v', true, "", true)));
        t("labels", "Copy".equals(HoldEditPlan.label("copy")) && "Paste".equals(HoldEditPlan.label("paste")) && "Cut".equals(HoldEditPlan.label("cut")) && HoldEditPlan.label("x").isEmpty());
        System.out.println("HoldEditPlanTest: " + ok + " PASS / " + bad + " FAIL");
        if (bad > 0) System.exit(1);
    }
}
