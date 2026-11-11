import com.mgb.keyboard.typing.WordIndex;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class WordIndexTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    static String f(String body, int count) { return "#JABRA-WORDS 1\n#lang en\n#count " + count + "\n" + body; }
    public static void main(String[] a) throws Exception {
        WordIndex w = WordIndex.parseOrNull(f("the\nhello\nhelp\nhelicopter\nHeLLo2\nhe\nhell\nzoo\n", 8));
        eq("parsed", w != null, true);
        eq("lang", w.lang(), "en");
        eq("size", w.size(), 8);
        eq("word 0", w.word(0), "the");
        eq("word out of range", w.word(99), "");
        eq("word negative", w.word(-1), "");
        // lookup is in rank order
        eq("he* rank order", w.lookup("he", 10).toString(), "[hello, help, helicopter, HeLLo2, he, hell]");
        eq("he* max 2", w.lookup("he", 2).toString(), "[hello, help]");
        eq("hel* ", w.lookup("hel", 10).toString(), "[hello, help, helicopter, HeLLo2, hell]");
        eq("case-insensitive prefix", w.lookup("HEL", 3).toString(), "[hello, help, helicopter]");
        eq("exact included", w.lookup("hell", 10).toString(), "[hello, HeLLo2, hell]");
        eq("skipExact", w.lookup("hell", 10, true).toString(), "[hello, HeLLo2]");
        eq("no match", w.lookup("xyz", 5).size(), 0);
        eq("empty prefix", w.lookup("", 5).size(), 0);
        eq("null prefix", w.lookup(null, 5).size(), 0);
        eq("max 0", w.lookup("he", 0).size(), 0);
        eq("max negative", w.lookup("he", -3).size(), 0);
        eq("single letter", w.lookup("z", 5).toString(), "[zoo]");
        eq("whole word prefix", w.lookup("zoo", 5).toString(), "[zoo]");
        eq("longer than any", w.lookup("zoos", 5).size(), 0);
        // rank / contains
        eq("rank the", w.rank("the"), 0);
        eq("rank case-insens", w.rank("THE"), 0);
        eq("rank zoo", w.rank("zoo"), 7);
        eq("rank unknown", w.rank("nope"), -1);
        eq("rank null", w.rank(null), -1);
        eq("contains", w.contains("help"), true);
        eq("contains not", w.contains("hel"), false);
        // dedupe keeps first rank
        WordIndex d = WordIndex.parseOrNull(f("Go\ngo\nGO\nrun\n", 4));
        eq("dedupe size", d.size(), 2);
        eq("dedupe first spelling", d.word(0), "Go");
        eq("dedupe rank run", d.rank("run"), 1);
        // CRLF, BOM, blank lines, comments
        WordIndex c = WordIndex.parseOrNull("﻿#JABRA-WORDS 1\r\n#lang en\r\n#count 2\r\n# comment\r\n\r\nab\r\nac\r\n");
        eq("crlf+bom ok", c != null && c.size() == 2, true);
        eq("crlf word clean", c.word(0), "ab");
        // refused files
        eq("null text", WordIndex.parseOrNull((String) null), null);
        eq("null stream", WordIndex.parseOrNull((java.io.InputStream) null), null);
        eq("empty", WordIndex.parseOrNull(""), null);
        eq("no magic", WordIndex.parseOrNull("#lang en\n#count 1\nab\n"), null);
        eq("word before magic", WordIndex.parseOrNull("ab\n#JABRA-WORDS 1\n#count 1\n"), null);
        eq("format 2", WordIndex.parseOrNull("#JABRA-WORDS 2\n#count 1\nab\n"), null);
        eq("format not a number", WordIndex.parseOrNull("#JABRA-WORDS x\n#count 1\nab\n"), null);
        eq("count too big (cut-off file)", WordIndex.parseOrNull(f("ab\nac\n", 3)), null);
        eq("count too small", WordIndex.parseOrNull(f("ab\nac\n", 1)), null);
        eq("no count", WordIndex.parseOrNull("#JABRA-WORDS 1\nab\n"), null);
        eq("count not a number", WordIndex.parseOrNull("#JABRA-WORDS 1\n#count z\nab\n"), null);
        eq("word with space", WordIndex.parseOrNull(f("ab cd\n", 1)), null);
        eq("word with tab", WordIndex.parseOrNull(f("ab\tcd\n", 1)), null);
        StringBuilder lg = new StringBuilder(); for (int i = 0; i < 41; i++) lg.append('a');
        eq("word too long", WordIndex.parseOrNull(f(lg + "\n", 1)), null);
        eq("zero words ok", WordIndex.parseOrNull(f("", 0)) != null, true);
        eq("zero words lookup", WordIndex.parseOrNull(f("", 0)).lookup("a", 3).size(), 0);
        // stream
        WordIndex s = WordIndex.parseOrNull(new ByteArrayInputStream(f("ab\nac\n", 2).getBytes(StandardCharsets.UTF_8)));
        eq("stream ok", s != null && s.size() == 2, true);
        // matchCase
        eq("mc lower", WordIndex.matchCase("he", "hello"), "hello");
        eq("mc Title", WordIndex.matchCase("He", "hello"), "Hello");
        eq("mc UPPER", WordIndex.matchCase("HE", "hello"), "HELLO");
        eq("mc single upper", WordIndex.matchCase("H", "hello"), "Hello");
        eq("mc mixed hEl", WordIndex.matchCase("hEl", "hello"), "hello");
        eq("mc empty", WordIndex.matchCase("", "hello"), "hello");
        eq("mc bangla", WordIndex.matchCase("আ", "আমি"), "আমি");
        eq("mc null word", WordIndex.matchCase("He", null), null);
        // real asset files
        WordIndex en = WordIndex.parseOrNull(new FileInputStream("app/src/main/assets/words/en.txt"));
        eq("en asset parses", en != null, true);
        eq("en asset lang", en.lang(), "en");
        eq("en asset 500+", en.size() >= 500, true);
        eq("en the first", en.word(0), "the");
        eq("en hel -> hello", en.lookup("hel", 5).contains("hello"), true);
        eq("en th -> the first", en.lookup("th", 3).get(0), "the");
        eq("en apostrophe word", en.contains("don't"), true);
        eq("en prefix don'", en.lookup("don'", 3).toString(), "[don't]");
        eq("en max 3", en.lookup("a", 3).size(), 3);
        WordIndex bn = WordIndex.parseOrNull(new FileInputStream("app/src/main/assets/words/bn.txt"));
        eq("bn asset parses", bn != null, true);
        eq("bn asset lang", bn.lang(), "bn");
        eq("bn asset 150+", bn.size() >= 150, true);
        eq("bn ami", bn.contains("আমি"), true);
        eq("bn prefix আম", bn.lookup("আম", 5).contains("আমি"), true);
        eq("bn prefix result starts with prefix", bn.lookup("আম", 9).stream().allMatch(x -> x.startsWith("আম")), true);
        // speed sanity: 10k lookups on the real list stay fast
        long t0 = System.nanoTime();
        for (int i = 0; i < 10000; i++) en.lookup("th", 3);
        long ms = (System.nanoTime() - t0) / 1000000L;
        eq("10k lookups < 500 ms", ms < 500, true);
        System.out.println("WordIndexTest ok=" + ok + " bad=" + bad + " (10k lookups " + ms + " ms)");
        if (bad > 0) System.exit(1);
    }
}
