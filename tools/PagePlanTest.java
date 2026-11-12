import com.mgb.keyboard.typing.PagePlan;
import com.mgb.keyboard.typing.EdgePlan;
import com.mgb.keyboard.typing.SizePct;

/** Run: javac -d out app/src/main/java/com/mgb/keyboard/typing/EdgePlan.java app/src/main/java/com/mgb/keyboard/typing/SizePct.java app/src/main/java/com/mgb/keyboard/typing/PagePlan.java tools/PagePlanTest.java && java -cp out PagePlanTest */
public class PagePlanTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (java.util.Objects.equals(a, b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }
    public static void main(String[] x) {
        eq("7 pages", PagePlan.ORDER.length, 7);
        eq("titles len", PagePlan.TITLES.length, 7);
        eq("icons len", PagePlan.ICONS.length, 7);
        eq("sanitize junk", PagePlan.sanitize("zzz"), PagePlan.HOME);
        eq("sanitize null", PagePlan.sanitize(null), PagePlan.HOME);
        eq("sanitize ok", PagePlan.sanitize("look"), "look");
        eq("title home", PagePlan.title(""), "Jabra Keyboard");
        eq("title layout", PagePlan.title("layout"), "Layout & Size");
        // every old group is on exactly one page
        String[] old = { "Languages & layouts", "Typing", "Look", "Layout & size", "Keys & gestures", "Clipboard & tools", "Data & Backup", "Tools & Panels" };
        for (String g : old) {
            int c = 0;
            for (String p : PagePlan.ORDER) for (String q : PagePlan.groupsOf(p)) if (q.equals(g)) c++;
            eq("group once " + g, c, 1);
            eq("pageOfGroup consistent " + g, java.util.Arrays.asList(PagePlan.groupsOf(PagePlan.pageOfGroup(g))).contains(g), true);
        }
        eq("languages in typing", PagePlan.pageOfGroup("Languages & layouts"), "typing");
        // v0.15.3 (N3): page "tools" is added at the END; old ids and positions stay
        eq("tools is last", PagePlan.ORDER[6], "tools");
        eq("old 6 keep place", String.join(",", java.util.Arrays.copyOf(PagePlan.ORDER, 6)), "typing,look,layout,keys,clip,data");
        eq("tools title", PagePlan.title("tools"), "Tools & Panels");
        eq("tools icon", PagePlan.icon("tools"), "tt");
        eq("tools sanitize", PagePlan.sanitize("tools"), "tools");
        eq("tools group page", PagePlan.pageOfGroup("Tools & Panels"), "tools");
        eq("clip group stays on clip", PagePlan.pageOfGroup("Clipboard & tools"), "clip");
        eq("tools no pin preview", PagePlan.pinPreview("tools"), false);
        eq("tools summary", PagePlan.toolsSummary(true, false), "Edit bar on, formatter off");
        eq("unknown group last page", PagePlan.pageOfGroup("Nope"), "data");
        eq("null group last page", PagePlan.pageOfGroup(null), "data");
        eq("groupsOf junk", PagePlan.groupsOf("zzz").length, 0);
        eq("pin home", PagePlan.pinPreview(""), true);
        eq("pin look", PagePlan.pinPreview("look"), true);
        eq("pin layout", PagePlan.pinPreview("layout"), true);
        eq("pin typing", PagePlan.pinPreview("typing"), false);
        eq("pin data", PagePlan.pinPreview("data"), false);
        eq("sum layout", PagePlan.layoutSummary(120, 6), "Height 120%, radius 6");
        eq("sum look", PagePlan.lookSummary("Blue", true), "Blue, Jabra look");
        eq("sum look empty", PagePlan.lookSummary("", false), "Classic, Classic look");
        eq("sum typing", PagePlan.typingSummary(true, false), "Probhat, space swipe off");
        eq("sum keys", PagePlan.keysSummary(true, false, true), "Vibrate on, sound off, popup on");
        eq("sum clip", PagePlan.clipSummary(false, true), "History off, edit bar on");
        eq("sum data", PagePlan.dataSummary(true, "0.11.0"), "Auto backup on, v0.11.0");
        eq("quick label", PagePlan.quickLabel("large"), "Large keys");
        eq("quick label junk", PagePlan.quickLabel("x"), "");
        int n = EdgePlan.PRESET_KEYS.length;
        int[] cur = new int[n]; java.util.Arrays.fill(cur, -1);
        cur[5] = 3; cur[9] = 7;
        int[] r = PagePlan.quickValues("ridmik", cur);
        eq("ridmik = preset", java.util.Arrays.equals(r, EdgePlan.RIDMIK), true);
        r[0] = 1;
        eq("ridmik copy", EdgePlan.RIDMIK[0], 120);
        int[] c = PagePlan.quickValues("compact", cur);
        eq("compact h", c[0], 90);
        eq("compact keeps fn_gap", c[5], 3);
        eq("compact keeps radius", c[9], 7);
        eq("cur untouched", cur[0], -1);
        int[] l = PagePlan.quickValues("large", cur);
        eq("large h", l[0], 135);
        // every written value is valid for its pref
        for (String id : PagePlan.QUICK) {
            int[] v = PagePlan.quickValues(id, cur);
            eq("h valid " + id, SizePct.heightValid(v[0]), true);
            eq("hl valid " + id, SizePct.heightValid(v[1]), true);
            eq("gap valid " + id, SizePct.gapValid(v[2]), true);
            eq("edge valid " + id, EdgePlan.edgeValid(v[3]), true);
        }
        eq("quick junk id", PagePlan.quickValues("zzz", cur), null);
        eq("quick null id", PagePlan.quickValues(null, cur), null);
        eq("quick wrong len", PagePlan.quickValues("large", new int[3]), null);
        eq("quick null cur", PagePlan.quickValues("large", null), null);
        System.out.println("PagePlanTest " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }
}
