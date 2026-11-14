package com.mgb.keyboard.typing;

import java.util.List;

/**
 * v0.17.85 (U9): pure rules for the Look page theme cards (mini keyboard tiles) and the custom-theme warning text. No Android classes.
 * Tile = 2 mini key rows + name (ThemeTile.rows = MINI_ROWS), TILE_DP high (>= 48 dp touch). Columns follow the width the grid really has.
 */
public final class ThemeCardPlan {
    private ThemeCardPlan() { }

    public static final int MINI_ROWS = 2, TILE_DP = 76, MIN_COLS = 2, MAX_COLS = 4;

    /** Columns for the free width in dp (grid width, not the screen): < 240 = 2, < 480 = 3, else 4. */
    public static int cols(int availDp) {
        if (availDp < 240) return MIN_COLS;
        if (availDp < 480) return 3;
        return MAX_COLS;
    }

    /** Free width of the grid: the right pane on two panes, else the screen, minus the page padding. Never below 0. */
    public static int availDp(int screenDp, boolean twoPane, int leftPaneDp, int padDp) {
        int w = twoPane ? screenDp - leftPaneDp : screenDp;
        return Math.max(0, w - Math.max(0, padDp));
    }

    /** Screen-reader text of one tile. */
    public static String describe(String name, boolean chosen) {
        String n = (name == null || name.trim().isEmpty()) ? "Theme" : "Theme " + name.trim();
        return chosen ? n + ", selected" : n;
    }

    /** Line above the grid: "Current theme: Name (3 of 20)". Bad index = no number. */
    public static String currentLine(String name, int index, int total) {
        String n = (name == null || name.trim().isEmpty()) ? "Custom" : name.trim();
        if (index < 0 || total <= 0 || index >= total) return "Current theme: " + n;
        return "Current theme: " + n + " (" + (index + 1) + " of " + total + ")";
    }

    /** Warning box text of the custom theme editor. invalid = a colour is not valid (nothing can be saved). Empty = no box. */
    public static String warnText(boolean invalid, List<String> warnings) {
        if (invalid) return "Some colours are not valid (use 6 digits like #1B3A57). Nothing is saved until they are fixed.";
        if (warnings == null || warnings.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String w : warnings) { if (sb.length() > 0) sb.append('\n'); sb.append("\u26A0 ").append(w); }
        return sb.toString();
    }
}
