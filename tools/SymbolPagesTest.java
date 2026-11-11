import com.mgb.keyboard.typing.SymbolPages;
import java.util.HashSet;

public class SymbolPagesTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (a.equals(b)) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + a + " want " + b); } }
    static void yes(String n, boolean c) { eq(n, c, true); }

    public static void main(String[] x) {
        // page count + cycle 0 -> 1 -> 2 -> 0
        eq("count", SymbolPages.COUNT, 3);
        eq("next 0", SymbolPages.next(0), 1);
        eq("next 1", SymbolPages.next(1), 2);
        eq("next 2", SymbolPages.next(2), 0);
        eq("next bad -1", SymbolPages.next(-1), 1);
        eq("next bad 9", SymbolPages.next(9), 1);
        int p = 0; for (int i = 0; i < 3; i++) p = SymbolPages.next(p);
        eq("3 taps = back on page 0", p, 0);
        eq("norm -1", SymbolPages.norm(-1), 0);
        eq("norm 3", SymbolPages.norm(3), 0);
        eq("norm 2", SymbolPages.norm(2), 2);

        // old pages unchanged, character for character (v0.9.4 arrays)
        String[] o0 = { "1234567890", "@#\u09F3%&-+()/", "*\"':;!?" };
        String[] o1 = { "~`|\u2022\u00B0^=_{}", "\\[]<>\u00A3\u20AC\u00A5\u20B9$", "\u2026\u00A9\u00AE\u2122\u00A7" };
        for (int r = 0; r < 3; r++) {
            eq("page0 row" + r + " old", SymbolPages.page(0)[r], o0[r]);
            eq("page1 row" + r + " old", SymbolPages.page(1)[r], o1[r]);
        }

        // every page: 3 rows, row limits, one char per key (no surrogates), no duplicate inside a page
        for (int pg = 0; pg < SymbolPages.COUNT; pg++) {
            String[] rows = SymbolPages.page(pg);
            eq("p" + pg + " rows", rows.length, 3);
            HashSet<Character> seen = new HashSet<Character>();
            boolean dup = false, sur = false, space = false, tooLong = false;
            for (int r = 0; r < 3; r++) {
                if (rows[r].length() > SymbolPages.ROW_MAX[r]) tooLong = true;
                for (int i = 0; i < rows[r].length(); i++) {
                    char c = rows[r].charAt(i);
                    if (Character.isSurrogate(c)) sur = true;
                    if (Character.isWhitespace(c)) space = true;
                    if (!seen.add(c)) dup = true;
                }
            }
            yes("p" + pg + " no duplicates", !dup);
            yes("p" + pg + " no surrogate", !sur);
            yes("p" + pg + " no blank key", !space);
            yes("p" + pg + " rows fit 360dp", !tooLong);
        }

        // new page 2 content
        String[] n = SymbolPages.page(2);
        yes("pi", n[0].indexOf('\u03C0') >= 0);
        yes("root", n[0].indexOf('\u221A') >= 0);
        yes("divide", n[0].indexOf('\u00F7') >= 0);
        yes("times", n[0].indexOf('\u00D7') >= 0);
        yes("pilcrow", n[0].indexOf('\u00B6') >= 0);
        yes("Delta", n[0].indexOf('\u0394') >= 0);
        yes("arrows", n[1].indexOf('\u2190') >= 0 && n[1].indexOf('\u2192') >= 0 && n[1].indexOf('\u2191') >= 0 && n[1].indexOf('\u2193') >= 0);
        yes("Greek alpha + Omega", n[2].indexOf('\u03B1') >= 0 && n[2].indexOf('\u03A9') >= 0);
        yes("old taka stays on page 0", SymbolPages.page(0)[1].indexOf('\u09F3') >= 0);
        yes("old rupee + euro stay on page 1", SymbolPages.page(1)[1].indexOf('\u20B9') >= 0 && SymbolPages.page(1)[1].indexOf('\u20AC') >= 0);
        eq("page2 key total", n[0].length() + n[1].length() + n[2].length(), 26);

        // page 2 shares nothing with pages 0 and 1 (new page = new symbols)
        HashSet<Character> old = new HashSet<Character>();
        for (int pg = 0; pg < 2; pg++) for (String s : SymbolPages.page(pg)) for (int i = 0; i < s.length(); i++) old.add(s.charAt(i));
        boolean shared = false;
        for (String s : n) for (int i = 0; i < s.length(); i++) if (old.contains(s.charAt(i))) shared = true;
        yes("page2 shares nothing with old pages", !shared);

        // returned arrays are copies (caller cannot change the table)
        String[] a = SymbolPages.page(0); a[0] = "X";
        eq("copy safe", SymbolPages.page(0)[0], "1234567890");
        eq("bad index = page 0", SymbolPages.page(7)[0], "1234567890");

        // switch label: old labels kept, shows where the tap goes
        eq("label 0 old", SymbolPages.switchLabel(0), "=<");
        eq("label 2 back to ?123", SymbolPages.switchLabel(2), "?123");
        yes("label 1 not empty", SymbolPages.switchLabel(1).length() > 0);
        yes("3 labels differ", !SymbolPages.switchLabel(0).equals(SymbolPages.switchLabel(1)) && !SymbolPages.switchLabel(1).equals(SymbolPages.switchLabel(2)) && !SymbolPages.switchLabel(0).equals(SymbolPages.switchLabel(2)));
        eq("label bad = page 0 label", SymbolPages.switchLabel(-5), "=<");

        System.out.println("SymbolPagesTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
