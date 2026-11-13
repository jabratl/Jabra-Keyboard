package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.17.51 (PC3) pure Java, no Android classes. Shortcut chips of the PC rows: parses text like "Ctrl+Shift+Z" into
 * modifier bits (PcKeyPlan.MOD_*) + an Android key code. Names are case-insensitive, spaces around "+" are ignored.
 * Modifiers: Ctrl / Control, Alt, Shift, Win / Meta / Cmd / Super. Keys: a-z, 0-9, F1-F12, Esc, Tab, Enter, Space, Backspace,
 * Del, Ins, Home, End, PgUp, PgDn, Up, Down, Left, Right, PrtSc, ScrLk, Pause, Menu, Caps, NumLock and , . / ; ' [ ] \ - = `
 * (a "+" key cannot be written because "+" is the separator). Anything else = invalid = null (the chip is dropped).
 */
public final class PcChord {
    private PcChord() { }

    public static final String DEFAULT_LIST = "Ctrl+A,Ctrl+C,Ctrl+V,Ctrl+X,Ctrl+Z";
    public static final int MAX_CHIPS = 5;

    /** One parsed shortcut. */
    public static final class Chord {
        public final int mods, code;
        public final String keyName;   // canonical key name (C, F5, PgUp ...)
        Chord(int mods, int code, String keyName) { this.mods = mods; this.code = code; this.keyName = keyName; }

        /** Canonical long text, always parses back to the same chord: Ctrl+Alt+Shift+Win+Key. */
        public String text() {
            StringBuilder sb = new StringBuilder();
            if ((mods & PcKeyPlan.MOD_CTRL) != 0) sb.append("Ctrl+");
            if ((mods & PcKeyPlan.MOD_ALT) != 0) sb.append("Alt+");
            if ((mods & PcKeyPlan.MOD_SHIFT) != 0) sb.append("Shift+");
            if ((mods & PcKeyPlan.MOD_META) != 0) sb.append("Win+");
            return sb.append(keyName).toString();
        }

        /** Short key label: ^C, ^⇧Z, Alt+F4, Win+D. */
        public String label() {
            StringBuilder sb = new StringBuilder();
            if ((mods & PcKeyPlan.MOD_CTRL) != 0) sb.append("^");
            if ((mods & PcKeyPlan.MOD_ALT) != 0) sb.append("Alt+");
            if ((mods & PcKeyPlan.MOD_SHIFT) != 0) sb.append("⇧");
            if ((mods & PcKeyPlan.MOD_META) != 0) sb.append("Win+");
            return sb.append(keyName).toString();
        }
    }

    private static final String[] NAMES = { "esc", "tab", "enter", "space", "backspace", "del", "ins", "home", "end", "pgup", "pgdn",
            "up", "down", "left", "right", "prtsc", "scrlk", "pause", "menu", "caps", "numlock" };
    private static final String[] SHOW = { "Esc", "Tab", "Enter", "Space", "Bksp", "Del", "Ins", "Home", "End", "PgUp", "PgDn",
            "Up", "Down", "Left", "Right", "PrtSc", "ScrLk", "Pause", "Menu", "Caps", "NumLk" };
    private static final int[] CODES = { 111, 61, 66, 62, 67, 112, 124, 122, 123, 92, 93,
            19, 20, 21, 22, 120, 116, 121, 82, 115, 143 };
    private static final String PUNCT = ",./;'[]\\-=`";
    private static final int[] PUNCT_CODES = { 55, 56, 76, 74, 75, 71, 72, 73, 69, 70, 68 };

    /** Parse one shortcut. null = empty, no key, two keys, or an unknown name. */
    public static Chord parse(String s) {
        if (s == null) return null;
        String[] parts = s.split("\\+", -1);
        int mods = 0, code = 0;
        String show = null;
        for (String p0 : parts) {
            String p = p0.trim();
            if (p.isEmpty()) return null;
            String l = p.toLowerCase(java.util.Locale.ROOT);
            int m = modOf(l);
            if (m != 0) { mods |= m; continue; }
            if (code != 0) return null;   // two keys
            int c = keyOf(l);
            if (c == 0) return null;
            code = c;
            show = showOf(l);
        }
        if (code == 0) return null;
        return new Chord(mods, code, show);
    }

    private static int modOf(String l) {
        switch (l) {
            case "ctrl": case "control": case "ctl": return PcKeyPlan.MOD_CTRL;
            case "alt": return PcKeyPlan.MOD_ALT;
            case "shift": return PcKeyPlan.MOD_SHIFT;
            case "win": case "meta": case "cmd": case "super": return PcKeyPlan.MOD_META;
            default: return 0;
        }
    }

    private static int keyOf(String l) {
        if (l.length() == 1) {
            char c = l.charAt(0);
            if (c >= 'a' && c <= 'z') return 29 + (c - 'a');
            if (c >= '0' && c <= '9') return 7 + (c - '0');
            int i = PUNCT.indexOf(c);
            return i >= 0 ? PUNCT_CODES[i] : 0;
        }
        if (l.length() >= 2 && l.charAt(0) == 'f') {
            try { int n = Integer.parseInt(l.substring(1)); if (n >= 1 && n <= 12 && !l.substring(1).startsWith("+") && !l.substring(1).startsWith("0")) return 130 + n; } catch (NumberFormatException e) { /* not an F key */ }
        }
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(l) || (i == 4 && l.equals("bksp")) || (i == 5 && l.equals("delete")) || (i == 6 && l.equals("insert"))
                || (i == 9 && l.equals("pageup")) || (i == 10 && l.equals("pagedown")) || (i == 15 && l.equals("printscreen")) || (i == 20 && (l.equals("numlk")))
                || (i == 2 && l.equals("return")) || (i == 19 && l.equals("capslock"))) return CODES[i];
        return 0;
    }

    private static String showOf(String l) {
        if (l.length() == 1) return l.toUpperCase(java.util.Locale.ROOT);
        if (l.charAt(0) == 'f' && l.length() <= 3 && Character.isDigit(l.charAt(1))) return "F" + l.substring(1);
        int c = keyOf(l);
        for (int i = 0; i < CODES.length; i++) if (CODES[i] == c) return SHOW[i];
        return l;
    }

    /** Comma separated list -> valid chords (invalid parts dropped, duplicates dropped, at most [max]). Empty result = empty list (the caller decides). */
    public static List<Chord> parseList(String csv, int max) {
        List<Chord> out = new ArrayList<>();
        if (csv == null) return out;
        List<String> seen = new ArrayList<>();
        for (String part : csv.split("[,;\\n]")) {
            Chord c = parse(part);
            if (c == null) continue;
            String t = c.text();
            if (seen.contains(t)) continue;
            seen.add(t);
            out.add(c);
            if (out.size() >= max) break;
        }
        return out;
    }

    /** The list the PC layer shows: the user's text, or the default when the text is empty / has no valid chip. */
    public static List<Chord> chips(String csv) {
        List<Chord> l = parseList(csv, MAX_CHIPS);
        return l.isEmpty() ? parseList(DEFAULT_LIST, MAX_CHIPS) : l;
    }
}
