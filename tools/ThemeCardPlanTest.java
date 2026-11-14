import com.mgb.keyboard.typing.ThemeCardPlan;
import java.util.Arrays;
import java.util.ArrayList;

/** Run: javac -d out app/src/main/java/com/mgb/keyboard/typing/ThemeCardPlan.java tools/ThemeCardPlanTest.java && java -cp out ThemeCardPlanTest */
public class ThemeCardPlanTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (java.util.Objects.equals(a, b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }
    public static void main(String[] x) {
        eq("cols tiny", ThemeCardPlan.cols(100), 2);
        eq("cols 239", ThemeCardPlan.cols(239), 2);
        eq("cols 240", ThemeCardPlan.cols(240), 3);
        eq("cols 328 (360 phone)", ThemeCardPlan.cols(328), 3);
        eq("cols 479", ThemeCardPlan.cols(479), 3);
        eq("cols 480", ThemeCardPlan.cols(480), 4);
        eq("cols huge", ThemeCardPlan.cols(5000), 4);
        eq("cols negative", ThemeCardPlan.cols(-5), 2);
        for (int w = 0; w < 1500; w += 13) { int c = ThemeCardPlan.cols(w); eq("cols in range " + w, c >= 2 && c <= 4, true); }
        eq("avail one pane", ThemeCardPlan.availDp(360, false, 0, 32), 328);
        eq("avail two panes 1280", ThemeCardPlan.availDp(1280, true, 380, 32), 868);
        eq("avail never negative", ThemeCardPlan.availDp(100, false, 0, 500), 0);
        eq("avail negative pad", ThemeCardPlan.availDp(360, false, 0, -10), 360);
        eq("tile touch", ThemeCardPlan.TILE_DP >= 48, true);
        eq("describe plain", ThemeCardPlan.describe("Dark", false), "Theme Dark");
        eq("describe chosen", ThemeCardPlan.describe("Dark", true), "Theme Dark, selected");
        eq("describe null", ThemeCardPlan.describe(null, false), "Theme");
        eq("describe blank trimmed", ThemeCardPlan.describe("  ", true), "Theme, selected");
        eq("line normal", ThemeCardPlan.currentLine("Dark", 2, 20), "Current theme: Dark (3 of 20)");
        eq("line custom index out", ThemeCardPlan.currentLine("Custom", 20, 20), "Current theme: Custom");
        eq("line negative", ThemeCardPlan.currentLine("X", -1, 20), "Current theme: X");
        eq("line no name", ThemeCardPlan.currentLine(null, 0, 3), "Current theme: Custom (1 of 3)");
        eq("warn invalid", ThemeCardPlan.warnText(true, null).startsWith("Some colours are not valid"), true);
        eq("warn invalid wins", ThemeCardPlan.warnText(true, new ArrayList<>(Arrays.asList("a"))).startsWith("Some colours"), true);
        eq("warn none", ThemeCardPlan.warnText(false, new ArrayList<String>()), "");
        eq("warn null", ThemeCardPlan.warnText(false, null), "");
        eq("warn two", ThemeCardPlan.warnText(false, Arrays.asList("low contrast", "key = bg")), "\u26A0 low contrast\n\u26A0 key = bg");
        System.out.println("ThemeCardPlanTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
