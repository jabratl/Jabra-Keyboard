package com.mgb.keyboard.layout;

import com.mgb.keyboard.probhat.ProbhatLayout;
import com.mgb.keyboard.typing.BottomPlan;
import com.mgb.keyboard.typing.LongPressHints;
import com.mgb.keyboard.typing.NumRowPlan;
import com.mgb.keyboard.typing.RowGap;
import com.mgb.keyboard.typing.SpacePlan;
import com.mgb.keyboard.typing.SymbolPages;

/**
 * v0.17.4 (K4) the DEFAULT custom layout = exactly today's classic keyboard as a {@link LayoutDef} (pure Java, no Android).
 * Built from the same tested helpers the keyboard uses (ProbhatLayout, BottomPlan, SymbolPages, NumRowPlan, LongPressHints,
 * RowGap, SpacePlan), so it cannot drift: tools/LayoutDefaultsTest compares them.
 *
 * Layers: en (English), av (Bangla phonetic: keys give LATIN letters that feed the phonetic buffer), pb (Bangla fixed: keys give Bangla text),
 * s1 s2 s3 (symbol pages), nr (shared number row, only when numRow), bot (shared bottom row). en / av / pb hold a reference row
 * ("R 100 nr" / "R 100 bot") instead of copies, so the number row and the bottom row are edited once.
 * R6: the half-key gaps of the home row are SPACER keys built from the CURRENT row_gap value; after that the model owns them.
 * Ids: en_q, av_q, pb_q, pb_x1_1 (extra keys), nr_1, b_sym, s1_r1_1 ...
 *
 * Not in the model (stay in the classic code, decided for K5 / K8): mode key (edit bar OFF), gear + 123 keys of the symbols panel,
 * long-press ?123 (keyboard picker), long-press EN (Avro / Probhat), Bangla digits (numrow_bn), the Bangla "।" on the "?" key
 * (qmShown) and on the long-press of "." - the renderer keeps those two language rules.
 */
public final class LayoutDefaults {
    private LayoutDefaults() { }

    /** The settings that shape today's keyboard. Defaults = the default prefs of v0.17.3. */
    public static final class Options {
        public boolean numRow = true;          // numrow
        public int nrMode = 0;                 // nr_mode 0 / 1 / 2
        public String nrChars = "";            // nr_chars
        public int nrHpct = 100;               // nr_hpct
        public boolean numRowLarge = false;    // numrow_large
        public boolean rowGap = true;          // row_gap
        public boolean pbHomeGap = false;      // pb_home_gap
        public int botStyle = 0;               // bot_style 0 old / 1 Ridmik-like
        public boolean qmShown = false;        // key_qm
        public boolean pbRidmik = true;        // v0.17.69 (S2) bn_design 0: Bangla fixed (pb) in the Ridmik grid: bare sign keys, shift letters as hints, 9-key home row centered
        public boolean keyLang = true;         // EN key visible (key_lang, or space_lang OFF)
        public boolean keyGlobe = false, keyVoice = false, keyEmoji = false;
        public int spWpct = 100, spHpct = 100; // sp_wpct, sp_hpct
        public boolean hints = true;           // hint_on
        public boolean avroHints = true;       // bn_hints (v0.17.54 M2): Ridmik Avro hints on the Bangla phonetic (av) layer
        public String hintCustom = "";         // hint_custom
        public boolean accents = false;        // lp_accents (v0.17.46 P3): accent letters on hold
        public boolean punct = false;          // lp_punct (v0.17.46 P3): symbol options on the , and . keys
        public int keyDp = 48;                 // key height in dp (for the number row height)
        public float rowDp = 360f;             // real row width in dp (only used by the space bar width %)
    }

    public static LayoutDef build() { return build(new Options()); }

    public static LayoutDef build(Options o) {
        if (o == null) o = new Options();
        LayoutDef m = new LayoutDef();
        int nrPct = nrHeightPct(o);
        int botPct = SpacePlan.sanitizeH(o.spHpct);
        m.layers.add(letters("en", o, nrPct, botPct));
        m.layers.add(letters("av", o, nrPct, botPct));
        m.layers.add(letters("pb", o, nrPct, botPct));
        for (int p = 0; p < SymbolPages.COUNT; p++) m.layers.add(symbols(p));
        if (o.numRow) m.layers.add(numberRow(o, nrPct));
        m.layers.add(bottomRow(o, botPct));
        return LayoutOps.normalize(m);
    }

