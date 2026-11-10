package com.mgb.keyboard.typing;

/**
 * v0.0.9 Decides what the Enter key should do. Pure Java; the constants are copied from
 * android.view.inputmethod.EditorInfo so the logic can be tested on a computer.
 */
public final class EnterAction {

    private EnterAction() { }

    public static final int IME_MASK_ACTION = 0x000000ff;
    public static final int IME_ACTION_UNSPECIFIED = 0;
    public static final int IME_ACTION_NONE = 1;
    public static final int IME_FLAG_NO_ENTER_ACTION = 0x40000000;

    /**
     * Returns the editor action to perform (Go, Search, Send, Next, Done ...), or -1 when Enter should
     * just be a normal Enter key (new line). The app decides: multi-line boxes and "no action" boxes give -1.
     */
    public static int decide(int imeOptions) {
        if ((imeOptions & IME_FLAG_NO_ENTER_ACTION) != 0) return -1;
        int a = imeOptions & IME_MASK_ACTION;
        if (a == IME_ACTION_NONE || a == IME_ACTION_UNSPECIFIED) return -1;
        return a;
    }
}
