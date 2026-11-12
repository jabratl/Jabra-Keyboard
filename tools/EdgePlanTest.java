import com.mgb.keyboard.typing.EdgePlan;

/** Run: javac -d out app/src/main/java/com/mgb/keyboard/typing/EdgePlan.java tools/EdgePlanTest.java && java -cp out EdgePlanTest */
public class EdgePlanTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (a.equals(b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }
    public static void main(String[] x) {
        eq("edge unset", EdgePlan.edgeDp(-1), 2);
        eq("edge 0", EdgePlan.edgeDp(0), 0);
        eq("edge 8", EdgePlan.edgeDp(8), 8);
        eq("edge 9 invalid", EdgePlan.edgeDp(9), 2);
        eq("v unset follows gap 3", EdgePlan.vGapDp(-1, 3), 3);
        eq("v 0", EdgePlan.vGapDp(0, 3), 0);
        eq("v 5", EdgePlan.vGapDp(5, 2), 5);
        eq("v 99 invalid", EdgePlan.vGapDp(99, 4), 4);
        eq("fn unset", EdgePlan.fnGapDp(-1), 0);
        eq("fn 4", EdgePlan.fnGapDp(4), 4);
        eq("fn 20 invalid", EdgePlan.fnGapDp(20), 0);
        eq("weight 0 = old", EdgePlan.fnWeight(0, 360f, 2), 1.5f);
        eq("weight unset-ish", EdgePlan.fnWeight(-3, 360f, 2), 1.5f);
        float w4 = EdgePlan.fnWeight(4, 360f, 1);
        eq("weight 4dp lowered", w4 < 1.5f && w4 > 1.3f, true);
        eq("weight 8dp bigger drop", EdgePlan.fnWeight(8, 360f, 1) < w4, true);
        eq("weight tiny screen", EdgePlan.fnWeight(8, 90f, 0), 1.5f);   // unit < 10 -> old weight
        eq("weight clamp min", EdgePlan.fnWeight(8, 120f, 0), EdgePlan.FN_MIN);
        eq("weight floor 2", EdgePlan.fnWeight(8, 150f, 0) >= EdgePlan.FN_MIN, true);
        eq("preset sizes", EdgePlan.PRESET_KEYS.length, EdgePlan.RIDMIK.length);
        eq("preset values valid", EdgePlan.edgeValid(EdgePlan.RIDMIK[3]) && EdgePlan.vValid(EdgePlan.RIDMIK[4]) && EdgePlan.fnValid(EdgePlan.RIDMIK[5]), true);
        int[] a = { 120, -1, 2, 1, -1, 4 };
        eq("encode", EdgePlan.encode(a), "120,-1,2,1,-1,4");
        eq("roundtrip", java.util.Arrays.equals(EdgePlan.decode(EdgePlan.encode(a), 6), a), true);
        int[] pad = EdgePlan.decodeUpTo("120,-1,2,1,-1,4", 9);
        eq("padded len", pad.length, 9);
        eq("padded values", pad[5] == 4 && pad[6] == -1 && pad[8] == -1, true);
        eq("padded too long", EdgePlan.decodeUpTo("1,2,3,4,5,6,7,8,9,10", 9) == null, true);
        eq("padded junk", EdgePlan.decodeUpTo("1,x", 9) == null, true);
        eq("padded empty", EdgePlan.decodeUpTo("", 9) == null, true);
        eq("bool from", EdgePlan.BOOL_FROM, 7);
        eq("decode empty", EdgePlan.decode("", 6) == null, true);
        eq("decode null", EdgePlan.decode(null, 6) == null, true);
        eq("decode wrong count", EdgePlan.decode("1,2", 6) == null, true);
        eq("decode junk", EdgePlan.decode("1,2,x,4,5,6", 6) == null, true);
        System.out.println("EdgePlanTest " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }
}
