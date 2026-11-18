import com.mgb.keyboard.typing.ReplaceCheck;
import com.mgb.keyboard.typing.ReplaceEngine;
import com.mgb.keyboard.typing.ReplaceExtras;
import com.mgb.keyboard.typing.ReplacePairList;
import com.mgb.keyboard.typing.ReplaceRules;
import java.util.ArrayList;
import java.util.List;

/** v0.40.0 variants (a | b => c), Check pairs, pair list (make / label / name). Run: see tools/run_tests.sh */
public class ReplaceCheckTest {
    static int ok = 0, bad = 0;
    static void eq(String name, Object a, Object b) {
        if (String.valueOf(a).equals(String.valueOf(b))) ok++; else { bad++; System.out.println("FAIL " + name + ": got [" + a + "] want [" + b + "]"); }
    }
    static ReplaceEngine.Opt opt() { ReplaceEngine.Opt o = new ReplaceEngine.Opt(); o.bangla = false; return o; }
    static ReplaceEngine.Opt optCase() { ReplaceEngine.Opt o = opt(); o.matchCase = true; return o; }
    static String run(String text, String rules) { return ReplaceRules.run(text, ReplaceRules.parse(rules).rules, opt()).out; }
    static List<ReplaceCheck.Warn> chk(String rules) { return ReplaceCheck.check(ReplaceRules.parse(rules).rules, opt()); }
    static boolean has(List<ReplaceCheck.Warn> w, String part) { for (ReplaceCheck.Warn x : w) if (x.text.contains(part)) return true; return false; }

