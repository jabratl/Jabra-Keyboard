import com.mgb.keyboard.typing.BarInk;

/** v0.17.30 (UI6): readable tinted label colour for the Clipboard panel buttons. */
public class BarInkTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + name); } }

    static final int WHITE = 0xFFFFFFFF, BLACK = 0xFF000000;
    static final int RED_DARK = 0xFFB3261C, RED_LIGHT = 0xFFFF8A80;

    public static void main(String[] a) {
        t("lum white = 1", Math.abs(BarInk.lum(WHITE) - 1.0) < 1e-6);
        t("lum black = 0", Math.abs(BarInk.lum(BLACK)) < 1e-9);
        t("alpha ignored", Math.abs(BarInk.lum(0x00FFFFFF) - 1.0) < 1e-6);
        t("ratio white / black = 21", Math.abs(BarInk.ratio(WHITE, BLACK) - 21.0) < 1e-6);
        t("ratio order free", Math.abs(BarInk.ratio(WHITE, RED_DARK) - BarInk.ratio(RED_DARK, WHITE)) < 1e-9);
        t("ratio same colour = 1", Math.abs(BarInk.ratio(0xFF808080, 0xFF808080) - 1.0) < 1e-9);
        t("white key: dark red", BarInk.pick(WHITE, RED_DARK, RED_LIGHT) == RED_DARK);
        t("classic grey key (#F0F0F0): dark red", BarInk.pick(0xFFF0F0F0, RED_DARK, RED_LIGHT) == RED_DARK);
        t("dark key (#2B2B2B): light red", BarInk.pick(0xFF2B2B2B, RED_DARK, RED_LIGHT) == RED_LIGHT);
        t("black key: light red", BarInk.pick(BLACK, RED_DARK, RED_LIGHT) == RED_LIGHT);
        t("candidate order does not change the winner", BarInk.pick(WHITE, RED_LIGHT, RED_DARK) == RED_DARK);
        t("red key = nothing reads -> 0 (keep old)", BarInk.pick(0xFFD84315, RED_DARK, RED_LIGHT) == 0);
        { int g = BarInk.pick(0xFF808080, RED_DARK, RED_LIGHT); t("mid grey key: 0 or a colour that really reads", g == 0 || BarInk.ratio(0xFF808080, g) >= BarInk.MIN_RATIO); }
        t("accepted pick really reaches MIN_RATIO", BarInk.ratio(WHITE, BarInk.pick(WHITE, RED_DARK, RED_LIGHT)) >= BarInk.MIN_RATIO);
        t("accepted pick on dark reaches MIN_RATIO", BarInk.ratio(0xFF2B2B2B, BarInk.pick(0xFF2B2B2B, RED_DARK, RED_LIGHT)) >= BarInk.MIN_RATIO);
        System.out.println("BarInkTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
