import com.mgb.keyboard.typing.ClipFold;
import com.mgb.keyboard.typing.ClipCopy;

public class ClipFoldTest {
    static int n = 0, fails = 0;
    static void ok(boolean c, String m) { n++; if (!c) { fails++; System.out.println("FAIL " + m); } }
    public static void main(String[] a) {
        ok(ClipFold.keyFor("Pinned").equals("clip_fold_pin"), "pinned key");
        ok(ClipFold.keyFor("Recent").equals("clip_fold_rec"), "recent key");
        ok(ClipFold.keyFor(null).equals("clip_fold_rec"), "null = recent key");
        ok(ClipFold.interactive(true, false, false), "normal mode can fold");
        ok(!ClipFold.interactive(false, false, false), "switch OFF = no folding");
        ok(!ClipFold.interactive(true, true, false), "select mode: no folding");
        ok(!ClipFold.interactive(true, false, true), "search: no folding");
        ok(ClipFold.hidden(true, true, false, false), "folded + normal = hidden");
        ok(!ClipFold.hidden(true, false, false, false), "open = shown");
        ok(!ClipFold.hidden(true, true, true, false), "select mode shows everything");
        ok(!ClipFold.hidden(true, true, false, true), "search shows everything");
        ok(!ClipFold.hidden(false, true, false, false), "switch OFF shows everything even with a saved fold");
        ok(ClipFold.chevron(true).equals("\u25B8") && ClipFold.chevron(false).equals("\u25BE"), "chevrons");
        ok(ClipFold.countText(true, 12).equals(" \u00B7 12"), "count when folded");
        ok(ClipFold.countText(false, 12).isEmpty(), "no count when open");
        ok(ClipFold.countText(true, -3).equals(" \u00B7 0"), "negative count = 0");
        ok(ClipFold.title(12, false, false, 0, "All").equals(ClipCopy.title(12)), "switch OFF = old title exactly");
        ok(ClipFold.title(12, false, true, 3, "Links").equals(ClipCopy.title(12)), "switch OFF ignores the fold state");
        ok(ClipFold.title(12, true, false, 0, "All").equals(ClipCopy.title(12) + " \u25BE"), "open title has the down chevron");
        ok(ClipFold.title(12, true, true, 0, "All").equals(ClipCopy.title(12) + " \u25B8"), "folded, no filter = right chevron only");
        ok(ClipFold.title(12, true, true, 3, "Links").equals(ClipCopy.title(12) + " \u00B7 Links \u25B8"), "folded + filter = filter name shown");
        ok(ClipFold.title(12, true, false, 3, "Links").equals(ClipCopy.title(12) + " \u25BE"), "open row shows the filter itself, no name in title");
        ok(ClipFold.title(0, true, true, 2, null).equals(ClipCopy.title(0) + " \u25B8"), "null label is safe");
        System.out.println("ClipFoldTest " + (fails == 0 ? "OK " + n + "/" + n : "FAILED " + fails));
        if (fails > 0) System.exit(1);
    }
}
