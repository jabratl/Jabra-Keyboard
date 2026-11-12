import com.mgb.keyboard.typing.PanelLayout;

/** v0.16.8 (X8): numbers of the new panel kit. */
public class PanelLayoutTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + name); } }

    public static void main(String[] a) {
        t("columns 320 = 3", PanelLayout.columns(320) == 3);
        t("columns 360 = 3", PanelLayout.columns(360) == 3);
        t("columns 411 = 4", PanelLayout.columns(411) == 4);
        t("columns 200 = 2 (min)", PanelLayout.columns(200) == 2);
        t("columns 100 = 2 (min)", PanelLayout.columns(100) == 2);
        t("columns 600 = 5 (max)", PanelLayout.columns(600) == 5);
        t("columns 2000 = 5 (max)", PanelLayout.columns(2000) == 5);
        t("columns 0 / negative = 3", PanelLayout.columns(0) == 3 && PanelLayout.columns(-5) == 3);
        int prev = 0; boolean mono = true, range = true;
        for (int w = 1; w < 1500; w++) { int c = PanelLayout.columns(w); if (c < prev) mono = false; if (c < 2 || c > 5) range = false; prev = c; }
        t("columns never shrink when wider", mono);
        t("columns always 2..5", range);
        boolean wide = true;
        for (int w = 192; w < 1500; w++) if (w / PanelLayout.columns(w) < 96) wide = false;
        t("tile never narrower than 96 dp (width >= 192)", wide);
        t("tile height label", PanelLayout.tileHeightDp(false) == 48);
        t("tile height sub", PanelLayout.tileHeightDp(true) == 58);
        t("tile height >= touch", PanelLayout.tileHeightDp(false) >= PanelLayout.MIN_TOUCH && PanelLayout.tileHeightDp(true) >= PanelLayout.MIN_TOUCH);
        t("touch minimum 40", PanelLayout.MIN_TOUCH == 40);
        t("rows 0", PanelLayout.rowsFor(0, 3) == 0 && PanelLayout.rowsFor(-4, 3) == 0);
        t("rows 1", PanelLayout.rowsFor(1, 3) == 1);
        t("rows exact", PanelLayout.rowsFor(6, 3) == 2);
        t("rows round up", PanelLayout.rowsFor(7, 3) == 3);
        t("rows 13 in 4", PanelLayout.rowsFor(13, 4) == 4);
        t("rows bad columns = 1 col", PanelLayout.rowsFor(5, 0) == 5 && PanelLayout.rowsFor(5, -2) == 5);
        t("empty cells", PanelLayout.emptyCells(7, 3) == 2 && PanelLayout.emptyCells(6, 3) == 0 && PanelLayout.emptyCells(1, 4) == 3 && PanelLayout.emptyCells(0, 3) == 0);
        boolean cells = true;
        for (int n = 0; n < 40; n++) for (int c = 1; c <= 5; c++) if (PanelLayout.rowsFor(n, c) * c != n + PanelLayout.emptyCells(n, c)) cells = false;
        t("rows x columns = tiles + empty cells", cells);
        t("grid height", PanelLayout.gridHeightDp(7, 3, false, 2) == 3 * (48 + 4));
        t("grid height sub", PanelLayout.gridHeightDp(4, 2, true, 2) == 2 * (58 + 4));
        t("grid height empty / bad margin", PanelLayout.gridHeightDp(0, 3, false, 2) == 0 && PanelLayout.gridHeightDp(3, 3, false, -9) == 48);
        t("segment width", PanelLayout.segmentWidthDp(360, 3) == 120 && PanelLayout.segmentWidthDp(360, 0) == 360 && PanelLayout.segmentWidthDp(-1, 3) == 0);
        t("segments fit", PanelLayout.segmentsFit(360, 4) && PanelLayout.segmentsFit(360, 9) && !PanelLayout.segmentsFit(360, 10) && !PanelLayout.segmentsFit(360, 0));
        t("style sanitize", PanelLayout.sanitizeStyle(0) == 0 && PanelLayout.sanitizeStyle(1) == 1 && PanelLayout.sanitizeStyle(2) == 0 && PanelLayout.sanitizeStyle(-1) == 0 && PanelLayout.sanitizeStyle(Integer.MAX_VALUE) == 0);   // v0.16.9
        t("style isNew", PanelLayout.isNewStyle(1) && !PanelLayout.isNewStyle(0) && !PanelLayout.isNewStyle(7) && !PanelLayout.isNewStyle(-1));
        t("style names", PanelLayout.STYLE_NAMES.length == 2 && PanelLayout.STYLE_NAMES[0].equals("Classic") && PanelLayout.STYLE_NAMES[1].equals("New"));
        System.out.println("PanelLayoutTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
