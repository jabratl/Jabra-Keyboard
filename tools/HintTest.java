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
        // ---- v0.5.2 custom override
        eq("custom empty = old table", LongPressHints.hintFor('a', true, ""), "@");
        eq("custom null = old table", LongPressHints.hintFor('a', true, null), "@");
        eq("custom a", LongPressHints.hintFor('a', true, "a=!"), "!");
        eq("custom upper letter same", LongPressHints.hintFor('A', true, "a=!"), "!");
        eq("custom key letter upper in text", LongPressHints.hintFor('a', true, "A=!"), "!");
        eq("custom other letters unchanged", LongPressHints.hintFor('s', true, "a=!"), "#");
        eq("custom q over symbols", LongPressHints.hintFor('q', true, "q=1"), "1");
        eq("custom Bangla char", LongPressHints.hintFor('k', true, "k=\u0995"), "\u0995");
        eq("custom emoji (surrogate pair)", LongPressHints.hintFor('e', true, "e=\uD83D\uDE00"), "\uD83D\uDE00");
        eq("custom two chars refused", LongPressHints.normalizeCustom("a=ab"), "");
        eq("custom comma value refused", LongPressHints.normalizeCustom("a=,,s=#"), "s=#");
        eq("custom equals value refused", LongPressHints.normalizeCustom("a=="), "");
        eq("custom space value refused", LongPressHints.normalizeCustom("a= "), "");
        eq("custom digit key refused", LongPressHints.normalizeCustom("5=a,b=#"), "b=#");
        eq("custom duplicate: first wins", LongPressHints.normalizeCustom("a=@,a=#"), "a=@");
        eq("custom garbage", LongPressHints.normalizeCustom("zzz,=,,a"), "");
        eq("custom null text", LongPressHints.normalizeCustom(null), "");
        StringBuilder many = new StringBuilder();
        for (char c = 'a'; c <= 'z'; c++) many.append(c).append("=!,");
        many.append("a=#");
        eq("custom 26 max", LongPressHints.normalizeCustom(many.toString()).split(",").length, 26);
        eq("custom no hint for non-letter", LongPressHints.hintFor('5', true, "a=!"), null);
        eq("custom customFor none", LongPressHints.customFor('b', "a=!"), null);
        eq("custom 3-arg old tables intact", LongPressHints.hintFor('q', false, "z=!"), "1");
        System.out.println("HintTest " + ok + "/" + (ok + bad) + (bad == 0 ? " OK" : " FAILED"));
        if (bad != 0) System.exit(1);
    }
}
