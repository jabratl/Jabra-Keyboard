import com.mgb.keyboard.typing.PunctSpace;

public class PunctSpaceTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        if (got.equals(want)) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    static boolean k(String b, char c) { return PunctSpace.shouldRemove(b, c, true, false); }
    static boolean p(String b, char c) { return PunctSpace.shouldRemove(b, c, false, true); }
    public static void main(String[] a) {
        for (char c : new char[]{',', '.', '!', '?', '\u0964'}) eq("key word+space " + c, k("hello ", c), true);
        eq("key ; not in key set", k("hello ", ';'), false);
        eq("key : not in key set (smileys)", k("hello ", ':'), false);
        eq("key ) not in key set", k("hello ", ')'), false);
        eq("key letter typed = no", k("hello ", 'a'), false);
        eq("key no trailing space", k("hello", ','), false);
        eq("key two spaces", k("hello  ", ','), false);
        eq("key only a space", k(" ", ','), false);
        eq("key empty", k("", ','), false);
        eq("key null", k(null, ','), false);
        eq("key after newline", k("hello\n ", ','), false);
        eq("key after period (ellipsis)", k("hello. ", '.'), false);
        eq("key after comma", k("a, ", ','), false);
        eq("key after emoji", k("hi \uD83D\uDE00 ", ','), false);
        eq("key after digit", k("room 5 ", ','), true);
        eq("key after closing bracket", k("(ok) ", ','), true);
        eq("key after curly quote", k("said \u201Chi\u201D ", ','), true);
        eq("key after Bangla letter", k("\u0986\u09AE\u09BF ", ','), true);       // ami ends with vowel sign i
        eq("key after Bangla hasanta word", k("\u09B8\u09A4\u09CD ", '\u0964'), true);
        eq("key danda after Bangla", k("\u09AC\u09BE\u0982\u09B2\u09BE ", '\u0964'), true);
        // old rule after a tapped suggestion
        for (char c : ",.!?;:)\u0964".toCharArray()) eq("pick " + c, p("hello ", c), true);
        eq("pick no space", p("hello", ','), false);
        eq("pick letter", p("hello ", 'x'), false);
        eq("pick after period still old rule", p("hello. ", ','), true);
        eq("neither flag", PunctSpace.shouldRemove("hello ", ',', false, false), false);
        System.out.println("PunctSpaceTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
