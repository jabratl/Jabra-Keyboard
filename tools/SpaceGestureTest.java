import com.mgb.keyboard.extras.SpaceGesture;

public class SpaceGestureTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    public static void main(String[] a) {
        // plain tap -> space
        SpaceGesture g = new SpaceGesture(10f, 40f);
        g.down(100f);
        eq("tap up", g.up(), true);
        // quick drag (before the timer) = cursor, no space, no arm
        g = new SpaceGesture(10f, 40f);
        g.down(100f);
        eq("cursor n", g.move(125f), 2);
        eq("cursor kind", g.kind(), SpaceGesture.CURSOR);
        eq("arm after cursor drag fails", g.arm(), false);
        eq("not armed", g.armed(), false);
        eq("cursor up no space", g.up(), false);
        // armed + swipe = language steps, no cursor, no space
        g = new SpaceGesture(10f, 40f);
        g.down(100f);
        eq("small wiggle", g.move(104f), 0);
        eq("arm ok", g.arm(), true);
        eq("armed", g.armed(), true);
        eq("lang n (wiggle was < step, anchor reset at arm)", g.move(150f), 1);
        eq("lang kind", g.kind(), SpaceGesture.LANG);
        eq("lang left", g.move(70f), -1);
        eq("lang up no space", g.up(), false);
        // armed, not swiped = still a space (a slow tap still types)
        g = new SpaceGesture(10f, 40f);
        g.down(100f);
        g.arm();
        eq("armed no swipe: space", g.up(), true);
        eq("armed small move kind none", g.move(110f) == 0 && g.kind() == SpaceGesture.NONE, true);
        // arm twice
        g = new SpaceGesture(10f, 40f);
        g.down(0f);
        eq("arm 1", g.arm(), true);
        eq("arm 2", g.arm(), false);
        // armed: lang step size is respected (cursor step 10 would give 3, language gives 0)
        g = new SpaceGesture(10f, 40f);
        g.down(100f);
        g.arm();
        eq("30 px < 40 px step", g.move(130f), 0);
        eq("then 45 px", g.move(145f), 1);
        // new gesture resets everything
        g.down(50f);
        eq("reset armed", g.armed(), false);
        eq("reset up", g.up(), true);
        // after language swipe, a later drag stays language
        g = new SpaceGesture(10f, 40f);
        g.down(100f);
        g.arm();
        g.move(150f);
        g.move(200f);
        eq("still lang", g.kind(), SpaceGesture.LANG);
        // wiggle before arm that stays under one cursor step does not block arming
        g = new SpaceGesture(16f, 40f);
        g.down(100f);
        g.move(108f);
        eq("arm after tiny wiggle", g.arm(), true);
        System.out.println("SpaceGestureTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
