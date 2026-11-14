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
        // v0.17.84 (U8): 5 steps
        eq("progress 0", WizardPlan.progress(0), "\u25CF Enable   \u25CB Choose   \u25CB Language   \u25CB Theme   \u25CB Test");
        eq("progress 2", WizardPlan.progress(WizardPlan.LANGUAGE), "\u2713 Enable   \u2713 Choose   \u25CF Language   \u25CB Theme   \u25CB Test");
        eq("progress test", WizardPlan.progress(WizardPlan.TEST), "\u2713 Enable   \u2713 Choose   \u2713 Language   \u2713 Theme   \u25CF Test");
        eq("progress done", WizardPlan.progress(WizardPlan.DONE), "\u2713 Enable   \u2713 Choose   \u2713 Language   \u2713 Theme   \u2713 Test");
        eq("title 0", WizardPlan.title(0), "Setup: step 1 of 5");
        eq("title test", WizardPlan.title(WizardPlan.TEST), "Setup: step 5 of 5");
        eq("title done", WizardPlan.title(WizardPlan.DONE), "Setup finished");
        eq("title junk", WizardPlan.title(-1), "Setup finished");
        for (int s = 0; s <= WizardPlan.TEST; s++) { eq("text not empty " + s, WizardPlan.text(s).isEmpty(), false); eq("button not empty " + s, WizardPlan.button(s).isEmpty(), false); }
        eq("text done empty", WizardPlan.text(WizardPlan.DONE), "");
        eq("button done empty", WizardPlan.button(WizardPlan.DONE), "");
        eq("button test", WizardPlan.button(WizardPlan.TEST), "Done");
        eq("step order", WizardPlan.ENABLE < WizardPlan.CHOOSE && WizardPlan.CHOOSE < WizardPlan.LANGUAGE && WizardPlan.LANGUAGE < WizardPlan.THEME && WizardPlan.THEME < WizardPlan.TEST && WizardPlan.TEST < WizardPlan.DONE, true);
        eq("names match steps", WizardPlan.NAMES.length, WizardPlan.DONE);
        // 5-argument step
        eq("5 fresh", WizardPlan.step(false, false, false, false, false), WizardPlan.ENABLE);
        eq("5 enabled", WizardPlan.step(true, false, false, false, false), WizardPlan.CHOOSE);
        eq("5 chosen -> language", WizardPlan.step(true, true, false, false, false), WizardPlan.LANGUAGE);
        eq("5 language answered -> theme", WizardPlan.step(true, true, true, false, false), WizardPlan.THEME);
        eq("5 theme answered -> test", WizardPlan.step(true, true, true, true, false), WizardPlan.TEST);
        eq("5 skipped language only keeps theme", WizardPlan.step(true, true, false, true, false), WizardPlan.LANGUAGE);
        eq("5 not enabled beats answered flags", WizardPlan.step(false, true, true, true, false), WizardPlan.ENABLE);
        eq("5 done wins", WizardPlan.step(false, false, false, false, true), WizardPlan.DONE);
        // retry + failure + skip
        eq("retry enable text", WizardPlan.text(WizardPlan.ENABLE, true).startsWith("Jabra Keyboard is still OFF"), true);
        eq("retry choose text", WizardPlan.text(WizardPlan.CHOOSE, true).startsWith("Jabra Keyboard is not chosen"), true);
        eq("first time text unchanged", WizardPlan.text(WizardPlan.ENABLE, false), WizardPlan.text(WizardPlan.ENABLE));
        eq("retry only for enable / choose", WizardPlan.text(WizardPlan.THEME, true), WizardPlan.text(WizardPlan.THEME));
        eq("retry button enable", WizardPlan.button(WizardPlan.ENABLE, true), "Try again");
        eq("retry button choose", WizardPlan.button(WizardPlan.CHOOSE, true), "Try again");
        eq("retry button theme unchanged", WizardPlan.button(WizardPlan.THEME, true), "Next");
        eq("first button unchanged", WizardPlan.button(WizardPlan.ENABLE, false), "Open keyboard settings");
        eq("fail enable", WizardPlan.failText(WizardPlan.ENABLE).isEmpty(), false);
        eq("fail choose", WizardPlan.failText(WizardPlan.CHOOSE).isEmpty(), false);
        eq("fail other empty", WizardPlan.failText(WizardPlan.TEST), "");
        eq("skip language", WizardPlan.secondary(WizardPlan.LANGUAGE), "Skip");
        eq("skip theme", WizardPlan.secondary(WizardPlan.THEME), "Skip");
        eq("secondary test = choose again", WizardPlan.secondary(WizardPlan.TEST).startsWith("Keyboard not showing"), true);
        eq("secondary enable none", WizardPlan.secondary(WizardPlan.ENABLE), "");
        // language / theme choices
        eq("layout avro", WizardPlan.layoutIndex("avro"), 0);
        eq("layout probhat", WizardPlan.layoutIndex("probhat"), 1);
        eq("layout junk = avro", WizardPlan.layoutIndex("zzz"), 0);
        eq("layout null = avro", WizardPlan.layoutIndex(null), 0);
        eq("layout value 0", WizardPlan.layoutValue(0), "avro");
        eq("layout value 1", WizardPlan.layoutValue(1), "probhat");
        eq("layout value junk", WizardPlan.layoutValue(7), "avro");
        eq("theme next", WizardPlan.nextTheme(0, 5), 1);
        eq("theme wraps", WizardPlan.nextTheme(4, 5), 0);
        eq("theme bad cur", WizardPlan.nextTheme(99, 5), 0);
        eq("theme negative", WizardPlan.nextTheme(-3, 5), 0);
        eq("theme empty list", WizardPlan.nextTheme(2, 0), 0);
        System.out.println("WizardPlanTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
