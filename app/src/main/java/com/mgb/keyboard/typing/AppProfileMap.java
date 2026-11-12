package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.10.18 (W6.1) AUTO-PROFILE PER APP: which saved Profile belongs to which app (Android package name).
 * Pure Java (no Android classes), tested by tools/AppProfileMapTest.java. Immutable: every change returns a NEW object (or null when refused).
 * Text (pref ap_json): {"ver":1,"map":{"com.whatsapp":"Compact","com.google.android.keep":"Novel Writing"},"other":""}
 * "other" = profile for every app that is not listed ("" = do nothing). Junk / wrong version / too deep = an EMPTY map (never throws).
 * Profile names are stored cleaned (ProfileCodec.cleanName); the lookup by name in the profile list is done by the caller (a missing profile = mapping ignored).
 */
public final class AppProfileMap {
    public static final int MAX_APPS = 50;
    public static final int MAX_PKG = 150;
    public static final int MAX_TEXT = 20000;

    private final LinkedHashMap<String, String> map;
    private final String other;

    private AppProfileMap(LinkedHashMap<String, String> m, String other) { this.map = m; this.other = other == null ? "" : other; }

    public static AppProfileMap empty() { return new AppProfileMap(new LinkedHashMap<String, String>(), ""); }

    /** Android package name rule (loose): 1..150 chars, letters, digits, '_' and '.', no leading / trailing '.', no "..". */
    public static boolean validPackage(String p) {
        if (p == null || p.length() == 0 || p.length() > MAX_PKG) return false;
        if (p.charAt(0) == '.' || p.charAt(p.length() - 1) == '.' || p.indexOf("..") >= 0) return false;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '.';
            if (!ok) return false;
        }
        return true;
    }

    public int size() { return map.size(); }
    public boolean isEmpty() { return map.isEmpty() && other.length() == 0; }
    public String other() { return other; }
    public List<String> packages() { return new ArrayList<String>(map.keySet()); }
    public String profileOf(String pkg) { return pkg == null ? null : map.get(pkg); }

    /** Profile name for this app: its own mapping, else "other", else "" (= do nothing). */
    public String lookup(String pkg) {
        String n = profileOf(pkg);
        return n != null ? n : other;
    }

    /** Adds / replaces one app. null = refused (bad package, empty name, or more than MAX_APPS apps). */
    public AppProfileMap put(String pkg, String profileName) {
        String n = ProfileCodec.cleanName(profileName);
        if (!validPackage(pkg) || n.isEmpty()) return null;
        if (!map.containsKey(pkg) && map.size() >= MAX_APPS) return null;
        LinkedHashMap<String, String> m = new LinkedHashMap<String, String>(map);
        m.put(pkg, n);
        return new AppProfileMap(m, other);
    }

    /** Removes one app (unknown app = same content). */
    public AppProfileMap remove(String pkg) {
        LinkedHashMap<String, String> m = new LinkedHashMap<String, String>(map);
        m.remove(pkg);
        return new AppProfileMap(m, other);
    }

    /** Sets the "all other apps" profile ("" or null = none). */
    public AppProfileMap withOther(String profileName) {
        return new AppProfileMap(new LinkedHashMap<String, String>(map), ProfileCodec.cleanName(profileName));
    }

    /** Drops mappings whose profile no longer exists (names compared case-insensitively against the given list; null list = keep all). */
    public AppProfileMap dropMissing(List<String> existingNames) {
        if (existingNames == null) return this;
        LinkedHashMap<String, String> m = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : map.entrySet()) if (has(existingNames, e.getValue())) m.put(e.getKey(), e.getValue());
        return new AppProfileMap(m, has(existingNames, other) ? other : "");
    }

    private static boolean has(List<String> l, String n) {
        if (n == null || n.length() == 0) return false;
        for (String s : l) if (s != null && s.equalsIgnoreCase(n)) return true;
        return false;
    }

    public String toJson() {
        StringBuilder b = new StringBuilder("{\"ver\":1,\"map\":{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) b.append(',');
            first = false;
            b.append(ProfileCodec.q(e.getKey())).append(':').append(ProfileCodec.q(e.getValue()));
        }
        return b.append("},\"other\":").append(ProfileCodec.q(other)).append('}').toString();
    }

    /** Reads the pref text. Never null, never throws: junk = empty map. Bad entries are skipped, extra apps beyond MAX_APPS dropped. */
    public static AppProfileMap parse(String json) {
        try {
            if (json == null || json.length() == 0 || json.length() > MAX_TEXT) return empty();
            Object root = new R(json).top();
            if (!(root instanceof Map)) return empty();
            Map<?, ?> m = (Map<?, ?>) root;
            Object ver = m.get("ver");
            if (!(ver instanceof Double) || ((Double) ver).intValue() != 1) return empty();
            LinkedHashMap<String, String> out = new LinkedHashMap<String, String>();
            Object mm = m.get("map");
            if (mm instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) mm).entrySet()) {
                    if (out.size() >= MAX_APPS) break;
                    if (!(e.getKey() instanceof String) || !(e.getValue() instanceof String)) continue;
                    String k = (String) e.getKey();
                    String v = ProfileCodec.cleanName((String) e.getValue());
                    if (validPackage(k) && v.length() > 0) out.put(k, v);
                }
            }
            Object o = m.get("other");
            return new AppProfileMap(out, o instanceof String ? ProfileCodec.cleanName((String) o) : "");
        } catch (RuntimeException ex) {
            return empty();
        }
    }

    /** Tiny reader for this one shape: objects, strings, numbers, true / false / null. Throws RuntimeException on bad text. */
    private static final class R {
        private final String s;
        private int i = 0;
        R(String s) { this.s = s; }
        Object top() { Object v = val(0); ws(); if (i != s.length()) throw new IllegalStateException("tail"); return v; }
        private void ws() { while (i < s.length() && s.charAt(i) <= ' ') i++; }
        private Object val(int d) {
            if (d > 4) throw new IllegalStateException("deep");
            ws();
            if (i >= s.length()) throw new IllegalStateException("eof");
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                ws();
                if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    String k = str();
                    ws();
                    if (i >= s.length() || s.charAt(i) != ':') throw new IllegalStateException(":");
                    i++;
                    m.put(k, val(d + 1));
                    ws();
                    if (i >= s.length()) throw new IllegalStateException("eof");
                    char e = s.charAt(i++);
                    if (e == '}') return m;
                    if (e != ',') throw new IllegalStateException(",");
                }
            }
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i;
            while (i < s.length() && "+-.eE0123456789".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw new IllegalStateException("val");
            return Double.valueOf(s.substring(st, i));
        }
        private String str() {
            if (i >= s.length() || s.charAt(i) != '"') throw new IllegalStateException("str");
            i++;
            StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw new IllegalStateException("eof");
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                if (i >= s.length()) throw new IllegalStateException("eof");
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw new IllegalStateException("u");
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: b.append(e);
                }
            }
        }
    }
}
