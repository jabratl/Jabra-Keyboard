import com.mgb.keyboard.typing.ReplaceEngine;
import com.mgb.keyboard.typing.ReplaceEngine.Opt;
import com.mgb.keyboard.typing.ReplaceEngine.Result;

/**
 * v0.24.0 (R1) ReplaceEngine tests (computer only).
 * javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/ReplaceEngineTest.java && java -cp out ReplaceEngineTest
 */
public class ReplaceEngineTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    static Opt text() { return new Opt(); }
    static Opt word() { Opt o = new Opt(); o.mode = ReplaceEngine.MODE_WORD; return o; }
    static Opt rx() { Opt o = new Opt(); o.mode = ReplaceEngine.MODE_REGEX; return o; }
    static String all(String s, String f, String w, Opt o) { return ReplaceEngine.replaceAll(s, f, w, o).out; }

    public static void main(String[] x) {
        // ---- text mode
        t("plain replace all", all("a cat, a Cat", "cat", "dog", text()).equals("a dog, a dog"));
        Opt mc = text(); mc.matchCase = true;
        t("match case on", all("a cat, a Cat", "cat", "dog", mc).equals("a dog, a Cat"));
        t("special chars are literal in text", all("1+1=2 (ok)", "1+1", "two", text()).equals("two=2 (ok)"));
        t("dollar in With is literal in text", all("price", "price", "$1", text()).equals("$1"));
        t("empty With deletes", all("a--b--c", "--", "", text()).equals("abc"));
        t("blank find", ReplaceEngine.replaceAll("abc", "", "x", text()).blank && all("abc", "", "x", text()).equals("abc"));
        t("null safe", ReplaceEngine.replaceAll(null, null, null, null).blank && ReplaceEngine.replaceAll(null, "a", null, null).out.equals(""));
        t("no match = unchanged", ReplaceEngine.replaceAll("abc", "z", "y", text()).count == 0 && all("abc", "z", "y", text()).equals("abc"));
        t("count method", ReplaceEngine.count("aaaa", "aa", text()) == 2);
        t("count on error = 0", ReplaceEngine.count("aaaa", "(", rx()) == 0);

        // ---- first / nth
        Result f = ReplaceEngine.replaceFirst("x x x", "x", "y", text());
        t("first only", f.out.equals("y x x") && f.count == 3 && f.changed == 1);
        Result n2 = ReplaceEngine.replaceNth("x x x", "x", "y", text(), 2);
        t("nth = 2", n2.out.equals("x y x") && n2.count == 3 && n2.changed == 1);
        t("nth beyond = unchanged", ReplaceEngine.replaceNth("x x", "x", "y", text(), 5).out.equals("x x"));

        // ---- word mode
        t("whole word only", all("cat concat cat.", "cat", "dog", word()).equals("dog concat dog."));
        t("word: digits and underscore are word chars", all("a1 a a_ a", "a", "b", word()).equals("a1 b a_ b"));
        t("word: bangla vowel sign keeps the word together", all("কথা কথাটা কথা", "কথা", "গল্প", word()).equals("গল্প কথাটা গল্প"));

        // ---- escapes
        Opt es = text(); es.escapes = true;
        t("escapes: newline in With", all("a,b", ",", "\\n", es).equals("a\nb"));
        t("escapes: tab in Find", all("a\tb", "\\t", " ", es).equals("a b"));
        t("escapes: double backslash", all("a b", " ", "\\\\", es).equals("a\\b"));
        t("escapes off = literal backslash n", all("a,b", ",", "\\n", text()).equals("a\\nb"));

        // ---- bangla
        t("bangla: zero-width joiner in text still matches", all("ক\u200Cষ", "কষ", "X", text()).equals("X"));
        t("bangla: zero-width in find ignored", all("কষ", "ক\u200Cষ", "X", text()).equals("X"));
        t("bangla: ড় composed in find matches decomposed text", all("ঢা\u09A1\u09BCা", "ড়", "R", text()).equals("ঢাRা"));
        t("bangla: decomposed find matches composed text", all("আ\u09DCা", "\u09A1\u09BC", "R", text()).equals("আRা"));
        t("bangla: য় both forms", ReplaceEngine.count("\u09DF \u09AF\u09BC", "\u09DF", text()) == 2);
        Opt nb = text(); nb.bangla = false;
        t("bangla OFF: exact only", ReplaceEngine.count("\u09DC \u09A1\u09BC", "\u09DC", nb) == 1);
        t("find of only zero-width chars matches nothing", ReplaceEngine.replaceAll("abc", "\u200C", "x", text()).count == 0);

        // ---- regex
        t("regex digits", all("a1b22c333", "\\d+", "#", rx()).equals("a#b#c#"));
        t("regex group $1 $2 swap", all("john smith", "(\\w+) (\\w+)", "$2 $1", rx()).equals("smith john"));
        t("regex $0 and $&", all("ab", "a", "[$0$&]", rx()).equals("[aa]b"));
        t("regex $$ literal dollar", all("5", "5", "$$5", rx()).equals("$5"));
        t("regex unknown group stays", all("a", "a", "$7", rx()).equals("$7"));
        t("regex named group", all("2026-11-17", "(?<y>\\d+)-(?<m>\\d+)-(?<d>\\d+)", "${d}/${m}/${y}", rx()).equals("17/11/2026"));
        t("regex unknown name stays", all("a", "a", "${zz}", rx()).equals("${zz}"));
        t("regex two digit group", all("abcdefghijk", "(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)", "$11-$1", rx()).equals("k-a"));
        t("regex escapes in With", all("a b", " ", "\\n", rx()).equals("a\nb"));
        t("regex case: upper", all("hello world", "(\\w+) (\\w+)", "\\U$1\\E $2", rx()).equals("HELLO world"));
        t("regex case: lower", all("HELLO", "(\\w+)", "\\L$1", rx()).equals("hello"));
        t("regex case: one letter up", all("hello world", "\\b(\\w)", "\\u$1", rx()).equals("Hello World"));
        t("regex case: one letter down", all("ABC", "(\\w+)", "\\l$1", rx()).equals("aBC"));
        t("regex multiline anchors", all("a\nb\nc", "^", "- ", rx()).equals("- a\n- b\n- c"));
        t("regex end anchor per line", all("a\nb", "$", ";", rx()).equals("a;\nb;"));
        t("regex case-insensitive default", all("Cat", "cat", "x", rx()).equals("x"));
        Opt rmc = rx(); rmc.matchCase = true;
        t("regex match case", all("Cat", "cat", "x", rmc).equals("Cat"));
        t("regex empty matches do not loop", all("abc", "x*", "-", rx()).equals("-a-b-c-"));
        t("regex remove double spaces", all("a   b  c", " {2,}", " ", rx()).equals("a b c"));
        t("regex remove trailing spaces", all("a  \nb \n", "[ \\t]+$", "", rx()).equals("a\nb\n"));

        // ---- errors and safety
        Result bad1 = ReplaceEngine.replaceAll("abc", "(", "x", rx());
        t("bad regex = error + unchanged", bad1.err != null && bad1.err.startsWith("Pattern error") && bad1.out.equals("abc"));
        t("bad regex star", ReplaceEngine.replaceAll("abc", "*a", "x", rx()).err != null);
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < ReplaceEngine.MAX_TEXT + 1; i++) big.append('a');
        Result tl = ReplaceEngine.replaceAll(big.toString(), "a", "b", text());
        t("text too long = error", tl.err != null && tl.out.length() == big.length());
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < ReplaceEngine.MAX_MATCHES + 5; i++) many.append("a ");
        Result mm = ReplaceEngine.replaceAll(many.toString(), "a", "b", text());
        t("too many matches = error + unchanged", mm.err != null && mm.out.equals(many.toString()) && mm.changed == 0);
        StringBuilder ev = new StringBuilder();
        for (int i = 0; i < 30000; i++) ev.append('a');
        Result grow = ReplaceEngine.replaceAll(ev.toString(), "a", "0123456789012345678901234567890123456789", text());
        t("output too long = error", grow.err != null && grow.out.equals(ev.toString()));
        long t0 = System.currentTimeMillis();
        Result slow = ReplaceEngine.replaceAll("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!", "(a+)+\\1$", "x", rx());
        long dt = System.currentTimeMillis() - t0;
        t("catastrophic regex is stopped", slow.err != null && slow.out.startsWith("aaaa") && dt < 3000);

        // ---- hits / preview / info
        Result h = ReplaceEngine.replaceAll("one two one", "one", "1", text());
        t("hits recorded", h.hits.size() == 2 && h.hits.get(0).found.equals("one") && h.hits.get(0).repl.equals("1") && h.hits.get(1).start == 8);
        t("line text", ReplaceEngine.line("one two one", h.hits.get(0), 4).equals("[one \u2192 1] two\u2026"));
        t("line newline shown", ReplaceEngine.line("a\nb", ReplaceEngine.replaceAll("a\nb", "b", "c", text()).hits.get(0), 5).contains("\u23CE"));
        t("line empty found", ReplaceEngine.line("abc", ReplaceEngine.replaceAll("abc", "^", "-", rx()).hits.get(0), 2).contains("\u2205"));
        StringBuilder lots = new StringBuilder();
        for (int i = 0; i < 200; i++) lots.append("z ");
        Result lh = ReplaceEngine.replaceAll(lots.toString(), "z", "y", text());
        t("hits capped, count full", lh.hits.size() == ReplaceEngine.MAX_HITS && lh.count == 200 && lh.changed == 200);
        t("info texts", ReplaceEngine.info(ReplaceEngine.replaceAll("a", "", "", text())).equals("Type what to find")
            && ReplaceEngine.info(ReplaceEngine.replaceAll("a", "z", "", text())).equals("No match")
            && ReplaceEngine.info(ReplaceEngine.replaceAll("a", "a", "", text())).equals("1 match")
            && ReplaceEngine.info(ReplaceEngine.replaceAll("aa", "a", "", text())).equals("2 matches")
            && ReplaceEngine.info(bad1).startsWith("Pattern error")
            && ReplaceEngine.info(null).equals(""));

        // ---- pack / unpack
        Opt p = new Opt(); p.mode = 2; p.matchCase = true; p.bangla = false; p.escapes = true;
        Opt q = ReplaceEngine.unpack(ReplaceEngine.pack(p));
        t("pack round trip", q.mode == 2 && q.matchCase && !q.bangla && q.escapes);
        t("pack default", ReplaceEngine.pack(new Opt()) == 8 && ReplaceEngine.unpack(8).bangla && ReplaceEngine.unpack(8).mode == 0);
        t("unpack garbage = default", ReplaceEngine.unpack(-5).bangla && ReplaceEngine.unpack(-5).mode == 0 && ReplaceEngine.unpack(3).mode == 0);
        t("copy is independent", p.copy().mode == 2 && p.copy() != p);
        t("mode names", ReplaceEngine.MODE_NAMES.length == 3);

        // v0.26.0 (R3): lineParts = the same line in 4 pieces
        {
            ReplaceEngine.Result lp = ReplaceEngine.replaceAll("one two one \u3042\u3044 x\ny", "one", "1", text());
            ReplaceEngine.Hit h0 = lp.hits.get(0), h1 = lp.hits.get(1);
            String[] a = ReplaceEngine.lineParts("one two one \u3042\u3044 x\ny", h0, 4);
            t("parts: 4 pieces", a.length == 4);
            t("parts: found + repl", a[1].equals("one") && a[2].equals("1"));
            t("parts: join = line()", (a[0] + "[" + a[1] + " \u2192 " + a[2] + "]" + a[3]).equals(ReplaceEngine.line("one two one \u3042\u3044 x\ny", h0, 4)));
            String[] b = ReplaceEngine.lineParts("one two one \u3042\u3044 x\ny", h1, 6);
            t("parts: join second = line()", (b[0] + "[" + b[1] + " \u2192 " + b[2] + "]" + b[3]).equals(ReplaceEngine.line("one two one \u3042\u3044 x\ny", h1, 6)));
            t("parts: newline shown", (b[3]).contains("\u23CE"));
            ReplaceEngine.Result e = ReplaceEngine.replaceAll("abc", "^", "-", rx());
            String[] c = ReplaceEngine.lineParts("abc", e.hits.get(0), 2);
            t("parts: empty found = empty sign", c[1].equals("\u2205"));
            String[] n = ReplaceEngine.lineParts(null, null, 3);
            t("parts: null safe", n.length == 4 && n[0].isEmpty() && n[3].isEmpty());
        }

        System.out.println("ReplaceEngineTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
