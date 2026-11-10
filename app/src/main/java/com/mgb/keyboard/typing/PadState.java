package com.mgb.keyboard.typing;

/**
 * v0.2.3 Cursor / select pad state. Pure Java (no Android classes) so it can be tested on a computer.
 * Select ON = arrows / Home / End are sent WITH the Shift meta bit, so they extend the selection.
 */
public final class PadState {

    /** Same value as android.view.KeyEvent.META_SHIFT_ON. */
    public static final int META_SHIFT = 0x1;

    private boolean select = false;

    public boolean isSelect() { return select; }

    public void setSelect(boolean v) { select = v; }

    /** Flips Select and returns the new value. */
    public boolean toggle() { select = !select; return select; }

    /** Meta bits for the next movement key. */
    public int meta() { return select ? META_SHIFT : 0; }
}
