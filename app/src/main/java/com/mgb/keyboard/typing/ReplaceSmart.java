package com.mgb.keyboard.typing;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * v0.41.0 SMART MATCHING (idea from the Bulk Replace tool, item 5). PURE JAVA (no Android), never throws.
 * pattern(term) = the regex SOURCE of ONE plain-text Find, where small spelling differences in the TEXT still match:
 *  - space / hyphen / apostrophe (and zero-width characters) between the parts of the term: "Xiao Yan" also finds "XiaoYan", "Xiao-Yan", "Xiao'Yan", "Xiao  Yan".
 *    A line break is NOT a gap (the match never joins two lines).
 *  - accents: "cafe" also finds "caf\u00E9" (a letter of the term matches the letter with any accent, and a combining mark after it);
 *  - full-width letters / digits / marks: "cat" also finds the full-width form;
 *  - long vowels: a term with a macron / circumflex (a, i, u, e, o) also matches the doubled spelling (\u014D = o, oo, ou; \u0101 = a, aa);
 *  - Russian: e also finds the letter with two dots.
 * The TEXT is never changed or normalized (only the term is expanded), so the positions of the matches stay right.
 * Returns null when smart cannot help (empty term, only gaps, longer than MAX_LEN characters): the engine then uses its normal pattern.
 * Used by ReplaceEngine (Opt.smart) for TEXT / WORD modes. Not used for REGEX.
 */
public final class ReplaceSmart {
    private ReplaceSmart() { }

    public static final int MAX_LEN = 60;

    /** Gap between two parts: zero or more of: a space-like character (not a line break), hyphens, apostrophes, soft hyphen, zero-width characters. */
    private static final String GAP = "[[^\\S\\r\\n]\\-\\u2010\\u2011\\u2012\\u00AD'\\u2019\\u2018\\u02BC`\\u00B4\\u200B-\\u200D\\uFEFF]*";
    private static final String ZWO = "[\\u200B-\\u200D\\uFEFF]*";

    private static Map<Character, String> variants = null;

    private static synchronized Map<Character, String> variants() {
        if (variants != null) return variants;
        Map<Character, String> m = new HashMap<Character, String>();
        try {
            for (int pass = 0; pass < 2; pass++) {
                int a = pass == 0 ? 0xC0 : 0x1E00, b = pass == 0 ? 0x24F : 0x1EFF;
                for (int c = a; c <= b; c++) {
                    String d = Normalizer.normalize(String.valueOf((char) c), Normalizer.Form.NFD);
                    if (d.length() < 2 || !isAscii(d.charAt(0))) continue;
                    boolean marks = true;
                    for (int i = 1; i < d.length(); i++) { char x = d.charAt(i); if (x < 0x300 || x > 0x36F) { marks = false; break; } }
                    if (!marks) continue;
                    String old = m.get(d.charAt(0));
                    m.put(d.charAt(0), (old == null ? "" : old) + (char) c);
                }
            }
        } catch (RuntimeException e) { /* the map stays as far as it got */ }
        variants = m;
        return m;
    }

