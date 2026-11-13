package com.mgb.keyboard.typing;

/** v0.1.2 PC mode state (pure Java, no Android). Mode (Phone / PC) + sticky Ctrl / Alt (1 tap = once, 2nd tap quickly = lock, tap again = off). v0.17.49 (PC1): + Shift and Win (Meta) with the same rules, mods() mask, metaReal(), expireOnce(). */
public final class PcState {
    public static final int OFF = 0, ONCE = 1, LOCK = 2;
    public static final long DOUBLE_MS = 400;

    private boolean pc = false;
    private int ctrl = OFF, alt = OFF, shift = OFF, win = OFF;   // v0.17.49: shift, win
    private long ctrlAt = 0, altAt = 0, shiftAt = 0, winAt = 0;

    public boolean isPc() { return pc; }
    public void setPc(boolean v) { pc = v; if (!v) clearMods(); }
    /** Switch Phone <-> PC. Returns the new state (true = PC). Leaving PC clears Ctrl / Alt. */
    public boolean toggleMode() { setPc(!pc); return pc; }

    public int ctrl() { return ctrl; }
    public int alt() { return alt; }
    public int shift() { return shift; }   // v0.17.49 (PC1)
    public int win() { return win; }   // v0.17.49 (PC1)

    private static int next(int cur, long last, long now) {
        if (cur == OFF) return ONCE;
        if (cur == ONCE) return (last > 0 && now >= last && now - last <= DOUBLE_MS) ? LOCK : OFF;
        return OFF;
    }

    public void tapCtrl(long now) { int n = next(ctrl, ctrlAt, now); ctrl = n; ctrlAt = n == OFF ? 0 : now; }
    public void tapAlt(long now) { int n = next(alt, altAt, now); alt = n; altAt = n == OFF ? 0 : now; }
    public void tapShift(long now) { int n = next(shift, shiftAt, now); shift = n; shiftAt = n == OFF ? 0 : now; }   // v0.17.49 (PC1)
    public void tapWin(long now) { int n = next(win, winAt, now); win = n; winAt = n == OFF ? 0 : now; }   // v0.17.49 (PC1)

    /** Call after one key was sent: a ONCE modifier ends, a LOCK stays. */
    public void keySent() {
        if (ctrl == ONCE) { ctrl = OFF; ctrlAt = 0; }
        if (alt == ONCE) { alt = OFF; altAt = 0; }
        if (shift == ONCE) { shift = OFF; shiftAt = 0; }
        if (win == ONCE) { win = OFF; winAt = 0; }
    }
    public void clearMods() { ctrl = OFF; alt = OFF; shift = OFF; win = OFF; ctrlAt = 0; altAt = 0; shiftAt = 0; winAt = 0; }

    /** v0.17.49 (PC1): true while any modifier waits as ONCE. */
    public boolean hasOnce() { return ctrl == ONCE || alt == ONCE || shift == ONCE || win == ONCE; }

    /** v0.17.49 (PC1): a ONCE modifier that was tapped idleMs ago or longer ends (LOCK never expires). Returns true if something changed. */
    public boolean expireOnce(long now, long idleMs) {
        boolean ch = false;
        if (ctrl == ONCE && ctrlAt > 0 && now >= ctrlAt && now - ctrlAt >= idleMs) { ctrl = OFF; ctrlAt = 0; ch = true; }
        if (alt == ONCE && altAt > 0 && now >= altAt && now - altAt >= idleMs) { alt = OFF; altAt = 0; ch = true; }
        if (shift == ONCE && shiftAt > 0 && now >= shiftAt && now - shiftAt >= idleMs) { shift = OFF; shiftAt = 0; ch = true; }
        if (win == ONCE && winAt > 0 && now >= winAt && now - winAt >= idleMs) { win = OFF; winAt = 0; ch = true; }
        return ch;
    }

    /** Android meta state bits (same values as KeyEvent.META_CTRL_ON = 0x1000 and META_ALT_ON = 0x02). */
    public static final int META_CTRL = 0x1000, META_ALT = 0x02;
    public static final int META_SHIFT = 0x01, META_WIN = 0x10000;   // v0.17.49: KeyEvent.META_SHIFT_ON, META_META_ON
    public int meta() { return (ctrl != OFF ? META_CTRL : 0) | (alt != OFF ? META_ALT : 0) | (shift != OFF ? META_SHIFT : 0) | (win != OFF ? META_WIN : 0); }

    /** v0.17.49 (PC1): active modifiers as a PcKeyPlan.MOD_* mask. */
    public int mods() {
        return (ctrl != OFF ? PcKeyPlan.MOD_CTRL : 0) | (alt != OFF ? PcKeyPlan.MOD_ALT : 0) | (shift != OFF ? PcKeyPlan.MOD_SHIFT : 0) | (win != OFF ? PcKeyPlan.MOD_META : 0);
    }

    /** v0.17.49 (PC1): real meta flags (plain + LEFT bit) of the active modifiers. */
    public int metaReal() { return PcKeyPlan.realMeta(mods()); }
}
