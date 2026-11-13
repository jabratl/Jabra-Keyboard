import com.mgb.keyboard.layout.*;
import java.util.*;

/** Run: bash tools/run_tests.sh  (K3: layout model + LayoutOps + LayoutText, includes a seeded fuzz test) */
public class LayoutOpsTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (a == null ? b == null : a.equals(b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }
    static void tr(String n, boolean c) { eq(n, c, true); }

    /** en: row0 q w e r t | row1 a s d | row2 shift z x backspace | row3 = reference to layer bot (sym space enter). */
    static LayoutDef base() {
        LayoutDef m = new LayoutDef();
        LayerDef en = new LayerDef("en");
        en.rows.add(row("q", "w", "e", "r", "t"));
        en.rows.add(row("a", "s", "d"));
        RowDef r2 = new RowDef();
        r2.keys.add(KeyDef.act("shift", "shift", 1.5f)); r2.keys.add(KeyDef.ch("z", "z", 1f)); r2.keys.add(KeyDef.ch("x", "x", 1f));
        r2.keys.add(KeyDef.act("bksp", "backspace", 1.5f));
        en.rows.add(r2);
        RowDef ref = new RowDef(); ref.share = "bot"; en.rows.add(ref);
        m.layers.add(en);
        LayerDef bot = new LayerDef("bot");
        RowDef b = new RowDef();
        b.keys.add(KeyDef.act("sym", "sym", 1.5f)); b.keys.add(KeyDef.act("space", "space", 5f)); b.keys.add(KeyDef.act("enter", "enter", 1.5f));
        bot.rows.add(b); m.layers.add(bot);
        return m;
    }
    static RowDef row(String... ids) {
        RowDef r = new RowDef();
        for (String s : ids) r.keys.add(KeyDef.ch(s, s, 1f));
        return r;
    }
    static KeyDef key(LayoutDef m, String id) {
        for (LayerDef l : m.layers) for (RowDef r : l.rows) for (KeyDef k : r.keys) if (k.id.equals(id)) return k;
        return null;
    }
    static RowDef rowOf(LayoutDef m, String id) {
        for (LayerDef l : m.layers) for (RowDef r : l.rows) for (KeyDef k : r.keys) if (k.id.equals(id)) return r;
        return null;
    }
    static String ids(RowDef r) { StringBuilder b = new StringBuilder(); for (KeyDef k : r.keys) b.append(k.id).append(' '); return b.toString().trim(); }
    static RowDef en(LayoutDef m, int i) { return m.layer("en").rows.get(i); }

    public static void main(String[] a) {
        // ---------- LayoutText ----------
        eq("clean null", LayoutText.clean(null, 5), "");
        eq("clean keeps plain", LayoutText.clean("abc", 5), "abc");
        eq("clean cuts to max code points", LayoutText.clean("abcdefgh", 3), "abc");
        eq("clean control chars removed", LayoutText.clean("a\u0000b\u0007c\u007F", 9), "abc");
        eq("clean keeps newline and tab", LayoutText.clean("a\nb\tc", 9), "a\nb\tc");
        eq("clean unpaired high surrogate removed", LayoutText.clean("a\uD83Db", 9), "ab");
        eq("clean unpaired low surrogate removed", LayoutText.clean("a\uDE00b", 9), "ab");
        eq("clean emoji kept whole", LayoutText.clean("\uD83D\uDE00", 1), "\uD83D\uDE00");
        eq("clean emoji not cut (max 2)", LayoutText.clean("a\uD83D\uDE00b", 2), "a\uD83D\uDE00");
        eq("clean Bangla conjunct 3 cp kept", LayoutText.clean("\u0995\u09CD\u09B7", 3), "\u0995\u09CD\u09B7");
        eq("clean ZWJ kept", LayoutText.clean("a\u200Db", 3), "a\u200Db");
        eq("clean ZWNJ kept", LayoutText.clean("a\u200Cb", 3), "a\u200Cb");
        eq("clean 100000 chars -> max", LayoutText.cp(LayoutText.clean(rep("x", 100000), 16)), 16);
        eq("clean max 0", LayoutText.clean("abc", 0), "");
        eq("cleanSwipe whitelisted @paste", LayoutText.cleanSwipe("@paste", 16), "@paste");
        eq("cleanSwipe unknown @id ignored", LayoutText.cleanSwipe("@hack", 16), "");
        eq("cleanSwipe lone @ is text", LayoutText.cleanSwipe("@", 16), "@");
        eq("cleanSwipe text with @ inside ok", LayoutText.cleanSwipe("a@b", 16), "a@b");
        eq("cleanWord lowercases nothing, drops junk", LayoutText.cleanWord("ab_1!X", 16), "ab_1");
        eq("cleanId keeps case", LayoutText.cleanId("Ab_1 /x"), "Ab_1x");
        eq("cleanId max 16", LayoutText.cleanId(rep("a", 40)).length(), 16);
        tr("isLatinLetters q", LayoutText.isLatinLetters("q"));
        tr("isLatinLetters Bangla false", !LayoutText.isLatinLetters("\u0995"));
        tr("isLatinLetters digit false", !LayoutText.isLatinLetters("1"));

        // ---------- KeyDef ----------
        KeyDef q = KeyDef.ch("q", "q", 1f);
        eq("shiftOut Latin = upper", q.shiftOut(), "Q");
        KeyDef bn = KeyDef.ch("b", "\u0995", 1f);
        eq("shiftOut Bangla = same", bn.shiftOut(), "\u0995");
        KeyDef dg = KeyDef.ch("d", "1", 1f); dg.out[1] = "!";
        eq("shiftOut explicit", dg.shiftOut(), "!");
        eq("shiftOut action = empty", KeyDef.act("s", "shift", 1f).shiftOut(), "");
        KeyDef c1 = q.copy(); c1.out[0] = "z"; c1.sw[0] = "x"; c1.lpList.add("m");
        eq("copy is deep (out)", q.out[0], "q");
        eq("copy is deep (sw)", q.sw[0], "");
        eq("copy is deep (lpList)", q.lpList.size(), 0);

        // ---------- base model ----------
        LayoutDef m = base();
        eq("base valid", LayoutOps.valid(m), null);
        eq("base not fatal", LayoutOps.fatal(m).size(), 0);
        eq("effective rows of en = 4 (ref resolved)", m.effectiveRows("en").size(), 4);
        eq("ref row shows bot keys", ids(m.effectiveRows("en").get(3)), "sym space enter");
        eq("keyCount en counts own keys only", m.layer("en").keyCount(), 12);
        eq("copy equals", m.copy().equals(m), true);
        String sig0 = m.sig();

        // ---------- weight ----------
        LayoutDef w1 = LayoutOps.setWeight(m, "q", 2f);
        eq("setWeight 2", key(w1, "q").weight, 2f);
        eq("old model untouched", m.sig(), sig0);
        eq("setWeight quantises 1.4 -> 1.5", key(LayoutOps.setWeight(m, "q", 1.4f), "q").weight, 1.5f);
        eq("setWeight min 0.25", key(LayoutOps.setWeight(m, "q", 0.01f), "q").weight, 0.25f);
        eq("setWeight negative -> 0.25", key(LayoutOps.setWeight(m, "q", -5f), "q").weight, 0.25f);
        eq("setWeight max 8", key(LayoutOps.setWeight(m, "q", 99f), "q").weight, 8f);
        eq("setWeight infinity -> 8", key(LayoutOps.setWeight(m, "q", Float.POSITIVE_INFINITY), "q").weight, 8f);
        eq("setWeight NaN -> 1 (same = no change)", LayoutOps.setWeight(m, "q", Float.NaN) == m, true);
        eq("setWeight same value = same instance", LayoutOps.setWeight(m, "q", 1f) == m, true);
        eq("setWeight unknown id = same instance", LayoutOps.setWeight(m, "nope", 2f) == m, true);
        eq("ops accept null model", LayoutOps.setWeight(null, "q", 2f), null);
        eq("ops accept null id", LayoutOps.setWeight(m, null, 2f) == m, true);

        // ---------- content ----------
        LayoutDef o1 = LayoutOps.setOut(m, "q", 0, "\u0995\u09CD\u09B7");
        eq("setOut conjunct", key(o1, "q").out[0], "\u0995\u09CD\u09B7");
        eq("setOut 100000 chars cut to 16", LayoutText.cp(key(LayoutOps.setOut(m, "q", 0, rep("y", 100000)), "q").out[0]), 16);
        eq("setOut surrogate half removed", key(LayoutOps.setOut(m, "q", 0, "a\uD83D"), "q").out[0], "a");
        eq("setOut bad idx refused", LayoutOps.setOut(m, "q", 2, "x") == m, true);
        eq("setOut same = same instance", LayoutOps.setOut(m, "q", 0, "q") == m, true);
        eq("setOut empty on action refused", LayoutOps.setOut(m, "shift", 0, "") == m, true);
        eq("setOut text on action key -> CHAR", key(LayoutOps.setOut(m, "shift", 0, "S"), "shift").kind, KeyDef.CHAR);
        eq("setOut action cleared", key(LayoutOps.setOut(m, "shift", 0, "S"), "shift").action, "");
        eq("setLabel", key(LayoutOps.setLabel(m, "q", 0, "Qx"), "q").label[0], "Qx");
        eq("setLabel max 6", LayoutText.cp(key(LayoutOps.setLabel(m, "q", 0, "abcdefghij"), "q").label[0]), 6);
        eq("setLp", key(LayoutOps.setLp(m, "q", "#"), "q").lp, "#");
        eq("setLp unknown @action ignored", key(LayoutOps.setLp(LayoutOps.setLp(m, "q", "x"), "q", "@evil"), "q").lp, "");
        eq("setSwipe up", key(LayoutOps.setSwipe(m, "q", 0, "1"), "q").sw[0], "1");
        eq("setSwipe right @paste", key(LayoutOps.setSwipe(m, "q", 3, "@paste"), "q").sw[3], "@paste");
        eq("setSwipe bad dir refused", LayoutOps.setSwipe(m, "q", 4, "1") == m, true);
        eq("setSwipe control chars removed", key(LayoutOps.setSwipe(m, "q", 1, "a\u0001b"), "q").sw[1], "ab");
        eq("setLpList keeps good items", key(LayoutOps.setLpList(m, "q", Arrays.asList("1", "", "@hack", "@tab", "2")), "q").lpList.toString(), "[1, @tab, 2]");
        eq("setLpList max 16", key(LayoutOps.setLpList(m, "q", Arrays.asList("1","2","3","4","5","6","7","8","9","0","a","b","c","d","e","f","g","h")), "q").lpList.size(), 16);   // v0.17.46: 8 -> 16
        eq("setLpList null = empty, no change", LayoutOps.setLpList(m, "q", null) == m, true);
        LayoutDef ac = LayoutOps.setAction(m, "q", "tab");
        eq("setAction -> ACTION", key(ac, "q").kind, KeyDef.ACTION);
        eq("setAction id", key(ac, "q").action, "tab");
        eq("setAction wipes out", key(ac, "q").out[0], "");
        eq("setAction unknown refused", LayoutOps.setAction(m, "q", "format_disk") == m, true);
        eq("setCombo", key(LayoutOps.setCombo(m, "q", "ctrl+67"), "q").combo, "ctrl+67");
        eq("setCombo is ACTION combo", key(LayoutOps.setCombo(m, "q", "ctrl+67"), "q").action, "combo");
        eq("setCombo empty refused", LayoutOps.setCombo(m, "q", "") == m, true);
        eq("setFlags", key(LayoutOps.setFlags(m, "q", true, false, true, false), "q").noPopup, true);
        eq("setLabelIcon", key(LayoutOps.setLabelIcon(m, "bksp", "Back_Icon!"), "bksp").labelIcon, "ack_con");
        LayoutDef sp = LayoutOps.makeSpacer(m, "q");
        eq("makeSpacer kind", key(sp, "q").kind, KeyDef.SPACER);
        eq("makeSpacer wipes out", key(sp, "q").out[0], "");
        eq("makeSpacer keeps weight", key(sp, "q").weight, 1f);
        eq("makeSpacer twice = same instance", LayoutOps.makeSpacer(sp, "q") == sp, true);
        eq("setSwipe on spacer refused", LayoutOps.setSwipe(sp, "q", 0, "1") == sp, true);
        eq("setLp on spacer refused", LayoutOps.setLp(sp, "q", "1") == sp, true);
        eq("setOut empty on spacer refused", LayoutOps.setOut(sp, "q", 0, "") == sp, true);
        eq("fill spacer", key(LayoutOps.fill(sp, "q", "9"), "q").out[0], "9");
        eq("fill spacer kind CHAR", key(LayoutOps.fill(sp, "q", "9"), "q").kind, KeyDef.CHAR);
        eq("fill non-spacer refused", LayoutOps.fill(m, "q", "9") == m, true);

        // ---------- move ----------
        eq("moveRight", ids(rowOf(LayoutOps.moveRight(m, "q"), "q")), "w q e r t");
        eq("moveLeft", ids(rowOf(LayoutOps.moveLeft(m, "e"), "e")), "q e w r t");
        eq("moveLeft at edge refused", LayoutOps.moveLeft(m, "q") == m, true);
        eq("moveRight at edge refused", LayoutOps.moveRight(m, "t") == m, true);
        LayoutDef mu = LayoutOps.moveDown(m, "e");
        eq("moveDown row0 loses key", ids(en(mu, 0)), "q w r t");
        eq("moveDown row1 same index (idx 2)", ids(en(mu, 1)), "a s e d");
        eq("moveDown index clamped to row end", ids(en(LayoutOps.moveDown(m, "t"), 1)), "a s d t");
        eq("moveDown from row2 into ref row refused", LayoutOps.moveDown(m, "z") == m, true);
        eq("moveUp from row0 refused", LayoutOps.moveUp(m, "q") == m, true);
        eq("moveUp", ids(en(LayoutOps.moveUp(m, "d"), 0)), "q w d e r t");
        eq("moveUp row1 loses key", ids(en(LayoutOps.moveUp(m, "d"), 1)), "a s");
        LayoutDef one = LayoutOps.delete(LayoutOps.delete(m, "a"), "s");
        eq("row1 has only d now", ids(en(one, 1)), "d");
        eq("moveUp last key of a row refused", LayoutOps.moveUp(one, "d") == one, true);

        // ---------- insert / delete ----------
        LayoutDef ib = LayoutOps.insertBefore(m, "w");
        eq("insertBefore count", en(ib, 0).keys.size(), 6);
        eq("insertBefore is spacer at index 1", en(ib, 0).keys.get(1).kind, KeyDef.SPACER);
        eq("insertAfter position", en(LayoutOps.insertAfter(m, "t"), 0).keys.get(5).kind, KeyDef.SPACER);
        eq("insert id unique + valid", LayoutOps.valid(ib), null);
        eq("delete", ids(en(LayoutOps.delete(m, "w"), 0)), "q e r t");
        eq("delete last key of a row refused", LayoutOps.delete(one, "d") == one, true);
        eq("delete unknown refused", LayoutOps.delete(m, "zz") == m, true);

        // ---------- merge ----------
        LayoutDef mg = LayoutOps.merge(m, "q");
        eq("merge removes neighbour", ids(en(mg, 0)), "q e r t");
        eq("merge width = sum", key(mg, "q").weight, 2f);
        eq("merge keeps first out", key(mg, "q").out[0], "q");
        eq("merge: second letter -> long-press", key(mg, "q").lp, "w");
        tr("mergeMovesSecond true", LayoutOps.mergeMovesSecond(m, "q"));
        LayoutDef lpq = LayoutOps.setLp(m, "q", "9");
        eq("merge keeps existing long-press", key(LayoutOps.merge(lpq, "q"), "q").lp, "9");
        eq("mergeMovesSecond false when lp exists", LayoutOps.mergeMovesSecond(lpq, "q"), false);
        eq("merge last key refused", LayoutOps.merge(m, "t") == m, true);
        LayoutDef s2 = LayoutOps.makeSpacer(LayoutOps.makeSpacer(m, "q"), "w");
        LayoutDef s2m = LayoutOps.merge(s2, "q");
        eq("two spacers -> one spacer", key(s2m, "q").kind, KeyDef.SPACER);
        eq("two spacers width sum", key(s2m, "q").weight, 2f);
        eq("two spacers: one key less", en(s2m, 0).keys.size(), 4);
        LayoutDef sk = LayoutOps.merge(LayoutOps.makeSpacer(m, "q"), "q");
        eq("spacer + key = key content", key(sk, "q").out[0], "w");
        eq("spacer + key: first id stays", key(sk, "w"), null);
        LayoutDef ks = LayoutOps.merge(LayoutOps.makeSpacer(m, "w"), "q");
        eq("key + spacer = key", key(ks, "q").out[0], "q");
        eq("key + spacer: no long-press", key(ks, "q").lp, "");
        LayoutDef big = LayoutOps.setWeight(LayoutOps.setWeight(m, "q", 6f), "w", 6f);
        eq("merge width clamped to 8", key(LayoutOps.merge(big, "q"), "q").weight, 8f);
        eq("merge with action neighbour: no long-press from action", key(LayoutOps.merge(m, "x"), "x").lp, "");
        eq("merge result valid", LayoutOps.valid(mg), null);

        // ---------- split ----------
        LayoutDef sw2 = LayoutOps.setWeight(m, "q", 2f);
        LayoutDef spl = LayoutOps.split(sw2, "q");
        eq("split: first weight", key(spl, "q").weight, 1f);
        eq("split: key count +1", en(spl, 0).keys.size(), 6);
        eq("split: second is spacer", en(spl, 0).keys.get(1).kind, KeyDef.SPACER);
        eq("split: second weight", en(spl, 0).keys.get(1).weight, 1f);
        eq("split: first keeps letter", key(spl, "q").out[0], "q");
        eq("split valid (ids unique)", LayoutOps.valid(spl), null);
        LayoutDef w05 = LayoutOps.setWeight(m, "q", 0.5f);
        eq("split 0.5 -> two 0.25", key(LayoutOps.split(w05, "q"), "q").weight, 0.25f);
        LayoutDef w025 = LayoutOps.setWeight(m, "q", 0.25f);
        eq("split 0.25 refused", LayoutOps.split(w025, "q") == w025, true);
        eq("split then merge back = width", key(LayoutOps.merge(spl, "q"), "q").weight, 2f);

        // ---------- limits: 80 keys, 8 rows ----------
        LayoutDef full = fullLayer();
        eq("full layer valid", LayoutOps.valid(full), null);
        eq("full layer has 80 keys", full.layer("en").keyCount(), 80);
        eq("81st key refused (insert)", LayoutOps.insertAfter(full, "f1") == full, true);
        eq("81st key refused (split)", LayoutOps.split(full, "f1") == full, true);
        eq("addRow refused at 8 rows", LayoutOps.addRow(full, "en", 0) == full, true);
        eq("duplicateRow refused at 8 rows", LayoutOps.duplicateRow(full, "en", 0) == full, true);
        LayoutDef rows7 = base();
        eq("addRow ok", LayoutOps.addRow(rows7, "en", 0).layer("en").rows.size(), 5);
        LayoutDef r8 = rows7;
        for (int i = 0; i < 10; i++) r8 = LayoutOps.addRow(r8, "en", -1);
        eq("9th row refused (stops at 8)", r8.layer("en").rows.size(), 8);
        eq("addRow at top: one empty wide spot", LayoutOps.addRow(rows7, "en", -1).layer("en").rows.get(0).keys.get(0).kind, KeyDef.SPACER);
        eq("addRow unknown layer refused", LayoutOps.addRow(rows7, "zz", 0) == rows7, true);
        eq("add row valid", LayoutOps.valid(r8), null);

        // ---------- rows ----------
        LayoutDef dr = LayoutOps.duplicateRow(m, "en", 1);
        eq("duplicateRow adds a row", dr.layer("en").rows.size(), 5);
        eq("duplicateRow ids unique", LayoutOps.valid(dr), null);
        eq("duplicateRow same letters", en(dr, 2).keys.get(0).out[0], "a");
        tr("duplicateRow new id differs", !en(dr, 2).keys.get(0).id.equals("a"));
        eq("deleteRow", LayoutOps.deleteRow(m, "en", 1).layer("en").rows.size(), 3);
        eq("deleteRow bad index refused", LayoutOps.deleteRow(m, "en", 9) == m, true);
        LayoutDef onlyRow = LayoutOps.deleteRow(LayoutOps.deleteRow(LayoutOps.deleteRow(m, "en", 3), "en", 2), "en", 1);
        eq("deleteRow last row refused", LayoutOps.deleteRow(LayoutOps.deleteRow(onlyRow, "en", 0), "en", 0) == LayoutOps.deleteRow(onlyRow, "en", 0), true);
        eq("setRowHeight", LayoutOps.setRowHeight(m, "en", 0, 130).layer("en").rows.get(0).heightPct, 130);
        eq("setRowHeight min 50", LayoutOps.setRowHeight(m, "en", 0, 1).layer("en").rows.get(0).heightPct, 50);
        eq("setRowHeight max 200", LayoutOps.setRowHeight(m, "en", 0, 5000).layer("en").rows.get(0).heightPct, 200);
        eq("setRowHeight same = same instance", LayoutOps.setRowHeight(m, "en", 0, 100) == m, true);
        LayoutDef sr = LayoutOps.swapRows(m, "en", 0, 1);
        eq("swapRows", ids(en(sr, 0)), "a s d");
        eq("swapRows same index refused", LayoutOps.swapRows(m, "en", 1, 1) == m, true);
        eq("swapRows bad index refused", LayoutOps.swapRows(m, "en", 0, 9) == m, true);
        LayoutDef ab = LayoutOps.autoBalanceRow(LayoutOps.setWeight(m, "q", 4f), "en", 0);
        eq("autoBalance equal weights", key(ab, "w").weight, key(ab, "q").weight);
        eq("autoBalance keeps total (8/5=1.6 -> 1.5)", key(ab, "t").weight, 1.5f);
        eq("autoBalance already equal = same instance", LayoutOps.autoBalanceRow(m, "en", 0) == m, true);
        LayoutDef aw = LayoutOps.applyWidthToRow(LayoutOps.setWeight(m, "q", 2f), "q");
        eq("applyWidthToRow", key(aw, "t").weight, 2f);
        LayoutDef cs = LayoutOps.copyKeyStyle(LayoutOps.setFlags(LayoutOps.setWeight(m, "q", 2f), "q", true, false, false, false), "q", "a");
        eq("copyKeyStyle weight", key(cs, "a").weight, 2f);
        eq("copyKeyStyle bold", key(cs, "a").bold, true);
        eq("copyKeyStyle keeps letter", key(cs, "a").out[0], "a");
        eq("copyKeyStyle onto itself refused", LayoutOps.copyKeyStyle(m, "q", "q") == m, true);

        // ---------- reset ----------
        LayoutDef messy = LayoutOps.makeSpacer(LayoutOps.setOut(m, "q", 0, "9"), "w");
        eq("resetRow restores letters", ids(en(LayoutOps.resetRow(messy, "en", 0, m), 0)), "q w e r t");
        eq("resetRow restores key", key(LayoutOps.resetRow(messy, "en", 0, m), "q").out[0], "q");
        eq("resetRow result valid", LayoutOps.valid(LayoutOps.resetRow(messy, "en", 0, m)), null);
        eq("resetRow unchanged row = same instance", LayoutOps.resetRow(m, "en", 0, m) == m, true);
        eq("resetRow null defaults refused", LayoutOps.resetRow(messy, "en", 0, null) == messy, true);
        LayoutDef rl = LayoutOps.resetLayer(LayoutOps.deleteRow(messy, "en", 1), "en", m);
        eq("resetLayer back to default (rows)", rl.layer("en").rows.size(), 4);
        eq("resetLayer valid", LayoutOps.valid(rl), null);
        eq("resetLayer equals default", rl.equals(m), true);
        eq("resetLayer unknown layer in defaults refused", LayoutOps.resetLayer(messy, "pb", m) == messy, true);

        // ---------- bulk ----------
        LayoutDef bw = LayoutOps.setAllWeight(m, Arrays.asList("q", "w", "zz"), 2f);
        eq("setAllWeight q", key(bw, "q").weight, 2f);
        eq("setAllWeight w", key(bw, "w").weight, 2f);
        eq("setAllWeight null list", LayoutOps.setAllWeight(m, null, 2f) == m, true);
        eq("makeSpacerAll", key(LayoutOps.makeSpacerAll(m, Arrays.asList("q", "w")), "w").kind, KeyDef.SPACER);
        eq("deleteAll skips the last key of a row", en(LayoutOps.deleteAll(m, Arrays.asList("a", "s", "d")), 1).keys.size(), 1);
        eq("deleteAll normal", en(LayoutOps.deleteAll(m, Arrays.asList("q", "w")), 0).keys.size(), 3);

        // ---------- fatal ----------
        eq("fatal: no backspace", LayoutOps.fatal(LayoutOps.delete(m, "bksp")).size(), 1);
        eq("fatal: backspace reason", LayoutOps.fatal(LayoutOps.delete(m, "bksp")).get(0), "English layer has no backspace key");
        eq("fatal: no space (shared row)", LayoutOps.fatal(LayoutOps.delete(m, "space")).get(0), "English layer has no space key");
        eq("fatal: no symbols key", LayoutOps.fatal(LayoutOps.delete(m, "sym")).get(0), "English layer has no way to open symbols");
        eq("fatal: swipe @sym counts as reachable", LayoutOps.fatal(LayoutOps.setSwipe(LayoutOps.delete(m, "sym"), "q", 0, "@sym")).size(), 0);
        eq("fatal: null", LayoutOps.fatal(null).get(0), "invalid: null");
        eq("fatal: no layers", LayoutOps.fatal(new LayoutDef()).get(0), "no layers");
        LayoutDef noEn = m.copy(); noEn.layers.remove(0);
        eq("fatal: no English layer", LayoutOps.fatal(noEn).size() > 0, true);
        LayoutDef noLetters = m;
        for (String id : new String[]{"q","w","e","r","t","a","s","d","z","x"}) noLetters = LayoutOps.makeSpacer(noLetters, id);
        eq("fatal: no letter key", LayoutOps.fatal(noLetters).get(0), "English layer has no letter key");
        eq("warnings are not fatal: spacer-only row ok", LayoutOps.fatal(LayoutOps.makeSpacer(m, "q")).size(), 0);

        // ---------- valid() catches broken hand-made models ----------
        LayoutDef bad1 = m.copy(); key(bad1, "q").weight = 99f;
        tr("valid: weight out of range", LayoutOps.valid(bad1) != null);
        LayoutDef bad2 = m.copy(); key(bad2, "w").id = "q";
        tr("valid: duplicate id", LayoutOps.valid(bad2) != null);
        LayoutDef bad3 = m.copy(); bad3.layer("en").rows.get(0).keys.clear();
        tr("valid: empty row", LayoutOps.valid(bad3) != null);
        LayoutDef bad4 = m.copy(); key(bad4, "q").out[0] = rep("z", 40);
        tr("valid: text too long", LayoutOps.valid(bad4) != null);
        LayoutDef bad5 = m.copy(); bad5.layers.add(new LayerDef("zz"));
        tr("valid: bad layer id", LayoutOps.valid(bad5) != null);

        // ---------- immutability of everything ----------
        eq("original still same after all ops", m.sig(), sig0);

        // ---------- fuzz ----------
        Random rnd = new Random(20261113L);
        String[] texts = {"", "a", "Q", "\u0995\u09CD\u09B7", "\uD83D\uDE00", "a\uD83D", "\u0000x", "@paste", "@evil", "@", rep("w", 100000), "\u200D", "x\ny", "\t"};
        float[] fl = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -3f, 0f, 0.1f, 0.25f, 0.75f, 1f, 2.3f, 8f, 100f, 1e30f};
        String[] actions = {"tab", "shift", "space", "bogus", "", "combo", "lang"};
        String[] layers = {"en", "bot", "zz", null};
        int fails = 0, ops = 0, threw = 0, refused = 0;
        for (int seq = 0; seq < 2000; seq++) {
            LayoutDef cur = base();
            for (int step = 0; step < 14; step++) {
                List<String> all = new ArrayList<String>();
                for (LayerDef l : cur.layers) for (RowDef r : l.rows) for (KeyDef k : r.keys) all.add(k.id);
                String id = rnd.nextInt(12) == 0 ? (rnd.nextBoolean() ? "nope" : null) : all.get(rnd.nextInt(all.size()));
                String lay = layers[rnd.nextInt(layers.length)];
                int ri = rnd.nextInt(12) - 2;
                LayoutDef next = cur;
                try {
                    switch (rnd.nextInt(30)) {
                        case 0: next = LayoutOps.setWeight(cur, id, fl[rnd.nextInt(fl.length)]); break;
                        case 1: next = LayoutOps.setOut(cur, id, rnd.nextInt(4) - 1, texts[rnd.nextInt(texts.length)]); break;
                        case 2: next = LayoutOps.setLabel(cur, id, rnd.nextInt(3), texts[rnd.nextInt(texts.length)]); break;
                        case 3: next = LayoutOps.setLp(cur, id, texts[rnd.nextInt(texts.length)]); break;
                        case 4: next = LayoutOps.setSwipe(cur, id, rnd.nextInt(6) - 1, texts[rnd.nextInt(texts.length)]); break;
                        case 5: next = LayoutOps.setAction(cur, id, actions[rnd.nextInt(actions.length)]); break;
                        case 6: next = LayoutOps.makeSpacer(cur, id); break;
                        case 7: next = LayoutOps.fill(cur, id, texts[rnd.nextInt(texts.length)]); break;
                        case 8: next = LayoutOps.moveLeft(cur, id); break;
                        case 9: next = LayoutOps.moveRight(cur, id); break;
                        case 10: next = LayoutOps.moveUp(cur, id); break;
                        case 11: next = LayoutOps.moveDown(cur, id); break;
                        case 12: next = LayoutOps.insertBefore(cur, id); break;
                        case 13: next = LayoutOps.insertAfter(cur, id); break;
                        case 14: next = LayoutOps.delete(cur, id); break;
                        case 15: next = LayoutOps.merge(cur, id); break;
                        case 16: next = LayoutOps.split(cur, id); break;
                        case 17: next = LayoutOps.addRow(cur, lay, ri); break;
                        case 18: next = LayoutOps.deleteRow(cur, lay, ri); break;
                        case 19: next = LayoutOps.duplicateRow(cur, lay, ri); break;
                        case 20: next = LayoutOps.setRowHeight(cur, lay, ri, rnd.nextInt(600) - 100); break;
                        case 21: next = LayoutOps.swapRows(cur, lay, ri, rnd.nextInt(8)); break;
                        case 22: next = LayoutOps.autoBalanceRow(cur, lay, ri); break;
                        case 23: next = LayoutOps.applyWidthToRow(cur, id); break;
                        case 24: next = LayoutOps.copyKeyStyle(cur, id, all.get(rnd.nextInt(all.size()))); break;
                        case 25: next = LayoutOps.resetRow(cur, lay, ri, base()); break;
                        case 26: next = LayoutOps.resetLayer(cur, lay, base()); break;
                        case 27: next = LayoutOps.setCombo(cur, id, texts[rnd.nextInt(texts.length)]); break;
                        case 28: next = LayoutOps.setLpList(cur, id, Arrays.asList(texts[rnd.nextInt(texts.length)], texts[rnd.nextInt(texts.length)])); break;
                        default: next = LayoutOps.deleteAll(cur, Arrays.asList(id, all.get(rnd.nextInt(all.size())))); break;
                    }
                } catch (Throwable t) { threw++; fails++; System.out.println("FUZZ threw: " + t); }
                ops++;
                if (next == cur) refused++;
                String v = LayoutOps.valid(next);
                if (v != null) { fails++; if (fails < 5) System.out.println("FUZZ invalid: " + v); }
                if (next != cur && next.equals(cur)) { fails++; if (fails < 5) System.out.println("FUZZ changed instance but equal model"); }
                cur = next;
            }
        }
        eq("fuzz: 2000 sequences, no exception, always valid (" + ops + " ops, " + refused + " refused)", fails, 0);
        eq("fuzz: nothing threw", threw, 0);
        tr("fuzz: many ops really changed the model", ops - refused > 5000);

        System.out.println("LayoutOpsTest " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }

    static String rep(String s, int n) { StringBuilder b = new StringBuilder(); for (int i = 0; i < n; i++) b.append(s); return b.toString(); }

    /** en with 8 rows x 10 keys = 80 keys (+ bot layer for the shared row). */
    static LayoutDef fullLayer() {
        LayoutDef m = new LayoutDef();
        LayerDef en = new LayerDef("en");
        int n = 1;
        for (int r = 0; r < 8; r++) {
            RowDef row = new RowDef();
            for (int i = 0; i < 10; i++) { row.keys.add(KeyDef.ch("f" + n, "a", 1f)); n++; }
            en.rows.add(row);
        }
        m.layers.add(en);
        return m;
    }
}
