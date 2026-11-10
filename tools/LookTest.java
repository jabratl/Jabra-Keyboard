import com.mgb.keyboard.typing.LookColors;

/** v0.1.5 logic tests.  java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/LookTest.java ; java -cp out LookTest */
public class LookTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    public static void main(String[] a) {
        t("classic func", LookColors.funcFill(0) == 0xFFC3C9D0);
        t("blue func", LookColors.funcFill(1) == 0xFFB5CFE8);
        t("mint func", LookColors.funcFill(2) == 0xFFB4DCC8);
        t("sand func", LookColors.funcFill(3) == 0xFFE0D2B8);
        t("dark func", LookColors.funcFill(4) == 0xFF2B2D31);
        t("unknown theme -> classic", LookColors.funcFill(99) == 0xFFC3C9D0 && LookColors.funcFill(-1) == 0xFFC3C9D0);
        t("darken 0 keeps", LookColors.darken(0xFFFFFFFF, 0f) == 0xFFFFFFFF);
        t("darken 1 = black, alpha kept", LookColors.darken(0xFFFFFFFF, 1f) == 0xFF000000);
        t("darken 20% white", LookColors.darken(0xFFFFFFFF, 0.2f) == 0xFFCCCCCC);
        t("darken keeps alpha", (LookColors.darken(0x80FFFFFF, 0.2f) >>> 24) == 0x80);
        t("darken clamps high", LookColors.darken(0xFF808080, 5f) == 0xFF000000);
        t("darken clamps low", LookColors.darken(0xFF808080, -3f) == 0xFF808080);
        t("darken channels", LookColors.darken(0xFF204060, 0.5f) == 0xFF102030);
        System.out.println("LookTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
