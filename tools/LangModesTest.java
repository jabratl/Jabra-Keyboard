import com.mgb.keyboard.typing.LangModes;
import com.mgb.keyboard.typing.LangOrder;
import com.mgb.keyboard.typing.LangSwipe;
import java.util.Arrays;

/** javac -encoding UTF-8 -d out $(find app -name '*.java') tools/LangModesTest.java && java -Dfile.encoding=UTF-8 -cp out LangModesTest */
public class LangModesTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL " + m); } }
    static boolean eq(int[] a, int... b) { return Arrays.equals(a, b); }
    static final int EN = 0, AV = 1, PB = 2;

    public static void main(String[] x) {
        ok(!LangModes.custom(true, 0), "default = old code");
        ok(LangModes.custom(false, 0) && LangModes.custom(true, 3), "english off / order = new code");
        ok(!LangModes.custom(true, 99) && !LangModes.custom(true, -1), "bad order = default");
        ok(LangModes.orderNames().length == 6, "6 orders");
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (String n : LangModes.orderNames()) { ok(n.startsWith("EN") || n.contains(">"), "name " + n); seen.add(n); }
        ok(seen.size() == 6, "all order names different");

        // EQUIVALENCE: order 0 + English on must give the SAME result as the old LangSwipe.step and LangOrder.tap/default for every state
        for (int b = 0; b < 2; b++) for (int p = 0; p < 2; p++) for (int a = 0; a < 2; a++) for (int pp = 0; pp < 2; pp++) for (int s = -3; s <= 3; s++) {
            int[] o = LangSwipe.step(b == 1, p == 1, a == 1, pp == 1, s);
            int[] n = LangModes.step(b == 1, p == 1, 0, true, a == 1, pp == 1, s);
            // old code treats a hidden current Bangla layout as EN; compare only when the current mode is switched on or EN
            boolean curOn = b == 0 || (p == 1 ? pp == 1 : a == 1);
            if (curOn) ok(Arrays.equals(o, n), "step same as LangSwipe b" + b + " p" + p + " a" + a + " pp" + pp + " s" + s);
        }
        for (int b = 0; b < 2; b++) for (int p = 0; p < 2; p++) for (int a = 0; a < 2; a++) for (int pp = 0; pp < 2; pp++) for (int f = 0; f < 3; f++) {
            if (a + pp == 0) continue;   // both Bangla off: old code stays EN, same here
            int[] o = LangOrder.tap(b == 1, p == 1, a == 1, pp == 1, 0, f);
            int[] n = LangModes.tap(b == 1, p == 1, 0, true, a == 1, pp == 1, 0, f);
            boolean curOn = b == 0 || (p == 1 ? pp == 1 : a == 1);
            if (curOn) ok(Arrays.equals(o, n), "tap same as LangOrder b" + b + " p" + p + " a" + a + " pp" + pp + " f" + f);
        }

        // cycle
        ok(Arrays.equals(LangModes.cycle(0, true, true, true), new int[] {EN, AV, PB}), "cycle 0");
        ok(Arrays.equals(LangModes.cycle(3, true, true, true), new int[] {AV, PB, EN}), "cycle 3 = AV PB EN");
        ok(Arrays.equals(LangModes.cycle(5, true, true, true), new int[] {PB, AV, EN}), "cycle 5");
        ok(Arrays.equals(LangModes.cycle(3, false, true, true), new int[] {AV, PB}), "english off");
        ok(Arrays.equals(LangModes.cycle(5, true, false, true), new int[] {PB, EN}), "avro off");
        ok(Arrays.equals(LangModes.cycle(0, false, false, false), new int[] {EN}), "all off = EN");
        ok(Arrays.equals(LangModes.cycle(0, false, false, true), new int[] {PB}), "only PB");

        // swipe with a manual order: order 3 = AV > PB > EN
        ok(eq(LangModes.step(false, false, 3, true, true, true, 1), 1, 0), "EN +1 wraps to AV");
        ok(eq(LangModes.step(true, false, 3, true, true, true, 1), 1, 1), "AV +1 = PB");
        ok(eq(LangModes.step(true, true, 3, true, true, true, 1), 0, 1), "PB +1 = EN (keeps probhat flag)");
        ok(eq(LangModes.step(true, false, 3, true, true, true, -1), 0, 0), "AV -1 wraps to EN (flag 0)");
        ok(eq(LangModes.step(true, true, 3, true, true, true, 0), 1, 1), "0 steps = same");
        ok(eq(LangModes.step(true, true, 3, true, true, true, 3), 1, 1), "3 steps round");
        // English OFF: from the hidden EN state
        ok(eq(LangModes.step(false, false, 0, false, true, true, 1), 1, 0), "EN off: +1 = first (AV)");
        ok(eq(LangModes.step(false, false, 0, false, true, true, -1), 1, 1), "EN off: -1 = last (PB)");
        ok(eq(LangModes.step(false, true, 0, false, true, true, 0), 0, 1), "EN off, 0 steps = unchanged");
        ok(eq(LangModes.step(true, false, 0, false, true, true, 1), 1, 1), "AV -> PB with EN off");
        ok(eq(LangModes.step(true, true, 0, false, true, true, 1), 1, 0), "PB -> AV wraps with EN off");
        ok(eq(LangModes.step(true, false, 0, false, true, false, 1), 1, 0), "only AV: stays");

        // normalize
        ok(eq(LangModes.normalize(false, true, 0, false, true, true), 1, 0), "EN off at start -> first on (AV)");
        ok(eq(LangModes.normalize(false, false, 5, false, true, true), 1, 1), "EN off order 5 -> PB first");
        ok(eq(LangModes.normalize(true, true, 0, true, true, false), 0, 1), "PB off at start -> EN (flag kept)");
        ok(eq(LangModes.normalize(true, false, 0, true, true, true), 1, 0), "on mode unchanged");
        ok(eq(LangModes.normalize(true, true, 0, false, false, false), 0, 1), "all off -> EN");

        // tap
        ok(eq(LangModes.tap(false, false, 0, false, true, true, 0, 0), 1, 0), "EN off: tap from hidden EN = first");
        ok(eq(LangModes.tap(true, false, 0, false, true, true, 0, 0), 1, 1), "EN off: tap toggles AV -> PB");
        ok(eq(LangModes.tap(true, true, 0, false, true, true, 0, 0), 1, 0), "EN off: tap PB -> AV");
        ok(eq(LangModes.tap(true, false, 3, true, true, true, 1, 0), 1, 1), "tap cycle follows order (AV -> PB)");
        ok(eq(LangModes.tap(true, true, 3, true, true, true, 2, 0), 0, 1), "tap cycle PB -> EN");
        ok(eq(LangModes.tap(true, false, 3, true, true, true, 0, 0), 0, 0), "order 3, tap 0: Bangla -> EN");
        ok(eq(LangModes.tap(false, true, 3, true, true, true, 0, 0), 1, 1), "order 3, tap 0: EN -> last used (PB)");
        ok(eq(LangModes.tap(false, true, 3, true, true, true, 0, 1), 1, 0), "fav Avro");
        ok(eq(LangModes.tap(false, false, 3, true, true, false, 0, 2), 1, 0), "fav Probhat but off -> Avro");
        ok(eq(LangModes.tap(false, false, 3, true, false, false, 0, 0), 0, 0), "only EN: stays");
        ok(eq(LangModes.tap(true, true, 0, false, false, true, 0, 0), 1, 1), "only PB: stays PB");

        // robustness: every combination returns a valid state in an ON mode (or EN when everything is off)
        for (int o = -1; o < 8; o++) for (int m = 0; m < 8; m++) for (int b = 0; b < 2; b++) for (int p = 0; p < 2; p++) for (int s = -4; s <= 4; s++) for (int t = 0; t < 3; t++) {
            boolean en = (m & 1) != 0, av = (m & 2) != 0, pb = (m & 4) != 0;
            int[] r = LangModes.step(b == 1, p == 1, o, en, av, pb, s);
            int[] q = LangModes.tap(b == 1, p == 1, o, en, av, pb, t, s & 1);
            for (int[] z : new int[][] { r, q }) {
                ok(z.length == 2 && (z[0] == 0 || z[0] == 1) && (z[1] == 0 || z[1] == 1), "valid state");
            }
            int[] nn = LangModes.normalize(b == 1, p == 1, o, en, av, pb);
            int[] cyc = LangModes.cycle(o, en, av, pb);
            boolean found = false; for (int c : cyc) if (c == LangModes.modeOf(nn[0] == 1, nn[1] == 1)) found = true;
            ok(found, "normalize lands inside the cycle");
        }
        System.out.println((fail == 0 ? "OK " : "FAILED ") + total + " checks");
        if (fail > 0) System.exit(1);
    }
}
