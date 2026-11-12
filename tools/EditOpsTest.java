import com.mgb.keyboard.typing.EditOps;

/** v0.16.13 (X13 part 1) tests (computer only).  java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/EditOpsTest.java ; java -Dfile.encoding=UTF-8 -cp out EditOpsTest */
public class EditOpsTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }
    static boolean rng(int[] r, int a, int b) { return r != null && r.length == 2 && r[0] == a && r[1] == b; }

    // Bangla: bangla = \u09AC\u09BE\u0982\u09B2\u09BE (ba + aa-kar + anusvara + la + aa-kar), ksha = \u0995\u09CD\u09B7 (ka + hasanta + ssa)
    static final String BANGLA = "\u09AC\u09BE\u0982\u09B2\u09BE";
    static final String KSHA = "\u0995\u09CD\u09B7";
    static final String EMOJI = "\uD83D\uDE00";

    public static void main(String[] a) {
        // ---- selectWord
        t("sw null", EditOps.selectWord(null, 0, 0) == null);
        t("sw empty", EditOps.selectWord("", 0, 0) == null);
        t("sw middle", rng(EditOps.selectWord("hello world", 2, 2), 0, 5));
        t("sw start of word", rng(EditOps.selectWord("hello world", 6, 6), 6, 11));
        t("sw end of word (left word)", rng(EditOps.selectWord("hello world", 5, 5), 0, 5));
        t("sw only spaces", EditOps.selectWord("   ", 1, 1) == null);
        t("sw between two spaces next to nothing", EditOps.selectWord("a  b", 2, 2) == null);
        t("sw punctuation not word", rng(EditOps.selectWord("hi, you.", 6, 6), 4, 7));
        t("sw underscore + digits", rng(EditOps.selectWord("x my_var2 y", 5, 5), 2, 9));
        t("sw bangla word with kar + anusvara", rng(EditOps.selectWord(BANGLA + " " + BANGLA, 2, 2), 0, 5));
        t("sw bangla cursor after last kar", rng(EditOps.selectWord("x " + BANGLA, 7, 7), 2, 7));
        t("sw hasanta conjunct is one word", rng(EditOps.selectWord(KSHA, 1, 1), 0, 3));
        t("sw bangla then english", rng(EditOps.selectWord(BANGLA + " abc", 7, 7), 6, 9));
        t("sw bangla digits", rng(EditOps.selectWord("\u09E7\u09E8\u09E9 x", 1, 1), 0, 3));
        t("sw selection start used", rng(EditOps.selectWord("ab cd ef", 3, 8), 3, 5));
        t("sw reversed selection swapped", rng(EditOps.selectWord("ab cd ef", 8, 3), 3, 5));
        t("sw out of range clamped", rng(EditOps.selectWord("ab", 99, 99), 0, 2));
        t("sw emoji is not a word", EditOps.selectWord(EMOJI + EMOJI, 2, 2) == null);
        t("sw emoji next to word", rng(EditOps.selectWord("ab" + EMOJI, 1, 1), 0, 2));
        t("sw ZWNJ inside word", rng(EditOps.selectWord("a\u200Cb c", 1, 1), 0, 3));
        t("sw newline stops word", rng(EditOps.selectWord("ab\ncd", 4, 4), 3, 5));

        // ---- selectLine
        t("sl empty", EditOps.selectLine("", 0, 0) == null);
        t("sl one line", rng(EditOps.selectLine("hello", 2, 2), 0, 5));
        t("sl second line", rng(EditOps.selectLine("ab\ncd\nef", 4, 4), 3, 5));
        t("sl cursor at end of line 1", rng(EditOps.selectLine("ab\ncd", 2, 2), 0, 2));
        t("sl cursor at start of line 2", rng(EditOps.selectLine("ab\ncd", 3, 3), 3, 5));
        t("sl empty line = null", EditOps.selectLine("ab\n\ncd", 3, 3) == null);
        t("sl multi-line selection", rng(EditOps.selectLine("ab\ncd\nef\ngh", 4, 7), 3, 8));
        t("sl selection ending after newline", rng(EditOps.selectLine("ab\ncd\nef", 0, 3), 0, 2));
        t("sl CRLF keeps CR out", rng(EditOps.selectLine("ab\r\ncd", 1, 1), 0, 2));
        t("sl bangla line", rng(EditOps.selectLine("x\n" + BANGLA + " " + KSHA + "\ny", 4, 4), 2, 11));

        // ---- collapse
        t("col empty selection = null", EditOps.collapse("abc", 1, 1) == null);
        t("col to end", rng(EditOps.collapse("abcdef", 1, 4), 4, 4));
        t("col reversed", rng(EditOps.collapse("abcdef", 4, 1), 4, 4));
        t("col null text", EditOps.collapse(null, 0, 0) == null);

        // ---- lineStart / lineEnd
        t("ls first line", EditOps.lineStart("hello", 3, 3) == 0);
        t("ls already there = -1", EditOps.lineStart("ab\ncd", 3, 3) == -1);
        t("ls second line", EditOps.lineStart("ab\ncd", 4, 4) == 3);
        t("ls empty text = -1", EditOps.lineStart("", 0, 0) == -1);
        t("ls selection collapses to start", EditOps.lineStart("ab\ncd", 3, 5) == 3);
        t("le first line", EditOps.lineEnd("ab\ncd", 0, 0) == 2);
        t("le already there = -1", EditOps.lineEnd("ab\ncd", 2, 2) == -1);
        t("le last line", EditOps.lineEnd("ab\ncd", 3, 3) == 5);
        t("le CRLF", EditOps.lineEnd("ab\r\ncd", 0, 0) == 2);
        t("le selection uses end", EditOps.lineEnd("ab\ncd\nef", 0, 4) == 5);
        t("le empty = -1", EditOps.lineEnd("", 0, 0) == -1);

        // ---- deleteWord
        t("dw empty", EditOps.deleteWord("", 0, 0) == null);
        t("dw cursor at start = null", EditOps.deleteWord("abc", 0, 0) == null);
        t("dw one word", rng(EditOps.deleteWord("hello", 5, 5), 0, 5));
        t("dw second word", rng(EditOps.deleteWord("hello world", 11, 11), 6, 11));
        t("dw trailing space", rng(EditOps.deleteWord("hello world ", 12, 12), 6, 12));
        t("dw selection wins", rng(EditOps.deleteWord("hello world", 2, 8), 2, 8));
        t("dw uses text before cursor only", rng(EditOps.deleteWord("hello world", 5, 5), 0, 5));
        t("dw bangla word", rng(EditOps.deleteWord("x " + BANGLA, 7, 7), 2, 7));
        t("dw does not cross line break", rng(EditOps.deleteWord("ab\ncd", 5, 5), 3, 5));
        t("dw line break only", rng(EditOps.deleteWord("ab\n", 3, 3), 2, 3));
        t("dw emoji word whole pair", rng(EditOps.deleteWord("a " + EMOJI, 4, 4), 2, 4));
        t("dw selection splitting emoji widened", rng(EditOps.deleteWord("a" + EMOJI + "b", 2, 3), 1, 3));

        // ---- deleteLine
        t("dl empty", EditOps.deleteLine("", 0, 0) == null);
        t("dl one line text", rng(EditOps.deleteLine("hello", 2, 2), 0, 5));
        t("dl first of three", rng(EditOps.deleteLine("ab\ncd\nef", 1, 1), 0, 3));
        t("dl middle of three", rng(EditOps.deleteLine("ab\ncd\nef", 4, 4), 3, 6));
        t("dl last of three takes break before", rng(EditOps.deleteLine("ab\ncd\nef", 7, 7), 5, 8));
        t("dl empty middle line", rng(EditOps.deleteLine("ab\n\ncd", 3, 3), 3, 4));
        t("dl multi-line selection", rng(EditOps.deleteLine("ab\ncd\nef\ngh", 4, 7), 3, 9));
        t("dl selection ending after newline", rng(EditOps.deleteLine("ab\ncd\nef", 0, 3), 0, 3));
        t("dl only a newline", rng(EditOps.deleteLine("\n", 0, 0), 0, 1));
        t("dl after delete no empty line left", "ab\nef".equals(apply("ab\ncd\nef", EditOps.deleteLine("ab\ncd\nef", 4, 4))));
        t("dl last line result", "ab\ncd".equals(apply("ab\ncd\nef", EditOps.deleteLine("ab\ncd\nef", 7, 7))));

        // ---- duplicateLine
        EditOps.Dup d = EditOps.duplicateLine("ab\ncd", 1, 1);
        t("dup first line", d != null && d.pos == 2 && "\nab".equals(d.text) && d.cursor == 5);
        d = EditOps.duplicateLine("ab\ncd", 4, 4);
        t("dup last line", d != null && d.pos == 5 && "\ncd".equals(d.text) && d.cursor == 8);
        d = EditOps.duplicateLine("", 0, 0);
        t("dup empty text = new empty line", d != null && d.pos == 0 && "\n".equals(d.text) && d.cursor == 1);
        d = EditOps.duplicateLine("ab\ncd\nef", 0, 4);
        t("dup two lines", d != null && d.pos == 5 && "\nab\ncd".equals(d.text));
        t("dup result text", "ab\ncd\ncd\nef".equals(applyDup("ab\ncd\nef", EditOps.duplicateLine("ab\ncd\nef", 4, 4))));
        d = EditOps.duplicateLine("ab\ncd\nef", 0, 3);
        t("dup selection ending after newline = 1 line", d != null && d.pos == 2 && "\nab".equals(d.text));
        d = EditOps.duplicateLine("x\n" + BANGLA + "\ny", 3, 3);
        t("dup bangla line", d != null && d.text.equals("\n" + BANGLA));
        d = EditOps.duplicateLine("ab\r\ncd", 0, 0);
        t("dup CRLF keeps CR in copy position", d != null && d.pos == 3 && "\nab\r".equals(d.text));

        System.out.println("EditOpsTest " + ok + "/" + (ok + bad));
        if (bad > 0) System.exit(1);
    }

    static String apply(String t, int[] del) { return del == null ? t : t.substring(0, del[0]) + t.substring(del[1]); }
    static String applyDup(String t, EditOps.Dup d) { return d == null ? t : t.substring(0, d.pos) + d.text + t.substring(d.pos); }
}
