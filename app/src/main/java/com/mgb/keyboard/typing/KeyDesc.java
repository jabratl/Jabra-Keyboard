package com.mgb.keyboard.typing;

/**
 * v0.10.20 (W7) spoken names for TalkBack. Pure Java (no Android), tested by tools/KeyDescTest.java.
 * describe(label) = spoken text for a key / button whose visible label is a symbol or abbreviation, or null = "the label reads fine, leave it".
 * Letters and digits return null on purpose: the screen reader already speaks them (also Bangla), and their label changes with Shift / Probhat,
 * so a fixed description would go stale. Keys that are relabelled while they live ("?" / danda key, language key) are NOT in the map.
 * Used only when a view is BUILT (no work on a key press).
 */
public final class KeyDesc {
    private KeyDesc() { }

    /** Spoken text for a visible label, or null when the label needs no help. */
    public static String describe(String label) {
        if (label == null) return null;
        String l = label.trim();
        if (l.length() == 0) return null;
        // prefixes used by panel buttons: "< More settings", "> App", "✓ Text", "● Name  (ready)"
        if (l.startsWith("< ") && l.length() > 2) return "Back, " + l.substring(2).trim();
        if (l.equals("> App")) return "Use this profile for this app";
        if (l.startsWith("✓ ") && l.length() > 2) return "Selected, " + l.substring(2).trim();
        if (l.startsWith("● ") && l.length() > 2) return l.substring(2).trim().replace("  (ready)", ", ready-made");
        if (l.startsWith("↶ ") && l.length() > 2) return "Undo, " + l.substring(2).trim();
        switch (l) {
            case "⇧": return "Shift";
            case "⌫": return "Backspace";
            case "⏎": return "Enter";
            case "space": return "Space";
            case "?123": return "Numbers and symbols";
            case "ABC": return "Letters";
            case "«": return "Back";
            case "☺": return "Emoji";
            case "🌐": return "Next keyboard";
            case "🎤": return "Voice typing";
            case ",": return "Comma";
            case ".": return "Period";
            case "↶": return "Undo";
            case "↷": return "Redo";
            case "Tab": return "Tab";   // v0.17.7 (K5b)
            case "⎘": return "Clipboard";
            case "✎": return "Edit";
            case "+": return "Plus";
            case "−": case "-": return "Minus";
            case "×": return "Close";
            case "✓": return "Selected";
            case "▲": return "Up";
            case "▼": return "Down";
            case "←": return "Left";
            case "→": return "Right";
            case "↑": return "Up";
            case "↓": return "Down";
            case "<": return "Previous";
            case ">": return "Next";
            // toolbar short labels (Toolbar.label): never spoken as letters
            case "Th": return "Theme";
            case "1H": return "One-hand mode";
            case "Sz": return "Size";
            case "Pad": return "Cursor pad";
            case "Vlt": return "Text Vault";
            case "Tt": return "Text tools";
            case "Pf": return "Profiles";
            case "Km": return "Key map";
            case "SWd": return "Select word";
            case "SLn": return "Select line";
            case "DWd": return "Delete word";
            case "DLn": return "Delete line";
            case "|<": return "Line start";
            case ">|": return "Line end";
            case "Dup": return "Duplicate line";
            case "Sel0": return "Select none";
            case "Ren": return "Rename";
            case "Del": return "Delete";
            default: return null;
        }
    }

    /** Spoken name for an icon (JabraIcons.NAMES) when the button has no text. Unknown name = the name itself. */
    public static String iconName(String icon) {
        if (icon == null) return "";
        switch (icon) {
            case "clip": return "Clipboard";
            case "tick": return "Selected";   // v0.17.68 (S1)
            case "undo": return "Undo";
            case "sort": return "Sort";
            case "checkon": return "Checked";
            case "checkoff": return "Not checked";
            case "dot": return "Ready";
            case "prev": return "Previous";
            case "next": return "Next";
            case "caret": return "Open list";
            case "warn": return "Warning";
            case "edit": return "Edit";
            case "more": return "More options";
            case "up": return "Close menu";
            case "info": return "Show help";   // v0.17.78 (U3)
            case "all": return "Select all";
            case "cut": return "Cut";
            case "copy": return "Copy";
            case "paste": return "Paste";
            case "plain": return "Paste as plain text";
            case "search": return "Search";
            case "close": return "Close";
            case "pin": return "Pin";
            case "pinned": return "Pinned";
            case "gear": return "Settings";
            case "shift": return "Shift";
            case "back": return "Backspace";
            case "enter": return "Enter";
            case "space": return "Space";
            case "fmt": return "Formatter";
            case "globe": return "Next keyboard";
            case "mic": return "Voice typing";
            case "emoji": return "Emoji";
            case "mode_phone": return "Phone mode";
            case "mode_pc": return "PC mode";
            case "numpad": return "Number pad";
            case "sym": return "Symbols";
            case "theme": return "Theme";
            case "onehand": return "One-hand mode";
            case "size": return "Size";
            case "pad": return "Cursor pad";
            case "vault": return "Text Vault";
            case "tt": return "Text tools";
            case "profiles": return "Profiles";
            case "keymap": return "Key map";
            case "selword": return "Select word";
            case "selline": return "Select line";
            case "delword": return "Delete word";
            case "delline": return "Delete line";
            case "linestart": return "Line start";
            case "lineend": return "Line end";
            case "dupline": return "Duplicate line";
            case "selnone": return "Select none";
            case "tab": return "Tab";   // v0.17.7 (K5b)
            default: return icon;
        }
    }

    /** Space key: "Space" or "Space, English" when the key shows the language name. */
    public static String spaceDesc(String shownLabel) {
        if (shownLabel == null) return "Space";
        String l = shownLabel.trim();
        if (l.length() == 0 || l.equalsIgnoreCase("space")) return "Space";
        return "Space, " + l;
    }
}
