import com.mgb.keyboard.typing.LongPressHints;

public class HintTest {
    static int ok = 0, bad = 0;
    static void eq(String name, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + name + ": got " + got + " want " + want); }
    }
    public static void main(String[] a) {
        // every a-z has a hint, and uppercase gives the same hint (shift never changes it)
        for (char c = 'a'; c <= 'z'; c++) {
            eq("has " + c, LongPressHints.hintFor(c, true) != null, true);
            eq("has off " + c, LongPressHints.hintFor(c, false) != null, true);
            eq("shift same " + c, LongPressHints.hintFor(Character.toUpperCase(c), true), LongPressHints.hintFor(c, true));
        }
        // number row OFF: digits on q..p
        String keys = "qwertyuiop", dig = "1234567890";
        for (int i = 0; i < 10; i++) eq("digit " + keys.charAt(i), LongPressHints.hintFor(keys.charAt(i), false), String.valueOf(dig.charAt(i)));
        // number row ON: no digit on q..p (no duplicates with the number row)
        for (int i = 0; i < 10; i++) {
            String h = LongPressHints.hintFor(keys.charAt(i), true);
            eq("no digit " + keys.charAt(i), Character.isDigit(h.charAt(0)), false);
        }
        // middle + bottom rows are the same with number row on or off
        for (char c : "asdfghjklzxcvbnm".toCharArray())
            eq("row same " + c, LongPressHints.hintFor(c, true), LongPressHints.hintFor(c, false));
        // all hints are exactly one character; q..p hints (row ON) are all different from the other hints
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (char c = 'a'; c <= 'z'; c++) {
            String h = LongPressHints.hintFor(c, true);
            eq("len1 " + c, h.length(), 1);
            eq("unique " + c + " " + h, seen.add(h), true);
        }
        // not a letter -> null
        eq("digit key", LongPressHints.hintFor('5', true), null);
        eq("symbol key", LongPressHints.hintFor('.', true), null);
        // active / label rules
        eq("active on", LongPressHints.active(true, false, false), true);
        eq("setting off", LongPressHints.active(false, false, false), false);
        eq("bangla", LongPressHints.active(true, true, false), false);
        eq("pc meta", LongPressHints.active(true, false, true), false);
        eq("label shown", LongPressHints.labelVisible(true, false), true);
        eq("label hidden", LongPressHints.labelVisible(true, true), false);
        eq("label inactive", LongPressHints.labelVisible(false, false), false);
        System.out.println("HintTest " + ok + "/" + (ok + bad) + (bad == 0 ? " OK" : " FAILED"));
        if (bad != 0) System.exit(1);
    }
}
