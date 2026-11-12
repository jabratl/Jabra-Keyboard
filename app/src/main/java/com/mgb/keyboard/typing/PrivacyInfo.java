package com.mgb.keyboard.typing;

import java.util.Locale;

/**
 * v0.10.10 (W4) pure text helpers for the "Privacy & storage" card in Data & Backup (no Android classes).
 * The card answers: what does this keyboard see, store and send?
 */
public final class PrivacyInfo {
    private PrivacyInfo() { }

    /** Bytes -> "512 B", "12 KB", "1.5 MB", "2.0 GB". Negative = "?". */
    public static String bytes(long n) {
        if (n < 0) return "?";
        if (n < 1024L) return n + " B";
        if (n < 1048576L - 512L) return ((n + 512L) / 1024L) + " KB";
        if (n < 1073741824L - 524288L) return String.format(Locale.US, "%.1f MB", n / 1048576.0);
        return String.format(Locale.US, "%.1f GB", n / 1073741824.0);
    }

    /** Sum of all sizes that are known (>= 0). Unknown (< 0) ones are skipped. */
    public static long total(long[] sizes) {
        long t = 0L;
        if (sizes == null) return 0L;
        for (long s : sizes) if (s > 0L) t += s;
        return t;
    }

    /** "Vault: 12 KB"; a file that does not exist yet (size < 0) = "Vault: none yet". */
    public static String storageLine(String label, long size) {
        String l = (label == null || label.isEmpty()) ? "Data" : label;
        return l + ": " + (size < 0L ? "none yet" : bytes(size));
    }

    /** pref clip_expire 0..3 -> when unpinned clips are deleted (same levels as ClipMeta.EXPIRE_NAMES). */
    public static String retention(int level) {
        switch (level) {
            case 1: return "after 1 hour";
            case 2: return "after 1 day";
            case 3: return "after 7 days";
            default: return "never";
        }
    }

    public static String clipLine(boolean historyOn, int expireLevel, boolean codeGuard) {
        if (!historyOn) return "Clipboard history: OFF (copied text is not saved)";
        return "Clipboard history: ON, unpinned clips deleted " + retention(expireLevel)
                + "; one-time code guard " + (codeGuard ? "ON (codes are removed after 2 minutes)" : "OFF");
    }

    public static String learnLine(boolean learnOn) {
        return "Learned words: " + (learnOn ? "ON (kept only on this phone)" : "OFF (nothing is learned)");
    }

    /** The fixed lines about permissions and what is never recorded. Same wording everywhere. */
    public static String[] permissionLines() {
        return new String[] {
            "Internet: used only when you tap \"Check for updates\" or \"Update emoji list\". Nothing is sent in the background.",
            "Install packages: used only to open the installer after you tap \"Download & install\".",
            "Vibrate: key feedback.",
            "Not used: microphone, contacts, location, storage, accounts.",
            "Password fields: nothing is recorded there (no clipboard history, no learned words).",
            "Your texts, clips and vault stay on this phone. No account, no cloud, no tracking."
        };
    }

    /** Two-tap clear button text. n = unpinned clips that would be deleted. */
    public static String clearLabel(boolean armed, int n) {
        if (n <= 0) return "Clear clipboard history now (nothing to clear)";
        return armed ? "Sure? Delete " + n + (n == 1 ? " clip" : " clips") + " (pinned stay)" : "Clear clipboard history now";
    }

    public static String clearDone(int removed) {
        if (removed <= 0) return "Nothing to clear.";
        return "Cleared " + removed + (removed == 1 ? " clip" : " clips") + ". Pinned clips were kept.";
    }
}
