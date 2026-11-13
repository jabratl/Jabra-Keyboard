package com.mgb.keyboard.layout;

import java.util.List;

/**
 * v0.17.5 (K5a.1) pure decisions of the safety net (6.8). The Android side (LayoutStore.kt) only reads / writes the text and calls these.
 * S1 load gate: {@link #gate(String)}; S2 crash-loop guard: {@link #tripped(int)}; never throws.
 */
public final class LayoutGuard {
    private LayoutGuard() { }

    /** Crash counter value at which the custom keyboard is switched OFF once (3 hard stops in a row). */
    public static final int TRIP_AT = 3;
    /** Result codes of {@link #gate}. */
    public static final String OK = "ok", EMPTY = "empty", BAD_TEXT = "bad_text", FATAL = "fatal";

    /** Result of the load gate: model (null when not usable), code, and a short reason for the app page. */
    public static final class Gate {
        public final LayoutDef model;
        public final String code, reason;
        Gate(LayoutDef m, String c, String r) { model = m; code = c; reason = r; }
        public boolean ok() { return model != null; }
    }

    /** S1: text -> decode -> fatal check. EMPTY (null / blank) means "nothing saved yet" (the store then builds the defaults). */
    public static Gate gate(String text) {
        try {
            if (text == null || text.trim().length() == 0) return new Gate(null, EMPTY, "no layout saved yet");
            LayoutDef m = LayoutCodec.decode(text);
            if (m == null) return new Gate(null, BAD_TEXT, "layout text is damaged");
            List<String> f = LayoutOps.fatal(m);
            if (f.size() > 0) return new Gate(null, FATAL, f.get(0));
            return new Gate(m, OK, "");
        } catch (Throwable t) {
            return new Gate(null, BAD_TEXT, "layout text is damaged");
        }
    }

    /** Same gate for a model that is about to be saved (S4). Returns null = OK, else the first reason. */
    public static String gateModel(LayoutDef m) {
        try {
            if (m == null) return "no layout";
            String t = LayoutCodec.encode(m);
            if (t == null) return "layout too big";
            Gate g = gate(t);
            return g.ok() ? null : g.reason;
        } catch (Throwable t) { return "layout invalid"; }
    }

    /** S2: true when the crash counter says the custom keyboard crashed hard {@link #TRIP_AT} times in a row. */
    public static boolean tripped(int safeN) { return safeN >= TRIP_AT; }

    /** S2: counter value to store before building the custom rows (never negative, never overflows). */
    public static int armed(int safeN) { return safeN < 0 ? 1 : (safeN >= 100 ? 100 : safeN + 1); }

    /** Only these two pref values exist: 0 = classic keyboard, 1 = custom layout. Anything else = classic. */
    public static boolean customOn(int kbCustom) { return kbCustom == 1; }
}
