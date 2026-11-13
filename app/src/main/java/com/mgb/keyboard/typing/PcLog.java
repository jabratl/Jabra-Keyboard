package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.17.52 (PC4) pure Java, no Android classes. Memory-only log of the Key test page (App > PC key test).
 * PRIVACY: records ONLY while the page is open (setOpen(true)), only keys sent from the PC rows / chips and the keys that the page's own box received,
 * never normal typing; never written to disk or network; the ring keeps the last 20 lines; closing the page clears everything.
 * Also holds the self-test results (name + pass / fail) and builds the one-line summary for bug reports.
 */
public final class PcLog {
    private PcLog() { }

    public static final int MAX = 20;

    private static final List<String> LINES = new ArrayList<String>();
    private static final List<String> TEST_NAMES = new ArrayList<String>();
    private static final List<Boolean> TEST_OK = new ArrayList<Boolean>();
    private static boolean open = false;
    private static Runnable listener = null;

    public static synchronized boolean isOpen() { return open; }

    /** true = the page is open and records; false = nothing is recorded and everything recorded so far is dropped. */
    public static void setOpen(boolean on) {
        Runnable l;
        synchronized (PcLog.class) {
            open = on;
            if (!on) { LINES.clear(); TEST_NAMES.clear(); TEST_OK.clear(); listener = null; }
            l = listener;
        }
        if (l != null) l.run();
    }

    /** The page sets a Runnable that redraws it after every new line / result (called on the thread that added it). */
    public static synchronized void setListener(Runnable r) { listener = r; }

    private static void fire() {
        Runnable l;
        synchronized (PcLog.class) { l = listener; }
        if (l != null) { try { l.run(); } catch (RuntimeException e) { /* a broken page must never break typing */ } }
    }

    /** Adds one line (ring of MAX, oldest dropped). Ignored while the page is closed. */
    public static void add(String line) {
        synchronized (PcLog.class) {
            if (!open || line == null) return;
            LINES.add(line);
            while (LINES.size() > MAX) LINES.remove(0);
        }
        fire();
    }

    /** Lines, oldest first (copy). */
    public static synchronized List<String> lines() { return new ArrayList<String>(LINES); }

    public static void clear() {
        synchronized (PcLog.class) { LINES.clear(); TEST_NAMES.clear(); TEST_OK.clear(); }
        fire();
    }

    // ---------- names ----------
    private static final int[] NAMED = { 111, 61, 112, 124, 122, 123, 92, 93, 19, 20, 21, 22, 66, 62, 67, 120, 116, 121, 82, 115, 143, 58 };
    private static final String[] NAMED_S = { "Esc", "Tab", "Del", "Ins", "Home", "End", "PgUp", "PgDn", "Up", "Down", "Left", "Right", "Enter", "Space", "Bksp",
            "PrtSc", "ScrLk", "Pause", "Menu", "Caps", "NumLk", "AltGr" };

    /** Readable name of an Android key code: CTRL / ALT / SHIFT / WIN, A-Z, 0-9, F1-F12, Home ..., KP0-KP9, else K<code>. */
    public static String codeName(int code) {
        if (code == PcKeyPlan.KC_CTRL_LEFT) return "CTRL";
        if (code == PcKeyPlan.KC_ALT_LEFT) return "ALT";
        if (code == PcKeyPlan.KC_SHIFT_LEFT) return "SHIFT";
        if (code == PcKeyPlan.KC_META_LEFT) return "WIN";
        if (code >= 29 && code <= 54) return String.valueOf((char) ('A' + (code - 29)));
        if (code >= 7 && code <= 16) return String.valueOf(code - 7);
        if (code >= 131 && code <= 142) return "F" + (code - 130);
        if (code >= 144 && code <= 153) return "KP" + (code - 144);
        for (int i = 0; i < NAMED.length; i++) if (NAMED[i] == code) return NAMED_S[i];
        return "K" + code;
    }

    /** Chord text for the log: Ctrl+Shift+Z, Home, Alt+F4. */
    public static String chordName(int code, int mods) {
        StringBuilder sb = new StringBuilder();
        if ((mods & PcKeyPlan.MOD_CTRL) != 0) sb.append("Ctrl+");
        if ((mods & PcKeyPlan.MOD_ALT) != 0) sb.append("Alt+");
        if ((mods & PcKeyPlan.MOD_SHIFT) != 0) sb.append("Shift+");
        if ((mods & PcKeyPlan.MOD_META) != 0) sb.append("Win+");
        return sb.append(codeName(code)).toString();
    }

