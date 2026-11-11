import com.mgb.keyboard.typing.HeightLock;

public class HeightLockTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    static void t(String n, boolean c) { eq(n, c, true); }
    public static void main(String[] a) {
        // constants = android.view.View values
        eq("VISIBLE = 0", HeightLock.VISIBLE, 0);
        eq("INVISIBLE = 4", HeightLock.INVISIBLE, 4);
        eq("GONE = 8", HeightLock.GONE, 8);
        // all 8 combinations (lock, probhatOn, avail)
        eq("L1 P1 A1", HeightLock.mode(true, true, true), HeightLock.VISIBLE);
        eq("L1 P1 A0", HeightLock.mode(true, true, false), HeightLock.VISIBLE);
        eq("L0 P1 A1", HeightLock.mode(false, true, true), HeightLock.VISIBLE);
        eq("L0 P1 A0", HeightLock.mode(false, true, false), HeightLock.VISIBLE);
        eq("L1 P0 A1 = keep space", HeightLock.mode(true, false, true), HeightLock.INVISIBLE);
        eq("L1 P0 A0 = nobody can switch", HeightLock.mode(true, false, false), HeightLock.GONE);
        eq("L0 P0 A1 = v0.9.5", HeightLock.mode(false, false, true), HeightLock.GONE);
        eq("L0 P0 A0 = v0.9.5", HeightLock.mode(false, false, false), HeightLock.GONE);
        // lock OFF == old rule (VISIBLE iff probhatOn else GONE) for every input
        boolean same = true;
        for (int p = 0; p < 2; p++) for (int av = 0; av < 2; av++) {
            int m = HeightLock.mode(false, p == 1, av == 1);
            if (m != (p == 1 ? HeightLock.VISIBLE : HeightLock.GONE)) same = false;
        }
        t("lock OFF = old behaviour for all inputs", same);
        // takesSpace
        t("visible takes space", HeightLock.takesSpace(HeightLock.VISIBLE));
        t("invisible takes space", HeightLock.takesSpace(HeightLock.INVISIBLE));
        t("gone no space", !HeightLock.takesSpace(HeightLock.GONE));
        t("junk value no space", !HeightLock.takesSpace(99));
        // jumps: English <-> Avro <-> Probhat swipe
        t("lock ON: English > Probhat no jump", !HeightLock.jumps(true, true, false, true));
        t("lock ON: Probhat > English no jump", !HeightLock.jumps(true, true, true, false));
        t("lock ON: English > Avro no jump", !HeightLock.jumps(true, true, false, false));
        t("lock ON: Probhat > Probhat no jump", !HeightLock.jumps(true, true, true, true));
        t("lock OFF: English > Probhat JUMPS (old bug)", HeightLock.jumps(false, true, false, true));
        t("lock OFF: Probhat > English JUMPS (old bug)", HeightLock.jumps(false, true, true, false));
        t("lock OFF: English > Avro no jump", !HeightLock.jumps(false, true, false, false));
        t("lock ON, Probhat not available: no row, no jump", !HeightLock.jumps(true, false, false, false));
        // full swipe cycle English > Avro > Probhat > English with lock ON: height never changes
        boolean[] on = { false, false, true, false };   // probhatOn per step
        boolean allSame = true;
        for (int i = 1; i < on.length; i++) if (HeightLock.jumps(true, true, on[i - 1], on[i])) allSame = false;
        t("lock ON: whole swipe cycle keeps the height", allSame);
        // same cycle with lock OFF jumps at least twice
        int j = 0;
        for (int i = 1; i < on.length; i++) if (HeightLock.jumps(false, true, on[i - 1], on[i])) j++;
        eq("lock OFF: cycle jumps twice (in + out)", j, 2);
        System.out.println("HeightLockTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
