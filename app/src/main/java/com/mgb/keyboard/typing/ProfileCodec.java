package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.5.1 WORKSPACE / LAYOUT PROFILES engine. Pure Java (no Android classes), tested by tools/ProfileTest.java.
 *
 * A profile = a name + a set of values for WHITELISTED pref keys only (look, size, toolbar, language, ... see KEYS).
 * Value null = "remove the key" (back to its default). Keys not in the profile are left alone on apply.
 * Never in a profile (on purpose): ct_json (custom theme colours, only the theme index is kept), clips, vault, formatter data.
 * File text: {"ver":1,"profiles":[{"name":"X","values":{"theme":3,"tb_order":"a,b","kh_pct_p":null}}]}
 */
public final class ProfileCodec {

    private ProfileCodec() { }

    public static final int MAX_PROFILES = 20;
    public static final int MAX_NAME = 24;

    /** Whitelist: key:type (b = boolean, i = int, s = string). Keys are NEVER renamed. */
    private static final String[] SPEC = {
        "theme:i", "look:i", "kh_pct_p:i", "kh_pct_l:i", "oh_w_p:i", "oh_w_l:i", "onehand:i", "pc_mode:b", "bn_layout:s",
        "numrow:b", "numrow_large:b", "letter_pct:i", "letter_bold:b", "key_gap:i", "key_border:i",
        "tb_custom:b", "tb_order:s", "tb_hidden:s", "tb_on:s", "tb_fold:b", "edit_bar:b", "mode_key:b",
        "haptic:b", "sound:b", "key_popup:b", "icons_on:b", "lp_delay:i", "cursor_speed:i", "hint_on:b", "hint_hide:b",
        "lang_tap:i", "lang_fav:i", "lang_avro_on:b", "lang_probhat_on:b", "xrow_chars:s",
        "haptic_lv:i", "hint_custom:s",   // v0.5.2
        "hint_digits:b", "hint_size_lv:i", "hint_alpha_lv:i",   // v0.8.6
        "tb_tip:b",   // v0.8.8
        "space_lang:b", "key_lang:b",   // v0.8.9
        "fb_down:b", "haptic_native:b",   // v0.9.1
        "sp_preview:b",   // v0.9.2
        "numrow_bn:b",   // v0.9.4
        "pop_ref:b",   // v0.9.6
        "h_lock:b",   // v0.9.7
        "sp_tick:b", "sp_step:i",   // v0.9.8
        "pop_fast:b"   // v0.9.9
    };
    public static final String[] KEYS = new String[SPEC.length];
    private static final char[] TYPE = new char[SPEC.length];
    static {
        for (int i = 0; i < SPEC.length; i++) { int c = SPEC[i].indexOf(':'); KEYS[i] = SPEC[i].substring(0, c); TYPE[i] = SPEC[i].charAt(c + 1); }
    }

    public static final class Profile {
        public final String name;
        public final Map<String, Object> values;   // Boolean / Integer / String / null (= remove key)
        public Profile(String name, Map<String, Object> values) { this.name = name; this.values = values; }
    }

    private static int idx(String key) {
        if (key == null) return -1;
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return -1;
    }

    public static boolean allowed(String key) { return idx(key) >= 0; }

    /** v0.5.3: type of a whitelisted key: 'b' boolean, 'i' int, 's' string; ' ' when the key is not whitelisted. */
    public static char typeOf(String key) { int i = idx(key); return i < 0 ? ' ' : TYPE[i]; }

    /** Checks one value for one key. Returns the clean value, or INVALID when it must be dropped. */
    private static final Object INVALID = new Object();

    private static Object norm(String key, Object v) {
        int i = idx(key);
        if (i < 0) return INVALID;
        char t = TYPE[i];
        if (t == 'b') return (v instanceof Boolean) ? v : INVALID;
        if (t == 'i') {
            if (!(v instanceof Number)) return INVALID;
            double d = ((Number) v).doubleValue();
            if (d != Math.rint(d) || d < -1 || d > 1000) return INVALID;
            int n = (int) d;
            if (key.equals("theme") && (n < 0 || n > 20)) return INVALID;
            if ((key.equals("onehand") || key.equals("lang_tap") || key.equals("lang_fav")) && (n < 0 || n > 2)) return INVALID;
            return Integer.valueOf(n);
        }
        if (!(v instanceof String)) return INVALID;
        String s = (String) v;
        if (s.length() > 600) return INVALID;
        if (key.equals("bn_layout") && !s.equals("avro") && !s.equals("probhat")) return INVALID;
        if (key.equals("xrow_chars") && s.length() > 40) return INVALID;
        return s;
    }

