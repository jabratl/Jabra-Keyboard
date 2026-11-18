import com.mgb.keyboard.typing.ReplaceCheck;
import com.mgb.keyboard.typing.ReplaceEngine;
import com.mgb.keyboard.typing.ReplaceExtras;
import com.mgb.keyboard.typing.ReplacePairList;
import com.mgb.keyboard.typing.ReplacePaste;
import com.mgb.keyboard.typing.ReplaceProtect;
import com.mgb.keyboard.typing.ReplaceRules;
import com.mgb.keyboard.typing.ReplaceSmart;
import java.util.ArrayList;
import java.util.List;

/** v0.41.0 paste pairs (3), smart matching (5), never-change list (6). Run: see tools/run_tests.sh */
public class ReplaceMoreTest {
    static int ok = 0, bad = 0;
    static void eq(String name, Object a, Object b) {
        if (String.valueOf(a).equals(String.valueOf(b))) ok++; else { bad++; System.out.println("FAIL " + name + ": got [" + a + "] want [" + b + "]"); }
    }
    static ReplaceEngine.Opt opt() { ReplaceEngine.Opt o = new ReplaceEngine.Opt(); o.bangla = false; return o; }
    static ReplaceEngine.Opt smart() { ReplaceEngine.Opt o = opt(); o.smart = true; return o; }
    static String one(String text, String find, String with, ReplaceEngine.Opt o) { return ReplaceEngine.replaceAll(text, find, with, o).out; }
    static String rules(String text, String r) { return ReplaceRules.run(text, ReplaceRules.parse(r).rules, opt()).out; }
    static List<String> list(String... t) { List<String> l = new ArrayList<String>(); for (String x : t) l.add(x); return l; }

