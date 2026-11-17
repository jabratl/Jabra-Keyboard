import com.mgb.keyboard.typing.ReplaceEngine;
import com.mgb.keyboard.typing.ReplacePeek;

/** v0.32.0 (PEEK) ReplacePeek tests (computer only): windows, surrogate pairs, no match, error, empty text, 50+ hits, sets, diffMiddle, null safety. */
public class ReplacePeekTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    static String mk(String s, int[] m) { return s.substring(m[0], m[1]); }
    static boolean valid(ReplacePeek.Peek p) {   // every mark inside its text, no pair cut
        for (ReplacePeek.Row r : p.rows) {
            if (r.bm[0] < 0 || r.bm[0] > r.bm[1] || r.bm[1] > r.before.length()) return false;
            if (r.am[0] < 0 || r.am[0] > r.am[1] || r.am[1] > r.after.length()) return false;
            if (broken(r.before) || broken(r.after)) return false;
        }
        return true;
    }
    static boolean broken(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) { if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) return true; i++; }
            else if (Character.isLowSurrogate(c)) return true;
        }
        return false;
    }

    public static void main(String[] x) {
        ReplaceEngine.Opt o = new ReplaceEngine.Opt();
        // ---- forResult
        String txt = "I like cats and cats like me";
        ReplaceEngine.Result r = ReplaceEngine.replaceAll(txt, "cats", "dogs", o);
        ReplacePeek.Peek p = ReplacePeek.forResult(txt, r, 3, 24);
        t("result: 2 rows", p.rows.size() == 2);
        t("result: summary 2 matches", p.summary.equals("2 matches"));
        t("result: before mark = found", mk(p.rows.get(0).before, p.rows.get(0).bm).equals("cats"));
        t("result: after mark = replacement", mk(p.rows.get(0).after, p.rows.get(0).am).equals("dogs"));
        t("result: no more line", p.more.isEmpty());
        t("result: marks valid", valid(p));
        t("result: hint", p.hint.contains("Release"));
        // more
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 60; i++) big.append("ab ");
        ReplaceEngine.Result r2 = ReplaceEngine.replaceAll(big.toString(), "ab", "xy", o);
        ReplacePeek.Peek p2 = ReplacePeek.forResult(big.toString(), r2, 3, 24);
        t("50+ hits: 3 rows", p2.rows.size() == 3);
        t("50+ hits: +57 more", p2.more.equals("+57 more matches"));
        t("50+ hits: text <= 600", p2.size() <= ReplacePeek.LIMIT);
        // no match / blank / error / empty text
        ReplaceEngine.Result rn = ReplaceEngine.replaceAll(txt, "zzz", "q", o);
        ReplacePeek.Peek pn = ReplacePeek.forResult(txt, rn, 3, 24);
        t("no match: one line", pn.rows.isEmpty() && pn.summary.equals("No match"));
        ReplacePeek.Peek pb = ReplacePeek.forResult(txt, ReplaceEngine.replaceAll(txt, "", "q", o), 3, 24);
        t("blank find: message", pb.rows.isEmpty() && pb.summary.equals("Type what to find"));
        ReplaceEngine.Opt ro = new ReplaceEngine.Opt(); ro.mode = ReplaceEngine.MODE_REGEX;
        ReplacePeek.Peek pe = ReplacePeek.forResult(txt, ReplaceEngine.replaceAll(txt, "(", "q", ro), 3, 24);
        t("regex error: message, no rows", pe.rows.isEmpty() && !pe.summary.isEmpty());
        ReplacePeek.Peek pz = ReplacePeek.forResult("", ReplaceEngine.replaceAll("", "a", "b", o), 3, 24);
        t("empty text: message", pz.rows.isEmpty() && pz.summary.startsWith("No text"));
        // long text
        StringBuilder huge = new StringBuilder(); for (int i = 0; i < 100001; i++) huge.append('a');
        ReplacePeek.Peek ph = ReplacePeek.forResult(huge.toString(), new ReplaceEngine.Result(), 3, 24);
        t("100001 chars: message", ph.rows.isEmpty() && ph.summary.contains("too long"));
        // window + emoji / CJK pair at the cut
        String emo = "\uD83D\uDE00";
        StringBuilder e = new StringBuilder();
        for (int i = 0; i < 30; i++) e.append(emo);
        e.append("\u4F60\u597D"); e.append("\uD840\uDC0B");   // rare CJK pair
        for (int i = 0; i < 30; i++) e.append(emo);
        String et = e.toString();
        for (int c = 1; c <= 30; c++) {
            ReplacePeek.Peek pp = ReplacePeek.forResult(et, ReplaceEngine.replaceAll(et, "\u4F60\u597D", "X", o), 3, c);
            if (!valid(pp)) { t("emoji cut ctx=" + c, false); break; }
            if (c == 30) t("emoji / CJK pairs never cut (ctx 1..30)", true);
        }
        ReplacePeek.Peek pw = ReplacePeek.forSet("E", "\u4F60\u597D => X", o, et);
        t("set: emoji line window valid", valid(pw) && pw.rows.size() == 1 && mk(pw.rows.get(0).after, pw.rows.get(0).am).equals("X"));
        // RTL (Arabic) marks are index ranges
        String ar = "\u0645\u0631\u062D\u0628\u0627 \u0628\u0627\u0644\u0639\u0627\u0644\u0645";
        ReplacePeek.Peek pr = ReplacePeek.forResult(ar, ReplaceEngine.replaceAll(ar, "\u0628\u0627\u0644\u0639\u0627\u0644\u0645", "\u0627\u0644\u0643\u0644", o), 3, 24);
        t("rtl: marks match found", pr.rows.size() == 1 && mk(pr.rows.get(0).before, pr.rows.get(0).bm).equals("\u0628\u0627\u0644\u0639\u0627\u0644\u0645"));
        // ---- forSet
        String rules = "# clean\ncolour => hue\ngrey => ash\ndel: DRAFT";
        String body = "the colour is grey\nsecond line\nthird colour";
        ReplacePeek.Peek ps = ReplacePeek.forSet("Clean text", rules, o, body);
        t("set: title", ps.title.equals("Clean text"));
        t("set: summary has no name prefix", !ps.summary.startsWith("Clean text:") && ps.summary.contains("change"));
        t("set: 2 changed lines (comment ignored)", ps.rows.size() == 2 && ps.total == 2 && ps.more.isEmpty());
        t("set: before mark 'colour'", mk(ps.rows.get(1).before, ps.rows.get(1).bm).equals("colour"));
        t("set: after mark 'color'", mk(ps.rows.get(1).after, ps.rows.get(1).am).equals("hue"));
        t("set: rule lines with counts", ps.rules.size() == 3 && ps.rules.get(0).startsWith("2x"));
        t("set: hint", ps.hint.contains("tap to load"));
        t("set: comment line not listed", !ps.text().contains("# clean"));
        // many changed lines -> more
        StringBuilder ml = new StringBuilder(); for (int i = 0; i < 8; i++) ml.append("colour ").append(i).append('\n');
        ReplacePeek.Peek pm = ReplacePeek.forSet("M", "colour => color", o, ml.toString());
        t("set: 3 rows + more lines", pm.rows.size() == 3 && pm.more.equals("+5 more lines"));
        // del: changes line count -> one row over the whole text
        ReplacePeek.Peek pd = ReplacePeek.forSet("D", "del: DRAFT", o, "keep\nDRAFT here\nkeep2");
        t("set: del rule = one row", pd.rows.size() == 1 && valid(pd));
        // long line window
        StringBuilder ll = new StringBuilder(); for (int i = 0; i < 40; i++) ll.append("word ");
        String line = ll + "colour " + ll;
        ReplacePeek.Peek pl = ReplacePeek.forSet("L", "colour => hue", o, line);
        t("set: long line = window with dots", pl.rows.size() == 1 && pl.rows.get(0).before.startsWith("\u2026") && pl.rows.get(0).before.endsWith("\u2026") && pl.rows.get(0).before.length() < 70);
        t("set: window marks", mk(pl.rows.get(0).before, pl.rows.get(0).bm).equals("colour") && mk(pl.rows.get(0).after, pl.rows.get(0).am).equals("hue"));
        // huge change part is cut
        StringBuilder cc = new StringBuilder(); for (int i = 0; i < 200; i++) cc.append('q');
        ReplacePeek.Peek pc = ReplacePeek.forSet("C", "re: q+ => Z", ro2(), "a " + cc + " b");
        t("set: long middle is cut", pc.rows.size() == 1 && pc.rows.get(0).before.length() <= 80);
        // no text / no rules / no change
        t("set: no text", ReplacePeek.forSet("S", "a => b", o, "").rows.isEmpty());
        t("set: no rules", ReplacePeek.forSet("S", "# only comment", o, "abc").summary.contains("no rules"));
        t("set: nothing changes = no rows", ReplacePeek.forSet("S", "zzz => b", o, "abc").rows.isEmpty());
        t("set: text too long", ReplacePeek.forSet("S", "a => b", o, huge.toString()).summary.contains("too long"));
        t("set: card text <= 600", ReplacePeek.forSet("Clean text", many(), o, ml.toString()).size() <= ReplacePeek.LIMIT);
        // tabs / returns are shown as signs and keep the length
        ReplacePeek.Peek pt = ReplacePeek.forSet("T", "a => b", o, "a\tx\r");
        t("set: tab / return flattened", pt.rows.size() == 1 && pt.rows.get(0).before.indexOf('\t') < 0 && pt.rows.get(0).before.indexOf('\r') < 0);
        // ---- diffMiddle
        int[] d = ReplacePeek.diffMiddle("hello world", "hello there world");
        t("diff: prefix 6, suffix 5", d[0] == 6 && d[1] == 5);
        d = ReplacePeek.diffMiddle("abc", "abc");
        t("diff: equal = no overlap", d[0] + d[1] <= 3 && d[0] == 3 && d[1] == 0);
        d = ReplacePeek.diffMiddle("", "xyz");
        t("diff: empty side", d[0] == 0 && d[1] == 0);
        d = ReplacePeek.diffMiddle("aaa", "aa");
        t("diff: no overlap", d[0] + d[1] <= 2);
        d = ReplacePeek.diffMiddle("x\uD83D\uDE00y", "x\uD83D\uDE01y");
        t("diff: pair not split", d[0] == 1 && d[1] == 1);
        d = ReplacePeek.diffMiddle(null, "a");
        t("diff: null", d[0] == 0 && d[1] == 0);
        // ---- null safety
        t("null: forResult", ReplacePeek.forResult(null, null, 3, 24).rows.isEmpty());
        t("null: forResult text null", ReplacePeek.forResult(null, new ReplaceEngine.Result(), 0, -5) != null);
        t("null: forSet all null", ReplacePeek.forSet(null, null, null, null) != null);
        t("null: forSet o null", ReplacePeek.forSet("a", "x => y", null, "x").rows.size() == 1);
        t("null: text() never throws", ReplacePeek.forSet(null, null, null, null).text() != null);
        t("message: title + line", ReplacePeek.message("X", "Not available").summary.equals("Not available") && ReplacePeek.message(null, null).rows.isEmpty());
        System.out.println("ReplacePeekTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }

    static ReplaceEngine.Opt ro2() { ReplaceEngine.Opt o = new ReplaceEngine.Opt(); return o; }
    static String many() { StringBuilder b = new StringBuilder(); for (int i = 0; i < 9; i++) b.append("colour").append(i).append(" => color").append(i).append('\n'); return b.toString(); }
}
