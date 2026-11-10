package com.mgb.keyboard.typing;

/**
 * v0.0.9 Shift / caps-lock / auto-capital state. Pure Java (no Android classes) so it can be tested on a computer.
 *
 * States: off | shift (next letter only) | caps (stays until tapped again).
 * "auto" = shift was switched on by the sentence-start rule, not by the user.
 *  - tap on Shift:   off -> shift;  shift -> caps (if 2nd tap comes within DOUBLE_MS) or off;  caps -> off
 *  - letter typed:   shift -> off (caps stays)
 *  - auto-capital:   only touches shift that it switched on itself; never touches caps or a manual shift
 */
public final class ShiftState {

    public static final long DOUBLE_MS = 350;

    private boolean shift = false;
    private boolean caps = false;
    private boolean auto = false;
    private long lastTap = -1000000L;

    /** True when letters should be upper-case (shift or caps). */
    public boolean isShift() { return shift; }
    public boolean isCaps() { return caps; }
    public boolean isAuto() { return auto; }

    /** Old-style setter used by existing code ("shift = false" after a letter). false also clears caps + auto. */
    public void setShift(boolean v) {
        shift = v;
        if (!v) { caps = false; auto = false; }
    }

    /** User tapped the Shift key. [now] = uptime in milliseconds. */
    public void tap(long now) {
        if (caps) {                       // caps lock -> off
            caps = false; shift = false; auto = false; lastTap = -1000000L;
            return;
        }
        if (shift && !auto && now - lastTap <= DOUBLE_MS) {   // second quick tap -> caps lock
            caps = true; shift = true; auto = false; lastTap = -1000000L;
            return;
        }
        if (shift) {                      // shift (or auto shift) -> off
            shift = false; auto = false; lastTap = -1000000L;
            return;
        }
        shift = true; auto = false; lastTap = now;            // off -> shift
    }

    /** A letter was typed: one-shot shift ends, caps lock stays. */
    public void typed() {
        if (caps) return;
        shift = false; auto = false;
    }

    /**
     * Sentence-start rule. [want] = the field wants a capital here. Returns true if the state changed
     * (caller should relabel the keys). Never touches caps lock or a shift the user pressed.
     */
    public boolean autoCap(boolean want) {
        if (caps) return false;
        if (want && !shift) { shift = true; auto = true; return true; }
        if (!want && shift && auto) { shift = false; auto = false; return true; }
        return false;
    }

    /** New text field: everything off. */
    public void reset() {
        shift = false; caps = false; auto = false; lastTap = -1000000L;
    }
}
