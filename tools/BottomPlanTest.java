import com.mgb.keyboard.typing.BottomPlan;

/** Run: javac -d out app/src/main/java/com/mgb/keyboard/typing/BottomPlan.java tools/BottomPlanTest.java && java -cp out BottomPlanTest */
public class BottomPlanTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (a.equals(b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }
    public static void main(String[] x) {
        eq("style junk", BottomPlan.style(7), 0);
        eq("style -1", BottomPlan.style(-1), 0);
        eq("style 1", BottomPlan.style(1), 1);
        eq("old sym", BottomPlan.symWeight(0), 1.2f);
        eq("old enter", BottomPlan.enterWeight(0), 1.4f);
        eq("old lang", BottomPlan.langWeight(0), 1.3f);
        eq("old space", BottomPlan.spaceWeight(0, 3f), 4f);
        eq("ridmik sym = enter", BottomPlan.symWeight(1), BottomPlan.enterWeight(1));
        eq("ridmik lang", BottomPlan.langWeight(1), 1f);
        float s = BottomPlan.spaceWeight(1, 1.55f * 2 + 4f);   // emoji, comma, danda, period
        eq("ridmik space 3.5", Math.abs(s - 3.5f) < 0.001f, true);
        eq("space min", BottomPlan.spaceWeight(1, 20f), 2f);
        eq("space max", BottomPlan.spaceWeight(1, 1f), 4.5f);
        eq("pbGap all", BottomPlan.pbGap(true, true, true), true);
        eq("pbGap english", BottomPlan.pbGap(false, true, true), false);
        eq("pbGap off", BottomPlan.pbGap(true, false, true), false);
        eq("pbGap rowgap off", BottomPlan.pbGap(true, true, false), false);
        eq("pb key weight on", BottomPlan.pbKeyWeight(true, true, true), 0.5f);
        eq("pb key weight off", BottomPlan.pbKeyWeight(true, false, true), 1f);
        eq("left english", BottomPlan.leftGapShown(false, false, true), true);
        eq("left english rowgap off", BottomPlan.leftGapShown(false, false, false), false);
        eq("left probhat off", BottomPlan.leftGapShown(true, false, true), false);
        eq("left probhat on", BottomPlan.leftGapShown(true, true, true), true);
        eq("right english", BottomPlan.rightGapShown(false, true), true);
        eq("right probhat", BottomPlan.rightGapShown(true, true), false);
        System.out.println("BottomPlanTest " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }
}