    // ---------- v0.17.50 (PC2): PC rows as a layout layer ----------
    /** Row height of the PC rows in % of the key height (75 = about the 36 dp of the old PC rows at 48 dp keys). */
    public static final int PC_ROW_PCT = 75;
    private static final String[] PC_ROW1 = { "pc_key_esc", "pc_key_tab", "pc_ctrl", "pc_alt", "pc_shift", "pc_win", "pc_key_del", "pc_key_ins" };
    private static final String[] PC_ROW3 = { "pc_key_home", "pc_key_pgup", "pc_key_up", "pc_key_pgdn", "pc_key_end", "pc_key_left", "pc_key_down", "pc_key_right" };

    private static RowDef pcRow(String[] actions) {
        RowDef r = row(PC_ROW_PCT);
        for (String a : actions) r.keys.add(act(a, a, 1f, com.mgb.keyboard.typing.PcActions.label(a), ""));   // key id = action name (unique inside the layer)
        return r;
    }

    /**
     * The PC layer (id "pc", NOT stored in the saved layout: built at draw time so it also works with an edited layout):
     * row 1 Esc Tab Ctrl Alt Shift Win Del Ins, row 2 F1-F12 (only when [fRow]), row 3 Home PgUp Up PgDn End Left Down Right.
     * Returns a model that holds only this layer.
     */
    public static LayoutDef pcModel(boolean fRow) {
        LayoutDef m = new LayoutDef();
        LayerDef l = new LayerDef("pc");
        l.rows.add(pcRow(PC_ROW1));
        if (fRow) {
            String[] f = new String[12];
            for (int i = 0; i < 12; i++) f[i] = "pc_key_f" + (i + 1);
            l.rows.add(pcRow(f));
        }
        l.rows.add(pcRow(PC_ROW3));
        m.layers.add(l);
        return m;
    }

    /**
     * v0.17.51 (PC3): same PC layer plus a 4th row: [chips] + More + Num. [chips] = the shortcut chips (label text, action pc_chip_<n>),
     * null / empty = no chips (row 4 is only More + Num). The old pcModel(fRow) stays exactly as in v0.17.50.
     */
    public static LayoutDef pcModel(boolean fRow, java.util.List<com.mgb.keyboard.typing.PcChord.Chord> chips) {
        LayoutDef m = pcModel(fRow);
        RowDef r = row(PC_ROW_PCT);
        if (chips != null) {
            for (int i = 0; i < chips.size() && i < com.mgb.keyboard.typing.PcChord.MAX_CHIPS; i++) {
                String a = com.mgb.keyboard.typing.PcActions.CHIP + i;
                r.keys.add(act(a, a, 1f, chips.get(i).label(), ""));
            }
        }
        r.keys.add(act(com.mgb.keyboard.typing.PcActions.MORE, com.mgb.keyboard.typing.PcActions.MORE, 1f, "More", ""));
        r.keys.add(act(com.mgb.keyboard.typing.PcActions.NUM, com.mgb.keyboard.typing.PcActions.NUM, 1f, "Num", ""));
        m.layers.get(0).rows.add(r);
        return m;
    }

    // ---------- helpers ----------
    private static int clampPct(int v) { return v < RowDef.H_MIN ? RowDef.H_MIN : (v > RowDef.H_MAX ? RowDef.H_MAX : v); }

    static int nrHeightPct(Options o) {
        int key = o.keyDp > 0 ? o.keyDp : 48;   // percent of the key height: nr_hpct x (key + 12 dp when Large) / key, not rounded to whole dp
        return clampPct(Math.round(NumRowPlan.sanitizePct(o.nrHpct) * (key + (o.numRowLarge ? NumRowPlan.LARGE_EXTRA_DP : 0)) / (float) key));
    }

    private static RowDef row(int pct) { RowDef r = new RowDef(); r.heightPct = pct; return r; }

    private static RowDef ref(String share, int pct) { RowDef r = new RowDef(); r.heightPct = pct; r.share = share; return r; }

    private static KeyDef act(String id, String action, float w, String label, String icon) {
        KeyDef k = KeyDef.act(id, action, w);
        k.label[0] = label; k.labelIcon = icon;
        return k;
    }

    private static float round2(float w) { return Math.round(w * 100f) / 100f; }

