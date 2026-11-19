package com.mgb.keyboard.typing;

/**
 * v0.43.0: COLLAPSIBLE parts of the clipboard. Pure Java, no Android (tools/ClipFoldTest.java).
 * Switch clip_fold (default ON). OFF = no chevron, nothing collapses = exactly v0.42.5.
 * What can collapse: the Pinned section, the Recent section (state keys clip_fold_pin / clip_fold_rec) and the Filter / Sort / View row (clip_tools_fold).
 * A collapse NEVER hides clips from select mode or search: while selecting or searching everything is open (a hidden clip could be deleted by mistake).
 */
public final class ClipFold {
    private ClipFold() { }

    public static final String KEY_ON = "clip_fold";
    public static final String KEY_PIN = "clip_fold_pin";
    public static final String KEY_REC = "clip_fold_rec";
    public static final String KEY_TOOLS = "clip_tools_fold";

    /** State key of a section by its header label ("Pinned" or anything else = Recent). */
    public static String keyFor(String label) { return "Pinned".equals(label) ? KEY_PIN : KEY_REC; }

    /** Can the user fold / unfold right now? Not while selecting, not while searching, not with the switch OFF. */
    public static boolean interactive(boolean featureOn, boolean selOn, boolean searching) { return featureOn && !selOn && !searching; }

    /** Is the content really hidden right now? (saved folded state, but only when the user could fold) */
    public static boolean hidden(boolean featureOn, boolean folded, boolean selOn, boolean searching) {
        return interactive(featureOn, selOn, searching) && folded;
    }

    /** Small triangle: right = folded (tap to open), down = open (tap to fold). */
    public static String chevron(boolean folded) { return folded ? "\u25B8" : "\u25BE"; }

    /** Quiet count after the label of a folded section: " \u00B7 12". Empty when open. */
    public static String countText(boolean folded, int n) { return folded ? " \u00B7 " + Math.max(0, n) : ""; }

    /**
     * Title of the top row. Switch OFF = exactly ClipCopy.title (old). ON = + the chevron; while the Filter row is folded and a filter is active
     * (chip != 0) the filter name is added, so a hidden filter is never a surprise: "Clipboard \u00B7 12 \u00B7 Links \u25B8".
     */
    public static String title(int total, boolean featureOn, boolean toolsFolded, int chip, String chipLabel) {
        String base = ClipCopy.title(total);
        if (!featureOn) return base;
        if (toolsFolded && chip != 0 && chipLabel != null && !chipLabel.isEmpty()) base += " \u00B7 " + chipLabel;
        return base + " " + chevron(toolsFolded);
    }
}
