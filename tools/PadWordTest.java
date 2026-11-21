import com.mgb.keyboard.typing.PadWord;

public class PadWordTest {
    static int ok = 0, bad = 0;
    static void eq(String n, int got, int want) {
        if (got == want) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    public static void main(String[] a) {
        String s = "hello world";
        eq("left from end", PadWord.step(s, 11, true), 6);
        eq("left from word start", PadWord.step(s, 6, true), 0);
        eq("left from inside word", PadWord.step(s, 8, true), 6);
        eq("left after first word", PadWord.step(s, 5, true), 0);
        eq("left at start", PadWord.step(s, 0, true), 0);
        eq("right from start", PadWord.step(s, 0, false), 5);
        eq("right from word end", PadWord.step(s, 5, false), 11);
        eq("right from inside", PadWord.step(s, 7, false), 11);
        eq("right at end", PadWord.step(s, 11, false), 11);
        String p = "ab, cd";
        eq("punct left", PadWord.step(p, 6, true), 4);
        eq("punct left over separators", PadWord.step(p, 4, true), 0);
        eq("punct right", PadWord.step(p, 2, false), 6);
        String nl = "a\nb";
        eq("newline left", PadWord.step(nl, 3, true), 2);
        eq("newline left again", PadWord.step(nl, 2, true), 0);
        eq("newline right", PadWord.step(nl, 1, false), 3);
        // Bangla with vowel signs (combining marks must not split the word)
        String bn = "\u0986\u09AE\u09BF \u09AD\u09BE\u09B2\u09CB";
        eq("bangla left from end", PadWord.step(bn, 8, true), 4);
        eq("bangla left again", PadWord.step(bn, 4, true), 0);
        eq("bangla right from start", PadWord.step(bn, 0, false), 3);
        eq("bangla right again", PadWord.step(bn, 3, false), 8);
        eq("digits and underscore", PadWord.step("a_1 b", 3, true), 0);
        eq("only separators left", PadWord.step("   ", 3, true), 0);
        eq("only separators right", PadWord.step("   ", 0, false), 3);
        eq("empty", PadWord.step("", 0, true), 0);
        eq("null window", PadWord.step(null, 5, true), 5);
        eq("pos beyond end clamped", PadWord.step("ab cd", 99, true), 3);
        eq("negative pos clamped", PadWord.step("ab cd", -4, false), 2);
        System.out.println("PadWordTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
