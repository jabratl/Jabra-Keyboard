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
        // v0.17.82 (U6): Smart key hints
        eq("modes", HintStyle.MODE_NAMES.length, 3);
        eq("mode 0 name", HintStyle.MODE_NAMES[HintStyle.MODE_SMART], "Smart");
        eq("clampMode bad", HintStyle.clampMode(-1) + HintStyle.clampMode(9), 0);
        eq("Smart typing = hint_on", HintStyle.typingOn(0, true), true);
        eq("Smart typing hint_on OFF", HintStyle.typingOn(0, false), false);
        eq("Always typing = hint_on", HintStyle.typingOn(1, true), true);
        eq("Off stops typing", HintStyle.typingOn(2, true), false);
        eq("label default = old rule", HintStyle.labelOn(0, true, false), true);
        eq("label hide = old rule", HintStyle.labelOn(0, true, true), false);
        eq("label Off", HintStyle.labelOn(2, true, false), false);
        eq("tooSmall Smart 29", HintStyle.tooSmall(0, 29f), true);
        eq("tooSmall Smart default 48", HintStyle.tooSmall(0, 48f), false);
        eq("tooSmall Smart min key 70% = 33.6", HintStyle.tooSmall(0, 33.6f), false);
        eq("tooSmall Always", HintStyle.tooSmall(1, 20f), false);
        eq("tooSmall unknown height", HintStyle.tooSmall(0, 0f), false);
        eq("contrast black/white", Math.round(HintStyle.contrast(0xFF000000, 0xFFFFFFFF)), 21L);
        eq("contrast same", Math.round(HintStyle.contrast(0xFF336699, 0xFF336699)), 1L);
        eq("blend a=255", HintStyle.blend(0xFF112233, 0xFFFFFFFF, 255), 0xFF112233);
        eq("blend a=0", HintStyle.blend(0xFF112233, 0xFFFFFFFF, 0), 0xFFFFFFFF);
        // theme that already reads (Classic: #222222 on white, alpha 170) = unchanged
        int[] ok1 = HintStyle.readable(0xFF222222, 0xFFFFFFFF, 170);
        eq("Classic colour kept", ok1[0], 0xFF222222); eq("Classic alpha kept", ok1[1], 170);
        // dark theme text on dark key: light text kept
        int[] ok2 = HintStyle.readable(0xFFEEEEEE, 0xFF303030, 170);
        eq("dark theme colour kept", ok2[0], 0xFFEEEEEE); eq("dark theme alpha kept", ok2[1], 170);
        // light text on a light key = unreadable -> black
        int[] bad1 = HintStyle.readable(0xFFF0F0F0, 0xFFFFFFFF, 170);
        eq("light on light -> black", bad1[0], 0xFF000000);
        eq("light on light result reads", HintStyle.contrast(HintStyle.blend(bad1[0], 0xFFFFFFFF, bad1[1]), 0xFFFFFFFF) >= HintStyle.MIN_CONTRAST, true);
        // dark text on a dark key -> white
        int[] bad2 = HintStyle.readable(0xFF202020, 0xFF101010, 170);
        eq("dark on dark -> white", bad2[0], 0xFFFFFFFF);
        // faint opacity + mid key: even black is too faint -> alpha raised to 255
        int[] faint = HintStyle.readable(0xFF808080, 0xFF808080, 110);
        eq("faint -> alpha 255", faint[1], 255);
        // alpha bits of the colour are ignored, bad alpha clamps
        eq("alpha bits ignored", HintStyle.readable(0x00222222, 0xFFFFFFFF, 170)[0], 0xFF222222);
        eq("alpha clamp", HintStyle.readable(0xFF222222, 0xFFFFFFFF, 999)[1], 255);
        // every theme colour pair result always reads or is at full opacity
        int[] fills = { 0xFFFFFFFF, 0xFF000000, 0xFF808080, 0xFFFF0000, 0xFF00AA66, 0xFFFFD54F, 0xFF1E1E1E, 0xFFB3E5FC };
        int[] cols = { 0xFF222222, 0xFFFFFFFF, 0xFF808080, 0xFFFFD54F, 0xFF1E1E1E };
        boolean allRead = true;
        for (int f : fills) for (int c : cols) for (int al : new int[] { 110, 170, 215, 255 }) {
            int[] r = HintStyle.readable(c, f, al);
            double k = HintStyle.contrast(HintStyle.blend(r[0], f, r[1]), f);
            if (k < HintStyle.MIN_CONTRAST && r[1] != 255) allRead = false;
        }
        eq("readable always reads or full opacity", allRead, true);
        // useful(): empty / same as key = not shown; Always shows any non-empty
        eq("useful normal", com.mgb.keyboard.typing.LongPressHints.useful(0, "@", "a"), true);
        eq("useful empty", com.mgb.keyboard.typing.LongPressHints.useful(0, "", "a"), false);
        eq("useful null", com.mgb.keyboard.typing.LongPressHints.useful(0, null, "a"), false);
        eq("useful same as key", com.mgb.keyboard.typing.LongPressHints.useful(0, "A", "a"), false);
        eq("useful same, Always", com.mgb.keyboard.typing.LongPressHints.useful(1, "a", "a"), true);
        eq("useful no label", com.mgb.keyboard.typing.LongPressHints.useful(0, "@", null), true);
        System.out.println("HintStyleTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
