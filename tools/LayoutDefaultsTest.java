import com.mgb.keyboard.layout.*;
import com.mgb.keyboard.probhat.ProbhatLayout;
import com.mgb.keyboard.typing.BottomPlan;
import com.mgb.keyboard.typing.LongPressHints;
import com.mgb.keyboard.typing.NumRowPlan;
import com.mgb.keyboard.typing.RowGap;
import com.mgb.keyboard.typing.SpacePlan;
import com.mgb.keyboard.typing.SymbolPages;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Run: bash tools/run_tests.sh  (or javac all app java + this file, then java -cp out LayoutDefaultsTest) */
public class LayoutDefaultsTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (a == null ? b == null : a.equals(b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }

    /** Tap texts (normal) of the CHAR keys of a row, in order, spacers skipped. */
    static String chars(RowDef r) {
        StringBuilder b = new StringBuilder();
        for (KeyDef k : r.keys) if (k.kind == KeyDef.CHAR) b.append(k.out[0]);
        return b.toString();
    }

    static KeyDef key(LayoutDef m, String layer, String id) {
        for (RowDef r : m.effectiveRows(layer)) for (KeyDef k : r.keys) if (k.id.equals(id)) return k;
        return null;
    }

    static float sum(RowDef r) { return r.totalWeight(); }

    public static void main(String[] x) {
        LayoutDef m = LayoutDefaults.build();
        eq("valid", LayoutOps.valid(m), null);
        eq("fatal gate passes", LayoutOps.fatal(m).size(), 0);
        eq("no warning at all on the defaults", LayoutCheck.check(m).size(), 0);
        eq("deterministic", m.equals(LayoutDefaults.build()), true);
        eq("null options = defaults", m.equals(LayoutDefaults.build(null)), true);
        eq("8 layers", m.layers.size(), 8);
        String[] ids = {"en", "av", "pb", "s1", "s2", "s3", "nr", "bot"};
        for (int i = 0; i < ids.length; i++) eq("layer order " + ids[i], m.layers.get(i).id, ids[i]);

        // English
        List<RowDef> en = m.effectiveRows("en");
        eq("en: 5 rows (number, 3 letter rows, bottom)", en.size(), 5);
        eq("en: number row", chars(en.get(0)), "1234567890");
        eq("en: row 1", chars(en.get(1)), "qwertyuiop");
        eq("en: row 2", chars(en.get(2)), "asdfghjkl");
        eq("en: row 3 letters", chars(en.get(3)), "zxcvbnm");
        eq("en: row 3 starts with shift", en.get(3).keys.get(0).action, "shift");
        eq("en: shift weight 1.5", en.get(3).keys.get(0).weight, 1.5f);
        KeyDef bs = en.get(3).keys.get(en.get(3).keys.size() - 1);
        eq("en: row 3 ends with backspace", bs.action, "backspace");
        eq("en: backspace weight 1.5", bs.weight, 1.5f);
        eq("en: backspace repeats on hold", bs.repeat, true);
        eq("en: row 3 total 10", sum(en.get(3)), 10f);
        eq("en: row 1 total 10", sum(en.get(1)), 10f);
        eq("en: home row = 9 keys + two gaps = 10", sum(en.get(2)), 10f);
        eq("en: gap left is a spacer of RowGap.side", en.get(2).keys.get(0).kind == KeyDef.SPACER && en.get(2).keys.get(0).weight == RowGap.side(true, 9), true);
        eq("en: gap right is a spacer", en.get(2).keys.get(en.get(2).keys.size() - 1).kind, KeyDef.SPACER);
        eq("en: shift of q is Q (auto)", key(m, "en", "en_q").shiftOut(), "Q");
        eq("en: q has no own shift text", key(m, "en", "en_q").out[1], "");
        boolean hintsOk = true;
        for (char c = 'a'; c <= 'z'; c++) { KeyDef k = key(m, "en", "en_" + c); if (!LongPressHints.hintFor(c, true, "").equals(k.lp)) hintsOk = false; }
        eq("en: long-press = LongPressHints (number row ON = symbols)", hintsOk, true);
        eq("en: comma long-press opens emoji", key(m, "en", "b_comma").lp, "@emoji");

        // Bangla phonetic: Latin letters, no long-press hints (hints are English only)
        List<RowDef> av = m.effectiveRows("av");
        eq("av: row 1", chars(av.get(1)), "qwertyuiop");
        eq("av: row 3 letters", chars(av.get(3)), "zxcvbnm");
        eq("av: no hints", key(m, "av", "av_q").lp, "");
        eq("av: same gaps as English", av.get(2).keys.get(0).kind, KeyDef.SPACER);

        // Bangla fixed (Probhat)
        List<RowDef> pb = m.effectiveRows("pb");
        boolean mapOk = true;
        String all = "qwertyuiopasdfghjklzxcvbnm";
        for (int i = 0; i < all.length(); i++) {
            char c = all.charAt(i);
            KeyDef k = key(m, "pb", "pb_" + c);
            if (!k.out[0].equals(ProbhatLayout.map(c)) || !k.shiftOut().equals(ProbhatLayout.map(Character.toUpperCase(c)))) mapOk = false;
        }
        eq("pb: every key = ProbhatLayout.map (normal + shift)", mapOk, true);
        eq("pb: row 1 = 10 letters + EXTRA_ROW1 (2)", pb.get(1).keys.size(), 10 + ProbhatLayout.EXTRA_ROW1.length);
        eq("pb: row 2 = 9 letters + EXTRA_ROW2 (1), no gap spacer", pb.get(2).keys.size(), 9 + ProbhatLayout.EXTRA_ROW2.length);
        eq("pb: row 3 = shift + 7 + EXTRA_ROW3 (2) + backspace", pb.get(3).keys.size(), 2 + 7 + ProbhatLayout.EXTRA_ROW3.length);
        boolean extraOk = true;
        String[][][] ex = {ProbhatLayout.EXTRA_ROW1, ProbhatLayout.EXTRA_ROW2, ProbhatLayout.EXTRA_ROW3};
        for (int r = 0; r < 3; r++) for (int i = 0; i < ex[r].length; i++) {
            KeyDef k = key(m, "pb", "pb_x" + (r + 1) + "_" + (i + 1));
            if (k == null || !k.out[0].equals(ex[r][i][0]) || !k.out[1].equals(ex[r][i][1])) extraOk = false;
        }
        eq("pb: extra keys = ProbhatLayout.EXTRA_ROW1-3", extraOk, true);
        eq("pb: ZWNJ key shows ZWNJ", key(m, "pb", "pb_x3_1").label[1], "ZWNJ");
        eq("pb: combining sign gets a dotted circle", key(m, "pb", "pb_x1_1").label[0], ProbhatLayout.extraLabel("ে"));
        eq("pb: letter label = ProbhatLayout.label when different", key(m, "pb", "pb_w").label[0], ProbhatLayout.label('w'));
        eq("pb: plain letter has no label override", key(m, "pb", "pb_q").label[0], "");
        eq("pb: row 1 total 12", sum(pb.get(1)), 12f);
        eq("pb: no hints", key(m, "pb", "pb_q").lp, "");

        // Probhat home gap
        LayoutDefaults.Options o = new LayoutDefaults.Options();
        o.pbHomeGap = true;
        LayoutDef g = LayoutDefaults.build(o);
        RowDef pr2 = g.effectiveRows("pb").get(2);
        eq("pb gap: left spacer 0.5", pr2.keys.get(0).kind == KeyDef.SPACER && pr2.keys.get(0).weight == 0.5f, true);
        eq("pb gap: extra key weight 0.5", key(g, "pb", "pb_x2_1").weight, BottomPlan.pbKeyWeight(true, true, true));
        eq("pb gap: row total 10", sum(pr2), 10f);
        o.rowGap = false;
        LayoutDef g2 = LayoutDefaults.build(o);
        eq("row gap OFF: no spacer in en row 2", g2.effectiveRows("en").get(2).keys.get(0).kind, KeyDef.CHAR);
        eq("row gap OFF: no spacer in pb row 2", g2.effectiveRows("pb").get(2).keys.get(0).kind, KeyDef.CHAR);

        // bottom row, old style
        RowDef bot = m.layer("bot").rows.get(0);
        eq("bot old: sym weight", key(m, "en", "b_sym").weight, BottomPlan.symWeight(0));
        eq("bot old: lang weight", key(m, "en", "b_lang").weight, BottomPlan.langWeight(0));
        eq("bot old: space weight (? key hidden)", key(m, "en", "b_space").weight, BottomPlan.spaceOld(false));
        eq("bot old: enter weight", key(m, "en", "b_enter").weight, BottomPlan.enterWeight(0));
        eq("bot old: order sym lang , space . enter", bot.keys.get(0).id + bot.keys.get(1).id + bot.keys.get(2).id + bot.keys.get(3).id + bot.keys.get(4).id + bot.keys.get(5).id, "b_symb_langb_commab_spaceb_periodb_enter");
        eq("bot: NO ? key by default", key(m, "en", "b_qm"), null);
        eq("bot: long-press . = ?", key(m, "en", "b_period").lp, "?");
        eq("bot old: total", sum(bot), 1.2f + 1.3f + 1f + 5f + 1f + 1.4f);

        // bottom row, Ridmik style (all combinations of the optional keys)
        boolean ridOk = true;
        for (int mask = 0; mask < 16; mask++) {
            LayoutDefaults.Options r = new LayoutDefaults.Options();
            r.botStyle = 1; r.qmShown = (mask & 1) != 0; r.keyGlobe = (mask & 2) != 0; r.keyVoice = (mask & 4) != 0; r.keyEmoji = (mask & 8) != 0;
            LayoutDef rm = LayoutDefaults.build(r);
            RowDef rb = rm.layer("bot").rows.get(0);
            float others = 0f;
            for (KeyDef k : rb.keys) if (!k.id.equals("b_space")) others += k.weight;
            float want = Math.round(BottomPlan.spaceWeight(1, others, r.qmShown) * 100f) / 100f;
            if (Math.abs(key(rm, "en", "b_space").weight - want) > 0.001f) ridOk = false;
            if (key(rm, "en", "b_sym").weight != 1.55f || key(rm, "en", "b_enter").weight != 1.55f) ridOk = false;
            if (LayoutOps.valid(rm) != null || !LayoutOps.fatal(rm).isEmpty()) ridOk = false;
            if (rm.equals(LayoutCodec.decode(LayoutCodec.encode(rm))) == false) ridOk = false;
            if (LayoutCheck.maxSeverity(LayoutCheck.check(rm)) >= LayoutCheck.MED) ridOk = false;
        }
        eq("bot Ridmik: space = BottomPlan.spaceWeight for every combination; valid; round trip; no MED/HIGH warning", ridOk, true);
        LayoutDefaults.Options q = new LayoutDefaults.Options();
        q.qmShown = true;
        LayoutDef qm = LayoutDefaults.build(q);
        eq("qm shown: ? key exists", key(qm, "en", "b_qm").out[0], "?");
        eq("qm shown: space = 4", key(qm, "en", "b_space").weight, BottomPlan.spaceOld(true));
        eq("qm shown: period has no long-press", key(qm, "en", "b_period").lp, "");
        LayoutDefaults.Options nl = new LayoutDefaults.Options();
        nl.keyLang = false;
        eq("no EN key", key(LayoutDefaults.build(nl), "en", "b_lang"), null);
        LayoutDefaults.Options ek = new LayoutDefaults.Options();
        ek.keyGlobe = true; ek.keyVoice = true; ek.keyEmoji = true;
        LayoutDef opt = LayoutDefaults.build(ek);
        eq("optional keys: globe voice emoji", key(opt, "en", "b_globe") != null && key(opt, "en", "b_voice") != null && key(opt, "en", "b_emoji") != null, true);
        eq("optional key icons", key(opt, "en", "b_voice").labelIcon, "mic");

        // space width % / bottom height %
        LayoutDefaults.Options w = new LayoutDefaults.Options();
        w.spWpct = 150; w.spHpct = 130;
        LayoutDef wm = LayoutDefaults.build(w);
        float others = 1.2f + 1.3f + 1f + 1f + 1.4f;
        float want = Math.round(SpacePlan.spaceWeight(5f, 150, others, 1f, 360f) * 100f) / 100f;
        eq("space width % goes through SpacePlan", key(wm, "en", "b_space").weight, want);
        eq("space width 150 % is wider than 100 %", key(wm, "en", "b_space").weight > 5f, true);
        eq("bottom row height %", wm.layer("bot").rows.get(0).heightPct, 130);
        eq("ref rows carry the same height", wm.layer("en").rows.get(4).heightPct, 130);
        eq("junk width % = 100", key(LayoutDefaults.build(junkSp()), "en", "b_space").weight, 5f);

        // number row
        eq("nr: layer exists", m.layer("nr") != null, true);
        eq("nr: shared reference in en av pb", m.layer("en").rows.get(0).share + m.layer("av").rows.get(0).share + m.layer("pb").rows.get(0).share, "nrnrnr");
        eq("nr: 10 digit keys", m.layer("nr").rows.get(0).keys.size(), 10);
        eq("nr: no long-press in mode 0", key(m, "en", "nr_1").lp, "");
        LayoutDefaults.Options n1 = new LayoutDefaults.Options();
        n1.nrMode = 1;
        LayoutDef nm1 = LayoutDefaults.build(n1);
        boolean nrSym = true;
        String[] lps = NumRowPlan.longPress(1, "");
        for (int i = 0; i < 10; i++) if (!key(nm1, "en", "nr_" + (i + 1)).lp.equals(lps[i])) nrSym = false;
        eq("nr mode 1: symbols on long-press = NumRowPlan", nrSym, true);
        LayoutDefaults.Options n2 = new LayoutDefaults.Options();
        n2.nrMode = 2; n2.nrChars = "!?#";
        LayoutDef nm2 = LayoutDefaults.build(n2);
        eq("nr mode 2: own characters", chars(nm2.layer("nr").rows.get(0)), "!?#");
        LayoutDefaults.Options n3 = new LayoutDefaults.Options();
        n3.nrHpct = 120;
        eq("nr height 120 %", LayoutDefaults.build(n3).layer("nr").rows.get(0).heightPct, 120);
        LayoutDefaults.Options n4 = new LayoutDefaults.Options();
        n4.numRowLarge = true;
        eq("nr large = (48 + 12) / 48 = 125 %", LayoutDefaults.build(n4).layer("nr").rows.get(0).heightPct, 125);
        LayoutDefaults.Options n5 = new LayoutDefaults.Options();
        n5.numRow = false;
        LayoutDef off = LayoutDefaults.build(n5);
        eq("numrow OFF: no nr layer", off.layer("nr"), null);
        eq("numrow OFF: 4 rows in en", off.effectiveRows("en").size(), 4);
        eq("numrow OFF: valid", LayoutOps.valid(off), null);
        eq("numrow OFF: q long-press = digit 1", key(off, "en", "en_q").lp, "1");

        // symbol pages
        boolean symOk = true;
        for (int p = 0; p < SymbolPages.COUNT; p++) {
            String id = "s" + (p + 1);
            List<RowDef> rows = m.effectiveRows(id);
            String[] pg = SymbolPages.page(p);
            if (rows.size() != 4) symOk = false;
            if (!chars(rows.get(0)).equals(pg[0]) || !chars(rows.get(1)).equals(pg[1]) || !chars(rows.get(2)).equals(pg[2])) symOk = false;
            if (!rows.get(2).keys.get(0).label[0].equals(SymbolPages.switchLabel(p))) symOk = false;
            if (!rows.get(2).keys.get(rows.get(2).keys.size() - 1).action.equals("backspace")) symOk = false;
            if (rows.get(2).keys.size() != pg[2].length() + 2) symOk = false;
        }
        eq("symbols: 3 pages = SymbolPages.page(0..2), page key label, backspace", symOk, true);
        eq("symbols: page 1 has the taka sign", chars(m.effectiveRows("s1").get(1)).contains("৳"), true);
        eq("symbols: page 1 has ?", chars(m.effectiveRows("s1").get(2)).contains("?"), true);
        eq("symbols: ABC key goes back", m.effectiveRows("s2").get(3).keys.get(0).action, "sym");
        eq("symbols: bottom row has a space bar", m.effectiveRows("s3").get(3).keys.get(2).action, "space");

        // ids and codec
        Set<String> seen = new HashSet<String>();
        boolean uniq = true;
        for (LayerDef l : m.layers) for (RowDef r : l.rows) for (KeyDef k : r.keys) if (!seen.add(k.id)) uniq = false;
        eq("all ids unique", uniq, true);
        eq("all ids <= 16 chars", LayoutOps.valid(m), null);
        eq("round trip through the codec", m.equals(LayoutCodec.decode(LayoutCodec.encode(m))), true);
        eq("English layer has 10 + 10 + 11 + 9 + 6 = 46 keys incl. gaps (shared rows resolved)", countKeys(m, "en"), 46);
        eq("total own keys", m.totalKeys(), 208);

        // reset helpers can use the defaults
        LayoutDef edited = LayoutOps.setOut(m, "en_q", 0, "z");
        eq("edited differs", edited.equals(m), false);
        LayoutDef reset = LayoutOps.resetLayer(edited, "en", LayoutDefaults.build());
        eq("resetLayer with the defaults restores en letters", chars(reset.effectiveRows("en").get(1)), "qwertyuiop");
        LayoutDef rr = LayoutOps.resetRow(edited, "en", 1, LayoutDefaults.build());
        eq("resetRow with the defaults restores row 1", chars(rr.effectiveRows("en").get(1)), "qwertyuiop");

        System.out.println("LayoutDefaultsTest " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }

    static LayoutDefaults.Options junkSp() {
        LayoutDefaults.Options o = new LayoutDefaults.Options();
        o.spWpct = -5; o.spHpct = 9999;
        return o;
    }

    static int countKeys(LayoutDef m, String layer) {
        int n = 0;
        for (RowDef r : m.effectiveRows(layer)) n += r.keys.size();
        return n;
    }
}