    public static void main(String[] a) {
        // ---- variants
        eq("var basic", run("Xiaoyan met Xiao Yan and Siao Yan.", "Xiaoyan | Xiao Yan | Siao Yan => Xiao Yan"), "Xiao Yan met Xiao Yan and Xiao Yan.");
        eq("var list", ReplaceRules.variants("a | b |  c | a"), "[a, b, c]");
        eq("var one", ReplaceRules.variants("a|b").size(), 0);
        eq("var none", ReplaceRules.variants("cat").size(), 0);
        eq("var longest first", run("Xiao Yan Wei", "Xiao | Xiao Yan | Xiao Yan Wei => X"), "X");
        eq("var word", run("cat category cats", "word: cat | cats => dog"), "dog category dog");
        eq("var keep", run("Cat CAT cat", "keep: cat | kitty => dog"), "Dog DOG dog");
        eq("var roundtrip", ReplaceRules.serialize(ReplaceRules.parse("a | b => c").rules), "a | b => c");
        eq("var count rule", ReplaceRules.parse("a | b | c => x").active(), 1);
        ReplaceRules.RunResult rr = ReplaceRules.run("a b c d", ReplaceRules.parse("a | b | c => x").rules, opt());
        eq("var changed", rr.changed, 3);
        eq("var out", rr.out, "x x x d");
        eq("var old pipe kept", run("a|b", "a|b => x"), "x");   // a pipe WITHOUT spaces is still plain text
        eq("var regex untouched", run("ab", "re: a | b => x"), "ab");   // regex rule: " | " is part of the pattern (needs spaces), no variants
        // formatter bridge
        String ft = ReplaceExtras.formatterText("t", "a | b => c\nre: x|y => z\nword: d | e => f", false, false);
        eq("fmt expand", ft.contains("a => c\nb => c"), true);
        eq("fmt re kept", ft.contains("re: x|y => z"), true);
        eq("fmt word", ft.contains("d => f\ne => f"), true);
        eq("fmt note", ft.contains("split into one line per variant"), true);
        eq("fmt plain same", ReplaceExtras.formatterText("t", "a => b", false, false).contains("variants"), false);

        // ---- check: duplicates
        eq("dup other with", has(chk("cat => dog\ncat => cow"), "also in rule 1 with another With"), true);
        eq("dup same with", has(chk("cat => dog\ncat => dog"), "already in rule 1 (same With)"), true);
        eq("dup case-insens", has(chk("Cat => dog\ncat => cow"), "also in rule 1"), true);
        eq("dup case-sens none", has(ReplaceCheck.check(ReplaceRules.parse("Cat => dog\ncat => cow").rules, optCase()), "also in rule"), false);
        eq("dup in variants", has(chk("a1 | b1 => x\nb1 => y"), "\"b1\" is also in rule 1"), true);
        // chain
        eq("chain", has(chk("alpha => beta\nbeta => gamma"), "Rule 1 makes \"beta\", which rule 2 changes again"), true);
        eq("chain inside", has(chk("alpha => the beta one\nbeta => gamma"), "rule 2 changes again"), true);
        eq("no chain reverse order", has(chk("beta => gamma\nalpha => beta"), "changes again"), false);
        eq("chain word ok", has(chk("word: alpha => betamax\nword: beta => gamma"), "changes again"), false);
        eq("chain word hit", has(chk("word: alpha => a beta b\nword: beta => gamma"), "changes again"), true);
        // short
        List<ReplaceCheck.Warn> sh = chk("cat => dog\nxiaoyan => xiao yan\nthe => a");
        eq("short cat", has(sh, "\"cat\" is short"), true);
        eq("short the", has(sh, "\"the\" is short"), true);
        eq("short long ok", has(sh, "xiaoyan\" is short"), false);
        eq("short count", ReplaceCheck.shortCount(sh), 2);
        eq("short word ok", has(chk("word: cat => dog"), "is short"), false);
        eq("short set word mode", has(ReplaceCheck.check(ReplaceRules.parse("cat => dog").rules, wordOpt()), "is short"), false);
        eq("short bangla not", has(chk("কা => খ"), "is short"), false);
        eq("short cjk not", has(chk("猫 => 狗"), "is short"), false);
        eq("short single letter not", has(chk("x => y"), "is short"), false);
        eq("short apostrophe not", has(chk("\u2019\u2019 => '"), "is short"), false);
        eq("short cyrillic", has(chk("\u043A\u043E\u0442 => \u043F\u0451\u0441"), "is short"), true);
        eq("short in variants", has(chk("cat | kit => dog"), "is short"), true);
        // fix
        String fixed = ReplaceRules.serialize(ReplaceCheck.withWholeWord(ReplaceRules.parse("cat => dog\nxiaoyan => x\nkeep: the => a\nre: a => b\n# c").rules, opt()));
        eq("fix", fixed, "word: cat => dog\nxiaoyan => x\nkeep: word: the => a\nre: a => b\n# c");
        eq("fix result", ReplaceRules.run("cat category", ReplaceRules.parse("cat => dog").rules, opt()).out, "dog dogegory");
        eq("fix result2", ReplaceRules.run("cat category", ReplaceRules.parse("word: cat => dog").rules, opt()).out, "dog category");
        // misc
        eq("empty with", has(chk("long text here =>"), "will be deleted"), true);
        eq("same", has(chk("abcd => abcd"), "both sides are the same"), true);
        eq("case change not same", has(chk("abc => Abc"), "both sides are the same"), false);
        eq("bad re", has(chk("re: (a => b"), "pattern is not valid"), true);
        eq("bad del-re", has(chk("del-re: [a"), "pattern is not valid"), true);
        eq("ok list", chk("Xiaoyan | Siao Yan => Xiao Yan\nfoobar => baz").size(), 0);
        eq("rule numbers skip comments", has(chk("# c\nabc => \n"), "Rule 1:"), true);
        eq("null", ReplaceCheck.check(null, null).size(), 0);
        eq("minor empty with", ReplaceCheck.serious(chk("long text here =>")), 0);
        eq("minor same", ReplaceCheck.serious(chk("abcd => abcd")), 0);
        eq("serious chain", ReplaceCheck.serious(chk("alpha => beta\nbeta => gamma")), 1);
        eq("serious null", ReplaceCheck.serious(null), 0);
        // the ready-made sets must not stop Replace all (no serious warning)
        for (com.mgb.keyboard.typing.ReplaceSets.Def d : com.mgb.keyboard.typing.ReplaceSets.all())
            eq("ready-made ok: " + d.name, ReplaceCheck.serious(ReplaceCheck.check(ReplaceRules.parse(d.text).rules, d.opt())), 0);
        eq("line ok", ReplaceCheck.line(new ArrayList<ReplaceCheck.Warn>()), "Check: no problems found");
        eq("line warn", ReplaceCheck.line(chk("cat => dog")).startsWith("Check: 1 warning. Rule 1"), true);
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 30; i++) many.append("x").append(i).append("xyz => \n");
        List<ReplaceCheck.Warn> mw = chk(many.toString());
        eq("max warn", mw.size(), ReplaceCheck.MAX_WARN + 1);
        eq("more line", mw.get(mw.size() - 1).text, "+ 18 more warnings.");

