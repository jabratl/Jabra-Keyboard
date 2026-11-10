package com.mgb.keyboard.typing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.4.0 pure backup format (no Android classes, tested by tools/BackupTest.java).
 * File = {"ver":1,"app":"0.4.0","time":123,"sha256":"..","prefs":{key:{"t":"b|i|l|f|s","v":..}},"files":{name:"text"}}
 * sha256 = hash of the canonical text of "prefs" + "files" (detects a damaged / edited file).
 */
public final class BackupCodec {
    private BackupCodec() { }

    public static final int OK = 0, BAD_JSON = 1, BAD_VER = 2, BAD_HASH = 3, TOO_BIG = 4;
    public static final int VER = 1;
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    // ---------- build ----------
    /** prefs values: Boolean, Integer, Long, Float or String (others are skipped). */
    public static String build(Map<String, Object> prefs, Map<String, String> files, String app, long time) {
        String p = prefsJson(prefs);
        String f = filesJson(files);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ver\":").append(VER).append(",\"app\":").append(q(app == null ? "" : app))
          .append(",\"time\":").append(time).append(",\"sha256\":\"").append(hash(p, f)).append("\"")
          .append(",\"prefs\":").append(p).append(",\"files\":").append(f).append("}");
        return sb.toString();
    }

    private static String prefsJson(Map<String, Object> prefs) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        if (prefs != null) for (Map.Entry<String, Object> e : prefs.entrySet()) {
            Object v = e.getValue();
            String t, val;
            if (v instanceof Boolean) { t = "b"; val = v.toString(); }
            else if (v instanceof Integer) { t = "i"; val = v.toString(); }
            else if (v instanceof Long) { t = "l"; val = v.toString(); }
            else if (v instanceof Float) { t = "f"; val = q(v.toString()); }
            else if (v instanceof String) { t = "s"; val = q((String) v); }
            else continue;
            if (!first) sb.append(',');
            first = false;
            sb.append(q(e.getKey())).append(":{\"t\":\"").append(t).append("\",\"v\":").append(val).append('}');
        }
        return sb.append('}').toString();
    }

    private static String filesJson(Map<String, String> files) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        if (files != null) for (Map.Entry<String, String> e : files.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append(q(e.getKey())).append(':').append(q(e.getValue()));
        }
        return sb.append('}').toString();
    }

    private static String hash(String p, String f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((p + "\n" + f).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    // ---------- validate / read ----------
    public static int validate(String text) {
        try {
            if (text == null) return BAD_JSON;
            if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return TOO_BIG;
            Object o = new P(text).root();
            if (!(o instanceof Map)) return BAD_JSON;
            Map<?, ?> m = (Map<?, ?>) o;
            Object ver = m.get("ver");
            if (!(ver instanceof Long) || ((Long) ver) != VER) return BAD_VER;
            Map<String, Object> prefs = prefsOf(m);
            Map<String, String> files = filesOf(m);
            if (prefs == null || files == null || !(m.get("sha256") instanceof String)) return BAD_JSON;
            if (!hash(prefsJson(prefs), filesJson(files)).equals(m.get("sha256"))) return BAD_HASH;
            return OK;
        } catch (Throwable t) { return BAD_JSON; }
    }

    /** Values of a VALID backup (call validate first). Never null. */
    public static Map<String, Object> prefs(String text) {
        try { return prefsOf((Map<?, ?>) new P(text).root()); } catch (Throwable t) { return new LinkedHashMap<String, Object>(); }
    }

    public static Map<String, String> files(String text) {
        try { return filesOf((Map<?, ?>) new P(text).root()); } catch (Throwable t) { return new LinkedHashMap<String, String>(); }
    }

    /** Counts for a preview: {prefs, files, time-as-int-seconds-unused=0}. Returns {-1,-1} when the text is not readable. */
    public static long[] summary(String text) {
        try {
            Map<?, ?> m = (Map<?, ?>) new P(text).root();
            Map<String, Object> p = prefsOf(m);
            Map<String, String> f = filesOf(m);
            Object t = m.get("time");
            return new long[]{p.size(), f.size(), t instanceof Long ? (Long) t : 0L};
        } catch (Throwable t) { return new long[]{-1, -1, 0}; }
    }

    public static String appVersion(String text) {
        try { Object a = ((Map<?, ?>) new P(text).root()).get("app"); return a instanceof String ? (String) a : ""; } catch (Throwable t) { return ""; }
    }

    private static Map<String, Object> prefsOf(Map<?, ?> m) {
        Object o = m.get("prefs");
        if (!(o instanceof Map)) return null;
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
            if (!(e.getValue() instanceof Map)) return null;
            Map<?, ?> c = (Map<?, ?>) e.getValue();
            Object t = c.get("t"), v = c.get("v");
            if (!(t instanceof String)) return null;
            String ts = (String) t;
            if (ts.equals("b") && v instanceof Boolean) out.put((String) e.getKey(), v);
            else if (ts.equals("i") && v instanceof Long) out.put((String) e.getKey(), Integer.valueOf((int) (long) (Long) v));
            else if (ts.equals("l") && v instanceof Long) out.put((String) e.getKey(), v);
            else if (ts.equals("f") && v instanceof String) out.put((String) e.getKey(), Float.valueOf((String) v));
            else if (ts.equals("s") && v instanceof String) out.put((String) e.getKey(), v);
            else return null;
        }
        return out;
    }

    private static Map<String, String> filesOf(Map<?, ?> m) {
        Object o = m.get("files");
        if (!(o instanceof Map)) return null;
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
            if (!(e.getValue() instanceof String)) return null;
            out.put((String) e.getKey(), (String) e.getValue());
        }
        return out;
    }

    // ---------- JSON text helpers ----------
    static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Tiny JSON reader: objects (insertion order), arrays, strings, numbers (integers as Long), true / false / null. */
    private static final class P {
        final String s; int i = 0;
        P(String s) { this.s = s; }
        Object root() { Object o = val(); ws(); if (i != s.length()) throw new IllegalStateException("trailing"); return o; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        Object val() {
            ws();
            if (i >= s.length()) throw new IllegalStateException("eof");
            char c = s.charAt(i);
            if (c == '{') {
                i++; Map<String, Object> m = new LinkedHashMap<String, Object>(); ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws(); String k = str(); ws(); expect(':'); m.put(k, val()); ws();
                    char d = s.charAt(i++);
                    if (d == '}') return m;
                    if (d != ',') throw new IllegalStateException("obj");
                }
            }
            if (c == '[') {
                i++; List<Object> l = new ArrayList<Object>(); ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(val()); ws();
                    char d = s.charAt(i++);
                    if (d == ']') return l;
                    if (d != ',') throw new IllegalStateException("arr");
                }
            }
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw new IllegalStateException("bad");
            String n = s.substring(st, i);
            if (n.indexOf('.') < 0 && n.indexOf('e') < 0 && n.indexOf('E') < 0) return Long.valueOf(Long.parseLong(n));
            return Double.valueOf(Double.parseDouble(n));
        }
        void expect(char c) { if (s.charAt(i++) != c) throw new IllegalStateException("expect"); }
        String str() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char d = s.charAt(i++);
                    switch (d) {
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: sb.append(d);
                    }
                } else sb.append(c);
            }
        }
    }
}
