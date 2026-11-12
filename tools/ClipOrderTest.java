import com.mgb.keyboard.typing.ClipOrder;
import java.util.*;

/** javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/ClipOrder.java tools/ClipOrderTest.java && java -cp out ClipOrderTest */
public class ClipOrderTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL " + m); } }
    public static void main(String[] a) {
        ok(ClipOrder.sanitize(0) == 0 && ClipOrder.sanitize(1) == 1, "valid values kept");
        ok(ClipOrder.sanitize(-1) == 0 && ClipOrder.sanitize(2) == 0 && ClipOrder.sanitize(99) == 0 && ClipOrder.sanitize(Integer.MIN_VALUE) == 0, "bad values = 0 (Pinned first)");
        ok(!ClipOrder.recentFirst(0) && ClipOrder.recentFirst(1) && !ClipOrder.recentFirst(7), "recentFirst");
        ok(ClipOrder.next(0) == 1 && ClipOrder.next(1) == 0 && ClipOrder.next(5) == 1, "next toggles, bad = goes to 1");
        ok(ClipOrder.name(0).equals("Pinned first") && ClipOrder.name(1).equals("Recent first") && ClipOrder.name(9).equals("Pinned first"), "names");
        ok(ClipOrder.NAMES.length == 2, "two names");
        ok(Arrays.equals(ClipOrder.labels(0), new String[]{"Pinned", "Recent"}), "labels pinned first");
        ok(Arrays.equals(ClipOrder.labels(1), new String[]{"Recent", "Pinned"}), "labels recent first");
        ok(Arrays.equals(ClipOrder.labels(-3), new String[]{"Pinned", "Recent"}), "labels bad = default");
        List<String> p = Arrays.asList("p1", "p2"), r = Arrays.asList("r1", "r2", "r3");
        List<List<String>> g0 = ClipOrder.groups(p, r, 0), g1 = ClipOrder.groups(p, r, 1);
        ok(g0.size() == 2 && g0.get(0) == p && g0.get(1) == r, "groups default");
        ok(g1.size() == 2 && g1.get(0) == r && g1.get(1) == p, "groups recent first");
        List<List<String>> gb = ClipOrder.groups(p, r, 42);
        ok(gb.get(0) == p, "groups bad mode = default");
        List<List<String>> gn = ClipOrder.groups(null, null, 1);
        ok(gn.size() == 2 && gn.get(0).isEmpty() && gn.get(1).isEmpty(), "null input = two empty lists");
        List<List<String>> gn2 = ClipOrder.groups(null, r, 0);
        ok(gn2.get(0).isEmpty() && gn2.get(1) == r, "null pinned");
        ok(g0.get(0).size() + g0.get(1).size() == 5 && g1.get(0).size() + g1.get(1).size() == 5, "no item lost");
        ok(ClipOrder.showHeaders(false, 1, 1), "headers: both groups");
        ok(!ClipOrder.showHeaders(true, 1, 1), "headers: not while searching");
        ok(!ClipOrder.showHeaders(false, 0, 3) && !ClipOrder.showHeaders(false, 3, 0), "headers: one group empty");
        ok(!ClipOrder.showHeaders(false, 0, 0), "headers: none");
        // toggling twice returns to start, for every int
        boolean twice = true;
        for (int m = -5; m < 6; m++) if (ClipOrder.next(ClipOrder.next(m)) != ClipOrder.sanitize(m)) twice = false;
        ok(twice, "next(next(m)) == sanitize(m)");
        ok(ClipOrder.PINNED_FIRST == 0 && ClipOrder.RECENT_FIRST == 1, "constants (pref meaning never changes)");
        System.out.println("ClipOrderTest " + (total - fail) + "/" + total + (fail == 0 ? " PASS" : " FAIL"));
        if (fail > 0) System.exit(1);
    }
}
