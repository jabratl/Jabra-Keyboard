import com.mgb.keyboard.typing.ClipPop;
import java.util.List;

public class ClipPopTest {
    static int fails = 0, n = 0;
    static void ok(boolean c, String m) { n++; if (!c) { fails++; System.out.println("FAIL " + m); } }

    static int ticks(List<ClipPop.Item> l, String prefix) { int t = 0; for (ClipPop.Item i : l) if (i.id.startsWith(prefix) && i.ticked) t++; return t; }

    public static void main(String[] a) {
        List<ClipPop.Item> m = ClipPop.moreItems(true);
        List<String> mi = ClipPop.ids(m);
        for (String id : ClipPop.oldMoreIds()) ok(mi.contains(id), "more has " + id);
        ok(!ClipPop.ids(ClipPop.moreItems(false)).contains("vault"), "no vault row without vault");
        ok(new java.util.HashSet<>(mi).size() == mi.size(), "more: no duplicate ids");
        List<ClipPop.Item> h = ClipPop.headerItems(2, true, false, 30, 5);
        List<String> hi = ClipPop.ids(h);
        for (String id : ClipPop.oldHeaderIds()) ok(hi.contains(id), "header has " + id);
        ok(hi.contains("sort0") && hi.contains("sort3") && hi.contains("view0") && hi.contains("view1"), "sort + view when pills OFF");
        ok(ticks(h, "sort") == 1 && ticks(h, "view") == 1, "one tick in Sort, one in View");
        ok(h.get(h.size() - 1).id.equals("clear") && h.get(h.size() - 1).danger, "Clear is last + danger");
        ok(new java.util.HashSet<>(hi).size() == hi.size(), "header: no duplicate ids");
        ok(!ClipPop.ids(ClipPop.headerItems(0, false, true, 30, 0)).contains("sort0"), "pills ON: no Sort / View rows");
        ok(!ClipPop.ids(ClipPop.headerItems(0, false, true, 0, 3)).contains("deleted"), "no Deleted row when Undo is Off");
        ok(ClipPop.headerItems(0, false, true, 10, 7).get(1).label.equals("Deleted 7"), "Deleted n label");
        String uv = ""; for (ClipPop.Item it : ClipPop.headerItems(0, false, true, 0, 0)) if (it.id.equals("undo")) uv = it.value;
        ok(uv.equals("Off"), "Undo value Off");
        int[] q = { 0, 10, 30, 50, 100 };
        ok(ClipPop.nextUndo(0, q) == 10 && ClipPop.nextUndo(50, q) == 100 && ClipPop.nextUndo(100, q) == 0, "undo cycle wraps");
        ok(ClipPop.nextUndo(10, q) == 30, "undo 10 -> 30");
        ok(ClipPop.nextUndo(20, q) == 30 && ClipPop.nextUndo(10, new int[] { 0, 10, 30 }) == 30, "custom value kept in the cycle");
        ok(ClipPop.nextUndo(10, q) != 10, "cycle always moves");
        ok(ClipPop.maxHeightDp(300, 200, 40) == 200, "content fits");
        ok(ClipPop.maxHeightDp(100, 400, 40) == 92, "clamped to space - 8");
        ok(ClipPop.maxHeightDp(10, 400, 40) == 40, "never below one row");
        // v0.36.5: popover stays small: the longest menu (3-dot, pills OFF) must be well under 400 dp
        int est = ClipPop.heightDp(ClipPop.headerItems(0, false, false, 30, 14));
        ok(est <= 300, "3-dot menu height " + est + " dp <= 300");
        int estMore = ClipPop.heightDp(ClipPop.moreItems(true));
        ok(estMore <= 250, "More menu height " + estMore + " dp <= 250");   // v0.41.1: +1 chip row (Recent / Pinned)
        ok(ClipPop.rows(ClipPop.headerItems(0, false, false, 30, 14)).size() == 8, "3-dot: 2 sort chip rows + 1 view chip row + 5 single rows = 8 visual rows");
        ok(ClipPop.rows(ClipPop.moreItems(false)).size() == 6, "More: 4 chip rows (All/None, Recent/Pinned, Edit/Replace, Merge/Unpin) + Paste 1 line + Export (no vault) = 6 visual rows");
        ok(ClipPop.ids(ClipPop.moreItems(false)).contains("selrecent") && ClipPop.ids(ClipPop.moreItems(false)).contains("selpinned"), "More has Recent + Pinned select rows");
        for (java.util.List<ClipPop.Item> r : ClipPop.rows(ClipPop.headerItems(0, false, false, 30, 14))) ok(r.size() <= 2, "max 2 per row");
        ok(ClipPop.ROW_DP >= 32, "row not below 32 dp (finger target)");
        ok(ClipPop.WIDTH_DP <= 180 && ClipPop.WIDTH_DP >= 150, "width 150..180 dp");
        ok(!ClipPop.reopenGuard(1100, 1000) && ClipPop.reopenGuard(1300, 1000) && ClipPop.reopenGuard(50, 0), "reopen guard");
        System.out.println(fails == 0 ? "ClipPopTest OK " + n + "/" + n : "ClipPopTest FAILED " + fails);
        if (fails > 0) System.exit(1);
    }
}