    /** Keeps only whitelisted keys with a valid value of the right type (null = remove stays). */
    public static Map<String, Object> clean(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (raw == null) return out;
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (!allowed(e.getKey())) continue;
            if (e.getValue() == null) { out.put(e.getKey(), null); continue; }
            Object n = norm(e.getKey(), e.getValue());
            if (n != INVALID) out.put(e.getKey(), n);
        }
        return out;
    }

    public static String cleanName(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        boolean sp = false;
        for (int i = 0; i < s.length() && b.length() < 200; i++) {
            char c = s.charAt(i);
            if (c < 32 || c == 127) c = ' ';
            if (c == ' ') { if (b.length() > 0) sp = true; continue; }
            if (sp) { b.append(' '); sp = false; }
            b.append(c);
        }
        String r = b.toString();
        if (r.length() > MAX_NAME) r = r.substring(0, MAX_NAME).trim();
        return r;
    }

    /** Snapshot of the current settings. all = the pref map (SharedPreferences.getAll()). Unset keys are saved as null (= default). */
    public static Profile capture(String name, Map<String, ?> all) {
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        for (String k : KEYS) {
            Object o = all == null ? null : all.get(k);
            Object n = (o == null) ? null : norm(k, o);
            v.put(k, n == INVALID ? null : n);
        }
        return new Profile(cleanName(name), v);
    }

    // ---- list helpers (return NEW lists; same name = replaced, case-insensitive)

    public static int indexOf(List<Profile> l, String name) {
        if (l == null || name == null) return -1;
        for (int i = 0; i < l.size(); i++) if (l.get(i).name.equalsIgnoreCase(name)) return i;
        return -1;
    }

    /** Adds or replaces by name. Returns null when the name is empty or the list would pass MAX_PROFILES. */
    public static List<Profile> put(List<Profile> l, Profile p) {
        if (p == null || p.name.isEmpty()) return null;
        List<Profile> out = new ArrayList<Profile>(l == null ? new ArrayList<Profile>() : l);
        int i = indexOf(out, p.name);
        if (i >= 0) out.set(i, p);
        else { if (out.size() >= MAX_PROFILES) return null; out.add(p); }
        return out;
    }

    public static List<Profile> remove(List<Profile> l, int i) {
        List<Profile> out = new ArrayList<Profile>(l);
        if (i >= 0 && i < out.size()) out.remove(i);
        return out;
    }

    /** Rename. Returns null when the new name is empty or already used by ANOTHER profile. */
    public static List<Profile> rename(List<Profile> l, int i, String newName) {
        String n = cleanName(newName);
        if (n.isEmpty() || l == null || i < 0 || i >= l.size()) return null;
        int j = indexOf(l, n);
        if (j >= 0 && j != i) return null;
        List<Profile> out = new ArrayList<Profile>(l);
        out.set(i, new Profile(n, l.get(i).values));
        return out;
    }

    // ---- starters (built from today's keys; NOT stored until the user saves)

    private static Profile st(String name, Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return new Profile(name, clean(m));
    }

    public static List<Profile> starters() {
        List<Profile> l = new ArrayList<Profile>();
        // Normal = size / layout keys back to default (theme, look, language are NOT touched)
        l.add(st("Normal", "kh_pct_p", null, "kh_pct_l", null, "oh_w_p", null, "oh_w_l", null, "letter_pct", null, "letter_bold", null,
            "key_gap", null, "onehand", 0, "pc_mode", false, "numrow", true, "numrow_large", false, "tb_fold", false, "tb_order", "", "tb_hidden", "", "tb_on", ""));
        l.add(st("Novel Writing", "pc_mode", false, "onehand", 0, "numrow", true, "edit_bar", true, "tb_custom", true,
            "tb_order", "fmt,clip,vault,tt,undo,redo", "tb_on", "vault,tt,undo,redo", "tb_hidden", ""));
        l.add(st("PC", "pc_mode", true, "onehand", 0, "numrow", true, "edit_bar", true, "mode_key", true, "tb_custom", true,
            "tb_order", "mode,all,cut,copy,paste,fmt,clip", "tb_on", "undo,redo,pad", "tb_hidden", ""));
        l.add(st("Compact", "kh_pct_p", 80, "kh_pct_l", 80, "onehand", 0, "pc_mode", false, "numrow", true, "numrow_large", false));
        l.add(st("One-Handed", "onehand", 2, "oh_w_p", 80, "kh_pct_p", 90, "pc_mode", false, "numrow", true));
        l.add(st("Large", "kh_pct_p", 120, "kh_pct_l", 100, "onehand", 0, "pc_mode", false, "numrow_large", true));   // v0.5.2: Compact / Normal / Large size presets
        return l;
    }

    // ---- file text

    public static String toJson(List<Profile> l) {
        StringBuilder b = new StringBuilder("{\"ver\":1,\"profiles\":[");
        boolean first = true;
        for (Profile p : l) {
            if (!first) b.append(',');
            first = false;
            b.append("{\"name\":").append(q(p.name)).append(",\"values\":{");
            boolean f2 = true;
            for (Map.Entry<String, Object> e : p.values.entrySet()) {
                if (!f2) b.append(',');
                f2 = false;
                b.append(q(e.getKey())).append(':');
                Object v = e.getValue();
                if (v == null) b.append("null");
                else if (v instanceof String) b.append(q((String) v));
                else b.append(String.valueOf(v));
            }
            b.append("}}");
        }
        return b.append("]}").toString();
    }

    /** Reads the file text. null = damaged or from a newer version (the caller must NOT overwrite the file then). Unknown keys / bad values are dropped. */
    public static List<Profile> parse(String json) {
        try {
            if (json == null) return null;
            Object root = new P(json).value();
            if (!(root instanceof Map)) return null;
            Map<?, ?> m = (Map<?, ?>) root;
            Object ver = m.get("ver");
            if (!(ver instanceof Number) || ((Number) ver).intValue() != 1) return null;
            Object arr = m.get("profiles");
            if (!(arr instanceof List)) return null;
            List<Profile> out = new ArrayList<Profile>();
            for (Object o : (List<?>) arr) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> pm = (Map<?, ?>) o;
                Object n = pm.get("name");
                Object vs = pm.get("values");
                String name = cleanName(n instanceof String ? (String) n : "");
                if (name.isEmpty() || !(vs instanceof Map) || indexOf(out, name) >= 0 || out.size() >= MAX_PROFILES) continue;
                Map<String, Object> raw = new LinkedHashMap<String, Object>();
                for (Map.Entry<?, ?> e : ((Map<?, ?>) vs).entrySet()) if (e.getKey() instanceof String) raw.put((String) e.getKey(), e.getValue());
                out.add(new Profile(name, clean(raw)));
            }
            return out;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static String describe(Profile p) {
        int n = 0;
        for (Object o : p.values.values()) if (o != null) n++;
        return n + " set / " + (p.values.size() - n) + " default";
    }

    static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') b.append("\\\"");
            else if (c == '\\') b.append("\\\\");
            else if (c < 32) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    /** Tiny JSON reader (objects, arrays, strings, numbers, true / false / null). Throws RuntimeException on bad text. */
    private static final class P {
        private final String s;
        private int i = 0;
        P(String s) { this.s = s; }

        Object value() {
            Object v = val(0);
            ws();
            if (i != s.length()) throw new IllegalStateException("tail");
            return v;
        }
        private void ws() { while (i < s.length() && s.charAt(i) <= ' ') i++; }
        private Object val(int d) {
            if (d > 8) throw new IllegalStateException("deep");
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
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<Object>();
                ws();
                if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(val(d + 1));
                    ws();
                    if (i >= s.length()) throw new IllegalStateException("eof");
                    char e = s.charAt(i++);
                    if (e == ']') return l;
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
