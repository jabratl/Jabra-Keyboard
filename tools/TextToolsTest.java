import com.mgb.keyboard.typing.TextTools;
import java.util.TimeZone;

/**
 * v0.4.6 TextTools tests (computer only).
 * javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/TextToolsTest.java && java -cp out TextToolsTest
 */
public class TextToolsTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static boolean eq(int[] a, int... b) { return java.util.Arrays.equals(a, b); }

    public static void main(String[] x) {
        // counts: words, chars, chars no space, sentences, lines, paragraphs
        t("counts empty", eq(TextTools.counts(""), 0, 0, 0, 0, 0, 0) && eq(TextTools.counts(null), 0, 0, 0, 0, 0, 0));
        t("counts simple", eq(TextTools.counts("Hello world. Bye!"), 3, 17, 15, 2, 1, 1));
        t("counts no end mark = 1 sentence", TextTools.counts("hello there")[3] == 1);
        t("counts only spaces", eq(TextTools.counts("   "), 0, 3, 0, 0, 1, 0));
        t("counts lines + paragraphs", eq(TextTools.counts("a\nb\n\nc\n"), 3, 7, 3, 1, 4, 2));
        t("counts bangla words", TextTools.counts("আমি তুমি সে")[0] == 3);
        t("counts bangla danda sentences", TextTools.counts("আমি যাই। তুমি এসো। সে থাক।")[3] == 3);
        t("counts code points (emoji = 1)", TextTools.counts("a😀b")[1] == 3);
        t("counts line text", TextTools.countsLine("a b").startsWith("Words 2 "));
        t("counts dots only", TextTools.counts("...")[3] == 0);
        t("counts ellipsis sentence", TextTools.counts("Wait... what?")[3] == 2);

        // case
        t("upper", TextTools.caseConvert("Hello wörld", TextTools.UPPER).equals("HELLO WÖRLD"));
        t("lower", TextTools.caseConvert("Hello WORLD", TextTools.LOWER).equals("hello world"));
        t("title", TextTools.caseConvert("the qUICK brown-fox\njumps", TextTools.TITLE).equals("The Quick Brown-Fox\nJumps"));
        t("title keeps digits", TextTools.caseConvert("3 apples", TextTools.TITLE).equals("3 Apples"));
        t("sentence", TextTools.caseConvert("HELLO there. how ARE you? fine!\nok", TextTools.SENTENCE).equals("Hello there. How are you? Fine!\nOk"));
        t("sentence quote", TextTools.caseConvert("he said. \"yes\" ok", TextTools.SENTENCE).equals("He said. \"Yes\" ok"));
        t("bangla unchanged by case", TextTools.caseConvert("আমি তুমি", TextTools.UPPER).equals("আমি তুমি") && TextTools.caseConvert("আমি তুমি", TextTools.TITLE).equals("আমি তুমি"));
        t("mixed bangla english title", TextTools.caseConvert("আমি love you", TextTools.TITLE).equals("আমি Love You"));
        t("case null / bad mode", TextTools.caseConvert(null, 0).equals("") && TextTools.caseConvert("aB", 99).equals("aB"));

        // clean
        t("clean spaces", TextTools.clean("a   b \t c", true, false, false).equals("a b c"));
        t("clean trailing", TextTools.clean("a  \nb\t\n", false, false, true).equals("a\nb\n"));
        t("clean blank lines", TextTools.clean("a\n\n\n\nb\n\nc", false, true, false).equals("a\n\nb\n\nc"));
        t("clean all", TextTools.clean("a   b  \n\n\n\n c", true, true, true).equals("a b\n\n c"));
        t("clean none = same", TextTools.clean("a  b\n\n\nc ", false, false, false).equals("a  b\n\n\nc "));
        t("clean crlf", TextTools.clean("a\r\nb", false, false, false).equals("a\nb"));
        t("clean keeps newline count of single break", TextTools.clean("a\nb", true, true, true).equals("a\nb"));
        t("clean bangla", TextTools.clean("আমি   তুমি ", true, false, true).equals("আমি তুমি"));
        t("clean null", TextTools.clean(null, true, true, true).equals(""));

        // repeat
        t("repeat 3", TextTools.repeat("ab", 3, "-").equals("ab-ab-ab"));
        t("repeat sep newline", TextTools.repeat("x", 2, "\n").equals("x\nx"));
        t("repeat n<1 = 1", TextTools.repeat("x", 0, ",").equals("x") && TextTools.repeat("x", -5, ",").equals("x"));
        t("repeat cap 50", TextTools.repeat("x", 500, "").length() == 50);
        StringBuilder big = new StringBuilder(); for (int i = 0; i < 4000; i++) big.append('y');
        t("repeat output cap", TextTools.repeat(big.toString(), 50, "").length() <= TextTools.MAX_OUT && TextTools.repeat(big.toString(), 50, "").length() == 12 * 4000);
        t("repeat empty", TextTools.repeat("", 5, ",").equals("") && TextTools.repeat(null, 5, ",").equals(""));
        t("repeat null sep", TextTools.repeat("a", 2, null).equals("aa"));

        // date (fixed time: 2026-11-11 12:09:00 UTC = Wednesday)
        long ms = 1794398940000L;
        TimeZone utc = TimeZone.getTimeZone("UTC");
        t("date dd/MM/yyyy", TextTools.dateTime(0, ms, utc).equals("11/11/2026"));
        t("date iso", TextTools.dateTime(1, ms, utc).equals("2026-11-11"));
        t("date long", TextTools.dateTime(3, ms, utc).equals("Wednesday, 11 November 2026"));
        t("time", TextTools.dateTime(4, ms, utc).equals("12:09"));
        t("date + time", TextTools.dateTime(5, ms, utc).equals("2026-11-11 12:09"));
        t("date bad preset = first", TextTools.dateTime(99, ms, utc).equals("11/11/2026") && TextTools.dateTime(-1, ms, utc).equals("11/11/2026"));
        t("timezone applied", TextTools.dateTime(4, ms, TimeZone.getTimeZone("Asia/Dhaka")).equals("18:09"));
        t("separator tables same size", TextTools.SEPARATORS.length == TextTools.SEPARATOR_NAMES.length);

        System.out.println("TextToolsTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
