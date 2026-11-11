import com.mgb.keyboard.typing.UserWords;
import java.util.Arrays;
import java.util.List;

public class UserWordsTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    static void times(UserWords u, String w, int n) { for (int i = 0; i < n; i++) u.learn(w); }
    public static void main(String[] a) {
        // acceptable
        eq("ok word", UserWords.acceptable("hello"), true);
        eq("ok 2 letters", UserWords.acceptable("hi"), true);
        eq("1 letter no", UserWords.acceptable("a"), false);
        eq("null no", UserWords.acceptable(null), false);
        eq("empty no", UserWords.acceptable(""), false);
        eq("digit no", UserWords.acceptable("a1b"), false);
        eq("symbol no", UserWords.acceptable("a@b"), false);
        eq("space no", UserWords.acceptable("a b"), false);
        eq("apostrophe inside ok", UserWords.acceptable("don't"), true);
        eq("curly apostrophe inside ok", UserWords.acceptable("don’t"), true);
        eq("leading apostrophe no", UserWords.acceptable("'ab"), false);
        eq("trailing apostrophe no", UserWords.acceptable("ab'"), false);
        eq("4 same letters no", UserWords.acceptable("hahaha".replace("ha", "h") + "hhhh"), false);
        eq("3 same letters ok", UserWords.acceptable("sooo".substring(0, 3) + "n"), true);
        eq("40 chars ok", UserWords.acceptable("abcdefghij".repeat(4)), true);
        eq("41 chars no", UserWords.acceptable("abcdefghij".repeat(4) + "k"), false);
        eq("bangla ok", UserWords.acceptable("আমি"), true);
        eq("bangla digit no", UserWords.acceptable("১২"), false);
        eq("only marks no", UserWords.acceptable("াা"), false);

        // boundary
        eq("space boundary", UserWords.isBoundary(" "), true);
        eq("newline boundary", UserWords.isBoundary("\n"), true);
        eq("comma boundary", UserWords.isBoundary(","), true);
        eq("danda boundary", UserWords.isBoundary("।"), true);
        eq("letter not boundary", UserWords.isBoundary("a"), false);
        eq("digit not boundary", UserWords.isBoundary("5"), false);
        eq("apostrophe not boundary", UserWords.isBoundary("'"), false);
        eq("bangla letter not boundary", UserWords.isBoundary("আ"), false);
        eq("two chars not boundary", UserWords.isBoundary("a "), false);
        eq("word+space not boundary", UserWords.isBoundary("hello "), false);
        eq("null not boundary", UserWords.isBoundary(null), false);
        eq("empty not boundary", UserWords.isBoundary(""), false);

        // learn + show threshold
        UserWords u = new UserWords();
        eq("empty size", u.size(), 0);
        eq("clean at start", u.isDirty(), false);
        eq("learn true", u.learn("Hello"), true);
        eq("learn rejected", u.learn("a"), false);
        eq("dirty after learn", u.isDirty(), true);
        eq("size 1", u.size(), 1);
        eq("count 1", u.count("hello"), 1);
        eq("count ignores case", u.count("HELLO"), 1);
        eq("once = not shown", u.lookup("he", 5, false), Arrays.asList());
        u.learn("hello");
        eq("twice = shown", u.lookup("he", 5, false), Arrays.asList("hello"));
        eq("stored lower case", u.lookup("HE", 5, false), Arrays.asList("hello"));
        eq("exact skipped", u.lookup("hello", 5, true), Arrays.asList());
        eq("exact kept", u.lookup("hello", 5, false), Arrays.asList("hello"));
        eq("no match", u.lookup("xy", 5, false), Arrays.asList());
        eq("empty prefix", u.lookup("", 5, false), Arrays.asList());
        eq("null prefix", u.lookup(null, 5, false), Arrays.asList());
        eq("max 0", u.lookup("he", 0, false), Arrays.asList());

        // order: count desc, then newest
        UserWords o = new UserWords();
        times(o, "cat", 2); times(o, "car", 5); times(o, "cap", 2); times(o, "can", 3);
        eq("order by count then newest", o.lookup("ca", 10, false), Arrays.asList("car", "can", "cap", "cat"));
        eq("max cut", o.lookup("ca", 2, false), Arrays.asList("car", "can"));

        // inner capital kept
        UserWords c = new UserWords();
        times(c, "iPhone", 2);
        eq("inner capital kept", c.lookup("ip", 3, false), Arrays.asList("iPhone"));
        times(c, "Dhaka", 2);
        eq("first capital lowered", c.lookup("dh", 3, false), Arrays.asList("dhaka"));
        UserWords c2 = new UserWords();
        c2.learn("mcdonald"); c2.learn("McDonald");
        eq("plain then inner capital upgrades", c2.lookup("mc", 3, false), Arrays.asList("McDonald"));
        c2.learn("mcdonald");
        eq("capital form stays", c2.lookup("mc", 3, false), Arrays.asList("McDonald"));
        eq("count merged", c2.count("MCDONALD"), 3);

        // bangla
        UserWords b = new UserWords();
        String bn = "আমি";
        times(b, bn, 2);
        eq("bangla prefix", b.lookup("আ", 3, false), Arrays.asList(bn));
        eq("bangla not matched by latin", b.lookup("a", 3, false), Arrays.asList());

        // forget + clear
        UserWords f = new UserWords();
        times(f, "alpha", 2); times(f, "beta", 2);
        f.markClean();
        eq("forget true", f.forget("ALPHA"), true);
        eq("forget dirty", f.isDirty(), true);
        eq("forget gone", f.count("alpha"), 0);
        eq("forget again false", f.forget("alpha"), false);
        eq("forget null", f.forget(null), false);
        f.markClean();
        f.clear();
        eq("clear size", f.size(), 0);
        eq("clear dirty", f.isDirty(), true);
        f.markClean();
        f.clear();
        eq("clear empty not dirty", f.isDirty(), false);

        // cap
        UserWords cap = new UserWords();
        for (int i = 0; i < UserWords.MAX_WORDS; i++) cap.learn("w" + word(i));
        eq("full size", cap.size(), UserWords.MAX_WORDS);
        times(cap, "keeper", 5);
        eq("still capped", cap.size(), UserWords.MAX_WORDS);
        eq("strong word kept", cap.count("keeper"), 5);
        cap.learn("newcomer");
        eq("capped after new", cap.size(), UserWords.MAX_WORDS);
        eq("newcomer present", cap.count("newcomer"), 1);
        eq("keeper survives eviction", cap.count("keeper"), 5);

        // merge
        List<String> l = Arrays.asList("Hello", "help");
        List<String> bl = Arrays.asList("hello", "held", "helm", "hell");
        eq("merge dedupe", UserWords.merge(l, bl, 10), Arrays.asList("Hello", "help", "held", "helm", "hell"));
        eq("merge max", UserWords.merge(l, bl, 3), Arrays.asList("Hello", "help", "held"));
        eq("merge max 1", UserWords.merge(l, bl, 1), Arrays.asList("Hello"));
        eq("merge max 0", UserWords.merge(l, bl, 0), Arrays.asList());
        eq("merge null learned", UserWords.merge(null, bl, 2), Arrays.asList("hello", "held"));
        eq("merge null bundled", UserWords.merge(l, null, 5), Arrays.asList("Hello", "help"));
        eq("merge both null", UserWords.merge(null, null, 5), Arrays.asList());
        eq("merge skips blanks", UserWords.merge(Arrays.asList("", null, "ab"), Arrays.asList("", "ac"), 5), Arrays.asList("ab", "ac"));

        // file round trip
        UserWords r = new UserWords();
        times(r, "zeta", 2); times(r, "iPad", 3); times(r, bn, 4); r.learn("once");
        String txt = r.serialize();
        eq("magic first", txt.startsWith("#JABRA-LEARN 1\n#count 4\n"), true);
        UserWords r2 = UserWords.parseOrNull(txt);
        eq("parse not null", r2 != null, true);
        eq("round size", r2.size(), 4);
        eq("round count zeta", r2.count("zeta"), 2);
        eq("round count ipad", r2.count("iPad"), 3);
        eq("round count bangla", r2.count(bn), 4);
        eq("round count once", r2.count("once"), 1);
        eq("round form kept", r2.lookup("ip", 2, false), Arrays.asList("iPad"));
        eq("round newest first kept", r2.serialize(), txt);
        eq("parsed is clean", r2.isDirty(), false);
        r2.learn("later");
        eq("learn after parse works", r2.count("later"), 1);

        // bad files refused
        eq("null text", UserWords.parseOrNull(null), null);
        eq("empty text", UserWords.parseOrNull(""), null);
        eq("no magic", UserWords.parseOrNull("#count 1\n2\thello\n"), null);
        eq("word before magic", UserWords.parseOrNull("2\thello\n#JABRA-LEARN 1\n#count 1\n"), null);
        eq("wrong format", UserWords.parseOrNull("#JABRA-LEARN 2\n#count 1\n2\thello\n"), null);
        eq("count too high (cut off)", UserWords.parseOrNull("#JABRA-LEARN 1\n#count 2\n2\thello\n"), null);
        eq("count too low", UserWords.parseOrNull("#JABRA-LEARN 1\n#count 0\n2\thello\n"), null);
        eq("no count", UserWords.parseOrNull("#JABRA-LEARN 1\n2\thello\n"), null);
        eq("bad number", UserWords.parseOrNull("#JABRA-LEARN 1\n#count 1\nx\thello\n"), null);
        eq("zero count line", UserWords.parseOrNull("#JABRA-LEARN 1\n#count 1\n0\thello\n"), null);
        eq("no tab", UserWords.parseOrNull("#JABRA-LEARN 1\n#count 1\n2 hello\n"), null);
        eq("bad word", UserWords.parseOrNull("#JABRA-LEARN 1\n#count 1\n2\ta1\n"), null);
        eq("duplicate", UserWords.parseOrNull("#JABRA-LEARN 1\n#count 2\n2\thello\n3\tHELLO\n"), null);
        eq("bad magic number", UserWords.parseOrNull("#JABRA-LEARN x\n#count 0\n"), null);
        eq("good empty", UserWords.parseOrNull("#JABRA-LEARN 1\n#count 0\n") != null, true);
        eq("crlf ok", UserWords.parseOrNull("#JABRA-LEARN 1\r\n#count 1\r\n2\thello\r\n") != null, true);
        eq("bom ok", UserWords.parseOrNull("﻿#JABRA-LEARN 1\n#count 1\n2\thello\n") != null, true);
        eq("too big", UserWords.parseOrNull("#JABRA-LEARN 1\n#count 0\n" + "x".repeat(UserWords.MAX_BYTES)), null);

        eq("constants", UserWords.MAX_WORDS + "/" + UserWords.MIN_SHOW + "/" + UserWords.MIN_WORD, "3000/2/2");
        System.out.println("UserWordsTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
    static String word(int i) {
        StringBuilder sb = new StringBuilder();
        do { sb.append((char) ('a' + i % 25)).append('z'); i /= 25; } while (i > 0);
        return sb.toString();
    }
}
