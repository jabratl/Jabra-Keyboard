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
    /** v0.9.11: one swipe changes the language by at most this many steps (user request: max 1 language per swipe). */
    public static final int LANG_MAX = 1;

    private final SpaceDrag cursor;
    private final SpaceDrag lang;
    private final float langStepPx;   // v0.9.11
    private float armX;               // v0.9.11
    private boolean armed;
    private boolean langMoved;
    private int langNet;   // v0.9.8: sum of language steps of this touch (+ = next)
    private float lastX;
    private int kind = NONE;

    public SpaceGesture(float cursorStepPx, float langStepPx) {
        cursor = new SpaceDrag(cursorStepPx);
        lang = new SpaceDrag(langStepPx);
        this.langStepPx = langStepPx > 0f ? langStepPx : 1f;
    }

    public void down(float x) {
        cursor.down(x);
        lang.down(x);
        armed = false;
        langMoved = false;
        langNet = 0;
        lastX = x;
        kind = NONE;
    }

    /** Steps for this move (negative = left). Read kind() to know if they are cursor or language steps. */
    public int move(float x) {
        lastX = x;
        int n;
        if (armed) {
            // v0.9.11: language = ABSOLUTE position since arm (not a step counter): net is clamped to -LANG_MAX..+LANG_MAX,
            // so a long swipe never goes past 1 language and coming back to the start point gives the start language again.
            int total = (int) ((x - armX) / langStepPx);
            total = Math.max(-LANG_MAX, Math.min(LANG_MAX, total));
            n = total - langNet;
            if (n != 0) { langMoved = true; langNet += n; kind = LANG; } else kind = NONE;
        } else {
            n = cursor.move(x);
            kind = n != 0 ? CURSOR : NONE;
        }
        return n;
    }

    public int kind() { return kind; }

    /** v0.9.8: total language steps since down() (+ = next, - = previous). */
    public int langNet() { return langNet; }

    /** Called by the 0.2 s timer. true = now armed (only when the finger has not started a cursor drag). */
    public boolean arm() {
        if (armed || cursor.wasDragged()) return false;
        armed = true;
        lang.down(lastX);
        armX = lastX;
        return true;
    }

    public boolean armed() { return armed; }

    /** true = type a space (plain tap, or armed without a swipe). */
    public boolean up() { return !cursor.wasDragged() && !langMoved; }
}
