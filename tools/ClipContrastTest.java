import com.mgb.keyboard.typing.ClipContrast;

/** javac -encoding UTF-8 -d out $(find app -name '*.java') tools/ClipContrastTest.java && java -Dfile.encoding=UTF-8 -cp out ClipContrastTest */
public class ClipContrastTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL " + m); } }
    static boolean near(double a, double b) { return Math.abs(a - b) < 0.02; }

    public static void main(String[] a) {
        // helper maths (known WCAG values)
        ok(near(ClipContrast.ratio(0xFF000000, 0xFFFFFFFF), 21.0), "black / white = 21");
        ok(near(ClipContrast.ratio(0xFFFFFFFF, 0xFF000000), 21.0), "order does not matter");
        ok(near(ClipContrast.ratio(0xFF777777, 0xFF777777), 1.0), "same = 1");
        ok(near(ClipContrast.ratio(0xFF767676, 0xFFFFFFFF), 4.54), "#767676 on white = 4.54");
        ok(ClipContrast.textOk(0xFF000000, 0xFFFFFFFF), "text ok");
        ok(!ClipContrast.textOk(0xFFAAAAAA, 0xFFFFFFFF), "light grey on white not ok for text");
        ok(ClipContrast.iconOk(0xFF767676, 0xFFFFFFFF), "icon ok");
        ok(ClipContrast.ratio(0x00000000, 0x00FFFFFF) > 20, "alpha ignored");

        // real clipboard colours (values copied from JabraTokens.kt / ClipLook.kt)
        int ACCENT = 0xFF2271B1, ACCENT_SOFT = 0xFF9CC3E6, ON_ACCENT_SOFT = 0xFF1B1B1B;
        int GOLD_DARK = 0xFFFFD54F, GOLD_LIGHT = 0xFFB7791F, DANGER = 0xFFAA0000;
        int WHITE = 0xFFFFFFFF, CLASSIC_BG = 0xFFDDDDDD, DARK_CARD = 0xFF1E1E1E, DARK_BG = 0xFF121212;
        ok(ClipContrast.textOk(ON_ACCENT_SOFT, ACCENT_SOFT), "label on selected-blue button (Done / Add) >= 4.5");
        ok(ClipContrast.iconOk(ACCENT, WHITE), "selected border blue on white card >= 3");
        ok(ClipContrast.iconOk(ACCENT, CLASSIC_BG), "selected border blue on classic bg >= 3");
        ok(ClipContrast.iconOk(ACCENT_SOFT, DARK_CARD), "soft blue on dark card >= 3");
        ok(ClipContrast.iconOk(GOLD_DARK, DARK_CARD), "gold pin on dark card >= 3");
        ok(ClipContrast.iconOk(GOLD_DARK, DARK_BG), "gold pin on dark bg >= 3");
        ok(ClipContrast.iconOk(GOLD_LIGHT, WHITE), "gold pin on white card >= 3");
        int PIN_TEXT_LIGHT = 0xFF7A5000;   // ClipLook.pinText() in light themes (new look): the word "Pinned" is TEXT = 4.5:1
        ok(!ClipContrast.textOk(GOLD_LIGHT, CLASSIC_BG), "old gold #B7791F is too light as TEXT on the classic grey (why pinText exists)");
        ok(ClipContrast.textOk(PIN_TEXT_LIGHT, CLASSIC_BG), "Pinned word (new look) on classic grey >= 4.5");
        ok(ClipContrast.textOk(PIN_TEXT_LIGHT, WHITE), "Pinned word (new look) on white card >= 4.5");
        ok(ClipContrast.textOk(GOLD_DARK, DARK_BG), "Pinned word on dark bg >= 4.5");
        ok(ClipContrast.textOk(DANGER, WHITE), "danger red on white >= 4.5");
        ok(ClipContrast.iconOk(DANGER, CLASSIC_BG), "danger red on classic bg >= 3");
        System.out.println((fail == 0 ? "OK " : "FAILED ") + total + " checks");
        if (fail > 0) System.exit(1);
    }
}
