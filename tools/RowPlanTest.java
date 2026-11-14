import com.mgb.keyboard.typing.RowPlan;

/** v0.17.76 (U2) RowPlan rules (computer only). */
public class RowPlanTest {
    static int fail = 0, total = 0;
    static void t(String m, boolean ok) { total++; if (!ok) { fail++; System.out.println("FAIL " + m); } }

    public static void main(String[] a) {
        t("2 short = segmented", RowPlan.segmented(new String[]{"Off", "On"}));
        t("4 short = segmented", RowPlan.segmented(new String[]{"Low", "Mid", "High", "Max"}));
        t("5 options = chip + list", !RowPlan.segmented(new String[]{"a", "b", "c", "d", "e"}));
        t("3 long names = chip + list", !RowPlan.segmented(new String[]{"Follow the system", "Always light", "Always dark"}));
        t("one name over 14 chars = chip + list", !RowPlan.segmented(new String[]{"Short", "A very long option name"}));
        t("total over 30 chars = chip + list", !RowPlan.segmented(new String[]{"Aaaaaaaaaaaa", "Bbbbbbbbbbbb", "Cccccccccccc"}));
        t("1 option / empty / null = chip", !RowPlan.segmented(new String[]{"Only"}) && !RowPlan.segmented(new String[0]) && !RowPlan.segmented(null));
        t("null name does not crash", RowPlan.segmented(new String[]{null, "x"}));
        t("clamp", RowPlan.clamp(-3, 4) == 0 && RowPlan.clamp(9, 4) == 3 && RowPlan.clamp(2, 4) == 2 && RowPlan.clamp(5, 0) == 0);
        t("optionDesc", RowPlan.optionDesc("On", true).equals("On, selected") && RowPlan.optionDesc("Off", false).equals("Off") && RowPlan.optionDesc(null, false).equals(""));
        System.out.println("RowPlanTest: " + (total - fail) + "/" + total + (fail == 0 ? " OK" : " FAILED"));
        if (fail > 0) System.exit(1);
    }
}
