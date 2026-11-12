package com.mgb.keyboard.typing;

/**
 * v0.16.8 (plan X, step X8): the numbers of the new panel kit (PanelKit.kt): how many tile columns fit, how tall a tile is,
 * how many rows a grid needs. Pure Java, no Android (tools/PanelLayoutTest.java). Nothing here is stored. Nothing uses it yet.
 */
public final class PanelLayout {
    private PanelLayout() { }

    public static final int MIN_TOUCH = 40;      // dp, smallest tappable control in the kit (PanelSpec.H_BTN)
    public static final int H_TILE = 48;         // dp, tile with a label only
    public static final int H_TILE_SUB = 58;     // dp, tile with a label + a small second line
    public static final int MIN_COLS = 2, MAX_COLS = 5;
    public static final int MIN_TILE_W = 96;     // dp, a tile is never narrower than this

    /** Tile columns for a panel that is [widthDp] wide: 2 (narrow) .. 5 (tablet). Bad width (<= 0) = 3. */
    public static int columns(int widthDp) {
        if (widthDp <= 0) return 3;
        int c = widthDp / MIN_TILE_W;
        return c < MIN_COLS ? MIN_COLS : (c > MAX_COLS ? MAX_COLS : c);
    }

    /** Tile height in dp; never below the touch minimum. */
    public static int tileHeightDp(boolean hasSubtitle) {
        int h = hasSubtitle ? H_TILE_SUB : H_TILE;
        return h < MIN_TOUCH ? MIN_TOUCH : h;
    }

    /** Rows needed for [count] tiles in [columns] columns (0 tiles = 0 rows). Bad columns = 1. */
    public static int rowsFor(int count, int columns) {
        if (count <= 0) return 0;
        int c = columns < 1 ? 1 : columns;
        return (count + c - 1) / c;
    }

    /** Empty cells in the last row (they are filled with spacers so tiles keep the same width). */
    public static int emptyCells(int count, int columns) {
        if (count <= 0) return 0;
        int c = columns < 1 ? 1 : columns;
        return rowsFor(count, c) * c - count;
    }

    /** Height of a whole grid in dp: rows x (tile + 2 x margin). */
    public static int gridHeightDp(int count, int columns, boolean hasSubtitle, int marginDp) {
        int m = marginDp < 0 ? 0 : marginDp;
        return rowsFor(count, columns) * (tileHeightDp(hasSubtitle) + 2 * m);
    }

    /** Width of one segmented-control option in dp (all options equal); bad count = whole width. */
    public static int segmentWidthDp(int widthDp, int options) {
        if (options < 1 || widthDp <= 0) return Math.max(widthDp, 0);
        return widthDp / options;
    }

    /** True when [options] segments of the given width are all at least MIN_TOUCH wide (else use chips that scroll). */
    public static boolean segmentsFit(int widthDp, int options) { return options >= 1 && segmentWidthDp(widthDp, options) >= MIN_TOUCH; }
}
