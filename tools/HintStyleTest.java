import com.mgb.keyboard.typing.HintStyle;

public class HintStyleTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    public static void main(String[] a) {
        // digits: 10 distinct Bangla digits, right order, null when off / not a digit
        eq("bn len", HintStyle.BN_DIGITS.length(), 10);
        eq("1 -> \u09E7", HintStyle.digitHint('1', true), "\u09E7");
        eq("0 -> \u09E6", HintStyle.digitHint('0', true), "\u09E6");
        eq("9 -> \u09EF", HintStyle.digitHint('9', true), "\u09EF");
        for (char c = '0'; c <= '9'; c++) { eq("on " + c, HintStyle.digitHint(c, true) != null, true); eq("off " + c, HintStyle.digitHint(c, false), null); }
        eq("letter null", HintStyle.digitHint('a', true), null);
        eq("space null", HintStyle.digitHint(' ', true), null);
        eq("isDigit 5", HintStyle.isDigit('5'), true);
        eq("isDigit q", HintStyle.isDigit('q'), false);
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (char c = '0'; c <= '9'; c++) seen.add(HintStyle.digitHint(c, true));
        eq("distinct", seen.size(), 10);
        // level 1 = EXACTLY the old look (alpha 170, size rule 0.27 x height, 9..40)
        eq("default lv", HintStyle.LV_DEFAULT, 1);
        eq("alpha default", HintStyle.alpha(1), 170);
        eq("size default factor", HintStyle.sizeFactor(1), 1.00f);
        for (float h = 20f; h <= 300f; h += 7f) {
            float old = Math.max(9f, Math.min(40f, h * 0.27f));
            eq("old size rule h=" + h, HintStyle.textSize(h, 1), old);
        }
        // order: bigger level = bigger / stronger
        eq("alpha order", HintStyle.alpha(0) < HintStyle.alpha(1) && HintStyle.alpha(1) < HintStyle.alpha(2) && HintStyle.alpha(2) < HintStyle.alpha(3), true);
        eq("alpha max 255", HintStyle.alpha(3), 255);
        eq("size order", HintStyle.textSize(80f, 0) < HintStyle.textSize(80f, 1) && HintStyle.textSize(80f, 1) < HintStyle.textSize(80f, 2) && HintStyle.textSize(80f, 2) < HintStyle.textSize(80f, 3), true);
        // limits stay 9..40
        eq("min 9", HintStyle.textSize(1f, 0), 9f);
        eq("max 40", HintStyle.textSize(900f, 3), 40f);
        // bad levels clamp, never throw
        eq("clamp -5", HintStyle.clampLv(-5), 0);
        eq("clamp 99", HintStyle.clampLv(99), 3);
        eq("alpha -1", HintStyle.alpha(-1), HintStyle.alpha(0));
        eq("alpha 77", HintStyle.alpha(77), 255);
        eq("size 77", HintStyle.sizeFactor(77), HintStyle.sizeFactor(3));
        eq("names size", HintStyle.SIZE_NAMES.length, 4);
        eq("names alpha", HintStyle.ALPHA_NAMES.length, 4);
        eq("normal name", HintStyle.SIZE_NAMES[HintStyle.LV_DEFAULT], "Normal");
        System.out.println("HintStyleTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
