package com.mgb.keyboard.layout;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * v0.17.4 (K4) text format "JKL1" of a custom keyboard layout (pure Java, no Android). decode NEVER throws.
 *
 * <pre>
 * JKL1                      header (a name may follow after a space, ignored)
 * L en                      layer id (en av pb s1 s2 s3 nr bot)
 * R 100 [nr|bot]            row: height % and an optional share (a reference row has no K lines)
 * K C,100,q,,,,%40,,,,,,0,en_q,,,
 *   kind(C char / A action / S spacer), weight x 100, out0, out1, label0, label1, long-press,
 *   swipe up, down, left, right, action, flags (1 bold, 2 wideHint, 4 noPopup, 8 repeat),
 *   id, long-press options (items joined by "|"), combo, icon
 * </pre>
 * Every text field is escaped: "%", ",", "|", space and control characters become %XX (ASCII), everything else
 * (Bangla, emoji, ZWJ) stays as it is. Weights are saved with 2 decimals (0.25 steps and 1.55 are exact).
 * Decode is tolerant: garbage / unknown lines are skipped, missing trailing fields = defaults, every text is cleaned
 * (LayoutText), the model is normalised and must pass LayoutOps.valid. A newer version (JKL2 ...), a text over 60 KB,
 * too many layers / rows / keys, or nothing usable = null (the caller then uses the classic keyboard).
 */
public final class LayoutCodec {
    private LayoutCodec() { }

    public static final int VERSION = 1;
    public static final String HEADER = "JKL1";
    /** Size guard: the whole text in UTF-8 bytes. */
    public static final int MAX_BYTES = 60 * 1024;
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String HEX = "0123456789ABCDEF";

