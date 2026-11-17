import com.mgb.keyboard.typing.ReplaceEngine;
import com.mgb.keyboard.typing.ReplaceRules;
import java.util.List;

/** v0.25.0 (R2) ReplaceRules: parse / serialize, formatter syntax, bad lines, order, limits, time. Run: see tools/run_tests.sh */
public class ReplaceRulesTest {
    static int ok = 0, bad = 0;
    static void eq(String name, Object a, Object b) {
        if (String.valueOf(a).equals(String.valueOf(b))) ok++; else { bad++; System.out.println("FAIL " + name + ": got [" + a + "] want [" + b + "]"); }
    }
    static ReplaceEngine.Opt opt() { ReplaceEngine.Opt o = new ReplaceEngine.Opt(); o.bangla = false; return o; }
    static String run(String text, String rules) { return ReplaceRules.run(text, ReplaceRules.parse(rules).rules, opt()).out; }

    public static void main(String[] a) {
        // parse
        ReplaceRules.Parsed p = ReplaceRules.parse("cat => dog\nre: a(b+) => <$1>\ndel: spam\ndel-re: ^x\\d\n# note\n\nbroken line\n => nothing\nre: =>");
        eq("active", p.active(), 4);
        eq("rules incl comment", p.rules.size(), 5);
        eq("bad lines", p.bad.toString(), "[7, 8, 9]");
        eq("kind0", p.rules.get(0).kind, ReplaceRules.K_TEXT);
        eq("kind1", p.rules.get(1).kind, ReplaceRules.K_REGEX);
        eq("kind2", p.rules.get(2).kind, ReplaceRules.K_DEL);
        eq("kind3", p.rules.get(3).kind, ReplaceRules.K_DELRE);
        eq("kind4", p.rules.get(4).kind, ReplaceRules.K_COMMENT);
        // formatter syntax: one leading space of "with" dropped, trailing trimmed, empty with = remove
        eq("with trim", ReplaceRules.parse("a =>   b  ").rules.get(0).with, "  b");
        eq("empty with", ReplaceRules.parse("a =>").rules.get(0).with, "");
        // round trip
        String src = "cat => dog\nre: a(b+) => <$1>\ndel: spam\ndel-re: ^x\\d\n# note";
        eq("roundtrip", ReplaceRules.serialize(ReplaceRules.parse(src).rules), src);
        eq("roundtrip2", ReplaceRules.serialize(ReplaceRules.parse(ReplaceRules.serialize(ReplaceRules.parse(src).rules)).rules), src);
        // order: each rule runs on the result of the one before
        eq("order", run("a", "a => b\nb => c"), "c");
        eq("order rev", run("a", "b => c\na => b"), "b");
        // counts + changed
        ReplaceRules.RunResult rr = ReplaceRules.run("aa bb", ReplaceRules.parse("a => x\nb => y\nz => q").rules, opt());
        eq("out", rr.out, "xx yy");
        eq("changed", rr.changed, 4);
        eq("counts", rr.counts[0] + "," + rr.counts[1] + "," + rr.counts[2], "2,2,0");
        eq("no err", rr.err, null);
        // regex + del
        eq("regex", run("abbb", "re: a(b+) => <$1>"), "<bbb>");
        eq("del line", run("keep\nbuy spam now\nkeep2", "del: spam"), "keep\nkeep2");
        eq("del last line", run("keep\nspam", "del: spam"), "keep\n");
        eq("del-re", run("x1\ny\nx2", "del-re: ^x\\d"), "y\n");
        eq("del text is literal", run("a.b\naxb", "del: a.b"), "axb");
        // a bad rule is skipped and reported, the rest runs
        rr = ReplaceRules.run("abc", ReplaceRules.parse("re: ( => x\nb => B").rules, opt());
        eq("bad rule skipped", rr.out, "aBc");
        eq("bad rule err", rr.err != null && rr.err.startsWith("Rule 1:"), true);
        eq("bad rule count", rr.errors, 1);
        // flags of the set
        ReplaceEngine.Opt o = opt(); o.matchCase = true;
        eq("case on", ReplaceRules.run("Cat cat", ReplaceRules.parse("cat => dog").rules, o).out, "Cat dog");
        o = opt(); o.mode = ReplaceEngine.MODE_WORD;
        eq("word", ReplaceRules.run("cat concat", ReplaceRules.parse("cat => dog").rules, o).out, "dog concat");
        // 50 rule limit
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) sb.append("x").append(i).append(" => y\n");
        p = ReplaceRules.parse(sb.toString());
        eq("limit 50", p.active(), 50);
        eq("limit flag", p.tooMany, true);
        eq("comments do not count", ReplaceRules.parse("# a\n# b\nx => y").active(), 1);
        // empty / null never throw
        eq("null text", ReplaceRules.run(null, ReplaceRules.parse("a => b").rules, null).out, "");
        eq("null src", ReplaceRules.parse(null).rules.size(), 0);
        // time budget: 50 rules on a 100000 char text finish fast
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 100000; i++) big.append((char) ('a' + i % 26));
        sb = new StringBuilder();
        for (int i = 0; i < 50; i++) sb.append("re: [aeiou]x").append(i).append(" => z\n");
        long t = System.currentTimeMillis();
        rr = ReplaceRules.run(big.toString(), ReplaceRules.parse(sb.toString()).rules, opt());
        eq("budget fast", System.currentTimeMillis() - t < 1800, true);
        eq("big out kept", rr.out.length(), 100000);
        // ---- v0.29.0 word: / keep: per rule
        {
            ReplaceRules.Parsed pw = ReplaceRules.parse("word: cat => dog\nkeep: hello => world\nkeep: word: red => blue\nkeep: re: (b)ad => good\nplain => x");
            eq("word/keep parsed: 5 rules, none bad", pw.rules.size() == 5 && pw.bad.isEmpty() ? 1 : 0, 1);
            eq("word flag", pw.rules.get(0).word && !pw.rules.get(0).keep ? 1 : 0, 1);
            eq("keep flag", pw.rules.get(1).keep && !pw.rules.get(1).word ? 1 : 0, 1);
            eq("both flags", pw.rules.get(2).keep && pw.rules.get(2).word ? 1 : 0, 1);
            eq("keep before re:", pw.rules.get(3).keep && pw.rules.get(3).kind == ReplaceRules.K_REGEX ? 1 : 0, 1);
            eq("serialize round trip", ReplaceRules.serialize(ReplaceRules.parse(ReplaceRules.serialize(pw.rules)).rules).equals(ReplaceRules.serialize(pw.rules)) ? 1 : 0, 1);
            ReplaceEngine.Opt o0 = new ReplaceEngine.Opt(); o0.bangla = false;
            ReplaceRules.RunResult r1 = ReplaceRules.run("cat concat Cat", ReplaceRules.parse("word: cat => dog").rules, o0);
            eq("word: only whole words", r1.out.equals("dog concat dog") ? 1 : 0, 1);
            ReplaceRules.RunResult r2 = ReplaceRules.run("hello Hello HELLO", ReplaceRules.parse("keep: hello => world").rules, o0);
            eq("keep: case look", r2.out.equals("world World WORLD") ? 1 : 0, 1);
            ReplaceRules.RunResult r3 = ReplaceRules.run("hello Hello HELLO", ReplaceRules.parse("hello => world").rules, o0);
            eq("no keep = plain", r3.out.equals("world world world") ? 1 : 0, 1);
        }
        System.out.println("ReplaceRulesTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
