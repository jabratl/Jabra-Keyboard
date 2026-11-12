package com.mgb.keyboard.typing;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * v0.10.16 (W5) TEXT TOOLS+ : pure Java, no Android. Line tools, Bangla / English digits, URL, Base64, JSON.
 * apply(tool, text, joinSep) never throws: a problem = R.err (a short message) and R.out = "" (the panel then changes nothing).
 * Only java.util / java.nio.charset (API 24 safe: no java.util.Base64, no java.nio.file).
 * Used by TextToolsPanel page "More". Nothing runs unless that page is drawn.
 */
public final class TextTools2 {
    private TextTools2() { }

    public static final int SORT_AZ = 0, SORT_ZA = 1, DEDUPE = 2, REVERSE = 3, NUMBER = 4, TRIM = 5, JOIN = 6,
            TO_EN = 7, TO_BN = 8,
            URL_ENC = 9, URL_DEC = 10, B64_ENC = 11, B64_DEC = 12,
            JSON_PRETTY = 13, JSON_MIN = 14;
    public static final int TOOL_COUNT = 15;

    public static final String[] TOOL_NAMES = {
            "A-Z", "Z-A", "No dupes", "Reverse", "Number", "Trim", "Join",
            "To 0-9", "To ০-৯",
            "URL enc", "URL dec", "B64 enc", "B64 dec",
            "Pretty", "Minify" };
    public static final String[] GROUP_NAMES = { "Lines", "Digits", "Encode", "JSON" };
    public static final int[][] GROUP_TOOLS = {
            { SORT_AZ, SORT_ZA, DEDUPE, REVERSE, NUMBER, TRIM, JOIN },
            { TO_EN, TO_BN },
            { URL_ENC, URL_DEC, B64_ENC, B64_DEC },
            { JSON_PRETTY, JSON_MIN } };

    public static final String[] JOIN_SEPS = { " ", ", ", "; ", "" };
    public static final String[] JOIN_NAMES = { "Space", "Comma", "Semicolon", "None" };

    public static final int MAX_LINES_IN = 100000;
    public static final int MAX_ENC_IN = 50000;
    public static final int MAX_JSON = 20000;
    private static final int MAX_DEPTH = 100;

    /** Result: out (empty on error) + err (null = ok). */
    public static final class R {
        public final String out;
        public final String err;
        R(String out, String err) { this.out = out; this.err = err; }
        public boolean ok() { return err == null; }
    }

    private static R ok(String s) { return new R(s, null); }
    private static R fail(String m) { return new R("", m); }

    public static int groupOf(int tool) {
        for (int g = 0; g < GROUP_TOOLS.length; g++) for (int t : GROUP_TOOLS[g]) if (t == tool) return g;
        return 0;
    }

    public static int firstOf(int group) {
        if (group < 0 || group >= GROUP_TOOLS.length) return SORT_AZ;
        return GROUP_TOOLS[group][0];
    }

    public static R apply(int tool, String text, int joinSep) {
        try {
            if (text == null || text.isEmpty()) return ok("");
            switch (tool) {
                case SORT_AZ: case SORT_ZA: case DEDUPE: case REVERSE: case NUMBER: case TRIM: case JOIN:
                    if (text.length() > MAX_LINES_IN) return fail("Text too long (max " + MAX_LINES_IN + ")");
                    return ok(lines(tool, text, joinSep));
                case TO_EN: return ok(digits(text, false));
                case TO_BN: return ok(digits(text, true));
                case URL_ENC: case URL_DEC: case B64_ENC: case B64_DEC:
                    if (text.length() > MAX_ENC_IN) return fail("Text too long (max " + MAX_ENC_IN + ")");
                    if (tool == URL_ENC) return ok(urlEncode(text));
                    if (tool == URL_DEC) return urlDecode(text);
                    if (tool == B64_ENC) return ok(b64Encode(text.getBytes(StandardCharsets.UTF_8)));
                    return b64Decode(text);
                case JSON_PRETTY: return json(text, true);
                case JSON_MIN: return json(text, false);
                default: return fail("Unknown tool");
            }
        } catch (Throwable t) {
            return fail("Could not do this");
        }
    }

    // ---------------- lines ----------------

    private static boolean blank(String s) {
        for (int i = 0; i < s.length(); i++) if (!isWs(s.charAt(i))) return false;
        return true;
    }

    private static boolean isWs(char c) { return Character.isWhitespace(c) || c == ' ' || c == '　'; }

    private static String trim(String s) {
        int a = 0, b = s.length();
        while (a < b && isWs(s.charAt(a))) a++;
        while (b > a && isWs(s.charAt(b - 1))) b--;
        return s.substring(a, b);
    }

    private static String newline(String t) {
        if (t.indexOf("\r\n") >= 0) return "\r\n";
        if (t.indexOf('\n') >= 0) return "\n";
        if (t.indexOf('\r') >= 0) return "\r";
        return "\n";
    }