    public static void main(String[] a) {
        // ---------------- SMART (5)
        eq("smart off = exact", one("XiaoYan Xiao-Yan Xiao Yan", "Xiao Yan", "X", opt()), "XiaoYan Xiao-Yan X");
        eq("smart gaps", one("XiaoYan Xiao-Yan Xiao Yan Xiao'Yan Xiao  Yan", "Xiao Yan", "X", smart()), "X X X X X");
        eq("smart gap not newline", one("Xiao\nYan", "Xiao Yan", "X", smart()), "Xiao\nYan");
        eq("smart term no gap", one("Xiao Yan", "XiaoYan", "X", smart()), "Xiao Yan");
        eq("smart accent in text", one("un caf\u00E9 chaud", "cafe", "tea", smart()), "un tea chaud");
        eq("smart accent in term", one("un cafe chaud", "caf\u00E9", "tea", smart()), "un tea chaud");
        eq("smart accent combining", one("un cafe\u0301 chaud", "cafe", "tea", smart()), "un tea chaud");
        eq("smart off accent", one("un caf\u00E9", "cafe", "tea", opt()), "un caf\u00E9");
        eq("smart fullwidth", one("\uFF43\uFF41\uFF54 cat", "cat", "dog", smart()), "dog dog");
        eq("smart fullwidth term", one("cat", "\uFF43\uFF41\uFF54", "dog", smart()), "dog");
        eq("smart long vowel oo", one("Tokyo Toukyou Tookyoo T\u014Dky\u014D", "T\u014Dky\u014D", "X", smart()), "X X X X");
        eq("smart long vowel ou", one("Toukyou", "T\u014Dky\u014D", "X", smart()), "X");
        eq("smart russian yo", one("\u0435\u0451", "\u0435\u0435", "X", smart()), "X");
        eq("smart case insens", one("XIAO yan", "xiao Yan", "X", smart()), "X");
        eq("smart case sensitive", one("XIAO yan", "xiao Yan", "X", casey()), "XIAO yan");
        eq("smart word mode", one("Xiao-Yan Xiao-Yans", "Xiao Yan", "X", wordSmart()), "X Xiao-Yans");
        eq("smart list", one("Siao-Yan Xiaoyan", "Siao Yan|Xiao Yan", "X", listSmart()), "X X");
        eq("smart keep", one("XIAO-YAN", "xiao yan", "ming li", keepSmart()), "MING LI");
        eq("smart only gaps term falls back", one("a b", " ", "_", smart()), "a_b");
        eq("smart newline term", one("a\nb ab", "a\nb", "X", escSmart()), "X ab");
        eq("smart null for empty", ReplaceSmart.pattern("", true), null);
        eq("smart null too long", ReplaceSmart.pattern("abcdefghij".repeat(7), true), null);
        eq("smart regex ignored", one("a-b", "a.b", "X", regexSmart()), "X");
        eq("smart bangla nukta", one("\u09A1\u09BC", "\u09DC", "X", bnSmart()), "X");
        eq("pack smart", ReplaceEngine.unpack(ReplaceEngine.pack(smart())).smart, true);
        eq("pack no smart", ReplaceEngine.unpack(ReplaceEngine.pack(opt())).smart, false);
        eq("pack old value", ReplaceEngine.unpack(8 | 32).smart, false);
        eq("copy keeps smart", smart().copy().smart, true);
        // rule syntax smart:
        eq("rule smart parse", ReplaceRules.parse("smart: Xiao Yan => X").rules.get(0).smart, true);
        eq("rule smart serialize", ReplaceRules.serialize(ReplaceRules.parse("smart: word: Xiao Yan => X").rules), "word: smart: Xiao Yan => X");
        eq("rule smart roundtrip", ReplaceRules.serialize(ReplaceRules.parse(ReplaceRules.serialize(ReplaceRules.parse("keep: word: smart: a | b => c").rules)).rules), "keep: word: smart: a | b => c");
        eq("rule smart run", rules("Xiao-Yan met XiaoYan", "smart: Xiao Yan => X"), "X met X");
        eq("rule smart not for regex", ReplaceRules.parse("smart: re: a => b").rules.get(0).smart, false);
        eq("rule smart variants", rules("Siao-Yan XiaoYan", "smart: Xiao Yan | Siao Yan => X"), "X X");
        eq("rule plain unaffected", rules("Xiao-Yan", "Xiao Yan => X"), "Xiao-Yan");
        eq("whole word keeps smart", ReplaceCheck.withWholeWord(ReplaceRules.parse("smart: cat => dog").rules, opt()).get(0).smart, true);
        ReplacePairList.Made mk = ReplacePairList.make("Xiao Yan", "X", ReplaceEngine.MODE_TEXT, false, false, true);
        eq("make smart", ReplaceRules.serialize(java.util.Collections.singletonList(mk.rule)), "smart: Xiao Yan => X");
        eq("make smart regex off", ReplacePairList.make("a.b", "X", ReplaceEngine.MODE_REGEX, false, false, true).rule.smart, false);
        eq("make reserved smart", ReplacePairList.make("smart: x", "y", ReplaceEngine.MODE_TEXT, false, false).error != null, true);
        eq("label smart", ReplacePairList.label(mk.rule, 0, 60), "(smart) Xiao Yan \u2192 X");
        eq("formatter drops smart", ReplaceExtras.formatterText("n", "smart: a => b", false, false).contains("smart:"), false);
        eq("formatter smart rule kept", ReplaceExtras.formatterText("n", "smart: a | b => c", false, false).contains("\na => c\nb => c\n"), true);

        // ---------------- NEVER CHANGE (6)
        ReplaceEngine.Opt p = opt(); p.protect = list("Mark Twain");
        eq("protect partial", one("Mark met Mark Twain", "Mark", "Luke", p), "Luke met Mark Twain");
        ReplaceEngine.Result r = ReplaceEngine.replaceAll("Mark met Mark Twain", "Mark", "Luke", p);
        eq("protect count", r.count + "/" + r.changed + "/" + r.skipped, "1/1/1");
        eq("protect info", ReplaceEngine.info(r).contains("1 kept"), true);
        eq("protect none = old", one("Mark met Mark Twain", "Mark", "Luke", opt()), "Luke met Luke Twain");
        eq("protect case insens", one("mark twain, Mark", "mark", "Luke", p), "mark twain, Luke");
        ReplaceEngine.Opt pc = opt(); pc.protect = list("Mark Twain"); pc.matchCase = true;
        eq("protect match case", one("mark twain, Mark Twain, Mark", "mark", "Luke", pc), "Luke twain, Mark Twain, Mark");
        eq("protect nth skips protected", ReplaceEngine.replaceNth("Mark Twain, Mark, Mark", "Mark", "L", p, 1).out, "Mark Twain, L, Mark");
        eq("protect hits", ReplaceEngine.replaceAll("Mark Twain, Mark", "Mark", "L", p).hits.size(), 1);
        eq("protect whole match inside", one("a Mark Twain b", "Twain", "X", p), "a Mark Twain b");
        eq("protect match wider than term", one("Mark Twains", "Mark Twains", "X", p), "Mark Twains");
        eq("protect empty list", one("Mark", "Mark", "L", withProtect(list())), "L");
        eq("protect regex too", one("Mark Twain Mark", "Ma\\w+", "L", regexProtect()), "Mark Twain L");
        ReplaceRules.RunResult rr = ReplaceRules.run("Mark Twain, Mark, cat", ReplaceRules.parse("Mark => Luke\ncat => dog").rules, p);
        eq("protect set out", rr.out, "Mark Twain, Luke, dog");
        eq("protect set skipped", rr.skipped, 1);
        eq("protect set info", ReplaceRules.info(rr, 2).contains("1 kept"), true);
        eq("protect del line", ReplaceRules.run("Mark Twain\nother", ReplaceRules.parse("del: Mark").rules, p).out, "Mark Twain\nother");
        eq("protect smart together", one("Mark-Twain, Mark", "Mark", "L", protectSmart()), "Mark-Twain, L");
        // list helpers
        eq("protect clean", ReplaceProtect.clean("  a\nb  "), "a b");
        eq("protect parse", ReplaceProtect.parse("A\n\n# note\na\nB \n").toString(), "[A, B]");
        eq("protect text", ReplaceProtect.text(list("A", "B")), "A\nB");
        List<String> pl = new ArrayList<String>();
        eq("protect add", ReplaceProtect.add(pl, "Xiao Yan"), ReplaceProtect.ADDED);
        eq("protect add dup", ReplaceProtect.add(pl, "xiao yan"), ReplaceProtect.DUPLICATE);
        eq("protect add empty", ReplaceProtect.add(pl, "   "), ReplaceProtect.EMPTY);
        eq("protect addAll", java.util.Arrays.toString(ReplaceProtect.addAll(pl, "A\nB\nxiao yan\nC")), "[3, 1, 0]");
        List<String> big = new ArrayList<String>();
        for (int i = 0; i < ReplaceProtect.MAX_TERMS; i++) big.add("t" + i);
        eq("protect full", ReplaceProtect.add(big, "new"), ReplaceProtect.FULL);
        eq("protect long term cut", ReplaceProtect.clean("x".repeat(100)).length(), ReplaceProtect.MAX_LEN);
        eq("protect ranges", ReplaceProtect.ranges("ab Mark Twain Mark", list("Mark Twain", "Mark"), false).size(), 2);
        eq("protect label", ReplaceProtect.label(0) + "|" + ReplaceProtect.label(3), "Never change (none)|Never change (3)");
        // check: a Find on the never-change list
        List<ReplaceCheck.Warn> w = ReplaceCheck.check(ReplaceRules.parse("Mark Twain => X\ndog => cat").rules, p);
        boolean got = false; for (ReplaceCheck.Warn x : w) if (x.text.contains("Never-change") && x.rule == 1 && !x.minor) got = true;
        eq("check never-change", got, true);
        eq("check no protect", ReplaceCheck.check(ReplaceRules.parse("Mark Twain => X").rules, opt()).size(), 0);

        // ---------------- PASTE PAIRS (3)
        ReplacePaste.Result pr = ReplacePaste.parse("cat => dog\nred => blue\nword: sun => moon\nkeep: hello => world\n", null, 50);
        eq("paste basic", ReplaceRules.serialize(pr.rules), "cat => dog\nred => blue\nword: sun => moon\nkeep: hello => world");
        eq("paste counts", pr.added + "/" + pr.updated + "/" + pr.same + "/" + pr.bad, "4/0/0/0");
        eq("paste variants", ReplaceRules.serialize(ReplacePaste.parse("Xiaoyan | Xiao Yan | Siao Yan => Xiao Yan", null, 50).rules), "Xiaoyan | Xiao Yan | Siao Yan => Xiao Yan");
        eq("paste smart prefix", ReplaceRules.serialize(ReplacePaste.parse("smart: Xiao Yan => X", null, 50).rules), "smart: Xiao Yan => X");
        eq("paste tab", ReplaceRules.serialize(ReplacePaste.parse("cat\tdog\tnote\nred\tblue", null, 50).rules), "cat => dog\nred => blue");
        eq("paste arrow", ReplaceRules.serialize(ReplacePaste.parse("cat \u2192 dog\nred -> blue\nsun = moon", null, 50).rules), "cat => dog\nred => blue\nsun => moon");
        eq("paste crlf + blank + comment", ReplaceRules.serialize(ReplacePaste.parse("# my list\r\n\r\ncat => dog\r\n", null, 50).rules), "cat => dog");
        eq("paste empty with", ReplaceRules.parse(ReplaceRules.serialize(ReplacePaste.parse("cat =>", null, 50).rules)).rules.get(0).with, "");
        eq("paste regex ok", ReplacePaste.parse("re: (a+) => <$1>", null, 50).added, 1);
        eq("paste regex bad", ReplacePaste.parse("re: (a => b", null, 50).bad, 1);
        ReplacePaste.Result pb = ReplacePaste.parse("cat => dog\nno separator here\ndel: x\n=> empty find\nred => blue", null, 50);
        eq("paste bad lines", pb.added + "/" + pb.bad + "/" + pb.firstBad, "2/3/2");
        eq("paste summary", pb.summary(), "Pasted 2 pairs (3 lines skipped (first: line 2))");
        List<ReplaceRules.Rule> cur = ReplaceRules.parse("cat => dog\nred => blue").rules;
        ReplacePaste.Result pm = ReplacePaste.parse("cat => cow\nred => blue\nsun => moon", cur, 50);
        eq("paste merge", ReplaceRules.serialize(pm.rules), "cat => cow\nred => blue\nsun => moon");
        eq("paste merge counts", pm.added + "/" + pm.updated + "/" + pm.same, "1/1/1");
        eq("paste input list unchanged", ReplaceRules.serialize(cur), "cat => dog\nred => blue");
        ReplacePaste.Result pf = ReplacePaste.parse("a => 1\nb => 2\nc => 3", ReplaceRules.parse("x => y").rules, 3);
        eq("paste full", pf.rules.size() + "/" + pf.added + "/" + pf.full, "3/2/1");
        eq("paste full summary", pf.summary().contains("1 not added"), true);
        ReplacePaste.Result pe = ReplacePaste.parse("   \n ", null, 50);
        eq("paste empty", pe.empty, true);
        eq("paste empty summary", pe.summary().startsWith("Nothing to paste"), true);
        eq("paste null", ReplacePaste.parse(null, null, 50).empty, true);
        eq("paste no new", ReplacePaste.parse("cat => dog", ReplaceRules.parse("cat => dog").rules, 50).summary(), "No new pair found (1 repeat)");
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 700; i++) many.append("w").append(i).append(" => v").append(i).append('\n');
        eq("paste 500 line limit", ReplacePaste.parse(many.toString(), null, 1000).added, 500);
        eq("paste long line bad", ReplacePaste.parse("a".repeat(400) + " => b", null, 50).bad, 1);
        // pasted pairs run like a set
        ReplacePaste.Result pz = ReplacePaste.parse("cat => dog\nXiao Yan | XiaoYan => Xiao Yan", null, 50);
        eq("paste then run", ReplaceRules.run("cat XiaoYan", pz.rules, opt()).out, "dog Xiao Yan");

