import com.mgb.keyboard.layout.*;

/** v0.17.7 (K5b) swipe direction of a finger move. */
public class SwipeDirTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] a) {
        float m = 40f;
        t("up", SwipeDir.classify(0, -60, m) == SwipeDir.UP);
        t("down", SwipeDir.classify(0, 60, m) == SwipeDir.DOWN);
        t("left", SwipeDir.classify(-60, 0, m) == SwipeDir.LEFT);
        t("right", SwipeDir.classify(60, 0, m) == SwipeDir.RIGHT);
        t("tap", SwipeDir.classify(0, 0, m) == SwipeDir.NONE);
        t("tiny move", SwipeDir.classify(5, -8, m) == SwipeDir.NONE);
        t("just under min", SwipeDir.classify(0, -39.9f, m) == SwipeDir.NONE);
        t("exactly min", SwipeDir.classify(0, -40f, m) == SwipeDir.UP);
        t("up with small drift", SwipeDir.classify(10, -60, m) == SwipeDir.UP);
        t("left with small drift", SwipeDir.classify(-60, 12, m) == SwipeDir.LEFT);
        t("right with drift up", SwipeDir.classify(55, -20, m) == SwipeDir.RIGHT);
        t("diagonal = none", SwipeDir.classify(50, -50, m) == SwipeDir.NONE);
        t("diagonal 2", SwipeDir.classify(-45, 50, m) == SwipeDir.NONE);
        t("ratio edge horizontal", SwipeDir.classify(56, 40, m) == SwipeDir.RIGHT);
        t("ratio edge diagonal", SwipeDir.classify(55, 40, m) == SwipeDir.NONE);
        t("ratio edge vertical", SwipeDir.classify(40, -56, m) == SwipeDir.UP);
        t("huge", SwipeDir.classify(0, -100000, m) == SwipeDir.UP);
        t("NaN dx", SwipeDir.classify(Float.NaN, -60, m) == SwipeDir.NONE);
        t("NaN dy", SwipeDir.classify(60, Float.NaN, m) == SwipeDir.NONE);
        t("inf dx", SwipeDir.classify(Float.POSITIVE_INFINITY, 0, m) == SwipeDir.NONE);
        t("inf dy", SwipeDir.classify(0, Float.NEGATIVE_INFINITY, m) == SwipeDir.NONE);
        t("min 0 = default dp", SwipeDir.classify(0, -30, 0f) == SwipeDir.UP);
        t("min 0 small", SwipeDir.classify(0, -10, 0f) == SwipeDir.NONE);
        t("min NaN = default", SwipeDir.classify(30, 0, Float.NaN) == SwipeDir.RIGHT);
        t("min negative = default", SwipeDir.classify(-30, 0, -5f) == SwipeDir.LEFT);
        // minPx
        t("minPx 1", Math.abs(SwipeDir.minPx(1f) - 22f) < 0.001f);
        t("minPx 3", Math.abs(SwipeDir.minPx(3f) - 66f) < 0.001f);
        t("minPx 0 = 1", Math.abs(SwipeDir.minPx(0f) - 22f) < 0.001f);
        t("minPx NaN", Math.abs(SwipeDir.minPx(Float.NaN) - 22f) < 0.001f);
        t("minPx negative", Math.abs(SwipeDir.minPx(-2f) - 22f) < 0.001f);
        t("minPx inf", Math.abs(SwipeDir.minPx(Float.POSITIVE_INFINITY) - 22f) < 0.001f);
        // index = KeyDef.sw order
        t("index up", SwipeDir.index(SwipeDir.UP) == 0);
        t("index down", SwipeDir.index(SwipeDir.DOWN) == 1);
        t("index left", SwipeDir.index(SwipeDir.LEFT) == 2);
        t("index right", SwipeDir.index(SwipeDir.RIGHT) == 3);
        t("index none", SwipeDir.index(SwipeDir.NONE) == -1);
        t("index 9", SwipeDir.index(9) == -1);
        // textFor / hasAny
        KeyDef k = KeyDef.ch("k", "q", 1f);
        t("no swipes", !SwipeDir.hasAny(k));
        k.sw[0] = "1"; k.sw[3] = "@tab";
        t("has swipes", SwipeDir.hasAny(k));
        t("text up", SwipeDir.textFor(k, SwipeDir.UP).equals("1"));
        t("text right action", SwipeDir.textFor(k, SwipeDir.RIGHT).equals("@tab"));
        t("text down empty", SwipeDir.textFor(k, SwipeDir.DOWN).equals(""));
        t("text none", SwipeDir.textFor(k, SwipeDir.NONE).equals(""));
        t("text null key", SwipeDir.textFor(null, SwipeDir.UP).equals(""));
        t("hasAny null", !SwipeDir.hasAny(null));
        KeyDef n = KeyDef.ch("n", "w", 1f);
        n.sw[1] = null;
        t("null entry", SwipeDir.textFor(n, SwipeDir.DOWN).equals(""));
        KeyDef bang = KeyDef.ch("b", "\u0995", 1f);
        bang.sw[2] = "\u0995\u09CD\u09B7";
        t("bangla conjunct kept", SwipeDir.textFor(bang, SwipeDir.LEFT).equals("\u0995\u09CD\u09B7"));
        // every direction of a symmetric move
        for (int i = 1; i <= 50; i++) {
            float d = 40f + i;
            t("sym up " + i, SwipeDir.classify(0, -d, m) == SwipeDir.UP);
            t("sym right " + i, SwipeDir.classify(d, 0, m) == SwipeDir.RIGHT);
        }
        System.out.println("SwipeDirTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
