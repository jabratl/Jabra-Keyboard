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
        // v0.9.4 (R4): Bangla digits as main labels (digitLabel) + Latin digit as the hint (digitHintFor)
        eq("label 1 bn on+bangla", HintStyle.digitLabel('1', true, true), "\u09E7");
        eq("label 0 bn on+bangla", HintStyle.digitLabel('0', true, true), "\u09E6");
        eq("label 9 bn on+bangla", HintStyle.digitLabel('9', true, true), "\u09EF");
        for (char c = '0'; c <= '9'; c++) {
            eq("on+bangla = same as BN_DIGITS " + c, HintStyle.digitLabel(c, true, true), HintStyle.digitHint(c, true));
            eq("pref OFF stays Latin " + c, HintStyle.digitLabel(c, false, true), String.valueOf(c));
            eq("English stays Latin " + c, HintStyle.digitLabel(c, true, false), String.valueOf(c));
            eq("both off stays Latin " + c, HintStyle.digitLabel(c, false, false), String.valueOf(c));
            eq("bn row: hint is the Latin digit " + c, HintStyle.digitHintFor(c, false, true, true), String.valueOf(c));
            eq("bn row + hint_digits ON: still Latin hint " + c, HintStyle.digitHintFor(c, true, true, true), String.valueOf(c));
            eq("old rule kept (pref OFF, hint_digits ON) " + c, HintStyle.digitHintFor(c, true, false, true), HintStyle.digitHint(c, true));
            eq("old rule kept (English, hint_digits ON) " + c, HintStyle.digitHintFor(c, true, true, false), HintStyle.digitHint(c, true));
            eq("old rule kept (all OFF) " + c, HintStyle.digitHintFor(c, false, false, false), null);
        }
        eq("label letter unchanged", HintStyle.digitLabel('a', true, true), "a");
        eq("hint for letter null", HintStyle.digitHintFor('a', true, true, true), null);
        eq("label of Bangla digit stays", HintStyle.digitLabel('\u09E7', true, true), "\u09E7");
        eq("round trip: label then hint recovers Latin", HintStyle.digitHintFor('5', false, true, true) + HintStyle.digitLabel('5', true, true), "5\u09EB");
        System.out.println("HintStyleTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
