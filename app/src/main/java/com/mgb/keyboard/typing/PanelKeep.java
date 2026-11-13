package com.mgb.keyboard.typing;

/**
 * v0.17.35 (M2): pure rule "does an input RESTART close the panel the user opened?".
 * Many apps restart the input connection when the selection changes or the field is tapped:
 * onStartInputView(restarting = true) then ran showLetters() and the cursor pad (or clipboard) vanished.
 * keep = true: do NOT call showLetters() / showPanel("numpad") there. The panel closes only when the user closes it (ABC).
 * Not kept (old behaviour): a new field (restarting = false), letters on screen, the automatic number pad.
 */
public final class PanelKeep {
    private PanelKeep() { }

    public static boolean keepPanel(boolean restarting, boolean inPanel, boolean autoNumpad) {
        return restarting && inPanel && !autoNumpad;
    }

    /** The "suggestion row again" reset (sgTools) only rebuilds the LETTERS: it never closes a panel the user opened (restart or not). The row is back when ABC rebuilds the letters. */
    public static boolean keepForRowReset(boolean inPanel, boolean autoNumpad) {
        return inPanel && !autoNumpad;
    }
}
