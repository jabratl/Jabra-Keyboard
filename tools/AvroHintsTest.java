import com.mgb.keyboard.typing.AvroHints;
import com.mgb.keyboard.typing.LongPressHints;

/** v0.17.54 (M2): Ridmik Avro hint table + gate. Prints FAIL lines; exit 1 on any fail. */
public class AvroHintsTest {
    static int fails = 0, checks = 0;
    static void eq(String what, Object a, Object b) {
        checks++;
        if (a == null ? b != null : !a.equals(b)) { fails++; System.out.println("FAIL " + what + ": got " + a + " want " + b); }
    }
    public static void main(String[] x) {
        String top = "qwertyuiop", dig = "1234567890";
        for (int i = 0; i < 10; i++) {
            eq("top off " + top.charAt(i), AvroHints.hintFor(top.charAt(i), false), String.valueOf(dig.charAt(i)));
            eq("top on " + top.charAt(i), AvroHints.hintFor(top.charAt(i), true), null);
        }
        String ks = "asdfghjkl", hs = "@#&*-!?()";
        for (int i = 0; i < 9; i++) {
            eq("row2 " + ks.charAt(i), AvroHints.hintFor(ks.charAt(i), true), String.valueOf(hs.charAt(i)));
            eq("row2 numrow off " + ks.charAt(i), AvroHints.hintFor(ks.charAt(i), false), String.valueOf(hs.charAt(i)));
        }
        eq("z", AvroHints.hintFor('z', true), "\"");
        eq("x", AvroHints.hintFor('x', true), null);
        eq("c", AvroHints.hintFor('c', true), null);
        eq("v", AvroHints.hintFor('v', true), null);
        eq("b", AvroHints.hintFor('b', true), "'");
        eq("n", AvroHints.hintFor('n', true), ":");
        eq("m", AvroHints.hintFor('m', true), "/");
        eq("upper A", AvroHints.hintFor('A', true), "@");
        eq("upper M", AvroHints.hintFor('M', false), "/");
        eq("digit", AvroHints.hintFor('5', false), null);
        eq("symbol", AvroHints.hintFor('-', false), null);
        // every non-null hint is ONE char, never a letter
        for (char c = 'a'; c <= 'z'; c++) { String h = AvroHints.hintFor(c, false); if (h != null) { eq("len " + c, h.length(), 1); eq("not letter " + c, Character.isLetter(h.charAt(0)), false); } }
        // gate
        eq("gate on", AvroHints.active(true, true, false, false), true);
        eq("gate setting off", AvroHints.active(false, true, false, false), false);
        eq("gate english", AvroHints.active(true, false, false, false), false);
        eq("gate probhat", AvroHints.active(true, true, true, false), false);
        eq("gate latin only", AvroHints.active(true, true, false, true), false);
        // English table untouched
        eq("english a", LongPressHints.hintFor('a', true), "@");
        eq("english d", LongPressHints.hintFor('d', true), "$");
        eq("english q numrow on", LongPressHints.hintFor('q', true), "~");
        System.out.println((fails == 0 ? "OK " : "FAILED ") + checks + " checks");
        if (fails > 0) System.exit(1);
    }
}