    // ---------- escape ----------
    static String esc(String s) {
        if (s == null || s.length() == 0) return "";
        StringBuilder b = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hit = c == '%' || c == ',' || c == '|' || c == ' ' || c < 0x20 || c == 0x7F;
            if (hit && b == null) { b = new StringBuilder(s.length() + 8); b.append(s, 0, i); }
            if (b == null) continue;
            if (hit) b.append('%').append(HEX.charAt((c >> 4) & 15)).append(HEX.charAt(c & 15));
            else b.append(c);
        }
        return b == null ? s : b.toString();
    }

    private static int hexv(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        return -1;
    }

    /** Reverses esc; also reads %XX UTF-8 byte runs written by other encoders. A broken "%" stays as it is. */
    static String unesc(String s) {
        if (s == null || s.indexOf('%') < 0) return s == null ? "" : s;
        StringBuilder b = new StringBuilder(s.length());
        ByteArrayOutputStream run = new ByteArrayOutputStream();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < n && hexv(s.charAt(i + 1)) >= 0 && hexv(s.charAt(i + 2)) >= 0) {
                run.write(hexv(s.charAt(i + 1)) * 16 + hexv(s.charAt(i + 2)));
                i += 3;
                continue;
            }
            if (run.size() > 0) { b.append(new String(run.toByteArray(), UTF8)); run.reset(); }
            b.append(c);
            i++;
        }
        if (run.size() > 0) b.append(new String(run.toByteArray(), UTF8));
        return b.toString();
    }

    private static int utf8Len(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) n += 1;
            else if (c < 0x800) n += 2;
            else if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) { n += 4; i++; }
            else n += 3;
        }
        return n;
    }

    // ---------- encode ----------
    /** Text of a layout, or null when the model is invalid or the text would be over 60 KB (the caller keeps the old layout). Never throws. */
    public static String encode(LayoutDef m) {
        try {
            if (m == null || LayoutOps.valid(m) != null) return null;
            StringBuilder b = new StringBuilder(4096);
            b.append(HEADER).append('\n');
            for (LayerDef l : m.layers) {
                b.append("L ").append(l.id).append('\n');
                for (RowDef r : l.rows) {
                    b.append("R ").append(r.heightPct);
                    if (r.isRef()) b.append(' ').append(r.share);
                    b.append('\n');
                    if (r.isRef()) continue;
                    for (KeyDef k : r.keys) key(b, k);
                }
            }
            String t = b.toString();
            return utf8Len(t) > MAX_BYTES ? null : t;
        } catch (Throwable e) {
            return null;
        }
    }

    private static void key(StringBuilder b, KeyDef k) {
        b.append("K ").append(k.kind == KeyDef.ACTION ? 'A' : (k.kind == KeyDef.SPACER ? 'S' : 'C'));
        b.append(',').append(Math.round(k.weight * 100f));
        b.append(',').append(esc(k.out[0])).append(',').append(esc(k.out[1]));
        b.append(',').append(esc(k.label[0])).append(',').append(esc(k.label[1]));
        b.append(',').append(esc(k.lp));
        for (int i = 0; i < 4; i++) b.append(',').append(esc(k.sw[i]));
        b.append(',').append(esc(k.action));
        b.append(',').append((k.bold ? 1 : 0) | (k.wideHint ? 2 : 0) | (k.noPopup ? 4 : 0) | (k.repeat ? 8 : 0));
        b.append(',').append(esc(k.id)).append(',');
        for (int i = 0; i < k.lpList.size(); i++) { if (i > 0) b.append('|'); b.append(esc(k.lpList.get(i))); }
        b.append(',').append(esc(k.combo)).append(',').append(esc(k.labelIcon)).append('\n');
    }

    // ---------- decode ----------
    /** Format version of a text (1 = JKL1), or -1 when it has no JKL header. Never throws. */
    public static int version(String text) {
        try {
            if (text == null) return -1;
            int i = 0;
            if (text.length() > 0 && text.charAt(0) == '﻿') i = 1;
            while (i < text.length() && (text.charAt(i) == '\n' || text.charAt(i) == '\r' || text.charAt(i) == ' ')) i++;
            if (!text.startsWith("JKL", i)) return -1;
            int j = i + 3, v = 0, d = 0;
            while (j < text.length() && text.charAt(j) >= '0' && text.charAt(j) <= '9' && d < 6) { v = v * 10 + (text.charAt(j) - '0'); j++; d++; }
            return d == 0 ? -1 : v;
        } catch (Throwable e) {
            return -1;
        }
    }

    /** The layout, or null when the text is unusable (see class comment). Never throws. */
    public static LayoutDef decode(String text) {
        try {
            if (text == null || text.length() == 0 || text.length() > MAX_BYTES || utf8Len(text) > MAX_BYTES) return null;
            if (version(text) != VERSION) return null;
            LayoutDef m = new LayoutDef();
            LayerDef cur = null;
            RowDef row = null;
            boolean header = false;
            for (String raw : text.split("\n", -1)) {
                String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
                if (!header) { if (line.replace("﻿", "").trim().length() == 0) continue; header = true; continue; }
                if (line.length() < 2 || line.charAt(1) != ' ') continue;
                char t = line.charAt(0);
                String rest = line.substring(2);
                if (t == 'L') {
                    String id = rest.trim();
                    cur = null; row = null;
                    if (!LayoutText.LAYERS.contains(id) || m.layer(id) != null) continue;
                    cur = new LayerDef(id);
                    m.layers.add(cur);
                } else if (t == 'R') {
                    row = null;
                    if (cur == null) continue;
                    if (cur.rows.size() >= LayoutDef.MAX_ROWS) return null;
                    String[] p = rest.trim().split(" +");
                    RowDef r = new RowDef();
                    try { r.heightPct = Integer.parseInt(p[0]); } catch (Throwable e) { r.heightPct = 100; }
                    if (p.length > 1) r.share = p[1];
                    cur.rows.add(r);
                    row = r;
                } else if (t == 'K') {
                    if (row == null || row.isRef() || row.share.length() > 0) continue;
                    if (cur != null && keysIn(cur) >= LayoutDef.MAX_KEYS_LAYER) return null;
                    KeyDef k = parseKey(rest);
                    if (k != null) row.keys.add(k);
                }
            }
            if (m.layers.size() == 0) return null;
            // tolerant repairs: bad references and empty rows are dropped instead of failing the whole text
            for (LayerDef l : m.layers) {
                List<RowDef> keep = new ArrayList<RowDef>();
                for (RowDef r : l.rows) {
                    boolean ref = r.share.length() > 0;
                    if (ref) {
                        boolean ok = (r.share.equals("nr") || r.share.equals("bot")) && !r.share.equals(l.id) && m.layer(r.share) != null;
                        if (ok) { r.keys.clear(); keep.add(r); }
                    } else if (r.keys.size() > 0) keep.add(r);
                }
                l.rows = keep;
            }
            LayoutDef n = LayoutOps.normalize(m);
            return LayoutOps.valid(n) == null ? n : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static int keysIn(LayerDef l) {
        int n = 0;
        for (RowDef r : l.rows) n += r.keys.size();
        return n;
    }

    private static KeyDef parseKey(String rest) {
        String[] f = rest.split(",", -1);
        String kd = f[0].trim();
        int kind;
        if (kd.equals("C") || kd.equals("0")) kind = KeyDef.CHAR;
        else if (kd.equals("A") || kd.equals("1")) kind = KeyDef.ACTION;
        else if (kd.equals("S") || kd.equals("2")) kind = KeyDef.SPACER;
        else return null;
        KeyDef k = new KeyDef(g(f, 13), kind);
        try { k.weight = Integer.parseInt(g(f, 1).trim()) / 100f; } catch (Throwable e) { k.weight = 1f; }
        k.out[0] = g(f, 2); k.out[1] = g(f, 3);
        k.label[0] = g(f, 4); k.label[1] = g(f, 5);
        k.lp = g(f, 6);
        for (int i = 0; i < 4; i++) k.sw[i] = g(f, 7 + i);
        k.action = g(f, 11);
        int fl = 0;
        try { fl = Integer.parseInt(g(f, 12).trim()); } catch (Throwable e) { fl = 0; }
        k.bold = (fl & 1) != 0; k.wideHint = (fl & 2) != 0; k.noPopup = (fl & 4) != 0; k.repeat = (fl & 8) != 0;
        String ll = f.length > 14 ? f[14] : "";
        if (ll.length() > 0) for (String it : ll.split("\\|", -1)) k.lpList.add(unesc(it));
        k.combo = g(f, 15); k.labelIcon = g(f, 16);
        return k;
    }

    private static String g(String[] f, int i) { return i < f.length ? unesc(f[i]) : ""; }
}
