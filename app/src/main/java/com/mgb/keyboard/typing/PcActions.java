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

    /** True for every PC action name this class knows (modifier, key, v0.17.51 More / Num / chip keys). */
    public static boolean valid(String action) { return isMod(action) || isKey(action) || MORE.equals(action) || NUM.equals(action) || chipIndex(action) >= 0; }

    // ---------- v0.17.51 (PC3) ----------
    /** pc_more opens the More panel, pc_num opens the PC numpad panel, pc_chip_<0..4> = shortcut chip n (text comes from pref pc_chips_list, see PcChord). */
    public static final String MORE = "pc_more", NUM = "pc_num", CHIP = "pc_chip_";

    /** Chip number 0..PcChord.MAX_CHIPS-1 of a pc_chip_<n> action, -1 for anything else. */
    public static int chipIndex(String action) {
        if (action == null || !action.startsWith(CHIP) || action.length() != CHIP.length() + 1) return -1;
        char c = action.charAt(CHIP.length());
        return c >= '0' && c < '0' + PcChord.MAX_CHIPS ? c - '0' : -1;
    }

    // keys of the More panel (not layout actions, the panel sends the key code directly): label, Android key code
    private static final String[] MORE_LABELS = { "PrtSc", "ScrLk", "Pause", "Menu", "CapsLk", "NumLk", "AltGr" };
    private static final int[] MORE_CODES = { 120, 116, 121, 82, 115, 143, 58 };

    public static int moreCount() { return MORE_LABELS.length; }

    public static String moreLabel(int i) { return i >= 0 && i < MORE_LABELS.length ? MORE_LABELS[i] : ""; }

    public static int moreCode(int i) { return i >= 0 && i < MORE_CODES.length ? MORE_CODES[i] : 0; }

    // keypad keys of the PC numpad panel: label, Android key code (KEYCODE_NUMPAD_*)
    private static final String[] PAD_LABELS = { "7", "8", "9", "/", "4", "5", "6", "*", "1", "2", "3", "-", "0", ".", "=", "+" };
    private static final int[] PAD_CODES = { 151, 152, 153, 154, 148, 149, 150, 155, 145, 146, 147, 156, 144, 158, 161, 157 };

    public static int padCount() { return PAD_LABELS.length; }

    public static String padLabel(int i) { return i >= 0 && i < PAD_LABELS.length ? PAD_LABELS[i] : ""; }

    public static int padCode(int i) { return i >= 0 && i < PAD_CODES.length ? PAD_CODES[i] : 0; }

    /** KEYCODE_NUMPAD_ENTER. */
    public static final int PAD_ENTER = 160;

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
