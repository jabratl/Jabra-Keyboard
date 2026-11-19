import com.mgb.keyboard.typing.ReplacePads;

/** v0.24.0 (R1) ReplacePads tests (computer only). */
public class ReplacePadsTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] x) {
        t("2 pages + 2 names (Bangla pages removed in v0.42.0)", ReplacePads.ROWS.length == 2 && ReplacePads.PAGE_NAMES.length == 2);
        boolean rowsOk = true, tokOk = true, valOk = true;
        for (int p = 0; p < ReplacePads.ROWS.length; p++) {
            t("page " + p + " has 4 rows", ReplacePads.ROWS[p].length == 4);
            for (int r = 0; r < ReplacePads.ROWS[p].length; r++) {
                String[] tk = ReplacePads.tokens(p, r);
                if (tk.length == 0 || tk.length > ReplacePads.MAX_PER_ROW) rowsOk = false;
                for (String s : tk) {
                    if (s.length() == 0) tokOk = false;
                    if (ReplacePads.value(s).length() == 0) valOk = false;
                }
            }
        }
        t("rows 1.." + ReplacePads.MAX_PER_ROW + " keys", rowsOk);
        t("no empty token", tokOk);
        t("every key types something", valOk);
        t("tokens out of range = empty", ReplacePads.tokens(9, 0).length == 0 && ReplacePads.tokens(0, 9).length == 0 && ReplacePads.tokens(-1, 0).length == 0);
        t("ZWNJ / ZWJ values", ReplacePads.value("ZWNJ").equals("\u200C") && ReplacePads.value("ZWJ").equals("\u200D"));
        t("dotted circle token types the sign only", ReplacePads.value("\u25CC\u09BE").equals("\u09BE") && ReplacePads.label("\u25CC\u09BE").equals("\u25CC\u09BE"));
        t("a lone dotted circle types itself", ReplacePads.value("\u25CC").equals("\u25CC"));
        t("plain token", ReplacePads.value("q").equals("q") && ReplacePads.value("\\n").equals("\\n") && ReplacePads.value(null).equals(""));
        t("next page wraps", ReplacePads.next(0) == 1 && ReplacePads.next(1) == 0);
        boolean noBn = true;
        for (int p = 0; p < ReplacePads.ROWS.length; p++) for (int r = 0; r < ReplacePads.ROWS[p].length; r++) for (String s : ReplacePads.tokens(p, r)) for (char c : ReplacePads.value(s).toCharArray()) if (c >= 0x0980 && c <= 0x09FF) noBn = false;
        t("no Bangla key on the pads", noBn);
        t("append", ReplacePads.append("ab", "c").equals("abc") && ReplacePads.append(null, "c").equals("c") && ReplacePads.append("ab", "").equals("ab") && ReplacePads.append("ab", null).equals("ab"));
        StringBuilder full = new StringBuilder();
        for (int i = 0; i < ReplacePads.FIELD_MAX; i++) full.append('a');
        t("append stops at the limit", ReplacePads.append(full.toString(), "b").equals(full.toString()));
        t("backspace", ReplacePads.backspace("abc").equals("ab") && ReplacePads.backspace("").equals("") && ReplacePads.backspace(null).equals(""));
        t("backspace emoji = one", ReplacePads.backspace("a\uD83D\uDE00").equals("a"));
        t("regex helper keys exist", ReplacePads.ROWS[1][3].contains("\\d") && ReplacePads.ROWS[1][3].contains("$1"));
        // ---- v0.29.0 caret
        t("insertAt middle", ReplacePads.insertAt("abcd", 2, "X").equals("abXcd"));
        t("insertAt start / end / clamp", ReplacePads.insertAt("ab", 0, "X").equals("Xab") && ReplacePads.insertAt("ab", 99, "X").equals("abX") && ReplacePads.insertAt("ab", -5, "X").equals("Xab"));
        t("insertAt max kept", ReplacePads.insertAt(new String(new char[ReplacePads.FIELD_MAX]).replace('\0', 'a'), 3, "X").length() == ReplacePads.FIELD_MAX);
        t("backspaceAt middle", ReplacePads.backspaceAt("abcd", 2).equals("acd") && ReplacePads.backspaceAt("abcd", 0).equals("abcd"));
        t("backspaceAt emoji = one", ReplacePads.backspaceAt("a\uD83D\uDE00b", 3).equals("ab"));
        t("left / right", ReplacePads.left("abc", 2) == 1 && ReplacePads.left("abc", 0) == 0 && ReplacePads.right("abc", 2) == 3 && ReplacePads.right("abc", 3) == 3);
        t("left / right emoji", ReplacePads.left("a\uD83D\uDE00b", 3) == 1 && ReplacePads.right("a\uD83D\uDE00b", 1) == 3);
        t("clamp never inside a pair", ReplacePads.clampCaret("a\uD83D\uDE00b", 2) == 1);
        t("view caret mark", ReplacePads.view("abcd", 2, 60).equals("ab\u258Fcd") && ReplacePads.view("", 0, 60).equals("\u258F"));
        String longT = new String(new char[200]).replace('\0', 'x');
        String vw = ReplacePads.view(longT, 100, 60);
        t("view long = window with caret and ellipsis", vw.contains("\u258F") && vw.startsWith("\u2026") && vw.endsWith("\u2026") && vw.length() <= 64);
        t("view null safe", ReplacePads.view(null, 5, 60).equals("\u258F") && ReplacePads.insertAt(null, 0, "a").equals("a") && ReplacePads.backspaceAt(null, 0).isEmpty());
        System.out.println("ReplacePadsTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
