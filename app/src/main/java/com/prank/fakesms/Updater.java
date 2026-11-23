package com.prank.fakesms;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-app update from GitHub Releases (public repo).
 * Fetch latest release -> download the .apk -> open the system installer.
 * Call everything here from a background thread (network).
 */
public final class Updater {
    private Updater() {}

    public static class Info {
        public String tag, apkUrl, pageUrl, notes, sha;
        public int build;
    }

    public static volatile String lastError = "";

    /** Returns null on any problem. Tries the GitHub API, then the normal release page. */
    public static Info fetchLatest() {
        lastError = "";
        String repo = BuildConfig.GITHUB_REPO;
        if (repo == null || repo.isEmpty()) {
            lastError = "this build has no repo name (use the APK built by GitHub Actions)";
            return null;
        }
        String api = "";
        try {
            Info a = viaApi(repo);
            if (a != null) { lastError = ""; return a; }
            api = lastError;
        } catch (Throwable t) { api = "API " + t.getClass().getSimpleName(); }
        try {
            Info b = viaPage(repo);
            if (b != null) { lastError = ""; return b; }
        } catch (Throwable t) { lastError = api + "; page " + t.getClass().getSimpleName(); return null; }
        lastError = api + "; " + lastError;
        return null;
    }

    private static int buildOf(String tag) {
        Matcher m = Pattern.compile("-b(\\d+)$").matcher(tag);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static Info viaApi(String repo) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/" + repo + "/releases/latest").openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "FakeSmsPrank-Updater");
            if (c.getResponseCode() != 200) { lastError = "API HTTP " + c.getResponseCode(); return null; }
            JSONObject j = new JSONObject(readAll(c.getInputStream()));
            Info i = new Info();
            i.tag = j.optString("tag_name");
            i.build = buildOf(i.tag);
            i.pageUrl = j.optString("html_url");
            i.notes = j.optString("body");
            JSONArray assets = j.optJSONArray("assets");
            if (assets != null) {
                for (int k = 0; k < assets.length(); k++) {
                    JSONObject a = assets.getJSONObject(k);
                    if (a.optString("name").endsWith(".apk")) {
                        i.apkUrl = a.optString("browser_download_url");
                        String d = a.optString("digest");
                        if (d.startsWith("sha256:")) i.sha = d.substring(7);
                        break;
                    }
                }
            }
            return i;
        } finally { c.disconnect(); }
    }

    private static Info viaPage(String repo) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://github.com/" + repo + "/releases/latest").openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "FakeSmsPrank-Updater");
            int code = c.getResponseCode();
            if (code < 301 || code > 308) { lastError = "page HTTP " + code; return null; }
            String loc = c.getHeaderField("Location");
            if (loc == null || !loc.contains("/tag/")) { lastError = "page: no release yet"; return null; }
            String tag = loc.substring(loc.lastIndexOf("/tag/") + 5);
            int q = tag.indexOf('?'); if (q >= 0) tag = tag.substring(0, q);
            Info i = new Info();
            i.tag = tag;
            i.build = buildOf(tag);
            i.apkUrl = "https://github.com/" + repo + "/releases/download/" + tag + "/FakeSmsPrank-" + tag + ".apk";
            i.pageUrl = "https://github.com/" + repo + "/releases/tag/" + tag;
            i.notes = "";
            return i;
        } finally { c.disconnect(); }
    }

    public static boolean isNewer(Info i) { return i.build > BuildConfig.VERSION_CODE; }

    /** Downloads the apk into cache/updates/update.apk. Throws on failure. */
    public static File download(Context ctx, String url) throws Exception {
        File dir = new File(ctx.getCacheDir(), "updates");
        dir.mkdirs();
        File out = new File(dir, "update.apk");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) throw new RuntimeException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); OutputStream o = new FileOutputStream(out)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            }
        } finally { c.disconnect(); }
        if (out.length() < 20_000) throw new RuntimeException("file too small");
        return out;
    }

    /** true = ok or no hash published; false = hash mismatch (do not install). */
    public static boolean verify(File apk, Info info) {
        if (info.sha == null || info.sha.isEmpty()) return true;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new java.io.FileInputStream(apk)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString().equalsIgnoreCase(info.sha);
        } catch (Throwable t) { return false; }
    }

    /** @return true if the installer was opened, false if the user must first allow "install unknown apps". */
    public static boolean install(Context ctx, File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.getPackageManager().canRequestPackageInstalls()) {
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return false;
        }
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
        return true;
    }

    public static void openPage(Context ctx, String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    private static String readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }
}
