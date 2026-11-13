package com.mgb.keyboard.typing;

/**
 * v0.17.50 (PC2) pure Java, no Android classes. The ACTION names of the PC keys that live in a layout layer:
 *  modifiers: pc_ctrl, pc_alt, pc_shift, pc_win (sticky, PcState)
 *  keys: pc_key_<name> (esc tab del ins f1..f12 home pgup pgdn end up down left right).
 * Layout action text only keeps a-z 0-9 _ (LayoutText.cleanWord), so the plan's "pc_key:<NAME>" became "pc_key_<name>".
 */
public final class PcActions {
    private PcActions() { }

    public static final String CTRL = "pc_ctrl", ALT = "pc_alt", SHIFT = "pc_shift", WIN = "pc_win", PREFIX = "pc_key_";

    private static final String[] NAMES = { "esc", "tab", "del", "ins", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10", "f11", "f12",
            "home", "pgup", "pgdn", "end", "up", "down", "left", "right" };
    // Android KeyEvent key codes, same order as NAMES
    private static final int[] CODES = { 111, 61, 112, 124, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142,
            122, 92, 93, 123, 19, 20, 21, 22 };
    private static final String[] LABELS = { "Esc", "Tab", "Del", "Ins", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
            "Home", "PgUp", "PgDn", "End", "\u2191", "\u2193", "\u2190", "\u2192" };

    private static int index(String action) {
        if (action == null || !action.startsWith(PREFIX)) return -1;
        String n = action.substring(PREFIX.length());
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(n)) return i;
        return -1;
    }

    public static boolean isMod(String action) { return CTRL.equals(action) || ALT.equals(action) || SHIFT.equals(action) || WIN.equals(action); }

    public static boolean isKey(String action) { return index(action) >= 0; }

    /** True for every PC action name this class knows (modifier or key). */
    public static boolean valid(String action) { return isMod(action) || isKey(action); }

    /** Android key code of a pc_key_ action, 0 for anything else. */
    public static int keyCode(String action) { int i = index(action); return i < 0 ? 0 : CODES[i]; }

    /** Key label (Ctrl, F5, Up arrow ...). Empty for an unknown action. */
    public static String label(String action) {
        if (CTRL.equals(action)) return "Ctrl";
        if (ALT.equals(action)) return "Alt";
        if (SHIFT.equals(action)) return "Shift";
        if (WIN.equals(action)) return "Win";
        int i = index(action);
        return i < 0 ? "" : LABELS[i];
    }

    /** Keys that repeat while held (Del, PgUp, PgDn, arrows) = same set as the PC1 rows. */
    public static boolean isRepeat(String action) {
        int i = index(action);
        if (i < 0) return false;
        String n = NAMES[i];
        return n.equals("del") || n.equals("pgup") || n.equals("pgdn") || n.equals("up") || n.equals("down") || n.equals("left") || n.equals("right");
    }

    /** Label size in sp (same sizes as the PC1 rows: F-keys / Home / PgUp ... small, arrows big). */
    public static float textSp(String action) {
        int i = index(action);
        if (i < 0) return CTRL.equals(action) || ALT.equals(action) ? 12f : 11f;
        String n = NAMES[i];
        if (n.equals("up") || n.equals("down") || n.equals("left") || n.equals("right")) return 14f;
        if (n.startsWith("f") || n.equals("home") || n.equals("pgup") || n.equals("pgdn") || n.equals("end")) return 10f;
        return 12f;
    }

    /** All pc_key_ action names (for tests). */
    public static String[] allKeyActions() {
        String[] a = new String[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) a[i] = PREFIX + NAMES[i];
        return a;
    }
}
