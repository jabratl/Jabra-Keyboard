import com.mgb.keyboard.typing.BottomPlan;
import com.mgb.keyboard.typing.SpacePlan;

/** v0.17.2 (K2). Run: javac -d out app/src/main/java/com/mgb/keyboard/typing/BottomPlan.java app/src/main/java/com/mgb/keyboard/typing/SpacePlan.java tools/SpacePlanTest.java && java -cp out SpacePlanTest */
public class SpacePlanTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (a.equals(b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }
    static void near(String n, float a, float b) { if (Math.abs(a - b) < 0.001f) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }
    static void yes(String n, boolean c) { eq(n, c, true); }

    public static void main(String[] x) {
        // ---- sanitize
        eq("w 100", SpacePlan.sanitizeW(100), 100);
        eq("w min", SpacePlan.sanitizeW(60), 60);
        eq("w max", SpacePlan.sanitizeW(200), 200);
        eq("w below", SpacePlan.sanitizeW(59), 100);
        eq("w above", SpacePlan.sanitizeW(201), 100);
        eq("w unset -1", SpacePlan.sanitizeW(-1), 100);
        eq("w zero", SpacePlan.sanitizeW(0), 100);
        eq("w int max", SpacePlan.sanitizeW(Integer.MAX_VALUE), 100);
        eq("w int min", SpacePlan.sanitizeW(Integer.MIN_VALUE), 100);
        eq("h 100", SpacePlan.sanitizeH(100), 100);
        eq("h min", SpacePlan.sanitizeH(80), 80);
        eq("h max", SpacePlan.sanitizeH(160), 160);
        eq("h below", SpacePlan.sanitizeH(79), 100);
        eq("h above", SpacePlan.sanitizeH(161), 100);
        eq("h unset", SpacePlan.sanitizeH(-1), 100);
        yes("isDefault 100/100", SpacePlan.isDefault(100, 100));
        yes("isDefault unset", SpacePlan.isDefault(-1, -1));
        eq("isDefault w 120", SpacePlan.isDefault(120, 100), false);
        eq("isDefault h 90", SpacePlan.isDefault(100, 90), false);

        // ---- width: 100 % = the old number, exactly
        eq("100 % exact old", SpacePlan.spaceWeight(5f, 100, 5.9f, 1f, 360f), 5f);
        eq("unset exact old", SpacePlan.spaceWeight(5f, -1, 5.9f, 1f, 360f), 5f);
        eq("junk exact old", SpacePlan.spaceWeight(3.3f, 999, 5.9f, 1f, 360f), 3.3f);
        // narrower
        near("60 %", SpacePlan.spaceWeight(5f, 60, 5.9f, 1f, 360f), 3f);
        near("80 %", SpacePlan.spaceWeight(5f, 80, 5.9f, 1f, 360f), 4f);
        near("floor 1.5", SpacePlan.spaceWeight(2f, 60, 5.9f, 1f, 360f), 1.5f);
        near("base below floor stays base", SpacePlan.spaceWeight(1.2f, 60, 5.9f, 1f, 360f), 1.2f);
        // wider
        near("120 %", SpacePlan.spaceWeight(5f, 120, 5.9f, 1f, 411f), 6f);
        near("150 %", SpacePlan.spaceWeight(5f, 150, 5.9f, 1f, 411f), 7.5f);
        // 360 dp: total <= 1 * 360 / 24 = 15 -> space <= 15 - 5.9 = 9.1 ; 200 % of 5 = 10 -> capped
        near("200 % capped at 360 dp", SpacePlan.spaceWeight(5f, 200, 5.9f, 1f, 360f), 9.1f);
        near("200 % free at 411 dp", SpacePlan.spaceWeight(5f, 200, 5.9f, 1f, 411f), 10f);   // 411 / 24 = 17.1 - 5.9 = 11.2 >= 10
        near("200 % landscape", SpacePlan.spaceWeight(5f, 200, 5.9f, 1f, 800f), 10f);
        // very narrow screen: the cap is below base -> base (never narrower than before)
        near("narrow screen keeps base", SpacePlan.spaceWeight(5f, 200, 5.9f, 1f, 200f), 5f);
        // a lighter other key (0.5) makes the cap stricter
        near("light key stricter", SpacePlan.spaceWeight(5f, 200, 5.4f, 0.5f, 360f), 5f);
        // bad numbers
        near("NaN base", Float.isNaN(SpacePlan.spaceWeight(Float.NaN, 150, 5f, 1f, 360f)) ? -1f : 0f, -1f);
        near("zero base", SpacePlan.spaceWeight(0f, 150, 5f, 1f, 360f), 0f);
        near("negative base", SpacePlan.spaceWeight(-2f, 150, 5f, 1f, 360f), -2f);
        near("zero others = no cap", SpacePlan.spaceWeight(5f, 150, 0f, 1f, 360f), 7.5f);
        near("zero row dp = no cap", SpacePlan.spaceWeight(5f, 150, 5.9f, 1f, 0f), 7.5f);
        near("zero minOther = no cap", SpacePlan.spaceWeight(5f, 150, 5.9f, 0f, 360f), 7.5f);

        // ---- brute force: every pct step, both styles, key shown / hidden, other keys on / off
        boolean allOk = true;
        int cases = 0;
        for (int style = 0; style <= 1; style++)
            for (int q = 0; q <= 1; q++)
                for (int opt = 0; opt < 16; opt++) {
                    float others = BottomPlan.symWeight(style) + BottomPlan.enterWeight(style) + 1f + 1f;   // ?123, Enter, comma, period
                    float minOther = 1f;
                    if ((opt & 1) != 0) others += BottomPlan.langWeight(style);
                    if ((opt & 2) != 0) others += 1f;   // globe
                    if ((opt & 4) != 0) others += 1f;   // voice
                    if ((opt & 8) != 0) others += 1f;   // emoji
                    if (q == 1) others += 1f;   // ? key
                    float base = BottomPlan.spaceWeight(style, others, q == 1);
                    for (int pct = SpacePlan.W_MIN; pct <= SpacePlan.W_MAX; pct += SpacePlan.W_STEP)
                        for (float dp : new float[] { 320f, 360f, 411f, 600f, 800f }) {
                            float w = SpacePlan.spaceWeight(base, pct, others, minOther, dp);
                            cases++;
                            if (Float.isNaN(w) || w < Math.min(SpacePlan.SPACE_FLOOR, base) - 0.0001f) allOk = false;
                            if (pct >= 100 && w < base - 0.0001f) allOk = false;
                            if (pct <= 100 && w > base + 0.0001f) allOk = false;
                            if (pct == 100 && w != base) allOk = false;
                            if (pct > 100 && w > base + 0.0001f) {   // widened: the narrowest key keeps >= 24 dp
                                float keyDp = minOther / (others + w) * dp;
                                if (keyDp < SpacePlan.MIN_KEY_DP - 0.01f) allOk = false;
                            }
                        }
                }
        yes("brute force " + cases + " cases", allOk);

        // ---- height
        eq("h 100 = keyDp", SpacePlan.heightDp(48, 100), 48);
        eq("h 120", SpacePlan.heightDp(48, 120), 58);   // 57.6
        eq("h 80", SpacePlan.heightDp(48, 80), 38);   // 38.4
        eq("h 160", SpacePlan.heightDp(48, 160), 77);   // 76.8
        eq("h junk = keyDp", SpacePlan.heightDp(48, 5), 48);
        eq("h tiny key", SpacePlan.heightDp(1, 80), 1);
        eq("h zero key", SpacePlan.heightDp(0, 80), 1);
        eq("h big key", SpacePlan.heightDp(64, 160), 102);

        // ---- swipe progress
        near("p 0", SpacePlan.progress(0f, 44f), 0f);
        near("p half", SpacePlan.progress(22f, 44f), 0.5f);
        near("p neg half", SpacePlan.progress(-22f, 44f), -0.5f);
        near("p one", SpacePlan.progress(44f, 44f), 1f);
        near("p clamp +", SpacePlan.progress(500f, 44f), 1f);
        near("p clamp -", SpacePlan.progress(-500f, 44f), -1f);
        near("p zero step", SpacePlan.progress(10f, 0f), 0f);
        near("p neg step", SpacePlan.progress(10f, -5f), 0f);
        near("p NaN", SpacePlan.progress(Float.NaN, 44f), 0f);
        near("p inf", SpacePlan.progress(Float.POSITIVE_INFINITY, 44f), 0f);

        // ---- label slide
        near("slide 0", SpacePlan.slidePx(0f, 800f, 3f), 0f);
        near("slide full wide bar = 48 dp", SpacePlan.slidePx(1f, 800f, 3f), 144f);   // min(144, 200)
        near("slide full narrow bar = quarter", SpacePlan.slidePx(1f, 300f, 3f), 75f);   // min(144, 75)
        near("slide neg", SpacePlan.slidePx(-0.5f, 800f, 3f), -72f);
        near("slide clamp", SpacePlan.slidePx(5f, 800f, 3f), 144f);
        near("slide NaN", SpacePlan.slidePx(Float.NaN, 800f, 3f), 0f);
        near("slide zero bar", SpacePlan.slidePx(1f, 0f, 3f), 0f);
        near("slide zero density", SpacePlan.slidePx(1f, 800f, 0f), 0f);

        // ---- animation switch + tap cancel
        yes("anim on", SpacePlan.animOn(true, true, 1f));
        eq("anim: space swipe off", SpacePlan.animOn(false, true, 1f), false);
        eq("anim: pref off", SpacePlan.animOn(true, false, 1f), false);
        eq("anim: system animations off", SpacePlan.animOn(true, true, 0f), false);
        yes("anim: scale 0.5 still on", SpacePlan.animOn(true, true, 0.5f));
        yes("cancel at one step", SpacePlan.cancelsTap(44f, 44f));
        yes("cancel past one step", SpacePlan.cancelsTap(120f, 44f));
        eq("no cancel short travel", SpacePlan.cancelsTap(10f, 44f), false);
        eq("no cancel zero step", SpacePlan.cancelsTap(10f, 0f), false);
        eq("no cancel zero travel", SpacePlan.cancelsTap(0f, 44f), false);

        System.out.println("SpacePlanTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
