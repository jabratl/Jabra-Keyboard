import com.mgb.keyboard.typing.WordIndex;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class WordBuildTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    static String body(String t) { return t.substring(t.indexOf("#count")).replaceFirst("#count \\d+\n", ""); }
    public static void main(String[] a) throws Exception {
        // token
        eq("token word+count", WordBuild.token("you 28787591"), "you");
        eq("token tab", WordBuild.token("you\t5"), "you");
        eq("token blank", WordBuild.token("   "), "");
        eq("token null", WordBuild.token(null), "");
        eq("token BOM", WordBuild.token("\uFEFFthe 9"), "the");
        // cleanEn
        eq("en lower", WordBuild.cleanEn("Hello"), "hello");
        eq("en apostrophe inner", WordBuild.cleanEn("don't"), "don't");
        eq("en curly apostrophe", WordBuild.cleanEn("don\u2019t"), "don't");
        eq("en leading apostrophe", WordBuild.cleanEn("'tis"), null);
        eq("en trailing apostrophe", WordBuild.cleanEn("dogs'"), null);
        eq("en digit", WordBuild.cleanEn("abc1"), null);
        eq("en one letter", WordBuild.cleanEn("a"), null);
        eq("en two letters", WordBuild.cleanEn("is"), "is");
        eq("en accent", WordBuild.cleanEn("caf\u00E9"), null);
        eq("en fragment don", WordBuild.cleanEn("don"), null);
        eq("en fragment ll", WordBuild.cleanEn("ll"), null);
        eq("en fragment ve", WordBuild.cleanEn("ve"), null);
        eq("en too long", WordBuild.cleanEn("a23456789012345678901234567890b"), null);
        eq("en punct", WordBuild.cleanEn("hi!"), null);
        eq("en bangla", WordBuild.cleanEn("\u0986\u09AE\u09BF"), null);
        // cleanBn
        eq("bn word", WordBuild.cleanBn("\u0986\u09AE\u09BF"), "\u0986\u09AE\u09BF");
        eq("bn one letter word", WordBuild.cleanBn("\u0993"), "\u0993");
        eq("bn danda", WordBuild.cleanBn("\u0964"), null);
        eq("bn digit", WordBuild.cleanBn("\u09E7\u09E8"), null);
        eq("bn latin", WordBuild.cleanBn("abc"), null);
        eq("bn mixed", WordBuild.cleanBn("\u0986ab"), null);
        eq("bn starts with matra", WordBuild.cleanBn("\u09BE\u0995"), null);
        eq("bn hasanta word", WordBuild.cleanBn("\u099C\u09A8\u09CD\u09AF"), "\u099C\u09A8\u09CD\u09AF");
        eq("bn null", WordBuild.cleanBn(null), null);
        // build
        String en = WordBuild.build("en", "you 9\nthe 8\nYou 7\nx 6\ndon 5\nzebra 4\n\n", "#JABRA-WORDS 1\n#lang en\n#count 2\nthe\nbe\n", 100, 100000);
        eq("en header", en.startsWith("#JABRA-WORDS 1\n#lang en\n#count 4\n"), true);
        eq("en starter on top, dedupe, filter", body(en), "the\nbe\nyou\nzebra\n");
        String bn = WordBuild.build("bn", "\u0964 99\n\u0986\u09AE\u09BF 9\n\u09E7 5\nabc 4\n\u09A4\u09C1\u09AE\u09BF 3\n", "", 100, 100000);
        eq("bn count", bn.contains("#count 2\n"), true);
        eq("bn body", body(bn), "\u0986\u09AE\u09BF\n\u09A4\u09C1\u09AE\u09BF\n");
        // caps
        String capW = WordBuild.build("en", "aa 9\nbb 8\ncc 7\ndd 6\n", "", 2, 100000);
        eq("maxWords", capW.contains("#count 2\n"), true);
        String capB = WordBuild.build("en", "aaaa 9\nbbbb 8\ncccc 7\ndddd 6\n", "", 100, 64 + 10);
        eq("maxBytes (2 words of 5 bytes)", capB.contains("#count 2\n"), true);
        eq("empty input", WordBuild.build("en", "", "", 10, 1000), "#JABRA-WORDS 1\n#lang en\n#count 0\n");
        eq("null input", WordBuild.build("en", null, null, 10, 1000), "#JABRA-WORDS 1\n#lang en\n#count 0\n");
        eq("CRLF freq", body(WordBuild.build("en", "aa 1\r\nbb 2\r\n", "", 10, 1000)), "aa\nbb\n");
        eq("starter CRLF", body(WordBuild.build("en", "", "#x\r\nhi\r\n", 10, 1000)), "hi\n");
        eq("starter wins, case dedupe", body(WordBuild.build("en", "Hello 1\n", "hello\n", 10, 1000)), "hello\n");
        // the output is readable by the real WordIndex
        WordIndex w = WordIndex.parseOrNull(en);
        eq("WordIndex reads output", w != null, true);
        eq("WordIndex rank 0", w == null ? "" : w.word(0), "the");
        eq("WordIndex lookup", w == null ? "" : w.lookup("ze", 3).toString(), "[zebra]");
        // the REAL shipped files (skipped when run from another folder)
        String[] langs = {"en", "bn"};
        for (String l : langs) {
            File f = new File("app/src/main/assets/words/" + l + ".txt");
            if (!f.exists()) continue;
            byte[] raw = Files.readAllBytes(f.toPath());
            eq("real " + l + " <= 400 KB", raw.length <= 400 * 1024, true);
            WordIndex r = WordIndex.parseOrNull(new String(raw, StandardCharsets.UTF_8));
            eq("real " + l + " parses", r != null, true);
            if (r == null) continue;
            eq("real " + l + " big", r.size() >= 8000, true);
            eq("real " + l + " lang tag", r.lang(), l);
        }
        File fe = new File("app/src/main/assets/words/en.txt");
        if (fe.exists()) {
            WordIndex r = WordIndex.parseOrNull(new String(Files.readAllBytes(fe.toPath()), StandardCharsets.UTF_8));
            if (r != null) {
                eq("real en: starter 'the' first", r.word(0), "the");
                eq("real en: hello", r.contains("hello"), true);
                eq("real en: apostrophe kept", r.contains("don't"), true);
                eq("real en: no fragment didn", r.contains("didn"), false);
                eq("real en: prefix hel has results", r.lookup("hel", 3).size() >= 3, true);
            }
        }
        File fb = new File("app/src/main/assets/words/bn.txt");
        if (fb.exists()) {
            WordIndex r = WordIndex.parseOrNull(new String(Files.readAllBytes(fb.toPath()), StandardCharsets.UTF_8));
            if (r != null) {
                eq("real bn: starter first", r.word(0), "\u0986\u09AE\u09BF");
                eq("real bn: no danda", r.contains("\u0964"), false);
                eq("real bn: prefix has results", r.lookup("\u0986", 3).size() >= 3, true);
            }
        }
        System.out.println("WordBuildTest: " + ok + " PASS / " + bad + " FAIL");
        if (bad > 0) System.exit(1);
    }
}
