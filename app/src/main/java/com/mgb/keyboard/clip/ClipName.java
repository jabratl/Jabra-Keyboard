package com.mgb.keyboard.clip;

import com.mgb.keyboard.clip.ClipExport.Entry;
import com.mgb.keyboard.clip.ClipExport.Options;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * v0.17.63 (R3): file names + ZIP for clip export. Pure Java (java.util.zip only, no Android), tools/ClipNameTest.java.
 * - clean(): a typed name becomes a safe file name base (no / \ : * ? " < > |, no control chars, no .txt/.docx/.zip typed twice,
 *   no Windows reserved names, max 80 characters, never cuts a surrogate pair).
 * - autoBase(): 1 clip = its first words; more = Jabra-Clips_date_time (same as the old fileName()).
 * - toZip(): one file per clip inside a .zip (names 01_first-words.txt, unique), never throws (null = failed).
 * Old ClipExport.fileName() is NOT changed (it stays the fallback).
 */
public final class ClipName {
    private ClipName() { }

    public static final int MAX_BASE = 80;
    public static final int TITLE_CHARS = 28;
    public static final String MIME_ZIP = "application/zip";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String[] EXTS = { ".docx", ".txt", ".zip" };
    private static final String[] RESERVED = { "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9" };

    private static boolean badChar(char c) {
        return c < 32 || c == 127 || c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|'
            || c == '‎' || c == '‏' || c == '‮' || c == '﻿';   // bidi tricks + BOM are never wanted in a file name
    }

