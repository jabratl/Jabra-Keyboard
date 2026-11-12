import com.mgb.keyboard.typing.PanelSpec;

/** v0.10.11 (D2.1) PanelSpec tests (computer only). */
public class PanelSpecTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }

    public static void main(String[] a) {
        // scale values = what the panels use today
        t("GAP 2", PanelSpec.GAP == 2);
        t("spacing 4 / 8 / 12 / 16", PanelSpec.S1 == 4 && PanelSpec.S2 == 8 && PanelSpec.S3 == 12 && PanelSpec.S4 == 16);
        t("button 40, tab 38, edit 36, option 32, chip 30",
                PanelSpec.H_BTN == 40 && PanelSpec.H_TAB == 38 && PanelSpec.H_EDIT == 36 && PanelSpec.H_OPT == 32 && PanelSpec.H_CHIP == 30);
        t("text 11 / 12 / 14 / 16", PanelSpec.T_NOTE == 11f && PanelSpec.T_BTN == 12f && PanelSpec.T_BODY == 14f && PanelSpec.T_HEAD == 16f);
        t("scale is increasing", PanelSpec.GAP < PanelSpec.S1 && PanelSpec.S1 < PanelSpec.S2 && PanelSpec.S2 < PanelSpec.S3 && PanelSpec.S3 < PanelSpec.S4);
        t("text sizes increasing", PanelSpec.T_NOTE < PanelSpec.T_BTN && PanelSpec.T_BTN < PanelSpec.T_BODY && PanelSpec.T_BODY < PanelSpec.T_HEAD);
        // px
        t("px 40 @ 2.75 = 110", PanelSpec.px(40, 2.75f) == 110);
        t("px truncates 2 @ 2.625 = 5", PanelSpec.px(2, 2.625f) == 5);
        t("px 0 = 0", PanelSpec.px(0, 3f) == 0);
        t("px bad density 0 -> 3.0", PanelSpec.px(10, 0f) == 30);
        t("px negative density -> 3.0", PanelSpec.px(10, -1f) == 30);
        t("px NaN density -> 3.0", PanelSpec.px(10, Float.NaN) == 30);
        t("px infinite density -> 3.0", PanelSpec.px(10, Float.POSITIVE_INFINITY) == 30);
        t("px 1.0 = same", PanelSpec.px(36, 1f) == 36);
        // isSpacing / isHeight
        t("isSpacing true", PanelSpec.isSpacing(2) && PanelSpec.isSpacing(4) && PanelSpec.isSpacing(8) && PanelSpec.isSpacing(12) && PanelSpec.isSpacing(16));
        t("isSpacing false", !PanelSpec.isSpacing(0) && !PanelSpec.isSpacing(3) && !PanelSpec.isSpacing(6) && !PanelSpec.isSpacing(-4));
        t("isHeight true", PanelSpec.isHeight(30) && PanelSpec.isHeight(32) && PanelSpec.isHeight(36) && PanelSpec.isHeight(38) && PanelSpec.isHeight(40));
        t("isHeight false", !PanelSpec.isHeight(34) && !PanelSpec.isHeight(48) && !PanelSpec.isHeight(0));
        // snap
        t("snapSpacing exact", PanelSpec.snapSpacing(8) == 8 && PanelSpec.snapSpacing(2) == 2);
        t("snapSpacing 5 -> 4", PanelSpec.snapSpacing(5) == 4);
        t("snapSpacing 6 tie -> 8", PanelSpec.snapSpacing(6) == 8);
        t("snapSpacing 3 tie -> 4", PanelSpec.snapSpacing(3) == 4);
        t("snapSpacing 10 tie -> 12", PanelSpec.snapSpacing(10) == 12);
        t("snapSpacing 100 -> 16", PanelSpec.snapSpacing(100) == 16);
        t("snapSpacing negative -> GAP", PanelSpec.snapSpacing(-9) == PanelSpec.GAP);
        t("snapSpacing result always on scale", allSpacingOnScale());
        t("snapHeight exact", PanelSpec.snapHeight(40) == 40 && PanelSpec.snapHeight(30) == 30);
        t("snapHeight 34 tie -> 36", PanelSpec.snapHeight(34) == 36);
        t("snapHeight 48 -> 40", PanelSpec.snapHeight(48) == 40);
        t("snapHeight 0 -> 30", PanelSpec.snapHeight(0) == 30);
        t("snapHeight result always on scale", allHeightsOnScale());
        // touch
        t("touchOk all heights of the scale", PanelSpec.touchOk(30) && PanelSpec.touchOk(32) && PanelSpec.touchOk(36) && PanelSpec.touchOk(38) && PanelSpec.touchOk(40));
        t("touchOk too small", !PanelSpec.touchOk(29) && !PanelSpec.touchOk(0) && !PanelSpec.touchOk(-5));
        t("MIN_TOUCH is the smallest height", PanelSpec.MIN_TOUCH == PanelSpec.H_CHIP);
        // back button width
        t("back width: null / empty / short = 56", PanelSpec.backWidthDp(null) == 56 && PanelSpec.backWidthDp("") == 56 && PanelSpec.backWidthDp("<") == 56 && PanelSpec.backWidthDp("ABC") == 56);
        t("back width: '< More settings' (15 chars) = 136", PanelSpec.backWidthDp("< More settings") == 136);
        t("back width capped at 140", PanelSpec.backWidthDp("a very very long back label that goes on") == 140);
        t("back width never below 56", PanelSpec.backWidthDp("abcd") >= 56);

        System.out.println("PanelSpecTest: " + ok + " PASS, " + bad + " FAIL");
        if (bad > 0) System.exit(1);
    }

    static boolean allSpacingOnScale() {
        for (int v = -20; v <= 60; v++) if (!PanelSpec.isSpacing(PanelSpec.snapSpacing(v))) return false;
        return true;
    }
    static boolean allHeightsOnScale() {
        for (int v = -20; v <= 120; v++) if (!PanelSpec.isHeight(PanelSpec.snapHeight(v))) return false;
        return true;
    }
}