    // ---------- letters (en / av / pb) ----------
    private static LayerDef letters(String id, Options o, int nrPct, int botPct) {
        boolean pb = id.equals("pb");
        boolean en = id.equals("en");
        LayerDef l = new LayerDef(id);
        if (o.numRow) l.rows.add(ref("nr", nrPct));

        RowDef r1 = row(100);
        addLetters(r1, id, "qwertyuiop", o, en);
        if (pb) addExtras(r1, id, ProbhatLayout.EXTRA_ROW1, 1, 1f, o);
        l.rows.add(r1);

        RowDef r2 = row(100);
        boolean pbGap = BottomPlan.pbGap(pb, o.pbHomeGap, o.rowGap);
        boolean pbR = pb && o.pbRidmik;   // v0.17.69 (S2): Ridmik home row = 9 keys with half a key free on both sides
        float side = pbR ? (o.rowGap ? 0.5f : 0f) : (pbGap ? 0.5f : (pb ? 0f : RowGap.side(o.rowGap, RowGap.homeKeys(false))));
        if (side > 0f) r2.keys.add(KeyDef.spacer(id + "_gl", side));
        addLetters(r2, id, "asdfghjkl", o, en);
        if (pb) addExtras(r2, id, ProbhatLayout.EXTRA_ROW2, 2, BottomPlan.pbKeyWeight(true, o.pbHomeGap, o.rowGap), o);
        if ((!pb || pbR) && side > 0f) r2.keys.add(KeyDef.spacer(id + "_gr", side));
        l.rows.add(r2);

        RowDef r3 = row(100);
        r3.keys.add(act(id + "_shift", "shift", 1.5f, "⇧", "shift"));
        addLetters(r3, id, "zxcvbnm", o, en);
        if (pb) addExtras(r3, id, ProbhatLayout.EXTRA_ROW3, 3, 1f, o);
        KeyDef bs = act(id + "_bksp", "backspace", 1.5f, "⌫", "back");
        bs.repeat = true;
        r3.keys.add(bs);
        l.rows.add(r3);

        l.rows.add(ref("bot", botPct));
        return l;
    }

    private static void addLetters(RowDef r, String layer, String letters, Options o, boolean english) {
        for (int i = 0; i < letters.length(); i++) {
            char c = letters.charAt(i);
            KeyDef k = KeyDef.ch(layer + "_" + c, String.valueOf(c), 1f);
            if (layer.equals("pb")) {
                String n = ProbhatLayout.map(c), s = ProbhatLayout.map(Character.toUpperCase(c));
                k.out[0] = n == null ? String.valueOf(c) : n;
                k.out[1] = s == null ? k.out[0] : s;
                boolean rd = o.pbRidmik;   // v0.17.69 (S2): Ridmik look = bare signs (no dotted circle) + shift letter as the hint (hold types it)
                String ln = rd ? ProbhatLayout.labelBare(c) : ProbhatLayout.label(c), ls = rd ? ProbhatLayout.labelBare(Character.toUpperCase(c)) : ProbhatLayout.label(Character.toUpperCase(c));
                if (!ln.equals(k.out[0])) k.label[0] = ln;
                if (!ls.equals(k.out[1])) k.label[1] = ls;
                if (rd && o.hints && !k.out[1].equals(k.out[0])) k.lp = k.out[1];
            } else if (english && o.hints) {
                String h = LongPressHints.hintFor(c, o.numRow, o.hintCustom);
                if (h != null) k.lp = h;
            } else if (layer.equals("av") && o.hints && o.avroHints) {   // v0.17.54 (M2)
                String h = com.mgb.keyboard.typing.AvroHints.hintFor(c, o.numRow);
                if (h != null) k.lp = h;
            }
            if (english && o.accents && !layer.equals("pb")) k.lpList.addAll(LpAccents.accents(c));   // v0.17.46 (P3): hint (if any) stays the first option
            r.keys.add(k);
        }
    }

    private static void addExtras(RowDef r, String layer, String[][] pairs, int rowNo, float w, Options o) {
        for (int i = 0; i < pairs.length; i++) {
            KeyDef k = KeyDef.ch(layer + "_x" + rowNo + "_" + (i + 1), pairs[i][0], w);
            k.out[1] = pairs[i][1];
            boolean rd = o.pbRidmik;   // v0.17.69 (S2)
            String ln = rd ? ProbhatLayout.extraLabelBare(pairs[i][0]) : ProbhatLayout.extraLabel(pairs[i][0]);
            String ls = rd ? ProbhatLayout.extraLabelBare(pairs[i][1]) : ProbhatLayout.extraLabel(pairs[i][1]);
            if (!ln.equals(k.out[0])) k.label[0] = ln;
            if (!ls.equals(k.out[1])) k.label[1] = ls;
            if (rd && rowNo == 1 && o.hints && !k.out[1].equals(k.out[0])) k.lp = k.out[1];   // Ridmik: e-kar / o-kar show ৈ ৌ; the row 3 keys show no hint
            if (rd) for (String[] sw : ProbhatLayout.EXTRA_SWIPE_UP) if (sw[0].equals(pairs[i][0]) && rowNo == 3) k.sw[0] = sw[1];   // hidden swipe-up: ঞ on ৃ, ৎ on ্
            r.keys.add(k);
        }
    }

