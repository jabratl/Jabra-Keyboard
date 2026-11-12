import com.mgb.keyboard.typing.ScrollKeep;

/** javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/ScrollKeep.java tools/ScrollKeepTest.java && java -cp out ScrollKeepTest */
public class ScrollKeepTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL " + m); } }
    public static void main(String[] a) {
        ok(ScrollKeep.clamp(500, 2000, 600) == 500, "normal position kept");
        ok(ScrollKeep.clamp(1400, 2000, 600) == 1400, "exactly at the end kept");
        ok(ScrollKeep.clamp(1500, 2000, 600) == 1400, "past the end clamped (list got shorter)");
        ok(ScrollKeep.clamp(99999, 2000, 600) == 1400, "huge saved value clamped");
        ok(ScrollKeep.clamp(0, 2000, 600) == 0, "saved 0 = top");
        ok(ScrollKeep.clamp(-1, 2000, 600) == 0, "NONE = top");
        ok(ScrollKeep.clamp(-500, 2000, 600) == 0, "negative = top");
        ok(ScrollKeep.clamp(300, 0, 600) == 0, "empty content = 0");
        ok(ScrollKeep.clamp(300, -5, 600) == 0, "negative content = 0");
        ok(ScrollKeep.clamp(300, 500, 600) == 0, "content shorter than view = 0");
        ok(ScrollKeep.clamp(300, 600, 600) == 0, "content equals view = 0");
        ok(ScrollKeep.clamp(300, 601, 600) == 1, "1 px of scroll room");
        ok(ScrollKeep.clamp(300, 2000, -1) == 0, "unknown view height = 0");
        ok(ScrollKeep.clamp(300, 2000, 0) == 300, "view height 0 (not laid out) still safe");
        ok(ScrollKeep.clamp(Integer.MAX_VALUE, Integer.MAX_VALUE, 1) == Integer.MAX_VALUE - 1, "max int safe");
        ok(ScrollKeep.clamp(Integer.MIN_VALUE, 2000, 600) == 0, "min int = 0");
        ok(ScrollKeep.clamp(100, Integer.MAX_VALUE, Integer.MIN_VALUE) == 0, "min int view = 0");
        ok(!ScrollKeep.wants(ScrollKeep.NONE), "NONE not wanted");
        ok(!ScrollKeep.wants(0), "0 not wanted");
        ok(ScrollKeep.wants(1), "1 wanted");
        ok(ScrollKeep.wants(5000), "5000 wanted");
        System.out.println((total - fail) + "/" + total + " passed");
        if (fail > 0) System.exit(1);
    }
}
