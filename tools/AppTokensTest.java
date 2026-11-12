import com.mgb.keyboard.typing.AppTokens;

/** Run: javac -d out app/src/main/java/com/mgb/keyboard/typing/AppTokens.java tools/AppTokensTest.java && java -cp out AppTokensTest */
public class AppTokensTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (java.util.Objects.equals(a, b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }
    public static void main(String[] x) {
        eq("S1", AppTokens.S1, 4); eq("S2", AppTokens.S2, 8); eq("S3", AppTokens.S3, 12); eq("S4", AppTokens.S4, 16);
        eq("touch", AppTokens.TOUCH, 48);
        eq("scale 1", AppTokens.ms(180, 1f), 180);
        eq("scale 0 = off", AppTokens.ms(180, 0f), 0);
        eq("scale negative = off", AppTokens.ms(180, -1f), 0);
        eq("scale NaN = off", AppTokens.ms(180, Float.NaN), 0);
        eq("scale 0.5", AppTokens.ms(180, 0.5f), 90);
        eq("scale tiny stays >= 1", AppTokens.ms(180, 0.001f), 1);
        eq("scale 10 capped", AppTokens.ms(180, 10f), AppTokens.MAX_MS);
        eq("base 0", AppTokens.ms(0, 1f), 0);
        eq("base negative", AppTokens.ms(-5, 1f), 0);
        eq("animate 1", AppTokens.animate(1f), true);
        eq("animate 0", AppTokens.animate(0f), false);
        eq("animate NaN", AppTokens.animate(Float.NaN), false);
        eq("slide 3x", AppTokens.slidePx(3f), 24);
        eq("slide bad density", AppTokens.slidePx(0f), 0);
        eq("slide NaN", AppTokens.slidePx(Float.NaN), 0);
        for (float s = 0f; s <= 20f; s += 0.25f) {
            int m = AppTokens.ms(AppTokens.PAGE_MS, s);
            eq("range at " + s, m >= 0 && m <= AppTokens.MAX_MS, true);
            eq("off only at 0 at " + s, (m == 0) == (s <= 0f), true);
        }
        System.out.println("AppTokensTest: " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }
}
