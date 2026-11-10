import com.mgb.keyboard.typing.LookColors;

/** v0.1.5 logic tests.  java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/LookTest.java ; java -cp out LookTest */
public class LookTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static boolean allOpaque() { for (int i = 0; i <= 19; i++) if ((LookColors.funcFill(i) >>> 24) != 0xFF) return false; return true; }
    public static void main(String[] a) {
        t("classic func", LookColors.funcFill(0) == 0xFFC3C9D0);
        t("blue func", LookColors.funcFill(1) == 0xFFB5CFE8);
        t("mint func", LookColors.funcFill(2) == 0xFFB4DCC8);
        t("sand func", LookColors.funcFill(3) == 0xFFE0D2B8);
        t("dark func", LookColors.funcFill(4) == 0xFF2B2D31);
        t("unknown theme -> classic", LookColors.funcFill(99) == 0xFFC3C9D0 && LookColors.funcFill(-1) == 0xFFC3C9D0);
        // v0.2.4: new themes 5..17
        int[] want = {0xFFD2C6AB, 0xFF24302B, 0xFFE2DDD0, 0xFF16161A, 0xFF242424, 0xFF16213A, 0xFFAEBFE0, 0xFF1556A0, 0xFF14352C, 0xFF9FDCCD, 0xFFE9B3C5, 0xFFA0A5AE, 0xFF332B52};
        for (int i = 0; i < want.length; i++) t("func theme " + (5 + i), LookColors.funcFill(5 + i) == want[i]);
        java.util.HashSet<Integer> seen = new java.util.HashSet<Integer>();
        for (int i = 0; i <= 19; i++) seen.add(LookColors.funcFill(i));
        t("20 themes -> 20 different func colours", seen.size() == 20);
        t("v0.2.6 purple func", LookColors.funcFill(18) == 0xFF4A2C80);
        t("v0.2.6 coral func", LookColors.funcFill(19) == 0xFFFFC9BA);
        t("index 20 -> classic", LookColors.funcFill(20) == 0xFFC3C9D0);
        t("alpha opaque on all", allOpaque());
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
