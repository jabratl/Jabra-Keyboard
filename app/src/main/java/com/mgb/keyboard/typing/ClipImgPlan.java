package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v0.18.1: pure rules for copied images / screenshots in the clipboard panel (pref clip_img, default ON).
 * Max 8 images (newest kept), max 6 MB each, only png / jpg / webp / gif. No Android classes.
 */
public final class ClipImgPlan {
    private ClipImgPlan() { }

    public static final String PREF = "clip_img";
    public static final int MAX_IMAGES = 8;
    public static final long MAX_BYTES = 6L * 1024 * 1024;

    /** File extension for an image mime type, or null = not supported. */
    public static String ext(String mime) {
        if (mime == null) return null;
        String m = mime.toLowerCase(Locale.ROOT).trim();
        if (m.equals("image/png")) return "png";
        if (m.equals("image/jpeg") || m.equals("image/jpg")) return "jpg";
        if (m.equals("image/webp")) return "webp";
        if (m.equals("image/gif")) return "gif";
        return null;
    }

    public static boolean supported(String mime) { return ext(mime) != null; }

    public static String mimeOfName(String name) {
        if (name == null) return null;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".gif")) return "image/gif";
        return null;
    }

    public static boolean tooBig(long bytes) { return bytes <= 0 || bytes > MAX_BYTES; }

    /** "<time>_<hash>.<ext>" - same picture (same hash) never makes a second file name pattern. null = unsupported type. */
    public static String fileName(long time, int hash, String mime) {
        String e = ext(mime);
        if (e == null) return null;
        return time + "_" + Integer.toHexString(hash) + "." + e;
    }

    /** The hash part of a file name made by fileName (empty = not ours). */
    public static String hashOf(String name) {
        if (name == null) return "";
        int u = name.indexOf('_'), d = name.lastIndexOf('.');
        return (u > 0 && d > u) ? name.substring(u + 1, d) : "";
    }

    /** Names (given newest first) that are beyond the limit = to delete. */
    public static List<String> overflow(List<String> newestFirst, int max) {
        List<String> out = new ArrayList<>();
        if (newestFirst == null) return out;
        int keep = Math.max(0, max);
        for (int i = keep; i < newestFirst.size(); i++) out.add(newestFirst.get(i));
        return out;
    }

    /** A copied text that is only the address of an image we just stored (content:// or file://) must not become a text clip. */
    public static boolean isImageUriText(String t) {
        if (t == null) return false;
        String s = t.trim();
        return (s.startsWith("content://") || s.startsWith("file://")) && s.indexOf(' ') < 0 && s.indexOf('\n') < 0;
    }
}
