import com.mgb.keyboard.typing.LangSwipe;

public class LangSwipeTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    static String s(int[] t) { return t[0] + "," + t[1]; }
    public static void main(String[] a) {
        eq("arm 200", LangSwipe.ARM_MS, 200L);
        eq("step dp > cursor 16", LangSwipe.STEP_DP > 16f, true);
        // cycle sizes
        eq("cycle all", LangSwipe.cycle(false, true, true).length, 3);
        eq("cycle avro only", LangSwipe.cycle(false, true, false).length, 2);
        eq("cycle probhat only", LangSwipe.cycle(false, false, true).length, 2);
        eq("cycle none", LangSwipe.cycle(false, false, false).length, 1);
        // all on: EN -> Avro -> Probhat -> EN (right), reverse for left
        eq("EN +1", s(LangSwipe.step(false, false, true, true, 1)), "1,0");
        eq("Avro +1", s(LangSwipe.step(true, false, true, true, 1)), "1,1");
        eq("Probhat +1 wraps", s(LangSwipe.step(true, true, true, true, 1)), "0,1");
        eq("EN -1 wraps to Probhat", s(LangSwipe.step(false, false, true, true, -1)), "1,1");
        eq("Probhat -1", s(LangSwipe.step(true, true, true, true, -1)), "1,0");
        eq("Avro -1", s(LangSwipe.step(true, false, true, true, -1)), "0,0");
        eq("0 steps keeps", s(LangSwipe.step(true, true, true, true, 0)), "1,1");
        eq("+3 = full circle", s(LangSwipe.step(true, false, true, true, 3)), "1,0");
        eq("-3 = full circle", s(LangSwipe.step(false, false, true, true, -3)), "0,0");
        eq("+2 EN -> Probhat", s(LangSwipe.step(false, false, true, true, 2)), "1,1");
        eq("-7", s(LangSwipe.step(false, false, true, true, -7)), "1,1");
        // EN remembers the last Bangla layout flag
        eq("EN keeps probhat flag", s(LangSwipe.step(true, true, true, true, 1)), "0,1");
        // Probhat off: EN <-> Avro
        eq("EN +1 (probhat off)", s(LangSwipe.step(false, false, true, false, 1)), "1,0");
        eq("Avro +1 (probhat off)", s(LangSwipe.step(true, false, true, false, 1)), "0,0");
        // Avro off: EN <-> Probhat
        eq("EN +1 (avro off)", s(LangSwipe.step(false, false, false, true, 1)), "1,1");
        eq("Probhat -1 (avro off)", s(LangSwipe.step(true, true, false, true, -1)), "0,1");
        // current layout hidden: counts as EN
        eq("hidden Probhat, +1", s(LangSwipe.step(true, true, true, false, 1)), "1,0");
        // both off: stays EN
        eq("both off", s(LangSwipe.step(false, false, false, false, 1)), "0,0");
        eq("both off Bangla -> EN", s(LangSwipe.step(true, false, false, false, 1)), "0,0");
        // labels
        eq("label EN", LangSwipe.spaceLabel(false, false), "\u25C2 English \u25B8");
        eq("name avro", LangSwipe.name(true, false), "\u09AC\u09BE\u0982\u09B2\u09BE (Avro)");
        eq("name probhat", LangSwipe.name(true, true), "\u09AC\u09BE\u0982\u09B2\u09BE (Probhat)");
        eq("name EN ignores probhat", LangSwipe.name(false, true), "English");
        // language key visibility
        eq("vis default", LangSwipe.langKeyVisible(true, false), true);
        eq("vis keyOn swipeOn", LangSwipe.langKeyVisible(true, true), true);
        eq("vis keyOff swipeOff (no way to switch)", LangSwipe.langKeyVisible(false, false), true);
        eq("vis keyOff swipeOn", LangSwipe.langKeyVisible(false, true), false);
        System.out.println("LangSwipeTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
