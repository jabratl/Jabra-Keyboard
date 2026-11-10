package com.mgb.keyboard.typing;

/** v0.1.2 PC mode state (pure Java, no Android). Mode (Phone / PC) + sticky Ctrl / Alt (1 tap = once, 2nd tap quickly = lock, tap again = off). */
public final class PcState {
    public static final int OFF = 0, ONCE = 1, LOCK = 2;
    public static final long DOUBLE_MS = 400;

    private boolean pc = false;
    private int ctrl = OFF, alt = OFF;
    private long ctrlAt = 0, altAt = 0;

    public boolean isPc() { return pc; }
    public void setPc(boolean v) { pc = v; if (!v) clearMods(); }
    /** Switch Phone <-> PC. Returns the new state (true = PC). Leaving PC clears Ctrl / Alt. */
    public boolean toggleMode() { setPc(!pc); return pc; }

    public int ctrl() { return ctrl; }
    public int alt() { return alt; }

    private static int next(int cur, long last, long now) {
        if (cur == OFF) return ONCE;
        if (cur == ONCE) return (last > 0 && now >= last && now - last <= DOUBLE_MS) ? LOCK : OFF;
        return OFF;
    }

    public void tapCtrl(long now) { int n = next(ctrl, ctrlAt, now); ctrl = n; ctrlAt = n == OFF ? 0 : now; }
    public void tapAlt(long now) { int n = next(alt, altAt, now); alt = n; altAt = n == OFF ? 0 : now; }

    /** Call after one key was sent: a ONCE modifier ends, a LOCK stays. */
    public void keySent() { if (ctrl == ONCE) { ctrl = OFF; ctrlAt = 0; } if (alt == ONCE) { alt = OFF; altAt = 0; } }
    public void clearMods() { ctrl = OFF; alt = OFF; ctrlAt = 0; altAt = 0; }

    /** Android meta state bits (same values as KeyEvent.META_CTRL_ON = 0x1000 and META_ALT_ON = 0x02). */
    public static final int META_CTRL = 0x1000, META_ALT = 0x02;
    public int meta() { return (ctrl != OFF ? META_CTRL : 0) | (alt != OFF ? META_ALT : 0); }
}