    /** Safe file name base from anything typed. "" = nothing usable (caller uses the auto name). */
    public static String clean(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean lastSpace = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isWhitespace(c)) { if (!lastSpace && sb.length() > 0) sb.append(' '); lastSpace = true; continue; }
            lastSpace = false;
            if (badChar(c)) { sb.append('-'); continue; }
            if (Character.isSurrogate(c)) {
                if (Character.isHighSurrogate(c) && i + 1 < raw.length() && Character.isLowSurrogate(raw.charAt(i + 1))) { sb.append(c).append(raw.charAt(i + 1)); i++; }
                continue;   // lone surrogate: dropped
            }
            sb.append(c);
        }
        String s = sb.toString();
        boolean again = true;
        while (again) {   // "report.txt" typed in the name box -> "report" (the extension is added by the export)
            again = false;
            s = rtrim(s);
            String low = s.toLowerCase(Locale.ROOT);
            for (String x : EXTS) if (low.endsWith(x)) { s = s.substring(0, s.length() - x.length()); again = true; break; }
        }
        s = trimEnds(s).replaceAll(" {2,}", " ");
        s = cut(s, MAX_BASE);
        s = trimEnds(s);
        if (s.length() == 0) return "";
        boolean allDash = true;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) != '-' && s.charAt(i) != '_' && s.charAt(i) != '.') { allDash = false; break; }
        if (allDash) return "";
        String up = s.toUpperCase(Locale.ROOT);
        int dot = up.indexOf('.');
        String stem = dot > 0 ? up.substring(0, dot) : up;
        for (String r : RESERVED) if (r.equals(stem)) return s + "_";
        return s;
    }

    private static String rtrim(String s) {
        int b = s.length();
        while (b > 0 && (s.charAt(b - 1) == ' ' || s.charAt(b - 1) == '.')) b--;
        return s.substring(0, b);
    }

    private static String trimEnds(String s) {
        int a = 0, b = s.length();
        while (a < b && (s.charAt(a) == ' ' || s.charAt(a) == '.')) a++;
        while (b > a && (s.charAt(b - 1) == ' ' || s.charAt(b - 1) == '.')) b--;
        return s.substring(a, b);
    }

    private static String cut(String s, int max) {
        if (s.length() <= max) return s;
        int e = max;
        if (Character.isHighSurrogate(s.charAt(e - 1))) e--;
        return s.substring(0, e);
    }

    /** base + "." + ext; empty / unusable base = fallback base. */
    public static String withExt(String base, String ext, String fallbackBase) {
        String b = clean(base);
        if (b.length() == 0) b = clean(fallbackBase);
        if (b.length() == 0) b = "Jabra-Clips";
        return b + "." + ext;
    }

    private static String stamp(long now, TimeZone tz, String pat) {
        SimpleDateFormat f = new SimpleDateFormat(pat, Locale.US);
        f.setTimeZone(tz == null ? TimeZone.getDefault() : tz);
        return f.format(new Date(now));
    }

    /** Words of the clip's first non-empty line as a short file-name piece. "" when nothing usable. */
    public static String title(String text) {
        if (text == null) return "";
        String line = "";
        for (String l : text.split("\n")) { if (l.trim().length() > 0) { line = l.trim(); break; } }
        String c = clean(line.replace('.', ' '));
        if (c.length() <= TITLE_CHARS) return c;
        String h = cut(c, TITLE_CHARS);
        int sp = h.lastIndexOf(' ');
        if (sp >= 8) h = h.substring(0, sp);
        return trimEnds(h);
    }

    /** Suggested name base: 1 clip = its first words, else Jabra-Clips_yyyy-MM-dd_HHmm. */
    public static String autoBase(List<Entry> es, long now, TimeZone tz) {
        if (es != null && es.size() == 1) {
            String t = title(es.get(0).text);
            if (t.length() > 0) return t;
        }
        return "Jabra-Clips_" + stamp(now, tz, "yyyy-MM-dd_HHmm");
    }

    /** "name" -> "name_2026-11-14" (once: a name that already ends with a date is not stamped again). */
    public static String addDate(String base, long now, TimeZone tz) {
        String b = clean(base);
        String d = stamp(now, tz, "yyyy-MM-dd");
        if (b.length() == 0) return "Jabra-Clips_" + d;
        if (b.endsWith(d)) return b;
        return clean(cut(b, MAX_BASE - d.length() - 1) + "_" + d);
    }

    /** Names of the files inside the zip: 01_first-words.ext (zero padded to the count, unique ignoring case). */
    public static List<String> zipNames(List<Entry> es, String ext) {
        List<String> out = new ArrayList<String>();
        if (es == null) return out;
        int n = es.size();
        int w = String.valueOf(Math.max(n, 1)).length();
        if (w < 2) w = 2;
        Set<String> used = new HashSet<String>();
        for (int i = 0; i < n; i++) {
            String num = String.valueOf(i + 1);
            while (num.length() < w) num = "0" + num;
            String t = title(es.get(i).text);
            if (t.length() == 0) t = "clip";
            String name = num + "_" + t + "." + ext;
            int k = 2;
            while (!used.add(name.toLowerCase(Locale.ROOT))) name = num + "_" + t + "_" + (k++) + "." + ext;
            out.add(name);
        }
        return out;
    }

    /** ZIP with one .txt or .docx file per clip. null = failed (caller shows a message). */
    public static byte[] toZip(List<Entry> es, boolean docx, Options o) {
        try {
            if (es == null || es.isEmpty()) return null;
            if (o == null) o = new Options();
            List<String> names = zipNames(es, docx ? "docx" : "txt");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ZipOutputStream z = new ZipOutputStream(bo, UTF8);
            long when = o.now > 0 ? o.now : 0L;
            for (int i = 0; i < es.size(); i++) {
                List<Entry> one = new ArrayList<Entry>(1);
                one.add(es.get(i));
                byte[] b = docx ? ClipExport.toDocx(one, o) : ClipExport.toTxtBytes(one, o);
                if (b == null) return null;
                ZipEntry ze = new ZipEntry(names.get(i));
                if (when > 0) ze.setTime(when);
                z.putNextEntry(ze);
                z.write(b);
                z.closeEntry();
            }
            z.close();
            return bo.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }
}