        // ---- pair list
        ReplacePairList.Made m = ReplacePairList.make("  cat ", "dog  ", ReplaceEngine.MODE_TEXT, false, false);
        eq("make ok", m.error, null);
        eq("make text", ReplaceRules.serialize(java.util.Arrays.asList(m.rule)), "cat => dog");
        eq("make word", ReplaceRules.serialize(java.util.Arrays.asList(ReplacePairList.make("cat", "dog", ReplaceEngine.MODE_WORD, false, false).rule)), "word: cat => dog");
        eq("make keep", ReplaceRules.serialize(java.util.Arrays.asList(ReplacePairList.make("cat", "dog", ReplaceEngine.MODE_WORD, true, false).rule)), "keep: word: cat => dog");
        eq("make regex", ReplaceRules.serialize(java.util.Arrays.asList(ReplacePairList.make("a(b+)", "<$1>", ReplaceEngine.MODE_REGEX, false, false).rule)), "re: a(b+) => <$1>");
        eq("make list", ReplaceRules.serialize(java.util.Arrays.asList(ReplacePairList.make("a|b | c|a", "x", ReplaceEngine.MODE_TEXT, false, true).rule)), "a | b | c => x");
        eq("make empty with", ReplaceRules.serialize(java.util.Arrays.asList(ReplacePairList.make("cat", "", ReplaceEngine.MODE_TEXT, false, false).rule)), "cat => ");
        eq("err empty", ReplacePairList.make("  ", "x", 0, false, false).error != null, true);
        eq("err newline", ReplacePairList.make("a\nb", "x", 0, false, false).error != null, true);
        eq("err newline with", ReplacePairList.make("a", "x\ny", 0, false, false).error != null, true);
        eq("err arrow", ReplacePairList.make("a=>b", "x", 0, false, false).error != null, true);
        eq("err reserved re", ReplacePairList.make("re: x", "y", 0, false, false).error != null, true);
        eq("err reserved hash", ReplacePairList.make("#x", "y", 0, false, false).error != null, true);
        eq("err reserved word", ReplacePairList.make("Word: x", "y", 0, false, false).error != null, true);
        eq("err bad regex", ReplacePairList.make("(a", "y", ReplaceEngine.MODE_REGEX, false, false).error != null, true);
        eq("with arrow ok", ReplacePairList.make("a", "b=>c", 0, false, false).error, null);
        // every made rule survives save + load
        String[][] tryIt = { {"cat", "dog"}, {"a", " lead"}, {"x y", "z => w"}, {"ক", ""}, {"it's", "it’s"} };
        for (String[] t : tryIt) {
            ReplaceRules.Rule r = ReplacePairList.make(t[0], t[1], 0, false, false).rule;
            String txt = ReplaceRules.serialize(java.util.Arrays.asList(r));
            ReplaceRules.Parsed p = ReplaceRules.parse(txt);
            eq("roundtrip " + t[0], p.rules.size() == 1 && p.rules.get(0).find.equals(r.find) && p.rules.get(0).with.equals(r.with), true);
        }
        List<ReplaceRules.Rule> l = new ArrayList<ReplaceRules.Rule>();
        l.add(ReplacePairList.make("cat", "dog", 0, false, false).rule);
        eq("indexOf", ReplacePairList.indexOf(l, ReplacePairList.make("cat", "cow", 0, false, false).rule), 0);
        eq("indexOf none", ReplacePairList.indexOf(l, ReplacePairList.make("dog", "cow", 0, false, false).rule), -1);
        eq("indexOf kind", ReplacePairList.indexOf(l, ReplacePairList.make("cat", "cow", ReplaceEngine.MODE_REGEX, false, false).rule), -1);
        eq("label", ReplacePairList.label(l.get(0), 3, 40), "3×  cat → dog");
        eq("label 0", ReplacePairList.label(l.get(0), 0, 40), "cat → dog");
        eq("label word", ReplacePairList.label(ReplacePairList.make("cat", "", ReplaceEngine.MODE_WORD, false, false).rule, 1, 40), "1×  (word) cat → (delete)");
        eq("label cut", ReplacePairList.label(l.get(0), 0, 6), "cat →…");
        java.util.Set<String> names = new java.util.LinkedHashSet<String>();
        eq("name 1", ReplacePairList.nextName(names), "Pairs 1");
        names.add("Pairs 1"); names.add("pairs 2");
        eq("name 3", ReplacePairList.nextName(names), "Pairs 3");
        eq("text empty", ReplacePairList.text(null), "");
        // a 50 rule limit exists in the list
        eq("max", ReplacePairList.MAX, 50);

        System.out.println("ReplaceCheckTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
    static ReplaceEngine.Opt wordOpt() { ReplaceEngine.Opt o = opt(); o.mode = ReplaceEngine.MODE_WORD; return o; }
}
