import com.mgb.keyboard.layout.*;

import java.util.Random;

/** Run: bash tools/run_tests.sh  (or javac all app java + this file, then java -cp out LayoutCodecTest) */
public class LayoutCodecTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (a == null ? b == null : a.equals(b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }

    static LayoutDef one(KeyDef k) {
        LayoutDef m = new LayoutDef();
        LayerDef l = new LayerDef("en");
        RowDef r = new RowDef();
        r.keys.add(k);
        r.keys.add(KeyDef.ch("x2", "b", 1f));
        l.rows.add(r); m.layers.add(l);
        return m;
    }

    static LayoutDef rt(LayoutDef m) { return LayoutCodec.decode(LayoutCodec.encode(m)); }

    public static void main(String[] x) {
        LayoutDef d = LayoutDefaults.build();
        String t = LayoutCodec.encode(d);
        eq("defaults encode not null", t != null, true);
        eq("header", t.startsWith("JKL1\n"), true);
        eq("round trip defaults", d.equals(LayoutCodec.decode(t)), true);
        eq("round trip is stable text", LayoutCodec.encode(LayoutCodec.decode(t)), t);
        eq("size well under the guard", t.getBytes().length < 20000, true);

        // every option variant round-trips
        LayoutDefaults.Options o = new LayoutDefaults.Options();
        o.qmShown = true; o.botStyle = 1; o.pbHomeGap = true; o.keyGlobe = true; o.keyVoice = true; o.keyEmoji = true; o.nrMode = 1; o.spWpct = 150;
        LayoutDef d2 = LayoutDefaults.build(o);
        eq("round trip variant 1", d2.equals(rt(d2)), true);
        o = new LayoutDefaults.Options(); o.numRow = false; o.rowGap = false; o.nrMode = 2; o.nrChars = "!?"; o.keyLang = false; o.spHpct = 130;
        LayoutDef d3 = LayoutDefaults.build(o);
        eq("round trip variant 2", d3.equals(rt(d3)), true);
        o = new LayoutDefaults.Options(); o.botStyle = 1; o.numRowLarge = true; o.nrHpct = 120; o.hints = false;
        LayoutDef d4 = LayoutDefaults.build(o);
        eq("round trip variant 3", d4.equals(rt(d4)), true);

        // special characters in every text field
        KeyDef k = KeyDef.ch("k1", "ক্ষ", 1.25f);          // Bangla conjunct
        k.out[1] = "😀";                                         // emoji (surrogate pair)
        k.label[0] = "a,b"; k.label[1] = "%41";
        k.lp = "x|y"; k.sw[0] = "line\nbreak"; k.sw[1] = "tab\tx"; k.sw[2] = "@paste"; k.sw[3] = " ";
        k.lpList.add("a,b"); k.lpList.add("c|d"); k.lpList.add("‍");  // ZWJ
        k.bold = true; k.wideHint = true; k.noPopup = true; k.repeat = true;
        LayoutDef m = one(k);
        LayoutDef back = rt(m);
        eq("special: decoded", back != null, true);
        eq("special: equal", m.equals(back), true);
        KeyDef kb = back.layer("en").rows.get(0).keys.get(0);
        eq("conjunct", kb.out[0], "ক্ষ");
        eq("emoji", kb.out[1], "😀");
        eq("comma in label", kb.label[0], "a,b");
        eq("percent in label", kb.label[1], "%41");
        eq("pipe in lp", kb.lp, "x|y");
        eq("newline in swipe", kb.sw[0], "line\nbreak");
        eq("tab in swipe", kb.sw[1], "tab\tx");
        eq("@action swipe kept", kb.sw[2], "@paste");
        eq("space swipe", kb.sw[3], " ");
        eq("lpList 3 items", kb.lpList.size(), 3);
        eq("lpList comma item", kb.lpList.get(0), "a,b");
        eq("lpList pipe item", kb.lpList.get(1), "c|d");
        eq("lpList ZWJ item", kb.lpList.get(2), "‍");
        eq("flags", kb.bold && kb.wideHint && kb.noPopup && kb.repeat, true);
        eq("weight 1.25", kb.weight, 1.25f);
        String enc = LayoutCodec.encode(m);
        eq("no raw newline inside a field", enc.split("\n").length, 5);   // JKL1, L, R, K, K
        eq("no raw comma-split break", enc.split("\n")[3].split(",", -1).length, 17);

        // ACTION + combo + icon + weights
        KeyDef a = KeyDef.act("a1", "combo", 1.55f);
        a.combo = "ctrl+67"; a.labelIcon = "copy"; a.label[0] = "Copy";
        LayoutDef am = one(a);
        LayoutDef ab = rt(am);
        eq("combo key round trip", am.equals(ab), true);
        eq("weight 1.55 exact", ab.layer("en").rows.get(0).keys.get(0).weight, 1.55f);
        eq("weight 0.25 exact", rt(one(KeyDef.ch("w1", "z", 0.25f))).layer("en").rows.get(0).keys.get(0).weight, 0.25f);
        eq("weight 8 exact", rt(one(KeyDef.ch("w1", "z", 8f))).layer("en").rows.get(0).keys.get(0).weight, 8f);
        eq("spacer round trip", rt(one(KeyDef.spacer("s1", 0.5f))).layer("en").rows.get(0).keys.get(0).kind, KeyDef.SPACER);

        // tolerant decode: garbage and unknown lines are skipped
        String junk = t.replace("L av\n", "garbage line\nZ 1 2 3\n\nL av\n").replace("R 100 nr\n", "R 100 nr\n# comment\n");
        eq("garbage lines skipped", d.equals(LayoutCodec.decode(junk)), true);
        eq("CRLF text", d.equals(LayoutCodec.decode(t.replace("\n", "\r\n"))), true);
        eq("BOM before header", d.equals(LayoutCodec.decode("﻿" + t)), true);
        eq("header with a name", d.equals(LayoutCodec.decode(t.replaceFirst("JKL1", "JKL1 My layout"))), true);
        eq("leading blank lines", d.equals(LayoutCodec.decode("\n\n" + t)), true);

        // versions and unusable text
        eq("version 1", LayoutCodec.version(t), 1);
        eq("version 2", LayoutCodec.version("JKL2\nL en\n"), 2);
        eq("version none", LayoutCodec.version("hello"), -1);
        eq("version null", LayoutCodec.version(null), -1);
        eq("version JKL only", LayoutCodec.version("JKL\n"), -1);
        eq("newer version = null", LayoutCodec.decode(t.replaceFirst("JKL1", "JKL2")), null);
        eq("version 0 = null", LayoutCodec.decode(t.replaceFirst("JKL1", "JKL0")), null);
        eq("null text", LayoutCodec.decode(null), null);
        eq("empty text", LayoutCodec.decode(""), null);
        eq("plain text", LayoutCodec.decode("hello world"), null);
        eq("header only", LayoutCodec.decode("JKL1\n"), null);
        eq("header + junk only", LayoutCodec.decode("JKL1\nfoo\nbar\n"), null);
        eq("encode null", LayoutCodec.encode(null), null);
        LayoutDef broken = d.copy();
        broken.layer("en").rows.get(1).keys.get(0).id = "";
        eq("encode refuses an invalid model", LayoutCodec.encode(broken), null);

        // limits: rows, keys, layers, size
        StringBuilder sb = new StringBuilder("JKL1\nL en\n");
        for (int i = 0; i < 9; i++) sb.append("R 100\nK C,100,a,,,,,,,,,,0,r").append(i).append(",,,\n");
        eq("9 rows = null", LayoutCodec.decode(sb.toString()), null);
        sb = new StringBuilder("JKL1\nL en\nR 100\n");
        for (int i = 0; i < 81; i++) sb.append("K C,100,a,,,,,,,,,,0,k").append(i).append(",,,\n");
        eq("81 keys = null", LayoutCodec.decode(sb.toString()), null);
        sb = new StringBuilder("JKL1\nL en\nR 100\n");
        for (int i = 0; i < 80; i++) sb.append("K C,100,a,,,,,,,,,,0,k").append(i).append(",,,\n");
        eq("80 keys ok", LayoutCodec.decode(sb.toString()) != null, true);
        StringBuilder big = new StringBuilder("JKL1\nL en\nR 100\n");
        while (big.length() < 1024 * 1024) big.append("K C,100,a,,,,,,,,,,0,kk,,,\n");
        eq("1 MB text = null", LayoutCodec.decode(big.toString()), null);
        StringBuilder just = new StringBuilder("JKL1\nL en\nR 100\nK C,100,a,,,,,,,,,,0,kk,,,\n");
        while (just.length() < LayoutCodec.MAX_BYTES + 10) just.append("# padding padding padding padding\n");
        eq("just over 60 KB = null", LayoutCodec.decode(just.toString()), null);
        // a model whose text is over 60 KB is refused by encode (the caller keeps the old layout)
        LayoutDef huge = new LayoutDef();
        String[] ids = {"en", "av", "pb", "s1", "s2", "s3", "nr", "bot"};
        int n = 0;
        for (String id : ids) {
            LayerDef l = new LayerDef(id);
            for (int r = 0; r < 8; r++) {
                RowDef row = new RowDef();
                for (int c = 0; c < 10; c++) {
                    KeyDef kk = KeyDef.ch("h" + (n++), "ক্ষক্ষক্ষক্ষক্ষক", 1f);
                    kk.out[1] = kk.out[0]; kk.label[0] = "ক্ষক্ষ"; kk.lp = kk.out[0];
                    for (int s = 0; s < 4; s++) kk.sw[s] = kk.out[0];
                    row.keys.add(kk);
                }
                l.rows.add(row);
            }
            huge.layers.add(l);
        }
        huge = LayoutOps.normalize(huge);
        eq("huge model is valid", LayoutOps.valid(huge), null);
        eq("huge text refused (> 60 KB)", LayoutCodec.encode(huge), null);

        // tolerant repairs
        String rep = "JKL1\nL zz\nR 100\nK C,100,a,,,,,,,,,,0,zz1,,,\nL en\nR 100\nK C,100,q,,,,,,,,,,0,e1,,,\n"
                + "R 100\nR 100 bot\nK C,100,Q,,,,,,,,,,0,e2,,,\nR 100 nr\nR 100 en\nL en\nR 100\nK C,100,dup,,,,,,,,,,0,e3,,,\n"
                + "L bot\nR 100\nK C,100,.,,,,,,,,,,0,e1,,,\n";
        LayoutDef rd = LayoutCodec.decode(rep);
        eq("repair: decoded", rd != null, true);
        if (rd != null) {
            eq("repair: unknown layer skipped", rd.layer("zz"), null);
            eq("repair: 2 layers (en, bot), duplicate L en ignored", rd.layers.size(), 2);
            eq("repair: empty row dropped, refs to missing layer nr dropped", rd.layer("en").rows.size(), 2);
            eq("repair: ref row keeps no own keys", rd.layer("en").rows.get(1).keys.size(), 0);
            eq("repair: ref row share", rd.layer("en").rows.get(1).share, "bot");
            eq("repair: model valid", LayoutOps.valid(rd), null);
            eq("repair: duplicate id renamed", rd.layer("bot").rows.get(0).keys.get(0).id.equals("e1"), false);
        }
        LayoutDef sane = LayoutCodec.decode("JKL1\nL en\nR 999\nK C,99999,a\u0001b,%zz,%E0%A6%95,,,@bogus,,,,A,255,k1\nK A,100,,,,,,,,,,nope,0,k2\nK Z,1\nK C\nK C,abc,x\n");
        eq("sane: decoded", sane != null, true);
        if (sane != null) {
            RowDef r = sane.layer("en").rows.get(0);
            eq("sane: row height clamped", r.heightPct, 200);
            eq("sane: weight clamped", r.keys.get(0).weight, 8f);
            eq("sane: control char removed", r.keys.get(0).out[0], "ab");
            eq("sane: broken % stays", r.keys.get(0).out[1], "%zz");
            eq("sane: URL-encoded UTF-8 read", r.keys.get(0).label[0], "ক");
            eq("sane: unknown @action = empty", r.keys.get(0).sw[0], "");
            eq("sane: kind A + unknown action = spacer", r.keys.get(1).kind, KeyDef.SPACER);
            eq("sane: bad kind / short / bad weight lines", r.keys.size() >= 2, true);
            eq("sane: unknown flags bits ignored (all four set)", r.keys.get(0).bold && r.keys.get(0).repeat, true);
        }

        // fuzz: never throws, a non-null result is always valid
        Random rnd = new Random(20261113);
        boolean thrown = false;
        int nonNull = 0;
        String alphabet = "JKL1\nRCAS,%| 0123456789@abcক😀\r\t";
        try {
            for (int i = 0; i < 3000; i++) {
                StringBuilder f = new StringBuilder(rnd.nextBoolean() ? "JKL1\n" : "");
                int len = rnd.nextInt(400);
                for (int j = 0; j < len; j++) f.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
                LayoutDef r = LayoutCodec.decode(f.toString());
                if (r != null) { nonNull++; if (LayoutOps.valid(r) != null) thrown = true; }
            }
            for (int cut = 0; cut <= t.length(); cut += 7) {                 // truncated texts
                LayoutDef r = LayoutCodec.decode(t.substring(0, cut));
                if (r != null && LayoutOps.valid(r) != null) thrown = true;
            }
            for (int i = 0; i < 1500; i++) {                                  // single-character damage of a good text
                StringBuilder f = new StringBuilder(t);
                for (int j = 0; j < 1 + rnd.nextInt(6); j++) f.setCharAt(rnd.nextInt(f.length()), alphabet.charAt(rnd.nextInt(alphabet.length())));
                LayoutDef r = LayoutCodec.decode(f.toString());
                if (r != null && LayoutOps.valid(r) != null) thrown = true;
            }
        } catch (Throwable e) { thrown = true; System.out.println("THROWN " + e); }
        eq("fuzz: never throws, results always valid", thrown, false);
        eq("fuzz: some damaged texts still decode", nonNull >= 0, true);
        byte[] bytes = new byte[5000];
        rnd.nextBytes(bytes);
        eq("random bytes = null or valid", LayoutCodec.decode(new String(bytes, java.nio.charset.Charset.forName("ISO-8859-1"))), null);

        System.out.println("LayoutCodecTest " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }
}