    private static String lines(int tool, String text, int joinSep) {
        String nl = newline(text);
        List<String> ls = new ArrayList<String>();
        int start = 0, n = text.length();
        boolean trail = false;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                ls.add(text.substring(start, i));
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') i++;
                start = i + 1;
                if (start == n) trail = true;
            }
        }
        if (start < n) ls.add(text.substring(start));
        StringBuilder sb = new StringBuilder();
        if (tool == JOIN) {
            String sep = JOIN_SEPS[(joinSep < 0 || joinSep >= JOIN_SEPS.length) ? 0 : joinSep];
            boolean first = true;
            for (String l : ls) {
                if (blank(l)) continue;
                if (!first) sb.append(sep);
                sb.append(l);
                first = false;
            }
            return sb.toString();
        }
        if (tool == SORT_AZ || tool == SORT_ZA) {
            final boolean desc = tool == SORT_ZA;
            Collections.sort(ls, new Comparator<String>() {
                public int compare(String a, String b) {
                    int c = String.CASE_INSENSITIVE_ORDER.compare(a, b);
                    if (c == 0) c = a.compareTo(b);
                    return desc ? -c : c;
                }
            });
        } else if (tool == REVERSE) {
            Collections.reverse(ls);
        } else if (tool == DEDUPE) {
            HashSet<String> seen = new HashSet<String>();
            List<String> keep = new ArrayList<String>();
            for (String l : ls) {
                if (blank(l) || seen.add(l)) keep.add(l);   // blank lines (paragraph gaps) are always kept
            }
            ls = keep;
        } else if (tool == TRIM) {
            for (int i = 0; i < ls.size(); i++) ls.set(i, trim(ls.get(i)));
        } else if (tool == NUMBER) {
            int k = 1;
            for (int i = 0; i < ls.size(); i++) {
                if (!blank(ls.get(i))) ls.set(i, (k++) + ". " + ls.get(i));
            }
        }
        for (int i = 0; i < ls.size(); i++) {
            if (i > 0) sb.append(nl);
            sb.append(ls.get(i));
        }
        if (trail) sb.append(nl);
        return sb.toString();
    }

    // ---------------- digits ----------------

    private static String digits(String s, boolean toBangla) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (toBangla && c >= '0' && c <= '9') sb.append((char) ('০' + (c - '0')));
            else if (!toBangla && c >= '০' && c <= '৯') sb.append((char) ('0' + (c - '০')));
            else sb.append(c);
        }
        return sb.toString();
    }

    // ---------------- URL ----------------

    private static final String HEX = "0123456789ABCDEF";

    private static boolean unreserved(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~';
    }

    static String urlEncode(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            int c = x & 0xFF;
            if (unreserved(c)) sb.append((char) c);
            else sb.append('%').append(HEX.charAt(c >> 4)).append(HEX.charAt(c & 15));
        }
        return sb.toString();
    }

    private static int hexVal(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        return -1;
    }

    private static String utf8(byte[] b) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /** '+' = space (form style). A bad %-sequence or bytes that are not UTF-8 = error. */
    static R urlDecode(String s) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        int n = s.length();
        for (int i = 0; i < n; ) {
            char c = s.charAt(i);
            if (c == '%') {
                if (i + 2 >= n) return fail("Bad %-code at character " + (i + 1));
                int h = hexVal(s.charAt(i + 1)), l = hexVal(s.charAt(i + 2));
                if (h < 0 || l < 0) return fail("Bad %-code at character " + (i + 1));
                bo.write((h << 4) | l);
                i += 3;
            } else if (c == '+') {
                bo.write(' ');
                i++;
            } else {
                int cp = s.codePointAt(i);
                byte[] enc = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
                bo.write(enc, 0, enc.length);
                i += Character.charCount(cp);
            }
        }
        String out = utf8(bo.toByteArray());
        if (out == null) return fail("Not valid UTF-8 text");
        return ok(out);
    }

    // ---------------- Base64 ----------------

    private static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    static String b64Encode(byte[] b) {
        StringBuilder sb = new StringBuilder((b.length + 2) / 3 * 4);
        for (int i = 0; i < b.length; i += 3) {
            int b0 = b[i] & 0xFF;
            int b1 = i + 1 < b.length ? b[i + 1] & 0xFF : 0;
            int b2 = i + 2 < b.length ? b[i + 2] & 0xFF : 0;
            sb.append(B64.charAt(b0 >> 2));
            sb.append(B64.charAt(((b0 & 3) << 4) | (b1 >> 4)));
            sb.append(i + 1 < b.length ? B64.charAt(((b1 & 15) << 2) | (b2 >> 6)) : '=');
            sb.append(i + 2 < b.length ? B64.charAt(b2 & 63) : '=');
        }
        return sb.toString();
    }

    /** Whitespace ignored, '-' '_' accepted like '+' '/', padding optional. */
    static R b64Decode(String s) {
        StringBuilder t = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') continue;
            if (c == '-') c = '+';
            else if (c == '_') c = '/';
            t.append(c);
        }
        int end = t.length();
        int pad = 0;
        while (end > 0 && t.charAt(end - 1) == '=') { end--; pad++; }
        if (pad > 2) return fail("Not valid Base64");
        if (end == 0) return fail("Not valid Base64");
        if (end % 4 == 1) return fail("Not valid Base64");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        int acc = 0, bits = 0;
        for (int i = 0; i < end; i++) {
            int v = B64.indexOf(t.charAt(i));
            if (v < 0) return fail("Not valid Base64 (character " + (i + 1) + ")");
            acc = (acc << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                bo.write((acc >> bits) & 0xFF);
                acc &= (1 << bits) - 1;
            }
        }
        String out = utf8(bo.toByteArray());
        if (out == null) return fail("Decoded data is not text (not UTF-8)");
        return ok(out);
    }

    // ---------------- JSON ----------------

    private static final class JErr extends Exception {
        final int pos;
        JErr(int pos) { super("json"); this.pos = pos; }
    }

    private static final class J {
        final String s;
        final int n;
        final boolean pretty;
        final StringBuilder o = new StringBuilder();
        int i = 0;

        J(String s, boolean pretty) { this.s = s; this.n = s.length(); this.pretty = pretty; }

        void ws() {
            while (i < n) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++; else break;
            }
        }

        boolean peek(char c) { return i < n && s.charAt(i) == c; }

        void nlIndent(int k) {
            if (!pretty) return;
            o.append('\n');
            for (int j = 0; j < k; j++) o.append("  ");
        }

        void value(int d) throws JErr {
            if (d > MAX_DEPTH) throw new JErr(i);
            ws();
            if (i >= n) throw new JErr(i);
            char c = s.charAt(i);
            if (c == '{') obj(d);
            else if (c == '[') arr(d);
            else if (c == '"') str();
            else if (c == '-' || (c >= '0' && c <= '9')) num();
            else if (!lit("true") && !lit("false") && !lit("null")) throw new JErr(i);
        }

        boolean lit(String w) {
            if (s.startsWith(w, i)) { o.append(w); i += w.length(); return true; }
            return false;
        }

        void obj(int d) throws JErr {
            o.append('{');
            i++;
            ws();
            if (peek('}')) { i++; o.append('}'); return; }
            while (true) {
                nlIndent(d + 1);
                ws();
                if (!peek('"')) throw new JErr(i);
                str();
                ws();
                if (!peek(':')) throw new JErr(i);
                i++;
                o.append(pretty ? ": " : ":");
                value(d + 1);
                ws();
                if (peek(',')) { i++; o.append(','); continue; }
                if (peek('}')) { i++; break; }
                throw new JErr(i);
            }
            nlIndent(d);
            o.append('}');
        }

        void arr(int d) throws JErr {
            o.append('[');
            i++;
            ws();
            if (peek(']')) { i++; o.append(']'); return; }
            while (true) {
                nlIndent(d + 1);
                value(d + 1);
                ws();
                if (peek(',')) { i++; o.append(','); continue; }
                if (peek(']')) { i++; break; }
                throw new JErr(i);
            }
            nlIndent(d);
            o.append(']');
        }

        void str() throws JErr {
            int st = i;
            i++;
            while (true) {
                if (i >= n) throw new JErr(st);
                char c = s.charAt(i);
                if (c == '"') { i++; break; }
                if (c < 0x20) throw new JErr(i);
                if (c == '\\') {
                    if (i + 1 >= n) throw new JErr(i);
                    char e = s.charAt(i + 1);
                    if (e == 'u') {
                        if (i + 5 >= n) throw new JErr(i);
                        for (int k = 2; k <= 5; k++) if (hexVal(s.charAt(i + k)) < 0) throw new JErr(i);
                        i += 6;
                    } else if ("\"\\/bfnrt".indexOf(e) >= 0) {
                        i += 2;
                    } else throw new JErr(i);
                } else i++;
            }
            o.append(s, st, i);
        }

        void num() throws JErr {
            int st = i;
            if (peek('-')) i++;
            if (i >= n) throw new JErr(st);
            if (peek('0')) i++;
            else if (s.charAt(i) >= '1' && s.charAt(i) <= '9') { while (i < n && Character.isDigit(s.charAt(i)) && s.charAt(i) < 128) i++; }
            else throw new JErr(i);
            if (peek('.')) {
                i++;
                int a = i;
                while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
                if (i == a) throw new JErr(i);
            }
            if (peek('e') || peek('E')) {
                i++;
                if (peek('+') || peek('-')) i++;
                int a = i;
                while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
                if (i == a) throw new JErr(i);
            }
            o.append(s, st, i);
        }
    }

    static R json(String text, boolean pretty) {
        if (text.length() > MAX_JSON) return fail("JSON too long (max " + MAX_JSON + " characters)");
        J j = new J(text, pretty);
        try {
            j.value(0);
            j.ws();
            if (j.i < j.n) throw new JErr(j.i);
            return ok(j.o.toString());
        } catch (JErr e) {
            return fail("Not valid JSON (character " + (e.pos + 1) + ")");
        } catch (StackOverflowError so) {
            return fail("JSON too deep");
        }
    }
}
