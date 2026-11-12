import com.mgb.keyboard.typing.WizardPlan;

/** Run: javac -d out app/src/main/java/com/mgb/keyboard/typing/WizardPlan.java tools/WizardPlanTest.java && java -cp out WizardPlanTest */
public class WizardPlanTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (java.util.Objects.equals(a, b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }
    public static void main(String[] x) {
        eq("not enabled", WizardPlan.step(false, false, false), WizardPlan.ENABLE);
        eq("not enabled but selected flag", WizardPlan.step(false, true, false), WizardPlan.ENABLE);
        eq("enabled not chosen", WizardPlan.step(true, false, false), WizardPlan.CHOOSE);
        eq("both done, test step", WizardPlan.step(true, true, false), WizardPlan.TEST);
        eq("done flag wins", WizardPlan.step(false, false, true), WizardPlan.DONE);
        eq("done flag wins 2", WizardPlan.step(true, true, true), WizardPlan.DONE);
        // wanted(done, started, enabled, selected)
        eq("fresh phone", WizardPlan.wanted(false, false, false, false), true);
        eq("enabled only", WizardPlan.wanted(false, false, true, false), true);
        eq("old user fully set up, never saw wizard", WizardPlan.wanted(false, false, true, true), false);
        eq("wizard running, setup finished: stay to the test step", WizardPlan.wanted(false, true, true, true), true);
        eq("done never", WizardPlan.wanted(true, true, false, false), false);
        eq("done never 2", WizardPlan.wanted(true, false, true, true), false);
        eq("progress 0", WizardPlan.progress(0), "\u25CF Enable   \u25CB Choose   \u25CB Test");
        eq("progress 1", WizardPlan.progress(1), "\u2713 Enable   \u25CF Choose   \u25CB Test");
        eq("progress 2", WizardPlan.progress(2), "\u2713 Enable   \u2713 Choose   \u25CF Test");
        eq("progress done", WizardPlan.progress(3), "\u2713 Enable   \u2713 Choose   \u2713 Test");
        eq("title 0", WizardPlan.title(0), "Setup: step 1 of 3");
        eq("title 2", WizardPlan.title(2), "Setup: step 3 of 3");
        eq("title done", WizardPlan.title(3), "Setup finished");
        eq("title junk", WizardPlan.title(-1), "Setup finished");
        for (int s = 0; s <= 2; s++) { eq("text not empty " + s, WizardPlan.text(s).isEmpty(), false); eq("button not empty " + s, WizardPlan.button(s).isEmpty(), false); }
        eq("text done empty", WizardPlan.text(3), "");
        eq("button done empty", WizardPlan.button(3), "");
        eq("button test", WizardPlan.button(2), "Done");
        System.out.println("WizardPlanTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
