package com.mgb.keyboard.extras;

/**
 * v0.0.5: pure logic for "drag on the space key to move the cursor". No Android here (so it can be tested on a computer).
 * Usage: down(x) on finger down; move(x) on each move -> returns how many cursor steps to make
 * (negative = left, positive = right); wasDragged() on finger up (true = do NOT type a space).
 */
public final class SpaceDrag {
    private final float step;
    private float anchor;
    private boolean dragged;

    public SpaceDrag(float stepPx) {
        this.step = stepPx > 1f ? stepPx : 1f;
    }

    public void down(float x) {
        anchor = x;
        dragged = false;
    }

    public int move(float x) {
        int n = (int) ((x - anchor) / step);   // truncates toward zero
        if (n != 0) {
            anchor += n * step;
            dragged = true;
        }
        return n;
    }

    public boolean wasDragged() {
        return dragged;
    }
}
