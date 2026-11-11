package com.mgb.keyboard.typing;

import java.util.Locale;

/**
 * v0.10.8 (W3) pure helpers to check a downloaded APK against its published SHA-256.
 * Where the expected hash comes from (the release notes do NOT carry it: build.yml writes the table only to the job summary):
 *  1) the GitHub release asset field "digest" ("sha256:<64 hex>"), 2) a "SHA-256 ... <64 hex>" row in the release notes (if a later workflow adds one).
 * No hash anywhere = UNKNOWN = the update is allowed (old releases keep working).
 */
public final class ShaCheck {
    private ShaCheck() { }

    public static final int OK = 0, MISMATCH = 1, UNKNOWN = 2;

    private static boolean isHex64(String s) {
        if (s == null || s.length() != 64) return false;
        for (int i = 0; i < 64; i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    /** "sha256:ABC..." or a bare 64-hex string -> lower-case hex, else null (other algorithms are refused). */
    public static String parseDigest(String s) {
        try {
            if (s == null) return null;
            String t = s.trim();
            int i = t.indexOf(':');
            if (i >= 0) {
                if (!t.substring(0, i).trim().equalsIgnoreCase("sha256")) return null;
                t = t.substring(i + 1).trim();
            }
            return isHex64(t) ? t.toLowerCase(Locale.ROOT) : null;
        } catch (Throwable e) { return null; }
    }

    /** First 64-hex word that follows the label "SHA-256" (within 120 chars) in release notes, lower-case, else null. A 64-hex word elsewhere is ignored. */
    public static String parseFromNotes(String notes) {
        try {
            if (notes == null) return null;
            String low = notes.toLowerCase(Locale.ROOT);
            int from = 0;
            while (true) {
                int k = low.indexOf("sha-256", from);
                if (k < 0) k = low.indexOf("sha256", from);
                if (k < 0) return null;
                int end = Math.min(notes.length(), k + 7 + 120);
                String[] words = notes.substring(k + 6, end).split("[^0-9a-fA-F]+");
                for (String w : words) if (isHex64(w)) return w.toLowerCase(Locale.ROOT);
                from = k + 6;
                if (from >= notes.length()) return null;
            }
        } catch (Throwable e) { return null; }
    }

    /** Expected hash: the asset digest first, else the notes. null = unknown. */
    public static String expected(String digest, String notes) {
        String d = parseDigest(digest);
        return d != null ? d : parseFromNotes(notes);
    }

    /** OK / MISMATCH / UNKNOWN. expected null or not 64 hex = UNKNOWN. An unreadable actual hash = MISMATCH (never install what could not be checked while a hash exists). */
    public static int compare(String expectedHex, String actualHex) {
        if (!isHex64(expectedHex)) return UNKNOWN;
        if (!isHex64(actualHex)) return MISMATCH;
        return expectedHex.equalsIgnoreCase(actualHex) ? OK : MISMATCH;
    }

    public static String toHex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
        return sb.toString();
    }

    /** Message for the screen. */
    public static String message(int result) {
        if (result == OK) return "Download checked (SHA-256 matches).";
        if (result == MISMATCH) return "Download damaged, nothing installed. Tap Check for updates and try again.";
        return "Download could not be checked (no hash published for this release).";
    }
}
