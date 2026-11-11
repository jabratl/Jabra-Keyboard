import com.mgb.keyboard.typing.BubblePlan;

public class BubblePlanTest {
    static int pass = 0, fail = 0;
    static void t(String n, boolean ok) { if (ok) pass++; else { fail++; System.out.println("FAIL: " + n); } }

    public static void main(String[] a) {
        int gap = 12, h = 195;   // ~4 dp gap, ~65 dp bubble at density 3
        // mode
        t("overlay when fits", BubblePlan.mode(true, true, 400, h, gap) == BubblePlan.OVERLAY);
        t("exactly fits = overlay", BubblePlan.mode(true, true, h + gap, h, gap) == BubblePlan.OVERLAY);
        t("one px short = window", BubblePlan.mode(true, true, h + gap - 1, h, gap) == BubblePlan.WINDOW);
        t("top row no room = window", BubblePlan.mode(true, true, 20, h, gap) == BubblePlan.WINDOW);
        t("top 0 = window", BubblePlan.mode(true, true, 0, h, gap) == BubblePlan.WINDOW);
        t("pref OFF = window", BubblePlan.mode(false, true, 400, h, gap) == BubblePlan.WINDOW);
        t("no host = window", BubblePlan.mode(true, false, 400, h, gap) == BubblePlan.WINDOW);
        t("both off = window", BubblePlan.mode(false, false, 400, h, gap) == BubblePlan.WINDOW);
        t("negative top = window", BubblePlan.mode(true, true, -5, h, gap) == BubblePlan.WINDOW);
        t("zero height = window", BubblePlan.mode(true, true, 400, 0, gap) == BubblePlan.WINDOW);
        t("negative height = window", BubblePlan.mode(true, true, 400, -3, gap) == BubblePlan.WINDOW);
        t("gap 0 fits", BubblePlan.mode(true, true, h, h, 0) == BubblePlan.OVERLAY);
        t("huge key top ok", BubblePlan.mode(true, true, 100000, h, gap) == BubblePlan.OVERLAY);
        // row simulation (density 3, key 144 px, margins 12, toolbar 120, padding 12)
        int tb = 120, pad = 12, rowH = 168;
        int row1 = pad + tb + 6, row2 = row1 + rowH, row3 = row2 + rowH, row4 = row3 + rowH;
        int bh = Math.round(144 * 1.35f);
        t("row1 with toolbar: fits? " + row1 + " vs " + (bh + gap), (BubblePlan.mode(true, true, row1, bh, gap) == BubblePlan.OVERLAY) == (row1 - bh - gap >= 0));
        t("row2 overlay", BubblePlan.mode(true, true, row2, bh, gap) == BubblePlan.OVERLAY);
        t("row3 overlay", BubblePlan.mode(true, true, row3, bh, gap) == BubblePlan.OVERLAY);
        t("row4 overlay", BubblePlan.mode(true, true, row4, bh, gap) == BubblePlan.OVERLAY);
        int noTb1 = pad + 6, noTb2 = noTb1 + rowH;
        t("no toolbar row1 = window", BubblePlan.mode(true, true, noTb1, bh, gap) == BubblePlan.WINDOW);
        t("no toolbar row2 short = window", BubblePlan.mode(true, true, noTb2, bh, gap) == (noTb2 - bh - gap >= 0 ? BubblePlan.OVERLAY : BubblePlan.WINDOW));
        // x clamp
        t("x centred", BubblePlan.x(500, 100, 130, 1080) == 485);
        t("x clamp left (q)", BubblePlan.x(0, 100, 130, 1080) == 0);
        t("x clamp right (p)", BubblePlan.x(980, 100, 130, 1080) == 950);
        t("x host narrower than bubble", BubblePlan.x(10, 50, 200, 100) == 0);
        t("x negative key left", BubblePlan.x(-40, 100, 130, 1080) == 0);
        t("x exact right edge", BubblePlan.x(1080 - 100, 100, 100, 1080) == 980);
        // y
        t("y above key", BubblePlan.y(400, h, gap) == 400 - h - gap);
        t("y zero when exact", BubblePlan.y(h + gap, h, gap) == 0);
        t("y negative not clamped", BubblePlan.y(10, h, gap) < 0);
        // sum
        t("sum basic", BubblePlan.sum(new int[] { 10, 20, 5 }) == 35);
        t("sum null", BubblePlan.sum(null) == 0);
        t("sum empty", BubblePlan.sum(new int[0]) == 0);
        t("sum negative", BubblePlan.sum(new int[] { 10, -4 }) == 6);
        // rectValid
        int[] r = { 10, 20, 100, 144 };
        t("valid same size", BubblePlan.rectValid(r, 100, 144));
        t("invalid width changed", !BubblePlan.rectValid(r, 101, 144));
        t("invalid height changed", !BubblePlan.rectValid(r, 100, 143));
        t("invalid null", !BubblePlan.rectValid(null, 100, 144));
        t("invalid zero size", !BubblePlan.rectValid(new int[] { 0, 0, 0, 0 }, 0, 0));
        t("invalid wrong length", !BubblePlan.rectValid(new int[] { 1, 2, 3 }, 3, 3));
        // cache
        BubblePlan.RectCache<Object> c = new BubblePlan.RectCache<Object>();
        Object k1 = new Object(), k2 = new Object();
        t("cache empty", c.size() == 0 && c.get(k1) == null);
        c.put(k1, 1, 2, 3, 4);
        t("cache put/get", c.get(k1) != null && c.get(k1)[0] == 1 && c.get(k1)[3] == 4 && c.size() == 1);
        t("cache other key null", c.get(k2) == null);
        c.put(k1, 9, 9, 9, 9);
        t("cache overwrite", c.size() == 1 && c.get(k1)[0] == 9);
        c.put(k2, 5, 6, 7, 8);
        t("cache two keys", c.size() == 2);
        t("cache identity (equal strings differ)", new BubblePlan.RectCache<String>().get(new String("a")) == null);
        c.clear();
        t("cache clear", c.size() == 0 && c.get(k1) == null);
        c.put(null, 1, 1, 1, 1);
        t("cache null key ignored", c.size() == 0 && c.get(null) == null);
        System.out.println("BubblePlanTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
