import com.mgb.keyboard.typing.NumRowPlan;
import java.util.Arrays;

/** v0.16.7 (X7): number row options. */
public class NumRowPlanTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + name); } }
    static boolean eq(String[] a, String s) { return Arrays.equals(a, s.split("(?<=.)")); }

    public static void main(String[] x) {
        // height
        t("100% normal = key height", NumRowPlan.heightDp(48, false, 100) == 48);
        t("100% large = key + 12", NumRowPlan.heightDp(48, true, 100) == 60);
        t("70% normal", NumRowPlan.heightDp(48, false, 70) == 34);
        t("150% normal", NumRowPlan.heightDp(48, false, 150) == 72);
        t("large + 150%", NumRowPlan.heightDp(48, true, 150) == 90);
        t("large + 70%", NumRowPlan.heightDp(48, true, 70) == 42);
        t("unset -1 = 100", NumRowPlan.heightDp(48, false, -1) == 48 && NumRowPlan.heightDp(48, true, -1) == 60);
        t("69 / 151 / 0 = 100", NumRowPlan.heightDp(48, false, 69) == 48 && NumRowPlan.heightDp(48, false, 151) == 48 && NumRowPlan.heightDp(48, false, 0) == 48);
        t("big key 64", NumRowPlan.heightDp(64, false, 130) == 83);
        t("small key 40 @ 70", NumRowPlan.heightDp(40, false, 70) == 28);
        t("sanitizePct", NumRowPlan.sanitizePct(85) == 85 && NumRowPlan.sanitizePct(70) == 70 && NumRowPlan.sanitizePct(150) == 150 && NumRowPlan.sanitizePct(Integer.MIN_VALUE) == 100);
        // default test
        t("default = 100 + mode 0", NumRowPlan.isDefault(100, 0) && NumRowPlan.isDefault(-1, -1) && NumRowPlan.isDefault(999, 7));
        t("not default pct", !NumRowPlan.isDefault(110, 0));
        t("not default mode", !NumRowPlan.isDefault(100, 1) && !NumRowPlan.isDefault(100, 2));
        // mode
        t("mode sanitize", NumRowPlan.sanitizeMode(0) == 0 && NumRowPlan.sanitizeMode(1) == 1 && NumRowPlan.sanitizeMode(2) == 2 && NumRowPlan.sanitizeMode(3) == 0 && NumRowPlan.sanitizeMode(-1) == 0);
        // labels
        t("mode 0 digits", eq(NumRowPlan.labels(0, ""), "1234567890"));
        t("mode 0 ignores chars", eq(NumRowPlan.labels(0, "abc"), "1234567890"));
        t("mode 1 digits", eq(NumRowPlan.labels(1, "abc"), "1234567890"));
        t("mode 2 custom", NumRowPlan.labels(2, "()[]{}").length == 6 && NumRowPlan.labels(2, "()[]{}")[2].equals("["));
        t("mode 2 empty = digits", eq(NumRowPlan.labels(2, ""), "1234567890"));
        t("mode 2 null = digits", eq(NumRowPlan.labels(2, null), "1234567890"));
        t("mode 2 only spaces = digits", eq(NumRowPlan.labels(2, "   \n\t"), "1234567890"));
        t("mode 2 spaces dropped", NumRowPlan.labels(2, "a b c").length == 3);
        t("mode 2 duplicates dropped", NumRowPlan.labels(2, "aabbcc").length == 3);
        t("mode 2 long = 10", NumRowPlan.labels(2, "abcdefghijklmnop").length == 10);
        t("mode 2 short 1", NumRowPlan.labels(2, "@").length == 1);
        t("mode 2 emoji = 1 key each", NumRowPlan.labels(2, "😀😁").length == 2 && NumRowPlan.labels(2, "😀😁")[0].equals("😀"));
        t("mode 2 bangla", NumRowPlan.labels(2, "কখগ").length == 3);
        t("mode invalid = digits", eq(NumRowPlan.labels(9, "abc"), "1234567890"));
        // long press
        String[] lp = NumRowPlan.longPress(1, "");
        t("mode 1 symbols", lp.length == 10 && lp[0].equals("!") && lp[1].equals("@") && lp[8].equals("(") && lp[9].equals(")"));
        t("mode 0 no long press", Arrays.equals(NumRowPlan.longPress(0, ""), new String[10]) == false && NumRowPlan.longPress(0, "")[3].isEmpty());
        t("mode 2 no long press", NumRowPlan.longPress(2, "abc").length == 3 && NumRowPlan.longPress(2, "abc")[0].isEmpty());
        for (int m = 0; m <= 2; m++) for (String c : new String[]{ "", "a", "abcdefghijklmnop", "  " }) t("same length m" + m + " [" + c + "]", NumRowPlan.labels(m, c).length == NumRowPlan.longPress(m, c).length);
        // chars text
        t("sanitizeChars", NumRowPlan.sanitizeChars("a b\nc a").equals("abc") && NumRowPlan.sanitizeChars("").equals("") && NumRowPlan.sanitizeChars(null).equals(""));
        t("sanitizeChars max 10", NumRowPlan.sanitizeChars("abcdefghijklmnop").length() == 10);
        t("symbols count 10", NumRowPlan.SYMBOLS.length() == 10 && NumRowPlan.DIGITS.length() == 10);
        System.out.println("NumRowPlanTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
