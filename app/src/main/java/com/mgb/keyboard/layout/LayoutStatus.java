package com.mgb.keyboard.layout;

import java.util.List;

/**
 * v0.17.18 (L2) texts of the "Current layout" bar of the layout editor (pure Java, no Android, tested by tools/LayoutStatusTest.java).
 * The bar answers three questions at a glance: which layout am I editing, is it saved under its name, does the keyboard use it.
 */
public final class LayoutStatus {
    private LayoutStatus() { }

    /** "Name  - saved" / "Name  - unsaved changes" / "Not saved under a name yet". */
    public static String headline(String active, boolean unsaved) {
        String a = active == null ? "" : active.trim();
        if (a.length() == 0) return "Not saved under a name yet";
        return a + (unsaved ? "  ● unsaved changes" : "  ✓ saved");
    }

    /** One tap "Save" works only when the layout already has a name. */
    public static boolean canQuickSave(String active) { return active != null && active.trim().length() > 0; }

    /** What the keyboard does with this layout right now. */
    public static String useLine(boolean customOn) {
        return customOn ? "In use: the keyboard uses this layout (open the keyboard to see changes)."
                        : "Not in use: the custom keyboard is OFF. Use \"Save & use\" to turn it on.";
    }

    /** Names of layout rules that point at a layout other than the active one (each name once, case-insensitive). */
    public static String ruleWarning(String active, List<String> ruleNames) {
        if (ruleNames == null || ruleNames.isEmpty()) return "";
        String a = active == null ? "" : active.trim();
        StringBuilder b = new StringBuilder();
        List<String> seen = new java.util.ArrayList<String>();
        for (String n : ruleNames) {
            if (n == null) continue;
            String t = n.trim();
            if (t.length() == 0 || t.equalsIgnoreCase(a)) continue;
            boolean dup = false;
            for (String s : seen) if (s.equalsIgnoreCase(t)) dup = true;
            if (dup) continue;
            seen.add(t);
            if (b.length() > 0) b.append(", ");
            b.append('"').append(t).append('"');
        }
        if (b.length() == 0) return "";
        return "Layout rules use " + b + " for some language / screen / app: edits made here do not show there.";
    }

    /** Default name offered by "Save & use": the active name, else a free "My layout". */
    public static String suggestName(LayoutBook b) {
        if (b == null) return "My layout";
        String a = b.active();
        return (a != null && a.length() > 0) ? a : b.uniqueName("My layout");
    }
}
