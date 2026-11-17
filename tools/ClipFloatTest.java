import com.mgb.keyboard.typing.ClipFloat;

public class ClipFloatTest {
    static int n = 0, fails = 0;
    static void ok(boolean c, String m) { n++; if (!c) { fails++; System.out.println("FAIL " + m); } }
    public static void main(String[] a) {
        ok(ClipFloat.pillDp(34) == 36, "34 -> 36 (floor)");
        ok(ClipFloat.pillDp(44) == 44, "44 stays");
        ok(ClipFloat.coverDp(34) == 44, "cover = pill + gap");
        ok(ClipFloat.listPadDp(34) == 48, "list pad = cover + 4");
        ok(ClipFloat.listPadDp(34) > ClipFloat.coverDp(34), "last clip always above the pill");
        ok(ClipFloat.wants(true, true, false, false), "select + pref ON");
        ok(!ClipFloat.wants(false, true, false, false), "no select = no pill");
        ok(!ClipFloat.wants(true, false, false, false), "pref OFF = old bar");
        ok(!ClipFloat.wants(true, true, true, false), "searching = old bar");
        ok(!ClipFloat.wants(true, true, false, true), "old tray open = old bar");
        ok(ClipFloat.animate(false) && !ClipFloat.animate(true), "animate only on first show");
        ok(ClipFloat.scrollAction(50, 10, 6, false) == -1, "scroll down hides");
        ok(ClipFloat.scrollAction(10, 50, 6, false) == 1, "scroll up shows");
        ok(ClipFloat.scrollAction(12, 10, 6, false) == 0, "tiny move keeps");
        ok(ClipFloat.scrollAction(50, 10, 6, true) == 1, "end of list shows");
        ok(ClipFloat.countText(3).equals("3") && ClipFloat.countText(150).equals("99+") && ClipFloat.countText(-2).equals("0"), "count text");
        System.out.println("ClipFloatTest " + (fails == 0 ? "OK " + n + "/" + n : "FAILED " + fails));
        if (fails > 0) System.exit(1);
    }
}
