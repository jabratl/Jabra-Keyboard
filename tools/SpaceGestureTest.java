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
        // ---- v0.9.8 (V3): net language steps ----
        g = new SpaceGesture(16f, 40f);
        g.down(200f);
        eq("net 0 at start", g.langNet(), 0);
        g.arm();
        g.move(250f);
        eq("net +1", g.langNet(), 1);
        g.move(330f);
        eq("net +3 after two more", g.langNet(), 3);
        g.move(250f);
        eq("net back to +2", g.langNet(), 2);
        g.move(130f);
        eq("net -1 after big left", g.langNet(), -1);
        eq("lang swipe = no space", g.up(), false);
        g.down(100f);
        eq("net resets on down", g.langNet(), 0);
        eq("tap again = space", g.up(), true);
        // small step size (32 dp) moves sooner than large (60 dp)
        SpaceGesture sm = new SpaceGesture(16f, 32f), lg = new SpaceGesture(16f, 60f);
        sm.down(100f); lg.down(100f); sm.arm(); lg.arm();
        eq("small: 40px = 1 step", sm.move(140f), 1);
        eq("large: 40px = 0 steps", lg.move(140f), 0);
        eq("large: 65px = 1 step", lg.move(165f), 1);
        // cursor drag never counts as language
        g = new SpaceGesture(10f, 40f);
        g.down(100f);
        g.move(160f);
        eq("cursor drag net 0", g.langNet(), 0);
        System.out.println("SpaceGestureTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
