import com.mgb.keyboard.typing.SelectPlan;

/** javac -encoding UTF-8 -d out $(find app -name '*.java') tools/SelectPlanTest.java && java -Dfile.encoding=UTF-8 -cp out SelectPlanTest */
public class SelectPlanTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL " + m); } }

    public static void main(String[] a) {
        String[] n = { "Newest", "Oldest", "A-Z", "Used" };
        // clamp / next
        ok(SelectPlan.clamp(-3, 4) == 0 && SelectPlan.clamp(9, 4) == 3 && SelectPlan.clamp(2, 4) == 2 && SelectPlan.clamp(5, 0) == 0, "clamp");
        ok(SelectPlan.next(0, 4) == 1 && SelectPlan.next(3, 4) == 0 && SelectPlan.next(99, 4) == 0 && SelectPlan.next(-1, 4) == 1 && SelectPlan.next(0, 0) == 0 && SelectPlan.next(0, 1) == 0, "next wraps");
        // label / value / describe
        ok(SelectPlan.value(n, 1).equals("Oldest") && SelectPlan.value(n, 77).equals("Used") && SelectPlan.value(null, 0).equals("") && SelectPlan.value(new String[0], 0).equals(""), "value");
        ok(SelectPlan.value(new String[] { null, "x" }, 0).equals(""), "null name");
        ok(SelectPlan.label(n, 0).equals("Newest \u25BE") && SelectPlan.label(null, 0).equals(" \u25BE"), "label has the tail");
        ok(SelectPlan.describe("Sort", n, 2).equals("Sort: A-Z") && SelectPlan.describe(null, n, 2).equals("A-Z") && SelectPlan.describe("  ", n, 2).equals("A-Z"), "describe");
        // rowText
        ok(SelectPlan.rowText("List", true).equals("\u2713 List") && SelectPlan.rowText("Cards", false).equals("Cards") && SelectPlan.rowText(null, false).equals(""), "rowText");
        // rows
        ok(SelectPlan.visibleRows(0) == 0 && SelectPlan.visibleRows(3) == 3 && SelectPlan.visibleRows(6) == 6 && SelectPlan.visibleRows(40) == 6, "visibleRows");
        ok(!SelectPlan.scrolls(6) && SelectPlan.scrolls(7), "scrolls");
        ok(SelectPlan.popupHeight(3, 100) == 300 && SelectPlan.popupHeight(10, 100) == 600 && SelectPlan.popupHeight(0, 100) == 0 && SelectPlan.popupHeight(3, -5) == 0, "popupHeight");
        // width
        ok(SelectPlan.popupWidth(200, 150, 1080, 16, 0) == 200, "width = pill when content smaller");
        ok(SelectPlan.popupWidth(200, 500, 1080, 16, 0) == 500, "width = content");
        ok(SelectPlan.popupWidth(200, 5000, 1080, 16, 0) == 1048, "width never wider than screen minus margins");
        ok(SelectPlan.popupWidth(100, 100, 1080, 16, 300) == 300, "minW");
        ok(SelectPlan.popupWidth(100, 100, 200, 16, 300) == 168, "minW cannot beat the screen");
        ok(SelectPlan.popupWidth(0, 0, 0, 0, 0) == 1, "degenerate width >= 1");
        // x
        ok(SelectPlan.popupX(100, 300, 1080, 16) == 100, "x at pill");
        ok(SelectPlan.popupX(900, 300, 1080, 16) == 764, "x pushed back inside right edge");
        ok(SelectPlan.popupX(-50, 300, 1080, 16) == 16, "x not left of margin");
        // below / y
        ok(SelectPlan.below(200, 100, 400, 2000, 8), "fits below");
        ok(!SelectPlan.below(1700, 100, 400, 2000, 8), "no room below, room above -> above");
        ok(SelectPlan.below(500, 100, 2000, 1000, 8) == (1000 - 600 - 8 >= 500 - 8), "neither fits: the bigger side");
        ok(SelectPlan.popupY(200, 100, 400, 2000, 8, 16) == 308, "y below");
        ok(SelectPlan.popupY(1700, 100, 400, 2000, 8, 16) == 1292, "y above");
        int y = SelectPlan.popupY(500, 100, 2000, 1000, 8, 16);
        ok(y >= 16, "y not above the top margin even when too tall");
        int y2 = SelectPlan.popupY(0, 50, 300, 1000, 8, 16);
        ok(y2 == 58, "y below at the very top");
        int y3 = SelectPlan.popupY(950, 50, 300, 1000, 8, 16);
        ok(y3 + 300 <= 1000 - 16, "y keeps the bottom margin");
        System.out.println("SelectPlanTest: " + (total - fail) + "/" + total + (fail == 0 ? " OK" : " FAILED"));
        if (fail > 0) System.exit(1);
    }
}
