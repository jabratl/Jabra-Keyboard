import com.mgb.keyboard.typing.ClipMockPlan;
import com.mgb.keyboard.typing.ClipFilter;

public class ClipMockPlanTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] a) {
        t("short not expandable", !ClipMockPlan.expandable("hello"));
        t("38 chars not expandable", !ClipMockPlan.expandable("x".repeat(38)));
        t("39 chars expandable", ClipMockPlan.expandable("x".repeat(39)));
        t("line break expandable", ClipMockPlan.expandable("a\nb"));
        t("null not expandable", !ClipMockPlan.expandable(null));
        t("oneLine", ClipMockPlan.oneLine("a\nb").equals("a \u23CE b"));
        t("oneLine null", ClipMockPlan.oneLine(null).equals(""));
        t("swipe right", ClipMockPlan.swipe(81f, 1f) == 1);
        t("swipe left", ClipMockPlan.swipe(-81f, 1f) == -1);
        t("swipe small", ClipMockPlan.swipe(79f, 1f) == 0 && ClipMockPlan.swipe(-79f, 1f) == 0);
        t("swipe density", ClipMockPlan.swipe(200f, 3f) == 0 && ClipMockPlan.swipe(241f, 3f) == 1);
        t("swipe bad density", ClipMockPlan.swipe(81f, 0f) == 1);
        t("drag clamp", ClipMockPlan.drag(500f, 1f) == 110f && ClipMockPlan.drag(-500f, 1f) == -110f && ClipMockPlan.drag(20f, 1f) == 20f);
        t("hold fallback", ClipMockPlan.holdMs(0) == 450 && ClipMockPlan.holdMs(1500) == 1500 && ClipMockPlan.holdMs(-1) == 450);
        t("strip ok", ClipMockPlan.stripShown(true, ClipFilter.ALL, false, false, 2));
        t("strip off pref", !ClipMockPlan.stripShown(false, ClipFilter.ALL, false, false, 2));
        t("strip not on Links", !ClipMockPlan.stripShown(true, ClipFilter.LINKS, false, false, 2));
        t("strip not searching", !ClipMockPlan.stripShown(true, ClipFilter.ALL, true, false, 2));
        t("strip not select", !ClipMockPlan.stripShown(true, ClipFilter.ALL, false, true, 2));
        t("strip needs pins", !ClipMockPlan.stripShown(true, ClipFilter.ALL, false, false, 0));
        t("tabText", ClipMockPlan.tabText("All", 12).equals("All 12") && ClipMockPlan.tabText(null, 1).equals(" 1") && ClipMockPlan.tabText("All", -1).equals("All"));
        int[] o = ClipMockPlan.order(new long[] { 0, 5, 9, 0, 5 });
        t("order known first newest first, ties stable, unknown last in order", java.util.Arrays.equals(o, new int[] { 2, 1, 4, 0, 3 }));
        t("order all unknown = identity", java.util.Arrays.equals(ClipMockPlan.order(new long[] { 0, 0, 0 }), new int[] { 0, 1, 2 }));
        t("order empty / null", ClipMockPlan.order(new long[0]).length == 0 && ClipMockPlan.order(null).length == 0);
        t("order single", java.util.Arrays.equals(ClipMockPlan.order(new long[] { 7 }), new int[] { 0 }));
        System.out.println("ClipMockPlanTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