    // ---------- lines ----------
    /** "sent Ctrl+Shift+Z -> CTRL down, SHIFT down, Z down, Z up, SHIFT up, CTRL up | accepted: yes". real = false: meta bits only (no modifier events). */
    public static String sentLine(int code, int mods, boolean real, boolean accepted) {
        StringBuilder ev = new StringBuilder();
        if (real && mods != 0) {
            for (PcKeyPlan.Ev e : PcKeyPlan.plan(code, mods)) {
                if (ev.length() > 0) ev.append(", ");
                ev.append(codeName(e.code)).append(e.action == PcKeyPlan.ACTION_DOWN ? " down" : " up");
            }
        } else {
            ev.append(codeName(code)).append(" down, ").append(codeName(code)).append(" up");
            if (mods != 0) ev.append(" (modifiers as meta bits only)");
        }
        return "sent " + chordName(code, mods) + " -> " + ev + " | accepted: " + (accepted ? "yes" : "NO");
    }

    /** "got Z down (ctrl+shift)" = what the page's own box really received. */
    public static String recvLine(int action, int code, int meta) {
        int m = PcKeyPlan.modsFromMeta(meta);
        String ms = "";
        if (m != 0) {
            String full = chordName(code, m);
            ms = " (" + full.substring(0, full.length() - codeName(code).length()).replaceAll("\\+$", "").toLowerCase(java.util.Locale.ROOT) + ")";
        }
        return "got " + codeName(code) + (action == PcKeyPlan.ACTION_DOWN ? " down" : " up") + ms;
    }

    /** Hold keys (arrows, Del, PgUp, PgDn): one line per hold, not per repeat. */
    public static String holdLine(int code, int mods, boolean accepted) {
        return "sent " + chordName(code, mods) + " (hold, key down) | accepted: " + (accepted ? "yes" : "NO");
    }

    public static void sent(int code, int mods, boolean real, boolean accepted) { if (isOpen()) add(sentLine(code, mods, real, accepted)); }

    /** false for plain typing keys (letters, digits, Space, Backspace without a modifier): those are never logged. */
    public static boolean recvWorthy(int code, int meta) {
        if (PcKeyPlan.modsFromMeta(meta) != 0) return true;
        boolean plain = (code >= 29 && code <= 54) || (code >= 7 && code <= 16) || code == 62 || code == 67;
        return !plain;
    }

    public static void recv(int action, int code, int meta) { if (isOpen() && recvWorthy(code, meta)) add(recvLine(action, code, meta)); }

    public static void hold(int code, int mods, boolean accepted) { if (isOpen()) add(holdLine(code, mods, accepted)); }

    // ---------- self-test results ----------
    public static void testClear() {
        synchronized (PcLog.class) { TEST_NAMES.clear(); TEST_OK.clear(); }
        fire();
    }

    public static void testAdd(String name, boolean pass) {
        synchronized (PcLog.class) {
            if (!open || name == null) return;
            TEST_NAMES.add(name);
            TEST_OK.add(pass);
        }
        fire();
    }

    public static synchronized int testTotal() { return TEST_NAMES.size(); }

    public static synchronized int testPassed() { int n = 0; for (boolean b : TEST_OK) if (b) n++; return n; }

    /** "ok" / "FAIL" lines of the self-test, in order. */
    public static synchronized List<String> testLines() {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < TEST_NAMES.size(); i++) out.add((TEST_OK.get(i) ? "ok    " : "FAIL  ") + TEST_NAMES.get(i));
        return out;
    }

    /** One line to copy: "Test box: 6/7 ok, failed: Ctrl+A". No results yet = "Test box: not run". */
    public static synchronized String summary(String label) {
        String l = label == null || label.isEmpty() ? "Test box" : label;
        if (TEST_NAMES.isEmpty()) return l + ": not run";
        StringBuilder sb = new StringBuilder(l + ": " + testPassed() + "/" + TEST_NAMES.size() + " ok");
        boolean first = true;
        for (int i = 0; i < TEST_NAMES.size(); i++) {
            if (TEST_OK.get(i)) continue;
            sb.append(first ? ", failed: " : ", ").append(TEST_NAMES.get(i));
            first = false;
        }
        return sb.toString();
    }
}
