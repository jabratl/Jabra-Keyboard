import com.mgb.keyboard.typing.ClipCopy;
import com.mgb.keyboard.typing.ClipFilter;

/** javac -encoding UTF-8 -d out $(find app -name '*.java') tools/ClipCopyTest.java && java -Dfile.encoding=UTF-8 -cp out ClipCopyTest */
public class ClipCopyTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL " + m); } }

    public static void main(String[] a) {
        ok(ClipCopy.title(0).equals("Clipboard"), "title 0");
        ok(ClipCopy.title(-5).equals("Clipboard"), "title negative");
        ok(ClipCopy.title(12).equals("Clipboard \u00B7 12"), "title 12");
        ok(ClipCopy.countLine(0, 0).isEmpty(), "count 0");
        ok(ClipCopy.countLine(1, 0).equals("1 clip"), "count 1");
        ok(ClipCopy.countLine(5, 0).equals("5 clips"), "count 5");
        ok(ClipCopy.countLine(12, 3).equals("12 clips \u00B7 3 pinned"), "count pinned");
        ok(ClipCopy.countLine(-1, -1).isEmpty(), "count negative");
        // empty texts: every chip, never empty, never null, at most 2 lines
        for (int c = -2; c < ClipFilter.COUNT + 3; c++) {
            for (int s = 0; s < 2; s++) {
                String t = ClipCopy.empty(c, s == 1, "x");
                ok(t != null && !t.isEmpty(), "empty text chip " + c + " search " + s);
                ok(t.split("\n").length <= 2, "empty max 2 lines chip " + c);
            }
        }
        ok(ClipCopy.empty(0, false, null).startsWith("Nothing copied yet"), "empty all");
        ok(ClipCopy.empty(ClipFilter.PINNED, false, "").contains("\u2606"), "empty pinned mentions star");
        ok(ClipCopy.empty(0, true, "abc").equals("No result for \u201Cabc\u201D"), "no result");
        ok(ClipCopy.empty(0, true, "  abc  ").equals("No result for \u201Cabc\u201D"), "query trimmed");
        ok(ClipCopy.empty(0, true, "").equals("Type to search your clips"), "search empty query");
        ok(ClipCopy.empty(0, true, null).equals("Type to search your clips"), "search null query");
        ok(ClipCopy.empty(0, true, "   ").equals("Type to search your clips"), "search blank query");
        String longQ = "abcdefghijklmnopqrstuvwxyz0123456789";
        ok(ClipCopy.empty(0, true, longQ).length() < 40, "long query cut");
        ok(ClipCopy.empty(0, true, longQ).contains("\u2026"), "long query ellipsis");
        // quick delete rule
        ok(!ClipCopy.quickDelete(0), "quick 0");
        ok(!ClipCopy.quickDelete(-1), "quick -1");
        ok(ClipCopy.quickDelete(1), "quick 1");
        ok(ClipCopy.quickDelete(2), "quick 2");
        ok(!ClipCopy.quickDelete(3), "quick 3");
        ok(!ClipCopy.quickDelete(500), "quick 500");
        ok(ClipCopy.deleted(2).startsWith("Deleted 2"), "deleted msg");
        ok(ClipCopy.deleted(-3).startsWith("Deleted 0"), "deleted negative");
        ok(ClipCopy.MORE_LABELS.length == 3, "more labels");
        System.out.println((fail == 0 ? "OK " : "FAILED ") + total + " checks");
        if (fail > 0) System.exit(1);
    }
}