    private static boolean isAscii(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'); }

    private static boolean isSep(int cp) {
        if (cp == '\n' || cp == '\r') return false;   // a line break in the term stays a line break (a gap never crosses lines)
        return Character.isWhitespace(cp) || cp == 0x00A0 || cp == '-' || (cp >= 0x2010 && cp <= 0x2012) || cp == 0x00AD
            || cp == '\'' || cp == 0x2019 || cp == 0x2018 || cp == 0x02BC || cp == '`' || cp == 0x00B4 || cp == 0x3000;
    }

    private static String u(int cp) {
        return cp <= 0xFFFF ? String.format("\\u%04X", cp) : "\\x{" + Integer.toHexString(cp) + "}";
    }

    /** One class: the letter, its accented forms, its full-width form. */
    private static String letterClass(char base) {
        StringBuilder sb = new StringBuilder("[");
        sb.append(u(base));
        String v = variants().get(base);
        if (v != null) for (int i = 0; i < v.length(); i++) sb.append(u(v.charAt(i)));
        sb.append(u(base + 0xFEE0));
        sb.append("]");
        return sb.toString();
    }

    private static final class Tok {
        final int kind;        // 0 = gap, 1 = latin letter, 2 = other character
        final int cp;
        final StringBuilder marks = new StringBuilder();
        Tok(int kind, int cp) { this.kind = kind; this.cp = cp; }
    }

    private static String latinPattern(Tok t) {
        char b = (char) t.cp;
        char lc = Character.toLowerCase(b);
        boolean up = b != lc;
        String m = t.marks.toString();
        if (m.length() > 0 && (m.indexOf('\u0304') >= 0 || m.indexOf('\u0302') >= 0) && "aeiou".indexOf(lc) >= 0) {
            String[] dbl;
            switch (lc) {
                case 'a': dbl = new String[] { "aa" }; break;
                case 'i': dbl = new String[] { "ii" }; break;
                case 'u': dbl = new String[] { "uu" }; break;
                case 'e': dbl = new String[] { "ei", "ee" }; break;
                default: dbl = new String[] { "ou", "oo" }; break;
            }
            StringBuilder sb = new StringBuilder("(?:");
            for (String x : dbl) {
                String y = up ? Character.toUpperCase(x.charAt(0)) + x.substring(1) : x;
                sb.append(Pattern.quote(y)).append('|');
            }
            sb.append(letterClass(b)).append("\\p{M}*)");
            return sb.toString();
        }
        return letterClass(b) + "\\p{M}*";
    }

    private static String otherPattern(int cp) {
        if (cp == 0x0435 || cp == 0x0415 || cp == 0x0451 || cp == 0x0401) return "[\\u0435\\u0415\\u0451\\u0401]";
        return Pattern.quote(new String(Character.toChars(cp)));
    }

    /** The regex source of one smart Find text, or null when the normal pattern should be used. Never throws. */
    public static String pattern(String term) {
        try {
            if (term == null) return null;
            String t = term.trim();
            if (t.isEmpty() || t.codePointCount(0, t.length()) > MAX_LEN) return null;
            List<Tok> toks = new ArrayList<Tok>();
            for (int i = 0; i < t.length(); ) {
                int cp = t.codePointAt(i);
                i += Character.charCount(cp);
                if (cp >= 0xFF01 && cp <= 0xFF5E) cp -= 0xFEE0;           // full-width ASCII form -> plain
                if (ReplaceEngine.isZw(cp) || cp == 0x200B || cp == 0xFEFF) continue;
                Tok last = toks.isEmpty() ? null : toks.get(toks.size() - 1);
                if (cp >= 0x300 && cp <= 0x36F && last != null && last.kind == 1) { last.marks.append((char) cp); continue; }
                if (isSep(cp)) { if (last == null || last.kind != 0) toks.add(new Tok(0, 0)); continue; }
                if (isAscii((char) cp) && cp < 0x80) { toks.add(new Tok(1, cp)); continue; }
                if ((cp >= 0xC0 && cp <= 0x24F) || (cp >= 0x1E00 && cp <= 0x1EFF)) {
                    String d = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFD);
                    if (d.length() > 1 && isAscii(d.charAt(0))) {
                        Tok k = new Tok(1, d.charAt(0));
                        k.marks.append(d, 1, d.length());
                        toks.add(k);
                        continue;
                    }
                }
                toks.add(new Tok(2, cp));
            }
            boolean any = false;
            for (Tok k : toks) if (k.kind != 0) { any = true; break; }
            if (!any) return null;
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < toks.size(); i++) {
                Tok k = toks.get(i);
                if (k.kind == 0) { out.append(GAP); continue; }
                if (i > 0 && toks.get(i - 1).kind != 0) out.append(ZWO);
                out.append(k.kind == 1 ? latinPattern(k) : otherPattern(k.cp));
            }
            return out.toString();
        } catch (RuntimeException e) { return null; }
    }
}
