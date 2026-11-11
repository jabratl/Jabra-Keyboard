import com.mgb.keyboard.typing.TipPlace;

public class TipPlaceTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    public static void main(String[] a) {
        eq("hold 300", TipPlace.HOLD_MS, 300L);
        eq("text null", TipPlace.text(null), "");
        eq("text blank", TipPlace.text("   "), "");
        eq("text trim", TipPlace.text("  Clipboard "), "Clipboard");
        eq("text keep", TipPlace.text("Mode (Phone / PC)"), "Mode (Phone / PC)");
        String l = "123456789012345678901234567890";
        eq("text cut len", TipPlace.text(l).length(), TipPlace.MAX_CHARS);
        eq("text cut dots", TipPlace.text(l).endsWith("..."), true);
        eq("text exact max", TipPlace.text("1234567890123456789012345678"), "1234567890123456789012345678");
        eq("show on", TipPlace.shouldShow(true, "Cut"), true);
        eq("show off", TipPlace.shouldShow(false, "Cut"), false);
        eq("show empty", TipPlace.shouldShow(true, " "), false);
        eq("show null", TipPlace.shouldShow(true, null), false);
        // x: centred, clamped
        eq("x centre", TipPlace.x(100, 40, 80, 400), 80);
        eq("x left edge", TipPlace.x(0, 40, 120, 400), 0);
        eq("x right edge", TipPlace.x(380, 40, 120, 400), 280);
        eq("x tip wider than screen", TipPlace.x(10, 40, 500, 400), 0);
        eq("x exact", TipPlace.x(180, 40, 40, 400), 180);
        for (int k = 0; k <= 360; k += 20) {
            int x = TipPlace.x(k, 44, 150, 400);
            eq("x in range " + k, x >= 0 && x + 150 <= 400, true);
        }
        // y: above key, never negative
        eq("y above", TipPlace.y(300, 40, 6), 254);
        eq("y top clamp", TipPlace.y(20, 40, 6), 0);
        eq("y zero", TipPlace.y(46, 40, 6), 0);
        eq("alpha range", TipPlace.ALPHA_FILL > 0x80 && TipPlace.ALPHA_FILL < 0xFF, true);
        System.out.println("TipPlaceTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
