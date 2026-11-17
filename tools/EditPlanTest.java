import com.mgb.keyboard.typing.EditPlan;

/** v0.15.8 (N8): EditPlan = typed stepper value (clamped), which rows a reset touches, reset / undo texts. */
public class EditPlanTest {
    static int ok = 0, bad = 0;
    static void check(String name, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + name); } }
    static boolean is(Integer v, int want) { return v != null && v == want; }

    public static void main(String[] a) {
        // --- parseValue: plain numbers inside the range ---
        check("70", is(EditPlan.parseValue("70", 40, 140), 70));
        check("min", is(EditPlan.parseValue("40", 40, 140), 40));
        check("max", is(EditPlan.parseValue("140", 40, 140), 140));
        check("spaces around", is(EditPlan.parseValue("  85  ", 40, 140), 85));
        check("zero allowed when range starts at 0", is(EditPlan.parseValue("0", 0, 6), 0));
        check("leading zeros", is(EditPlan.parseValue("007", 0, 140), 7));
        // --- clamped ---
        check("999 in 40-140 -> 140", is(EditPlan.parseValue("999", 40, 140), 140));
        check("5 in 40-140 -> 40", is(EditPlan.parseValue("5", 40, 140), 40));
        check("0 in 70-150 -> 70", is(EditPlan.parseValue("0", 70, 150), 70));
        check("huge number -> max", is(EditPlan.parseValue("99999999999999999999", 0, 6), 6));
        check("negative below min -> min", is(EditPlan.parseValue("-5", 0, 6), 0));
        check("negative inside a negative range", is(EditPlan.parseValue("-3", -10, 10), -3));
        check("unicode minus", is(EditPlan.parseValue("\u22123", -10, 10), -3));
        // --- unit / decimal / junk after the number is ignored ---
        check("unit %", is(EditPlan.parseValue("90%", 40, 140), 90));
        check("unit dp", is(EditPlan.parseValue("4 dp", 0, 6), 4));
        check("decimal part ignored", is(EditPlan.parseValue("5.7", 0, 10), 5));
        check("two numbers: first one", is(EditPlan.parseValue("12 34", 0, 100), 12));
        // --- Bangla digits ---
        check("Bangla digits", is(EditPlan.parseValue("\u09E7\u09E6\u09E6", 40, 140), 100));
        // --- nothing usable ---
        check("null", EditPlan.parseValue(null, 0, 10) == null);
        check("empty", EditPlan.parseValue("", 0, 10) == null);
        check("blank", EditPlan.parseValue("   ", 0, 10) == null);
        check("letters", EditPlan.parseValue("abc", 0, 10) == null);
        check("only minus", EditPlan.parseValue("-", -5, 5) == null);
        check("letters then digits", EditPlan.parseValue("x5", 0, 10) == null);
        // --- swapped range ---
        check("swapped range still clamps", is(EditPlan.parseValue("999", 140, 40), 140));

        // --- changedIndexes ---
        check("none changed", EditPlan.changedIndexes(new int[] { -1, 2, 0 }, new int[] { -1, 2, 0 }).length == 0);
        int[] ch = EditPlan.changedIndexes(new int[] { 100, -1, 1, 3 }, new int[] { -1, -1, 0, 3 });
        check("changed: saved value != default", ch.length == 2 && ch[0] == 0 && ch[1] == 2);
        check("unset (-1) is never changed", EditPlan.changedIndexes(new int[] { -1 }, new int[] { 5 }).length == 0);
        check("null cur", EditPlan.changedIndexes(null, new int[] { 1 }).length == 0);
        check("null def", EditPlan.changedIndexes(new int[] { 1 }, null).length == 0);
        check("different lengths use the shorter", EditPlan.changedIndexes(new int[] { 1, 1, 1 }, new int[] { 0 }).length == 1);
        check("empty", EditPlan.changedIndexes(new int[0], new int[0]).length == 0);

        // --- texts ---
        check("done 0", EditPlan.doneText(0).startsWith("Nothing to reset"));
        check("done 1", EditPlan.doneText(1).equals("Reset 1 setting"));
        check("done 4", EditPlan.doneText(4).equals("Reset 4 settings"));
        check("undo text", EditPlan.undoText("Spacing", 3).equals("Undo reset: Spacing (3 settings)"));
        check("undo text 1", EditPlan.undoText("Size", 1).equals("Undo reset: Size (1 setting)"));
        check("undo text null name", EditPlan.undoText(null, 2).equals("Undo reset: this page (2 settings)"));

        System.out.println("EditPlanTest " + ok + "/" + (ok + bad) + (bad == 0 ? " OK" : " FAILED"));
        if (bad > 0) System.exit(1);
    }
}
