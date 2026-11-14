import com.mgb.keyboard.typing.LangSwipe;

public class LangSwipeTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    static String s(int[] t) { return t[0] + "," + t[1]; }
    public static void main(String[] a) {
        eq("arm 0 = no hold (v0.9.3)", LangSwipe.ARM_MS, 0L);
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
        eq("name avro (v0.16.2: Bangla-lA)", LangSwipe.name(true, false), "Bang\u09B2\u09BE");
        eq("name probhat (v0.16.2)", LangSwipe.name(true, true), "\u09AC\u09BE\u0982\u09B2\u09BE");
        eq("name EN ignores probhat", LangSwipe.name(false, true), "English");
        // language key visibility
        eq("sync key off, swipe off -> swipe on", LangSwipe.syncAfter("key_lang", false, false, false), 1);
        eq("sync key off, swipe on -> nothing", LangSwipe.syncAfter("key_lang", false, false, true), 0);
        eq("sync swipe off, key off -> key on", LangSwipe.syncAfter("space_lang", false, false, false), 2);
        eq("sync swipe off, key on -> nothing", LangSwipe.syncAfter("space_lang", false, true, false), 0);
        eq("sync turning ON never changes anything", LangSwipe.syncAfter("key_lang", true, true, false), 0);
        eq("sync other keys", LangSwipe.syncAfter("numrow", false, false, false), 0);
        eq("vis default", LangSwipe.langKeyVisible(true, false), true);
        eq("vis keyOn swipeOn", LangSwipe.langKeyVisible(true, true), true);
        eq("vis keyOff swipeOff (no way to switch)", LangSwipe.langKeyVisible(false, false), true);
        eq("vis keyOff swipeOn", LangSwipe.langKeyVisible(false, true), false);
                // v0.9.2 (R3): live bubble label + switch
        eq("label EN", LangSwipe.label(false, false), "English");
        eq("label EN (probhat remembered)", LangSwipe.label(false, true), "English");
        eq("label avro", LangSwipe.label(true, false), "Bang\u09B2\u09BE");
        eq("label probhat", LangSwipe.label(true, true), "\u09AC\u09BE\u0982\u09B2\u09BE");
        eq("preview ON+ON", LangSwipe.previewOn(true, true), true);
        eq("preview swipe OFF", LangSwipe.previewOn(false, true), false);
        eq("preview pref OFF", LangSwipe.previewOn(true, false), false);
        eq("preview both OFF", LangSwipe.previewOn(false, false), false);
        {   // the bubble after every swipe step shows the language that step reached; 3 steps = 3 different names, wraps
            int[] st = { 0, 0 };
            StringBuilder seq = new StringBuilder();
            for (int k = 0; k < 4; k++) { st = LangSwipe.step(st[0] == 1, st[1] == 1, true, true, 1); seq.append(LangSwipe.label(st[0] == 1, st[1] == 1)).append(k < 3 ? "," : ""); }
            eq("right x4 labels", seq.toString(), "Bang\u09B2\u09BE,\u09AC\u09BE\u0982\u09B2\u09BE,English,Bang\u09B2\u09BE");
            st = new int[] { 0, 0 }; seq = new StringBuilder();
            for (int k = 0; k < 3; k++) { st = LangSwipe.step(st[0] == 1, st[1] == 1, true, true, -1); seq.append(LangSwipe.label(st[0] == 1, st[1] == 1)).append(k < 2 ? "," : ""); }
            eq("left x3 labels", seq.toString(), "\u09AC\u09BE\u0982\u09B2\u09BE,Bang\u09B2\u09BE,English");
        }
        eq("Probhat off: EN +1 shows Avro", LangSwipe.label(LangSwipe.step(false, false, true, false, 1)[0] == 1, LangSwipe.step(false, false, true, false, 1)[1] == 1), "Bang\u09B2\u09BE");
        eq("Avro off: EN +1 shows Probhat", LangSwipe.label(LangSwipe.step(false, false, false, true, 1)[0] == 1, LangSwipe.step(false, false, false, true, 1)[1] == 1), "\u09AC\u09BE\u0982\u09B2\u09BE");
        eq("labels never empty / short", LangSwipe.label(true, true).length() <= 10 && LangSwipe.label(false, false).length() <= 10, true);
        eq("v0.16.2: bubble label = space caption name (names have no (Avro) / (Probhat) suffix any more)", LangSwipe.label(true, false).equals(LangSwipe.name(true, false)) && LangSwipe.label(true, true).equals(LangSwipe.name(true, true)), true);
        // ---- v0.9.8 (V3) ----
        eq("stepDp small", LangSwipe.stepDp(0), 32f);
        eq("stepDp normal = old 44", LangSwipe.stepDp(1), 44f);
        eq("stepDp large", LangSwipe.stepDp(2), 60f);
        eq("stepDp default = STEP_DP", LangSwipe.stepDp(LangSwipe.STEP_DEFAULT), LangSwipe.STEP_DP);
        eq("stepDp bad -1 = normal", LangSwipe.stepDp(-1), 44f);
        eq("stepDp bad 9 = normal", LangSwipe.stepDp(9), 44f);
        eq("steps ordered", LangSwipe.stepDp(0) < LangSwipe.stepDp(1) && LangSwipe.stepDp(1) < LangSwipe.stepDp(2), true);
        eq("step names 3", LangSwipe.STEP_NAMES.length, 3);
        eq("default level in range", LangSwipe.STEP_DEFAULT >= 0 && LangSwipe.STEP_DEFAULT < LangSwipe.STEP_NAMES.length, true);
        eq("tick on", LangSwipe.tickOn(true, true), true);
        eq("tick off (pref)", LangSwipe.tickOn(true, false), false);
        eq("tick off (no space swipe)", LangSwipe.tickOn(false, true), false);
        eq("tick off both", LangSwipe.tickOn(false, false), false);
        // Session: persist once at finger up
        LangSwipe.Session q = new LangSwipe.Session();
        eq("session idle", q.active(), false);
        eq("finish w/o swipe = null", q.finish(), null);
        eq("cancel w/o swipe = null", q.cancel(), null);
        q.begin(true, true);                       // start Probhat
        eq("session active", q.active(), true);
        q.move(false, true);                       // -> EN (probhat flag kept)
        q.move(true, false);                       // -> Avro
        q.begin(false, false);                     // second begin in same swipe must NOT overwrite the start
        eq("finish = last language", s(q.finish()), "1,0");
        eq("session ended", q.active(), false);
        eq("finish twice = null (write once)", q.finish(), null);
        q.begin(true, false);                      // start Avro
        q.move(false, false);
        q.move(true, false);                       // back to Avro
        eq("full circle = no write", q.finish(), null);
        q.begin(false, false);                     // start EN
        q.move(true, false);
        q.move(true, true);
        eq("cancel = start language", s(q.cancel()), "0,0");
        eq("cancel ends session", q.active(), false);
        eq("cancel twice = null", q.cancel(), null);
        q.move(true, true);                        // move without begin = ignored
        eq("move w/o begin ignored", q.finish(), null);
        q.begin(false, true);                      // EN with remembered Probhat flag
        q.move(true, true);
        eq("EN(p=1) -> Probhat saves 1,1", s(q.finish()), "1,1");
        // session follows LangSwipe.step over many moves
        q.begin(false, false);
        boolean b = false, pr = false;
        for (int i = 0; i < 7; i++) { int[] t = LangSwipe.step(b, pr, true, true, 1); b = t[0] == 1; pr = t[1] == 1; q.move(b, pr); }
        eq("7 steps right from EN = Avro", s(q.finish()), "1,0");
        q.begin(false, false);
        b = false; pr = false;
        for (int i = 0; i < 3; i++) { int[] t = LangSwipe.step(b, pr, true, true, -1); b = t[0] == 1; pr = t[1] == 1; q.move(b, pr); }
        eq("3 steps left = full circle = no write", q.finish(), null);
        q.begin(true, false); q.move(false, false);
        eq("cancel from EN returns Avro", s(q.cancel()), "1,0");
System.out.println("LangSwipeTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
