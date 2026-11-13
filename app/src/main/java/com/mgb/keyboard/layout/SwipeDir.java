package com.mgb.keyboard.layout;

/**
 * v0.17.7 (K5b) swipe direction of a finger move on a custom key. Pure Java (no Android), tested by tools/SwipeDirTest.java.
 * Direction ids = the index in KeyDef.sw (up, down, left, right), so sw[dir] is the text / @action of that swipe.
 * A short tap never counts: the finger must travel at least MIN_DP and one axis must clearly dominate (RATIO), else NONE (diagonal = no swipe).
 */
public final class SwipeDir {
    private SwipeDir() { }

    public static final int NONE = -1, UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;
    public static final float MIN_DP = 22f, RATIO = 1.4f;

    /** Minimum travel in pixels for a screen density (bad density = 1). */
    public static float minPx(float density) {
        float d = (density > 0f && !Float.isNaN(density) && !Float.isInfinite(density)) ? density : 1f;
        return MIN_DP * d;
    }

    /** Direction of a move of (dx, dy) pixels; [minPx] <= 0 or NaN = MIN_DP at density 1. Screen axes: dy < 0 = UP. Never throws. */
    public static int classify(float dx, float dy, float minPx) {
        if (Float.isNaN(dx) || Float.isNaN(dy) || Float.isInfinite(dx) || Float.isInfinite(dy)) return NONE;
        float m = (minPx > 0f && !Float.isNaN(minPx) && !Float.isInfinite(minPx)) ? minPx : MIN_DP;
        float ax = Math.abs(dx), ay = Math.abs(dy);
        if (Math.max(ax, ay) < m) return NONE;
        if (ax >= ay * RATIO) return dx < 0f ? LEFT : RIGHT;
        if (ay >= ax * RATIO) return dy < 0f ? UP : DOWN;
        return NONE;
    }

    /** Index into KeyDef.sw, or -1. */
    public static int index(int dir) { return (dir >= UP && dir <= RIGHT) ? dir : -1; }

    /** Swipe text / @action of key [k] for [dir]; "" = none. */
    public static String textFor(KeyDef k, int dir) {
        int i = index(dir);
        if (k == null || k.sw == null || i < 0 || i >= k.sw.length || k.sw[i] == null) return "";
        return k.sw[i];
    }

    /** True when the key has at least one swipe text (the renderer tracks the move only then). */
    public static boolean hasAny(KeyDef k) {
        if (k == null || k.sw == null) return false;
        for (String s : k.sw) if (s != null && s.length() > 0) return true;
        return false;
    }
}
