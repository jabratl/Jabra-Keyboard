package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.17.49 (PC1) pure Java, no Android classes. Turns (key code, modifier set) into the ordered key events a
 * hardware keyboard sends: modifier DOWN(s), key DOWN, key UP, modifier UP(s) in reverse order.
 * Real modifier key codes (CTRL_LEFT, ALT_LEFT, SHIFT_LEFT, META_LEFT) and real meta flags (the plain bit AND the *_LEFT bit),
 * so apps that read only meta state AND apps that watch modifier key events both see the combo.
 */
public final class PcKeyPlan {
    private PcKeyPlan() { }

    public static final int MOD_CTRL = 1, MOD_ALT = 2, MOD_SHIFT = 4, MOD_META = 8;
    public static final int ACTION_DOWN = 0, ACTION_UP = 1;   // same values as KeyEvent.ACTION_DOWN / ACTION_UP

    // Android KeyEvent key codes (hard-coded so this class stays pure Java)
    public static final int KC_ALT_LEFT = 57, KC_SHIFT_LEFT = 59, KC_CTRL_LEFT = 113, KC_META_LEFT = 117;
    // Android KeyEvent meta flags: plain (legacy) bits and the LEFT bits
    private static final int M_SHIFT = 0x1, M_ALT = 0x2, M_CTRL = 0x1000, M_META = 0x10000;
    private static final int M_ALT_L = 0x10, M_SHIFT_L = 0x40, M_CTRL_L = 0x2000, M_META_L = 0x20000;
    private static final int M_ALT_R = 0x20, M_SHIFT_R = 0x80, M_CTRL_R = 0x4000, M_META_R = 0x40000;

    private static final int[] ORDER = { MOD_CTRL, MOD_ALT, MOD_SHIFT, MOD_META };

    /** One key event of a plan. */
    public static final class Ev {
        public final int action, code, meta;
        public Ev(int action, int code, int meta) { this.action = action; this.code = code; this.meta = meta; }
        @Override public String toString() { return (action == ACTION_DOWN ? "D" : "U") + code + "/" + Integer.toHexString(meta); }
    }

    /** Key code of the LEFT modifier key for one MOD_* bit (0 for anything else). */
    public static int modCode(int mod) {
        switch (mod) {
            case MOD_CTRL: return KC_CTRL_LEFT;
            case MOD_ALT: return KC_ALT_LEFT;
            case MOD_SHIFT: return KC_SHIFT_LEFT;
            case MOD_META: return KC_META_LEFT;
            default: return 0;
        }
    }

    public static boolean isModCode(int code) { return code == KC_CTRL_LEFT || code == KC_ALT_LEFT || code == KC_SHIFT_LEFT || code == KC_META_LEFT; }

    /** Real meta flags (plain + LEFT bit) for a MOD_* set. */
    public static int realMeta(int mods) {
        int m = 0;
        if ((mods & MOD_CTRL) != 0) m |= M_CTRL | M_CTRL_L;
        if ((mods & MOD_ALT) != 0) m |= M_ALT | M_ALT_L;
        if ((mods & MOD_SHIFT) != 0) m |= M_SHIFT | M_SHIFT_L;
        if ((mods & MOD_META) != 0) m |= M_META | M_META_L;
        return m;
    }

    /** Old style meta bits (plain bit only) = what PC mode sent before v0.17.49. */
    public static int legacyMeta(int mods) {
        int m = 0;
        if ((mods & MOD_CTRL) != 0) m |= M_CTRL;
        if ((mods & MOD_ALT) != 0) m |= M_ALT;
        if ((mods & MOD_SHIFT) != 0) m |= M_SHIFT;
        if ((mods & MOD_META) != 0) m |= M_META;
        return m;
    }

    /** Meta flags (any left / right / plain bit) -> MOD_* set. */
    public static int modsFromMeta(int meta) {
        int m = 0;
        if ((meta & (M_CTRL | M_CTRL_L | M_CTRL_R)) != 0) m |= MOD_CTRL;
        if ((meta & (M_ALT | M_ALT_L | M_ALT_R)) != 0) m |= MOD_ALT;
        if ((meta & (M_SHIFT | M_SHIFT_L | M_SHIFT_R)) != 0) m |= MOD_SHIFT;
        if ((meta & (M_META | M_META_L | M_META_R)) != 0) m |= MOD_META;
        return m;
    }

    /** Ordered events: mods DOWN (Ctrl, Alt, Shift, Meta; meta grows with each), key DOWN, key UP, mods UP in reverse (meta shrinks). */
    public static List<Ev> plan(int code, int mods) {
        List<Ev> out = new ArrayList<>();
        List<Integer> downed = new ArrayList<>();
        int cur = 0;
        for (int m : ORDER) {
            if ((mods & m) == 0) continue;
            cur |= m;
            downed.add(m);
            out.add(new Ev(ACTION_DOWN, modCode(m), realMeta(cur)));
        }
        int full = realMeta(cur);
        out.add(new Ev(ACTION_DOWN, code, full));
        out.add(new Ev(ACTION_UP, code, full));
        for (int i = downed.size() - 1; i >= 0; i--) {
            int m = downed.get(i);
            cur &= ~m;
            out.add(new Ev(ACTION_UP, modCode(m), realMeta(cur)));
        }
        return out;
    }

    /** Only the modifier DOWN events (used when a key is held: mods go down first and stay down). */
    public static List<Ev> modsDown(int mods) {
        List<Ev> out = new ArrayList<>();
        int cur = 0;
        for (int m : ORDER) { if ((mods & m) == 0) continue; cur |= m; out.add(new Ev(ACTION_DOWN, modCode(m), realMeta(cur))); }
        return out;
    }

    /** Only the modifier UP events, in reverse order (end of a held key). */
    public static List<Ev> modsUp(int mods) {
        List<Ev> out = new ArrayList<>();
        int cur = mods & (MOD_CTRL | MOD_ALT | MOD_SHIFT | MOD_META);
        for (int i = ORDER.length - 1; i >= 0; i--) {
            int m = ORDER[i];
            if ((mods & m) == 0) continue;
            cur &= ~m;
            out.add(new Ev(ACTION_UP, modCode(m), realMeta(cur)));
        }
        return out;
    }
}
