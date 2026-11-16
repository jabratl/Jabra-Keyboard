import com.mgb.keyboard.typing.ReplacePads;

/** v0.24.0 (R1) ReplacePads tests (computer only). */
public class ReplacePadsTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] x) {
        t("4 pages + 4 names", ReplacePads.ROWS.length == 4 && ReplacePads.PAGE_NAMES.length == 4);
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
        t("next page wraps", ReplacePads.next(0) == 1 && ReplacePads.next(3) == 0);
        t("append", ReplacePads.append("ab", "c").equals("abc") && ReplacePads.append(null, "c").equals("c") && ReplacePads.append("ab", "").equals("ab") && ReplacePads.append("ab", null).equals("ab"));
        StringBuilder full = new StringBuilder();
        for (int i = 0; i < ReplacePads.FIELD_MAX; i++) full.append('a');
        t("append stops at the limit", ReplacePads.append(full.toString(), "b").equals(full.toString()));
        t("backspace", ReplacePads.backspace("abc").equals("ab") && ReplacePads.backspace("").equals("") && ReplacePads.backspace(null).equals(""));
        t("backspace emoji = one", ReplacePads.backspace("a\uD83D\uDE00").equals("a"));
        t("regex helper keys exist", ReplacePads.ROWS[1][3].contains("\\d") && ReplacePads.ROWS[1][3].contains("$1"));
        System.out.println("ReplacePadsTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
