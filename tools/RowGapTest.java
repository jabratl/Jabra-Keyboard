import com.mgb.keyboard.typing.RowGap;

public class RowGapTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    public static void main(String[] a) {
        eq("english on = half key", RowGap.side(true, 9), 0.5f);
        eq("english off = none", RowGap.side(false, 9), 0f);
        eq("probhat on = none (10 keys fill the row)", RowGap.side(true, 10), 0f);
        eq("probhat off = none", RowGap.side(false, 10), 0f);
        eq("more than full = none", RowGap.side(true, 12), 0f);
        eq("zero keys = none", RowGap.side(true, 0), 0f);
        eq("negative keys = none", RowGap.side(true, -3), 0f);
        eq("8 keys = 1 key each side", RowGap.side(true, 8), 1f);
        eq("1 key = capped at 1.5", RowGap.side(true, 1), 1.5f);
        eq("homeKeys english", RowGap.homeKeys(false), 9);
        eq("homeKeys probhat", RowGap.homeKeys(true), 10);
        // home row width == top row width: 9 keys + 2 * 0.5 = 10 units = the 10 keys of q..p
        eq("units equal top row", 9 + 2 * RowGap.side(true, 9), 10f);
        eq("side percent english", RowGap.sidePercent(true, 9), 5);
        eq("side percent off", RowGap.sidePercent(false, 9), 0);
        eq("side percent probhat", RowGap.sidePercent(true, 10), 0);
        System.out.println("RowGapTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
