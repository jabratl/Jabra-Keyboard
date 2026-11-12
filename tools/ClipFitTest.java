import com.mgb.keyboard.typing.ClipFit;

/** javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/ClipFit.java tools/ClipFitTest.java && java -cp out ClipFitTest */
public class ClipFitTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL " + m); } }
    public static void main(String[] a) {
        int max = 260 * 3;
        ok(ClipFit.extraPx(900, 700, max) == 200, "normal gap");
        ok(ClipFit.extraPx(700, 700, max) == 0, "equal = 0");
        ok(ClipFit.extraPx(600, 700, max) == 0, "panel taller than keyboard = never shrink");
        ok(ClipFit.extraPx(0, 700, max) == 0, "unknown target = 0");
        ok(ClipFit.extraPx(-5, 700, max) == 0, "negative target = 0");
        ok(ClipFit.extraPx(900, 0, max) == 0, "unmeasured panel = 0");
        ok(ClipFit.extraPx(900, -1, max) == 0, "negative measure = 0");
        ok(ClipFit.extraPx(5000, 700, max) == max, "huge gap clamped to max");
        ok(ClipFit.extraPx(900, 700, 0) == 0, "max 0 = 0");
        ok(ClipFit.extraPx(900, 700, -3) == 0, "max negative = 0");
        ok(ClipFit.extraPx(701, 700, max) == 1, "1 px gap");
        ok(ClipFit.extraPx(Integer.MAX_VALUE, 1, max) == max, "overflow safe (max int target)");
        ok(ClipFit.extraPx(Integer.MAX_VALUE, Integer.MAX_VALUE, max) == 0, "both max = 0");
        ok(ClipFit.extraPx(Integer.MIN_VALUE, 700, max) == 0, "min int target = 0");
        // typical phone 3x density: letters keyboard 280 dp = 840 px, clipboard built 262 dp = 786 px
        ok(ClipFit.extraPx(840, 786, 260 * 3) == 54, "typical phone");
        // key height 120%: keyboard 330 dp = 990 px
        ok(ClipFit.extraPx(990, 786, 260 * 3) == 204, "key height 120%");
        // key height 80%: keyboard 220 dp = 660 px, panel 786 px = no shrink
        ok(ClipFit.extraPx(660, 786, 260 * 3) == 0, "key height 80% keeps old panel");
        // landscape: low keyboard
        ok(ClipFit.extraPx(500, 786, 260 * 3) == 0, "landscape low keyboard");
        // search mode: panel taller
        ok(ClipFit.extraPx(840, 900, 260 * 3) == 0, "search mode taller than keyboard");
        ok(ClipFit.MAX_EXTRA_DP == 260, "constant");
        ok(ClipFit.on(true) && !ClipFit.on(false), "on()");
        // monotonic: bigger target never gives less extra
        boolean mono = true; int prev = 0;
        for (int t = 0; t < 3000; t += 7) { int e = ClipFit.extraPx(t, 800, 780); if (e < prev) mono = false; prev = e; }
        ok(mono, "monotonic in target");
        boolean bound = true;
        for (int t = -50; t < 4000; t += 13) for (int m = -50; m < 4000; m += 37) { int e = ClipFit.extraPx(t, m, 780); if (e < 0 || e > 780) bound = false; }
        ok(bound, "always 0..max");
        System.out.println("ClipFitTest " + (total - fail) + "/" + total + (fail == 0 ? " PASS" : " FAIL"));
        if (fail > 0) System.exit(1);
    }
}