    // ---------- symbol pages ----------
    private static LayerDef symbols(int p) {
        String id = "s" + (p + 1);
        LayerDef l = new LayerDef(id);
        String[] pg = SymbolPages.page(p);
        for (int i = 0; i < 2; i++) {
            RowDef r = row(100);
            addChars(r, id + "_r" + (i + 1), pg[i]);
            l.rows.add(r);
        }
        RowDef r3 = row(100);
        r3.keys.add(act(id + "_page", "sym", 1.5f, SymbolPages.switchLabel(p), ""));
        addChars(r3, id + "_r3", pg[2]);
        KeyDef bs = act(id + "_bksp", "backspace", 1.5f, "⌫", "back");
        bs.repeat = true;
        r3.keys.add(bs);
        l.rows.add(r3);
        RowDef r4 = row(100);
        r4.keys.add(act(id + "_abc", "sym", 1.5f, "ABC", ""));
        r4.keys.add(KeyDef.ch(id + "_comma", ",", 1f));
        r4.keys.add(act(id + "_space", "space", 3f, "space", ""));
        r4.keys.add(KeyDef.ch(id + "_period", ".", 1f));
        r4.keys.add(act(id + "_enter", "enter", 1.4f, "⏎", "enter"));
        l.rows.add(r4);
        return l;
    }

    private static void addChars(RowDef r, String prefix, String chars) {
        int n = 0;
        for (int i = 0; i < chars.length(); ) {
            int cp = chars.codePointAt(i);
            i += Character.charCount(cp);
            n++;
            r.keys.add(KeyDef.ch(prefix + "_" + n, new String(Character.toChars(cp)), 1f));
        }
    }

    // ---------- number row (shared) ----------
    private static LayerDef numberRow(Options o, int pct) {
        LayerDef l = new LayerDef("nr");
        RowDef r = row(pct);
        String[] lab = NumRowPlan.labels(o.nrMode, o.nrChars), lp = NumRowPlan.longPress(o.nrMode, o.nrChars);
        for (int i = 0; i < lab.length; i++) {
            KeyDef k = KeyDef.ch("nr_" + (i + 1), lab[i], 1f);
            k.lp = lp[i];
            r.keys.add(k);
        }
        l.rows.add(r);
        return l;
    }

    // ---------- bottom row (shared) ----------
    private static LayerDef bottomRow(Options o, int pct) {
        int st = BottomPlan.style(o.botStyle);
        LayerDef l = new LayerDef("bot");
        RowDef r = row(pct);
        r.keys.add(act("b_sym", "sym", BottomPlan.symWeight(st), "?123", ""));
        if (o.keyLang) r.keys.add(act("b_lang", "lang", BottomPlan.langWeight(st), "EN", ""));
        if (o.keyGlobe) r.keys.add(act("b_globe", "globe", 1f, "🌐", "globe"));
        if (o.keyVoice) r.keys.add(act("b_voice", "voice", 1f, "🎤", "mic"));
        if (o.keyEmoji) r.keys.add(act("b_emoji", "emoji", 1f, "☺", "emoji"));
        KeyDef comma = KeyDef.ch("b_comma", ",", 1f);
        comma.lp = "@emoji";
        if (o.punct) comma.lpList.addAll(LpAccents.comma());   // v0.17.46 (P3)
        r.keys.add(comma);
        KeyDef space = act("b_space", "space", 1f, "space", "");
        r.keys.add(space);
        if (o.qmShown) r.keys.add(KeyDef.ch("b_qm", "?", 1f));
        KeyDef period = KeyDef.ch("b_period", ".", 1f);
        if (!o.qmShown) period.lp = "?";
        if (o.punct) period.lpList.addAll(LpAccents.period());   // v0.17.46 (P3)
        r.keys.add(period);
        r.keys.add(act("b_enter", "enter", BottomPlan.enterWeight(st), "⏎", "enter"));
        // space bar weight: the classic rules first, then the width % (same order as MgbIme)
        float others = 0f, minOther = Float.MAX_VALUE;
        for (KeyDef k : r.keys) if (k != space) { others += k.weight; if (k.weight < minOther) minOther = k.weight; }
        float base = BottomPlan.ridmik(st) ? BottomPlan.spaceWeight(st, others, o.qmShown) : BottomPlan.spaceOld(o.qmShown);
        float w = SpacePlan.spaceWeight(base, o.spWpct, others, minOther, o.rowDp);
        space.weight = round2(w);
        l.rows.add(r);
        return l;
    }
}
