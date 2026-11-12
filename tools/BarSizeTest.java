import com.mgb.keyboard.typing.BarSize;

/** v0.16.6 (X6): toolbar size percentages. 100 = exactly the old look. */
public class BarSizeTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + name); } }

    public static void main(String[] a) {
        t("100 = old height 36", BarSize.heightDp(100) == 36);
        t("100 = old width 44", BarSize.widthDp(100) == 44);
        t("100 = old text 12", BarSize.textSp(100) == 12f);
        t("100 = old fold text 14", BarSize.foldSp(100) == 14f);
        t("unset -1 = old", BarSize.heightDp(-1) == 36 && BarSize.widthDp(-1) == 44 && BarSize.textSp(-1) == 12f && BarSize.foldSp(-1) == 14f);
        t("0 = old", BarSize.heightDp(0) == 36);
        t("too small 69 = old", BarSize.heightDp(69) == 36 && BarSize.sanitize(69) == 100);
        t("too big 151 = old", BarSize.heightDp(151) == 36 && BarSize.sanitize(151) == 100);
        t("huge = old", BarSize.heightDp(Integer.MAX_VALUE) == 36 && BarSize.heightDp(Integer.MIN_VALUE) == 36);
        t("70 height", BarSize.heightDp(70) == 25);
        t("150 height", BarSize.heightDp(150) == 54);
        t("70 width", BarSize.widthDp(70) == 31);
        t("150 width", BarSize.widthDp(150) == 66);
        t("70 text", Math.abs(BarSize.textSp(70) - 8.4f) < 0.001f);
        t("150 text", Math.abs(BarSize.textSp(150) - 18f) < 0.001f);
        t("150 fold text", Math.abs(BarSize.foldSp(150) - 21f) < 0.001f);
        t("valid edges", BarSize.valid(70) && BarSize.valid(150) && !BarSize.valid(69) && !BarSize.valid(151));
        t("sanitize keeps valid", BarSize.sanitize(85) == 85 && BarSize.sanitize(70) == 70 && BarSize.sanitize(150) == 150);
        int prev = 0, prevW = 0; boolean mono = true, big = true;
        for (int p = BarSize.MIN; p <= BarSize.MAX; p += BarSize.STEP) {
            int h = BarSize.heightDp(p), w = BarSize.widthDp(p);
            if (h < prev || w < prevW) mono = false;
            if (h < 20 || w < 28 || h > 60 || w > 70) big = false;
            prev = h; prevW = w;
        }
        t("never shrinks when percent grows", mono);
        t("always touchable and sane (20..60 high, 28..70 wide)", big);
        t("range 70..150 step 5", BarSize.MIN == 70 && BarSize.MAX == 150 && BarSize.STEP == 5 && BarSize.DEF == 100);
        t("base 36 / 44", BarSize.BASE_H_DP == 36 && BarSize.BASE_W_DP == 44);
        System.out.println("BarSizeTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
