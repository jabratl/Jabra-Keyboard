import com.mgb.keyboard.typing.WordDelete;

/** v0.3.2 tests (computer only).  java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/WordDeleteTest.java ; java -Dfile.encoding=UTF-8 -cp out WordDeleteTest */
public class WordDeleteTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }
    static int c(String s) { return WordDelete.count(s); }

    public static void main(String[] a) {
        t("null", WordDelete.count(null) == 0);
        t("empty", c("") == 0);
        t("one word", c("hello") == 5);
        t("two words", c("hello world") == 5);
        t("trailing space", c("hello world ") == 6);
        t("many spaces", c("hello   ") == 8);
        t("only spaces", c("   ") == 3);
        t("tab", c("a\tb") == 1);
        t("line break only", c("abc\n") == 1);
        t("word after line break", c("abc\ndef") == 3);
        t("spaces after line break keep break", c("abc\n  ") == 2);
        t("punctuation is part of word", c("hi, you.") == 4);
        t("bangla word", c("\u0986\u09AE\u09BF \u09AC\u09BE\u0982\u09B2\u09BE") == 5);
        t("emoji word", c("a \uD83D\uDE00\uD83D\uDE00") == 4);
        t("cut surrogate at window start (half pair kept)", c("\uDE00abc") == 3);
        StringBuilder sb = new StringBuilder(); for (int i = 0; i < WordDelete.WINDOW; i++) sb.append('x');
        t("long word = whole window", c(sb.toString()) == WordDelete.WINDOW);
        t("constants sane", WordDelete.FAST_AFTER > 12 && WordDelete.WINDOW >= 32);
        System.out.println("WordDeleteTest " + ok + "/" + (ok + bad));
        if (bad > 0) System.exit(1);
    }
}