        System.out.println("ReplaceMoreTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }

    static ReplaceEngine.Opt casey() { ReplaceEngine.Opt o = smart(); o.matchCase = true; return o; }
    static ReplaceEngine.Opt wordSmart() { ReplaceEngine.Opt o = smart(); o.mode = ReplaceEngine.MODE_WORD; return o; }
    static ReplaceEngine.Opt listSmart() { ReplaceEngine.Opt o = smart(); o.list = true; return o; }
    static ReplaceEngine.Opt keepSmart() { ReplaceEngine.Opt o = smart(); o.keepCase = true; return o; }
    static ReplaceEngine.Opt escSmart() { ReplaceEngine.Opt o = smart(); o.escapes = true; return o; }
    static ReplaceEngine.Opt regexSmart() { ReplaceEngine.Opt o = smart(); o.mode = ReplaceEngine.MODE_REGEX; return o; }
    static ReplaceEngine.Opt bnSmart() { ReplaceEngine.Opt o = smart(); o.bangla = true; return o; }
    static ReplaceEngine.Opt withProtect(List<String> l) { ReplaceEngine.Opt o = opt(); o.protect = l; return o; }
    static ReplaceEngine.Opt regexProtect() { ReplaceEngine.Opt o = opt(); o.mode = ReplaceEngine.MODE_REGEX; o.protect = list("Mark Twain"); return o; }
    static ReplaceEngine.Opt protectSmart() { ReplaceEngine.Opt o = smart(); o.protect = list("Mark-Twain"); return o; }
}
