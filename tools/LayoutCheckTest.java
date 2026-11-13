import com.mgb.keyboard.layout.*;
import com.mgb.keyboard.layout.LayoutCheck.Warn;
import com.mgb.keyboard.probhat.ProbhatLayout;

import java.util.List;
import java.util.Random;

/** Run: bash tools/run_tests.sh  (or javac all app java + this file, then java -cp out LayoutCheckTest) */
public class LayoutCheckTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (a == null ? b == null : a.equals(b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }

    static int count(List<Warn> w, String code) { int n = 0; for (Warn x : w) if (x.code.equals(code)) n++; return n; }

    static Warn first(List<Warn> w, String code) { for (Warn x : w) if (x.code.equals(code)) return x; return null; }

    static Warn firstIn(List<Warn> w, String code, String layer) { for (Warn x : w) if (x.code.equals(code) && x.layer.equals(layer)) return x; return null; }

    static KeyDef key(LayoutDef m, String id) {
        for (LayerDef l : m.layers) for (RowDef r : l.rows) for (KeyDef k : r.keys) if (k.id.equals(id)) return k;
        return null;
    }

    public static void main(String[] x) {
        LayoutDef d = LayoutDefaults.build();
        eq("defaults: no warning", LayoutCheck.check(d).size(), 0);
        eq("defaults: maxSeverity -1", LayoutCheck.maxSeverity(LayoutCheck.check(d)), -1);
        eq("check(null) = empty", LayoutCheck.check(null).size(), 0);
        eq("check(invalid) = empty", LayoutCheck.check(new LayoutDef(), null).size(), 0);
        eq("fatal(defaults) = empty", LayoutCheck.fatal(d).size(), 0);
        eq("fatal(null) = something", LayoutCheck.fatal(null).size() > 0, true);

        // duplicates
        LayoutDef dup = LayoutOps.setOut(d, "en_w", 0, "q");
        List<Warn> w = LayoutCheck.check(dup);
        Warn dw = firstIn(w, LayoutCheck.W_DUP, "en");
        eq("dup: warned in en", dw != null, true);
        eq("dup: keys in reading order", dw.keyIds.toString(), "[en_q, en_w]");
        eq("dup: severity MED", dw.severity, LayoutCheck.MED);
        eq("dup: fixable", dw.fixable, true);
        eq("dup: other layers (av, pb) untouched", firstIn(w, LayoutCheck.W_DUP, "av"), null);
        eq("dup: w is now missing = coverage warning", firstIn(w, LayoutCheck.W_COVERAGE, "en").items.contains("w"), true);
        eq("dup: coverage is HIGH", firstIn(w, LayoutCheck.W_COVERAGE, "en").severity, LayoutCheck.HIGH);
        LayoutDef caseDup = LayoutOps.setOut(d, "en_w", 0, "Q");
        eq("dup: case-insensitive for letters", firstIn(LayoutCheck.check(caseDup), LayoutCheck.W_DUP, "en") != null, true);
        LayoutDef shiftOnly = LayoutOps.setOut(d, "en_q", 1, "Q");
        eq("q + auto Q (explicit Q) never warns", LayoutCheck.check(shiftOnly).size(), 0);
        LayoutDef shiftDup = LayoutOps.setOut(d, "en_w", 1, "Q");
        Warn sd = firstIn(LayoutCheck.check(shiftDup), LayoutCheck.W_DUP, "en");
        eq("dup: same SHIFT output on 2 keys warns", sd != null && sd.text.contains("shift"), true);
        LayoutDef noDup = LayoutOps.setOut(d, "en_w", 1, "q");
        eq("same letter as shift of one key and tap of another is NOT a duplicate", firstIn(LayoutCheck.check(noDup), LayoutCheck.W_DUP, "en"), null);
        LayoutDef triple = LayoutOps.setOut(LayoutOps.setOut(d, "en_w", 0, "q"), "en_e", 0, "q");
        eq("dup: three keys = one warning with 3 ids", firstIn(LayoutCheck.check(triple), LayoutCheck.W_DUP, "en").keyIds.size(), 3);
        LayoutDef bn = LayoutOps.setOut(d, "pb_w", 0, ProbhatLayout.map('q'));
        eq("dup: Bangla layer", firstIn(LayoutCheck.check(bn), LayoutCheck.W_DUP, "pb") != null, true);
        LayoutDef sharedDup = LayoutOps.setOut(d, "nr_2", 0, "1");
        List<Warn> sw = LayoutCheck.check(sharedDup);
        eq("dup in the shared number row is reported in every layer that shows it", count(sw, LayoutCheck.W_DUP) >= 3, true);

        // auto-fix of duplicates
        LayoutDef fixed = LayoutCheck.autoFix(dup, dw);
        eq("fix dup: warning gone", firstIn(LayoutCheck.check(fixed), LayoutCheck.W_DUP, "en"), null);
        eq("fix dup: the FIRST key keeps its letter", key(fixed, "en_q").out[0], "q");
        eq("fix dup: the second key lost its tap but keeps its long-press, so it is still a key", key(fixed, "en_w").kind, KeyDef.CHAR);
        eq("fix dup: second key tap empty", key(fixed, "en_w").out[0], "");
        LayoutDef plain = LayoutOps.setLp(LayoutOps.setOut(d, "en_w", 0, "q"), "en_w", "");
        LayoutDef pf = LayoutCheck.autoFix(plain, firstIn(LayoutCheck.check(plain), LayoutCheck.W_DUP, "en"));
        eq("fix dup: a key left with nothing becomes an empty spot", key(pf, "en_w").kind, KeyDef.SPACER);
        eq("fix dup: result valid", LayoutOps.valid(pf), null);
        LayoutDef tf = LayoutCheck.autoFix(triple, firstIn(LayoutCheck.check(triple), LayoutCheck.W_DUP, "en"));
        eq("fix dup: three keys -> one fix clears both later ones", firstIn(LayoutCheck.check(tf), LayoutCheck.W_DUP, "en"), null);

        // overlap
        LayoutDef ov = LayoutOps.setLp(d, "en_a", "b");
        Warn ow = firstIn(LayoutCheck.check(ov), LayoutCheck.W_OVERLAP, "en");
        eq("overlap: long-press equals a tap of another key", ow != null, true);
        eq("overlap: severity LOW", ow.severity, LayoutCheck.LOW);
        eq("overlap: not auto-fixable", ow.fixable, false);
        LayoutDef ov2 = LayoutOps.setSwipe(d, "en_a", 1, "c");
        eq("overlap: swipe equals a tap of another key", firstIn(LayoutCheck.check(ov2), LayoutCheck.W_OVERLAP, "en") != null, true);
        LayoutDef ov3 = LayoutOps.setSwipe(d, "en_a", 1, "@paste");
        eq("@action swipes never overlap", LayoutCheck.check(ov3).size(), 0);
        LayoutDef ov4 = LayoutOps.setLp(d, "en_a", "a");
        eq("long-press equal to its OWN tap is not an overlap", firstIn(LayoutCheck.check(ov4), LayoutCheck.W_OVERLAP, "en"), null);

        // missing keys / exits
        LayoutDef noBs = LayoutOps.makeSpacer(d, "en_bksp");
        List<Warn> mb = LayoutCheck.check(noBs);
        Warn miss = firstIn(mb, LayoutCheck.W_MISSING, "en");
        eq("missing backspace in en", miss != null && miss.items.contains("backspace"), true);
        eq("missing backspace: av and pb still have theirs", firstIn(mb, LayoutCheck.W_MISSING, "av"), null);
        LayoutDef mf = LayoutCheck.autoFix(noBs, miss);
        eq("fix missing: backspace is back", key(mf, "en_bksp").action, "backspace");
        eq("fix missing: weight at least 1", key(mf, "en_bksp").weight >= 1f, true);
        eq("fix missing: repeats on hold", key(mf, "en_bksp").repeat, true);
        eq("fix missing: warning gone", firstIn(LayoutCheck.check(mf), LayoutCheck.W_MISSING, "en"), null);
        LayoutDef noEnter = LayoutOps.makeSpacer(d, "b_enter");
        eq("missing enter is reported for en av pb", count(LayoutCheck.check(noEnter), LayoutCheck.W_MISSING), 3);
        LayoutDef noShift = LayoutOps.makeSpacer(d, "pb_shift");
        eq("missing shift in pb", firstIn(LayoutCheck.check(noShift), LayoutCheck.W_MISSING, "pb").items.contains("shift"), true);
        LayoutDef shiftByCaps = LayoutOps.setSwipe(noShift, "pb_q", 0, "@caps");
        eq("@caps swipe counts as a shift way", firstIn(LayoutCheck.check(shiftByCaps), LayoutCheck.W_MISSING, "pb"), null);
        LayoutDef noSpaceS = LayoutOps.makeSpacer(d, "s1_space");
        eq("symbol page without space warns", firstIn(LayoutCheck.check(noSpaceS), LayoutCheck.W_MISSING, "s1") != null, true);
        LayoutDef noSym = LayoutOps.makeSpacer(d, "b_sym");
        List<Warn> ns = LayoutCheck.check(noSym);
        Warn ne = firstIn(ns, LayoutCheck.W_NOEXIT, "en");
        eq("no key to symbols = NOEXIT HIGH", ne != null && ne.severity == LayoutCheck.HIGH, true);
        eq("no key to symbols: fatal gate also refuses", LayoutCheck.fatal(noSym).size() > 0, true);
        eq("fix NOEXIT is refused or restores it: result never worse", LayoutCheck.fatal(LayoutCheck.autoFix(noSym, ne)).size(), 0);
        eq("fix NOEXIT: warning gone", firstIn(LayoutCheck.check(LayoutCheck.autoFix(noSym, ne)), LayoutCheck.W_NOEXIT, "en"), null);
        LayoutDef noBack = LayoutOps.makeSpacer(LayoutOps.makeSpacer(d, "s2_abc"), "s2_page");
        Warn nb = firstIn(LayoutCheck.check(noBack), LayoutCheck.W_NOEXIT, "s2");
        eq("symbol page without any sym key = NOEXIT (a way back to the letters)", nb != null && nb.text.contains("back"), true);
        LayoutDef noLang = LayoutOps.makeSpacer(d, "b_lang");
        eq("no language key with ONE language ON = fine", firstIn(LayoutCheck.check(noLang), LayoutCheck.W_NOEXIT, "en"), null);
        List<Warn> two = LayoutCheck.check(noLang, new LayoutCheck.Env(360f, 2));
        eq("no language key with 2 languages ON = NOEXIT HIGH", firstIn(two, LayoutCheck.W_NOEXIT, "en").severity, LayoutCheck.HIGH);
        LayoutDef langSwipe = LayoutOps.setSwipe(noLang, "en_q", 0, "@lang");
        eq("@lang swipe is a way to switch language", firstIn(LayoutCheck.check(langSwipe, new LayoutCheck.Env(360f, 2)), LayoutCheck.W_NOEXIT, "en"), null);

        // narrow / wide / empty / spread / touch
        LayoutDef nar = LayoutOps.setWeight(d, "en_e", 0.25f);
        Warn nw = first(LayoutCheck.check(nar), LayoutCheck.W_NARROW);
        eq("narrow: key below 0.5", nw != null && nw.keyIds.contains("en_e"), true);
        LayoutDef nf = LayoutCheck.autoFix(nar, nw);
        eq("fix narrow: widened to 0.5", key(nf, "en_e").weight, 0.5f);
        eq("fix narrow: warning gone", first(LayoutCheck.check(nf), LayoutCheck.W_NARROW), null);
        LayoutDef half = LayoutOps.setWeight(d, "en_e", 0.5f);
        eq("0.5 is not narrow", first(LayoutCheck.check(half), LayoutCheck.W_NARROW), null);
        eq("empty spots (home-row gaps of 0.5) are not narrow keys", first(LayoutCheck.check(d), LayoutCheck.W_NARROW), null);
        LayoutDef wide = LayoutOps.setWeight(d, "en_gl", 0.25f);
        wide = LayoutOps.setWeight(wide, "en_gr", 0.25f);
        wide = LayoutOps.delete(wide, "en_a");
        wide = LayoutOps.delete(wide, "en_s"); wide = LayoutOps.delete(wide, "en_d"); wide = LayoutOps.delete(wide, "en_f");
        Warn ww = firstIn(LayoutCheck.check(wide), LayoutCheck.W_WIDE, "en");
        eq("wide: a row far narrower than the widest row", ww != null && ww.severity == LayoutCheck.INFO, true);
        LayoutDef emptyRow = d;
        for (char c : "asdfghjkl".toCharArray()) emptyRow = LayoutOps.makeSpacer(emptyRow, "en_" + c);
        eq("empty row: only empty spots", firstIn(LayoutCheck.check(emptyRow), LayoutCheck.W_EMPTY_ROW, "en") != null, true);
        LayoutDef spread = d;
        for (int i = 0; i < 6; i++) spread = LayoutOps.delete(spread, "en_" + "zxcvbn".charAt(i));
        eq("rowspread: 10 keys next to a 3-key row", firstIn(LayoutCheck.check(spread), LayoutCheck.W_ROWSPREAD, "en") != null, true);
        eq("rowspread: defaults (bottom row not counted) = none", first(LayoutCheck.check(d), LayoutCheck.W_ROWSPREAD), null);
        LayoutDefaults.Options o = new LayoutDefaults.Options();
        o.pbHomeGap = true;
        LayoutDef gap = LayoutDefaults.build(o);
        Warn tw = first(LayoutCheck.check(gap), LayoutCheck.W_TOUCH);
        eq("touch: half-width Probhat extra key is under 24 dp at 360 dp", tw != null && tw.keyIds.contains("pb_x2_1"), true);
        eq("touch: severity INFO", tw.severity, LayoutCheck.INFO);
        eq("touch: fine on a 600 dp screen", first(LayoutCheck.check(gap, new LayoutCheck.Env(600f, 1)), LayoutCheck.W_TOUCH), null);
        eq("touch: bad screen width = skipped", first(LayoutCheck.check(gap, new LayoutCheck.Env(0f, 1)), LayoutCheck.W_TOUCH), null);

        // coverage
        LayoutDef noQ = LayoutOps.makeSpacer(d, "en_q");
        Warn cw = firstIn(LayoutCheck.check(noQ), LayoutCheck.W_COVERAGE, "en");
        eq("coverage: q missing in en", cw != null && cw.items.size() == 1 && cw.items.get(0).equals("q"), true);
        eq("coverage: av layer is checked too (same key is NOT shared, so av is fine)", firstIn(LayoutCheck.check(noQ), LayoutCheck.W_COVERAGE, "av"), null);
        LayoutDef cf = LayoutCheck.autoFix(noQ, cw);
        eq("fix coverage: q goes into an empty spot", firstIn(LayoutCheck.check(cf), LayoutCheck.W_COVERAGE, "en"), null);
        eq("fix coverage: the WIDEST spot is used = the emptied q spot, not a half-key gap", key(cf, "en_q").out[0], "q");
        eq("coverage: q on a long-press still counts as typeable", firstIn(LayoutCheck.check(LayoutOps.setLp(noQ, "en_w", "q")), LayoutCheck.W_COVERAGE, "en"), null);
        LayoutDef noKaar = LayoutOps.setOut(d, "pb_x1_1", 0, "ক");
        Warn pc = firstIn(LayoutCheck.check(noKaar), LayoutCheck.W_COVERAGE, "pb");
        eq("coverage: Bangla vowel sign e-kar missing in pb", pc != null && pc.items.contains("ে"), true);
        eq("coverage: pb defaults cover every ProbhatLayout letter", firstIn(LayoutCheck.check(d), LayoutCheck.W_COVERAGE, "pb"), null);
        LayoutDef two2 = LayoutOps.makeSpacer(LayoutOps.makeSpacer(d, "en_q"), "en_w");
        Warn tc = firstIn(LayoutCheck.check(two2), LayoutCheck.W_COVERAGE, "en");
        LayoutDef tcf = LayoutCheck.autoFix(two2, tc);
        eq("fix coverage: two letters, two spots -> both back", firstIn(LayoutCheck.check(tcf), LayoutCheck.W_COVERAGE, "en"), null);

        // phonetic
        LayoutDef ph = LayoutOps.setOut(d, "av_q", 0, "ক");
        eq("phonetic: Bangla letter on an Avro key", firstIn(LayoutCheck.check(ph), LayoutCheck.W_PHONETIC, "av") != null, true);
        eq("phonetic: severity INFO", firstIn(LayoutCheck.check(ph), LayoutCheck.W_PHONETIC, "av").severity, LayoutCheck.INFO);
        eq("phonetic: digits and punctuation in av are fine", first(LayoutCheck.check(d), LayoutCheck.W_PHONETIC), null);
        eq("phonetic: only the av layer", firstIn(LayoutCheck.check(ph), LayoutCheck.W_PHONETIC, "pb"), null);

        // saving is never blocked by warnings; fatal only blocks the 4 safety rules
        LayoutDef manyWarn = LayoutOps.setOut(LayoutOps.setOut(d, "en_w", 0, "q"), "en_e", 0, "q");
        eq("warnings do not make the model fatal", LayoutCheck.fatal(manyWarn).size(), 0);
        eq("autoFix of a warning that is not fixable = same instance", LayoutCheck.autoFix(ov, ow) == ov, true);
        eq("autoFix(null, null)", LayoutCheck.autoFix(null, null), null);
        eq("autoFix(model, null) = same", LayoutCheck.autoFix(d, null) == d, true);

        // fuzz: random edit sequences, check + every autoFix never throw, never produce an invalid / fatal model
        Random rnd = new Random(20261113);
        boolean bad2 = false;
        String[] ids = {"en_q", "en_w", "en_a", "en_bksp", "en_shift", "b_sym", "b_space", "b_enter", "pb_x1_1", "av_q", "s1_page", "nr_3", "en_gl", "b_lang"};
        try {
            for (int s = 0; s < 300; s++) {
                LayoutDef m = LayoutDefaults.build();
                for (int step = 0; step < 6; step++) {
                    String id = ids[rnd.nextInt(ids.length)];
                    switch (rnd.nextInt(6)) {
                        case 0: m = LayoutOps.makeSpacer(m, id); break;
                        case 1: m = LayoutOps.setOut(m, id, 0, String.valueOf((char) ('a' + rnd.nextInt(26)))); break;
                        case 2: m = LayoutOps.setWeight(m, id, rnd.nextFloat() * 2f); break;
                        case 3: m = LayoutOps.delete(m, id); break;
                        case 4: m = LayoutOps.setSwipe(m, id, rnd.nextInt(4), rnd.nextBoolean() ? "@sym" : "x"); break;
                        default: m = LayoutOps.merge(m, id); break;
                    }
                }
                for (Warn wn : LayoutCheck.check(m)) {
                    LayoutDef f = LayoutCheck.autoFix(m, wn);
                    if (LayoutOps.valid(f) != null) bad2 = true;
                    if (LayoutOps.fatal(m).isEmpty() && !LayoutOps.fatal(f).isEmpty()) bad2 = true;
                }
            }
        } catch (Throwable e) { bad2 = true; System.out.println("THROWN " + e); }
        eq("fuzz: 300 random edits, every warning's auto-fix keeps the model valid and never makes it fatal", bad2, false);

        System.out.println("LayoutCheckTest " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }
}
