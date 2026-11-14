package com.mgb.keyboard.gif;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v0.18.5: pure rules for the GIF panel (KLIPY, free key). No Android classes, tested by tools/GifPlanTest.java.
 * The key is pasted by the user (pref gif_klipy_key). Nothing is sent until the user opens the GIF panel / taps Search.
 * Only the typed search word, the key and a random anonymous id (pref gif_cid) go to api.klipy.com.
 */
public final class GifPlan {
    private GifPlan() { }

    public static final String PREF_KEY = "gif_klipy_key";
    public static final String PREF_CID = "gif_cid";
    public static final String API_ROOT = "https://api.klipy.com/api/v1/";
    public static final int PER_PAGE = 24;
    public static final int MAX_QUERY = 60;
    public static final int MAX_RESPONSE = 1024 * 1024;           // search answer (json) max 1 MB
    public static final int MAX_THUMB = 1536 * 1024;              // one preview max 1.5 MB
    public static final int MAX_GIF_BYTES = 6 * 1024 * 1024;      // same limit as copied clipboard pictures (ClipImgPlan.MAX_BYTES)
    public static final int KEEP_FILES = 12;                      // sent GIFs kept in the cache folder "gif"

    /** Smallest first (grid previews). */
    public static final String[] PREVIEW_ORDER = { "xs", "sm", "md", "hd" };
    /** Best size to send first: medium (sharp enough, small enough), then smaller, then the biggest. */
    public static final String[] SEND_ORDER = { "md", "sm", "hd", "xs" };

    /** The key as pasted: trimmed, quotes / spaces removed. "" = not a plausible key (16..128 letters, digits, - or _). */
    public static String cleanKey(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        while (t.length() > 1 && (t.startsWith("\"") || t.startsWith("'")) && (t.endsWith("\"") || t.endsWith("'"))) t = t.substring(1, t.length() - 1).trim();
        if (t.length() < 16 || t.length() > 128) return "";
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) return "";
        }
        return t;
    }

    /** Search word: trimmed, one space between words, at most MAX_QUERY characters. */
    public static String cleanQuery(String raw) {
        if (raw == null) return "";
        String t = raw.trim().replaceAll("\\s+", " ");
        if (t.length() > MAX_QUERY) t = t.substring(0, MAX_QUERY).trim();
        return t;
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (UnsupportedEncodingException e) { return ""; }
    }

    /** Request URL. Empty query = trending. null = no usable key. */
    public static String searchUrl(String key, String query, String customerId, int page) {
        String k = cleanKey(key);
        if (k.isEmpty()) return null;
        String q = cleanQuery(query);
        String cid = (customerId == null || customerId.trim().isEmpty()) ? "anon" : customerId.trim();
        StringBuilder sb = new StringBuilder(API_ROOT).append(k).append(q.isEmpty() ? "/gifs/trending" : "/gifs/search");
        sb.append("?customer_id=").append(enc(cid));
        sb.append("&page=").append(page < 1 ? 1 : page);
        sb.append("&per_page=").append(PER_PAGE);
        if (!q.isEmpty()) sb.append("&q=").append(enc(q));
        return sb.toString();
    }

    /** Only https links from the answer are used (never plain http). */
    public static boolean isHttps(String url) {
        if (url == null) return false;
        if (url.length() < 12 || url.length() > 1000) return false;
        if (!url.toLowerCase(Locale.ROOT).startsWith("https://")) return false;
        for (int i = 0; i < url.length(); i++) if (url.charAt(i) <= ' ') return false;
        return true;
    }

    /** Safe cache file name from the GIF id: lower case letters, digits, - and _, max 40, then ".gif". */
    public static String fileName(String slug) {
        StringBuilder sb = new StringBuilder();
        String s = slug == null ? "" : slug.toLowerCase(Locale.ROOT);
        for (int i = 0; i < s.length() && sb.length() < 40; i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') sb.append(c);
        }
        if (sb.length() == 0) sb.append("gif");
        return sb.append(".gif").toString();
    }

    /** Names (given newest first) beyond the limit = to delete. */
    public static List<String> overflow(List<String> newestFirst, int max) {
        List<String> out = new ArrayList<String>();
        if (newestFirst == null) return out;
        int m = max < 0 ? 0 : max;
        for (int i = m; i < newestFirst.size(); i++) out.add(newestFirst.get(i));
        return out;
    }

    /** Short text for a failed request. code <= 0 = no connection. */
    public static String errorText(int code) {
        if (code <= 0) return "No internet. Check the connection and tap Trending.";
        if (code == 401 || code == 403) return "Key not accepted. Hold the Key button to remove it, then paste a new key.";
        if (code == 429) return "Too many requests. Wait a minute and try again.";
        if (code >= 400 && code < 500) return "Request refused (" + code + "). Check the key.";
        return "Could not load GIFs (" + code + "). Try again later.";
    }
}
