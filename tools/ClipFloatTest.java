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
        System.out.println("ClipFloatTest " + (fails == 0 ? "OK " + n + "/" + n : "FAILED " + fails));
        if (fails > 0) System.exit(1);
    }
}
