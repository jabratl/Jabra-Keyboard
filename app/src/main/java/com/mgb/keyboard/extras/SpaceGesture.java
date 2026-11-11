package com.mgb.keyboard.extras;

/**
 * v0.8.9 space key gesture (pure Java, tested by tools/SpaceGestureTest.java). Built on SpaceDrag.
 * - Drag at once = cursor steps (same as before).
 * - Finger stays on the key (not dragged) until arm() is called (the caller's 0.2 s timer) = ARMED: from then on a horizontal
 *   drag gives LANGUAGE steps (bigger step size) and the cursor does not move.
 * - up() says whether a space must be typed: yes for a plain tap and for "armed but not swiped"; no after a cursor drag or a language swipe.
 */
public final class SpaceGesture {
    public static final int NONE = 0, CURSOR = 1, LANG = 2;

    private final SpaceDrag cursor;
    private final SpaceDrag lang;
    private boolean armed;
    private boolean langMoved;
    private float lastX;
    private int kind = NONE;

    public SpaceGesture(float cursorStepPx, float langStepPx) {
        cursor = new SpaceDrag(cursorStepPx);
        lang = new SpaceDrag(langStepPx);
    }

    public void down(float x) {
        cursor.down(x);
        lang.down(x);
        armed = false;
        langMoved = false;
        lastX = x;
        kind = NONE;
    }

    /** Steps for this move (negative = left). Read kind() to know if they are cursor or language steps. */
    public int move(float x) {
        lastX = x;
        int n;
        if (armed) {
            n = lang.move(x);
            if (n != 0) { langMoved = true; kind = LANG; } else kind = NONE;
        } else {
            n = cursor.move(x);
            kind = n != 0 ? CURSOR : NONE;
        }
        return n;
    }

    public int kind() { return kind; }

    /** Called by the 0.2 s timer. true = now armed (only when the finger has not started a cursor drag). */
    public boolean arm() {
        if (armed || cursor.wasDragged()) return false;
        armed = true;
        lang.down(lastX);
        return true;
    }

    public boolean armed() { return armed; }

    /** true = type a space (plain tap, or armed without a swipe). */
    public boolean up() { return !cursor.wasDragged() && !langMoved; }
}
