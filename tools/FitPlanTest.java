import com.mgb.keyboard.typing.FitPlan;

/** v0.10.21 (D4) tests: font cap + wide-screen column + clamps. */
public class FitPlanTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static boolean near(float a, float b) { return Math.abs(a - b) < 0.001f; }

    public static void main(String[] a) {
        // font scale
        t("scale 1.0 no cap", !FitPlan.needsCap(1.0f));
        t("scale 0.85 no cap", !FitPlan.needsCap(0.85f));
        t("scale 1.15 no cap (edge)", !FitPlan.needsCap(1.15f));
        t("scale 1.16 cap", FitPlan.needsCap(1.16f));
        t("scale 1.3 cap", FitPlan.needsCap(1.3f));
        t("scale 2.0 cap", FitPlan.needsCap(2.0f));
        t("NaN no cap", !FitPlan.needsCap(Float.NaN));
        t("0 / negative no cap", !FitPlan.needsCap(0f) && !FitPlan.needsCap(-2f));
        t("absurd scale no cap", !FitPlan.needsCap(1000f));
        t("sp unchanged at 1.0", near(FitPlan.cappedSp(12f, 1.0f), 12f));
        t("sp unchanged at 1.15", near(FitPlan.cappedSp(12f, 1.15f), 12f));
        t("sp 12 @1.3 -> final 13.8", near(FitPlan.cappedSp(12f, 1.3f) * 1.3f, 12f * 1.15f));
        t("sp 14 @2.0 -> final 16.1", near(FitPlan.cappedSp(14f, 2.0f) * 2.0f, 14f * 1.15f));
        t("sp 11 @1.5 -> final 12.65", near(FitPlan.cappedSp(11f, 1.5f) * 1.5f, 11f * 1.15f));
        t("capped sp smaller than input when scale large", FitPlan.cappedSp(12f, 2.0f) < 12f);
        t("sp 0 / negative unchanged", near(FitPlan.cappedSp(0f, 2f), 0f) && near(FitPlan.cappedSp(-1f, 2f), -1f));
        t("final size never above cap for many scales", maxOk());
        // width
        t("360dp phone no wrap", !FitPlan.shouldWrap(360) && FitPlan.maxContentWidthDp(360) == 360);
        t("411dp phone no wrap", FitPlan.maxContentWidthDp(411) == 411);
        t("700dp edge no wrap", !FitPlan.shouldWrap(700) && FitPlan.maxContentWidthDp(700) == 700);
        t("701dp wraps", FitPlan.shouldWrap(701) && FitPlan.maxContentWidthDp(701) == 600);
        t("tablet 1280dp column 600", FitPlan.maxContentWidthDp(1280) == 600);
        t("zero / negative width = 1", FitPlan.maxContentWidthDp(0) == 1 && FitPlan.maxContentWidthDp(-5) == 1);
        t("column never wider than screen", allColumnsFit());
        // clamps
        t("clamp inside", FitPlan.clamp(5, 0, 10) == 5);
        t("clamp low / high", FitPlan.clamp(-3, 0, 10) == 0 && FitPlan.clamp(30, 0, 10) == 10);
        t("clamp lo > hi = lo", FitPlan.clamp(5, 8, 2) == 8);
        t("clampLeft inside", FitPlan.clampLeft(100, 50, 400) == 100);
        t("clampLeft left edge", FitPlan.clampLeft(-20, 50, 400) == 0);
        t("clampLeft right edge", FitPlan.clampLeft(380, 50, 400) == 350);
        t("clampLeft box wider than screen = 0", FitPlan.clampLeft(30, 500, 400) == 0 && FitPlan.clampLeft(30, 400, 400) == 0);
        System.out.println("FitPlanTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
    static boolean maxOk() { for (float fs = 1.16f; fs <= 3f; fs += 0.1f) if (FitPlan.cappedSp(12f, fs) * fs > 12f * 1.15f + 0.01f) return false; return true; }
    static boolean allColumnsFit() { for (int w = 1; w <= 2000; w++) if (FitPlan.maxContentWidthDp(w) > w) return false; return true; }
}
