import com.mgb.keyboard.typing.QuickPaste;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** v0.38.0 (P1): tests of the pure paste helpers. */
public class QuickPasteTest {
    static int fails = 0, runs = 0;
    static void eq(String name, Object got, Object want) {
        runs++;
        if (got == null ? want != null : !got.equals(want)) { fails++; System.out.println("FAIL " + name + ": got [" + got + "] want [" + want + "]"); }
    }
    public static void main(String[] a) {
        // normalize
        eq("norm null", QuickPaste.normalize(null), "");
        eq("norm empty", QuickPaste.normalize(""), "");
        eq("norm crlf", QuickPaste.normalize("a\r\nb\rc\nd"), "a\nb\nc\nd");
        eq("norm nul", QuickPaste.normalize("a\u0000b"), "ab");
        eq("norm bangla", QuickPaste.normalize("\u0986\u09AE\u09BF"), "\u0986\u09AE\u09BF");
        // fit
        eq("fit null", QuickPaste.fit(null, 10), "");
        eq("fit room0", QuickPaste.fit("abc", 0), "");
        eq("fit neg", QuickPaste.fit("abc", -5), "");
        eq("fit all", QuickPaste.fit("abc", 3), "abc");
        eq("fit cut", QuickPaste.fit("abcdef", 4), "abcd");
        String emoji = "ab\uD83D\uDE00cd";   // a b (smile = 2 chars) c d
        eq("fit pair kept", QuickPaste.fit(emoji, 4), "ab\uD83D\uDE00");
        eq("fit pair split", QuickPaste.fit(emoji, 3), "ab");
        eq("fit crlf then cut", QuickPaste.fit("a\r\nb", 2), "a\n");
        eq("cut true", QuickPaste.wasCut("abcdef", 4), true);
        eq("cut false", QuickPaste.wasCut("abc", 4), false);
        eq("cut room0", QuickPaste.wasCut("abc", 0), false);
        // oneLine
        eq("one short", QuickPaste.oneLine("hello", 10), "hello");
        eq("one nl", QuickPaste.oneLine("a\nb\tc", 10), "a\u23CEb\u21E5c");
        eq("one cut", QuickPaste.oneLine("abcdefghij", 5), "abcd\u2026");
        eq("one null", QuickPaste.oneLine(null, 5), "");
        eq("one tiny max", QuickPaste.oneLine("abcdef", 0), "a\u2026");
        eq("one pair", QuickPaste.oneLine("abc\uD83D\uDE00def", 5), "abc\u2026");
        // pickList
        List<String> o = Arrays.asList("one", "  ", "two", "one", null, "three");
        eq("pick basic", QuickPaste.pickList("last", o, 10), Arrays.asList("last", "one", "two", "three"));
        eq("pick dup latest", QuickPaste.pickList("one", o, 10), Arrays.asList("one", "two", "three"));
        eq("pick blank latest", QuickPaste.pickList("   ", o, 10), Arrays.asList("one", "two", "three"));
        eq("pick null latest", QuickPaste.pickList(null, o, 2), Arrays.asList("one", "two"));
        eq("pick max1", QuickPaste.pickList("last", o, 1), Arrays.asList("last"));
        eq("pick max0", QuickPaste.pickList("last", o, 0), new ArrayList<String>());
        eq("pick null others", QuickPaste.pickList("x", null, 5), Arrays.asList("x"));
        // texts
        eq("done", QuickPaste.doneText(5, false), "Pasted 5");
        eq("done cut", QuickPaste.doneText(5, true), "Pasted 5 (cut to fit)");
        eq("none empty", QuickPaste.noneText("", 10), "Clipboard is empty");
        eq("none null", QuickPaste.noneText(null, 10), "Clipboard is empty");
        eq("none full", QuickPaste.noneText("abc", 0), "No room left (max 0 more)");
        eq("quick count", QuickPaste.QUICK.length, 4);
        // never throws
        try { QuickPaste.fit("x", Integer.MAX_VALUE); QuickPaste.oneLine("x", Integer.MIN_VALUE); QuickPaste.pickList("x", o, Integer.MIN_VALUE); runs++; } catch (Throwable t) { fails++; System.out.println("FAIL throws " + t); }
        System.out.println("QuickPasteTest: " + (runs - fails) + "/" + runs + " ok");
        if (fails > 0) System.exit(1);
    }
}
