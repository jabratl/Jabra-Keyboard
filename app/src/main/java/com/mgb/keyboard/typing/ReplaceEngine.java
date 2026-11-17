package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * v0.24.0 (R1) ADVANCED FIND & REPLACE engine (pure Java, no Android classes, tested by tools/ReplaceEngineTest.java).
 * Never throws: every problem comes back as Result.err and the text is left untouched.
 *
 * Modes: TEXT (plain), WORD (whole word; boundary knows Bangla vowel signs), REGEX (Java regex, ^ and $ work per line).
 * Options: matchCase, bangla (TEXT / WORD only: zero-width joiners in the Find are ignored and may sit between letters of the text;
 *          the three composed Bangla letters (dda-nukta, ddha-nukta, ya-nukta) match both of their forms), escapes (TEXT / WORD only: \n \t \\ in Find / With).
 * With (REGEX): $0 $& $1..$99 ${name} $$, backslash-n, backslash-t, double backslash, and case switches: backslash + capital U / L (until backslash + E), small u / l (next letter only).
 * With (TEXT / WORD): literal text (escapes only when the option is ON).
 * Safety: input up to MAX_TEXT characters, at most MAX_MATCHES matches, output up to MAX_OUT characters,
 *         a time budget for regex (a pattern that backtracks for ever is stopped and reported, never hangs the keyboard).
 */
public final class ReplaceEngine {

    private ReplaceEngine() { }

    public static final int MODE_TEXT = 0, MODE_WORD = 1, MODE_REGEX = 2;
    public static final String[] MODE_NAMES = { "Text", "Word", "Regex" };

    public static final int MAX_TEXT = 100000;
    public static final int MAX_MATCHES = 20000;
    public static final int MAX_OUT = 300000;
    public static final int MAX_HITS = 50;        // hits kept for the preview
    public static final long BUDGET_MS = 400;     // time budget of one run

    /** Options of one run. Default = Text mode, ignore case, Bangla-safe ON, escapes OFF. */
    public static final class Opt {
        public int mode = MODE_TEXT;
        public boolean matchCase = false;
        public boolean bangla = true;
        public boolean escapes = false;
        public boolean keepCase = false;   // v0.29.0: the replacement copies the case look of the found text (hello > world, Hello > World, HELLO > WORLD)

        public Opt copy() {
            Opt o = new Opt();
            o.mode = mode; o.matchCase = matchCase; o.bangla = bangla; o.escapes = escapes; o.keepCase = keepCase;
            return o;
        }
    }

    /** One match: position in the input, the found text and what it becomes. */
    public static final class Hit {
        public final int start, end;
        public final String found, repl;
        Hit(int start, int end, String found, String repl) { this.start = start; this.end = end; this.found = found; this.repl = repl; }
    }

    public static final class Result {
        public String out = "";       // text after the replacement (= input when nothing changed or on error)
        public int count = 0;         // matches found
        public int changed = 0;       // matches replaced
        public boolean blank = false; // Find is empty
        public String err = null;     // null = ok
        public List<Hit> hits = new ArrayList<Hit>();   // first MAX_HITS matches (found + replacement) for the preview
    }

    private static final class Timeout extends RuntimeException {
        private static final long serialVersionUID = 1L;
        Timeout() { super("timeout", null, false, false); }
    }

    /** CharSequence that stops a runaway regex: checks the clock every 4096 reads. */
    private static final class Guard implements CharSequence {
        private final CharSequence s;
        private final long end;
        private int n = 0;
        Guard(CharSequence s, long end) { this.s = s; this.end = end; }
        public char charAt(int i) {
            if ((++n & 0xFFF) == 0 && System.nanoTime() > end) throw new Timeout();
            return s.charAt(i);
        }
        public int length() { return s.length(); }
        public CharSequence subSequence(int a, int b) { return new Guard(s.subSequence(a, b), end); }
        @Override public String toString() { return s.toString(); }
    }

    // ------------------------------------------------------------------ public API

    /** Replace every match. */
    public static Result replaceAll(String text, String find, String with, Opt o) { return run(text, find, with, o, 1, Integer.MAX_VALUE); }

    /** Replace only the first match. */
    public static Result replaceFirst(String text, String find, String with, Opt o) { return run(text, find, with, o, 1, 1); }

    /** Replace only the n-th match (1-based). Other matches stay. count still says how many there are. */
    public static Result replaceNth(String text, String find, String with, Opt o, int n) { return run(text, find, with, o, n, n); }

    /** Count the matches (nothing is replaced). */
    public static int count(String text, String find, Opt o) {
        Result r = run(text, find, "", o, Integer.MAX_VALUE, Integer.MAX_VALUE);
        return r.err == null ? r.count : 0;
    }

    /** Replace the matches whose 1-based number is between first and last. */
    public static Result run(String text0, String find, String with0, Opt o0, int first, int last) {
        Result r = new Result();
        String text = text0 == null ? "" : text0;
        String with = with0 == null ? "" : with0;
        Opt o = o0 == null ? new Opt() : o0;
        r.out = text;
        try {
            if (find == null || find.length() == 0) { r.blank = true; return r; }
            if (text.length() > MAX_TEXT) { r.err = "Text is too long (more than " + MAX_TEXT + " characters)"; return r; }
            Pattern p;
            try {
                p = compile(find, o);
            } catch (PatternSyntaxException e) {
                r.err = "Pattern error: " + (e.getDescription() == null ? "?" : e.getDescription()) + (e.getIndex() >= 0 ? " (at " + e.getIndex() + ")" : "");
                return r;
            }
            String w = (o.mode != MODE_REGEX && o.escapes) ? unescape(with) : with;
            long end = System.nanoTime() + BUDGET_MS * 1000000L;
            Matcher m = p.matcher(new Guard(text, end));
            StringBuilder out = new StringBuilder(text.length() + 16);
            int pos = 0, idx = 0, changed = 0;
            while (m.find()) {
                idx++;
                if (idx > MAX_MATCHES) { r.err = "Too many matches (more than " + MAX_MATCHES + "). Make Find more exact"; r.out = text; r.count = 0; r.changed = 0; r.hits.clear(); return r; }
                boolean doIt = idx >= first && idx <= last;
                String found = text.substring(m.start(), m.end());
                String rep = null;
                if (doIt || r.hits.size() < MAX_HITS) { rep = o.mode == MODE_REGEX ? expand(with, m) : w; if (o.keepCase) rep = keepCase(found, rep); }
                if (doIt) {
                    out.append(text, pos, m.start());
                    out.append(rep);
                    pos = m.end();
                    changed++;
                    if (out.length() > MAX_OUT) { r.err = "Result would be too long"; r.out = text; r.count = 0; r.changed = 0; r.hits.clear(); return r; }
                }
                if (r.hits.size() < MAX_HITS) r.hits.add(new Hit(m.start(), m.end(), found, rep == null ? found : rep));
            }
            out.append(text, pos, text.length());
            r.count = idx;
            r.changed = changed;
            if (out.length() > MAX_OUT) { r.err = "Result would be too long"; r.out = text; r.changed = 0; return r; }
            r.out = changed == 0 ? text : out.toString();
        } catch (Timeout t) {
            r.err = "Too slow: this pattern is too complex";
            r.out = text; r.count = 0; r.changed = 0; r.hits.clear();
        } catch (StackOverflowError so) {
            r.err = "Pattern is too complex";
            r.out = text; r.count = 0; r.changed = 0; r.hits.clear();
        } catch (RuntimeException e) {
            r.err = "Could not run this replace";
            r.out = text; r.count = 0; r.changed = 0; r.hits.clear();
        }
        return r;
    }

    /** v0.29.0: copy the case look of found onto rep. ALL CAPS (2+ letters) > UPPER; Capitalized > first letter of each word upper; all small / mixed / no letters > unchanged. Never throws. */
    public static String keepCase(String found, String rep) {
        try {
            if (found == null || rep == null || rep.length() == 0) return rep;
            int letters = 0, up = 0, low = 0;
            for (int i = 0; i < found.length(); ) {
                int cp = found.codePointAt(i); i += Character.charCount(cp);
                if (!Character.isLetter(cp)) continue;
                letters++;
                if (Character.isUpperCase(cp)) up++; else if (Character.isLowerCase(cp)) low++;
            }
            if (letters == 0 || up == 0) return rep;
            if (low == 0 && letters >= 2 && up == letters) return rep.toUpperCase(Locale.ROOT);
            // Capitalized: every word of found starts with a capital and has no other capital
            boolean title = true, startWord = true;
            for (int i = 0; i < found.length(); ) {
                int cp = found.codePointAt(i); i += Character.charCount(cp);
                if (!Character.isLetter(cp)) { startWord = true; continue; }
                if (startWord && !Character.isUpperCase(cp)) { title = false; break; }
                if (!startWord && Character.isUpperCase(cp)) { title = false; break; }
                startWord = false;
            }
            if (!title) return rep;
            StringBuilder sb = new StringBuilder(rep.length());
            boolean sw = true;
            for (int i = 0; i < rep.length(); ) {
                int cp = rep.codePointAt(i); i += Character.charCount(cp);
                if (Character.isLetter(cp)) { sb.appendCodePoint(sw ? Character.toUpperCase(cp) : cp); sw = false; }
                else { sb.appendCodePoint(cp); sw = true; }
            }
            return sb.toString();
        } catch (RuntimeException e) { return rep; }
    }

    // ------------------------------------------------------------------ pattern

    static boolean isZw(int cp) { return cp == 0x200C || cp == 0x200D; }

    private static final String ZW = "[\\u200C\\u200D]*";

    /** Bangla: ড + nukta = ড়, ঢ + nukta = ঢ়, য + nukta = য়. Returns the composed form. */
    static String composeBangla(String s) {
        return s.replace("\u09A1\u09BC", "\u09DC").replace("\u09A2\u09BC", "\u09DD").replace("\u09AF\u09BC", "\u09DF");
    }

    /** Builds the Pattern of a Find text. Throws PatternSyntaxException (REGEX mode only). */
    static Pattern compile(String find, Opt o) {
        int flags = 0;
        if (!o.matchCase) flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        if (o.mode == MODE_REGEX) return Pattern.compile(find, flags | Pattern.MULTILINE);
        String f = o.escapes ? unescape(find) : find;
        StringBuilder sb = new StringBuilder();
        if (o.mode == MODE_WORD) sb.append("(?<![\\p{L}\\p{M}\\p{N}_])");
        if (!o.bangla) {
            sb.append(Pattern.quote(f));
        } else {
            f = composeBangla(f);
            int i = 0;
            boolean first = true;
            while (i < f.length()) {
                int cp = f.codePointAt(i);
                i += Character.charCount(cp);
                if (isZw(cp)) continue;                 // zero-width chars of the Find are ignored
                if (!first) sb.append(ZW);              // ... and may sit between the letters of the text
                first = false;
                if (cp == 0x09DC) sb.append("(?:\\u09DC|\\u09A1\\u09BC)");
                else if (cp == 0x09DD) sb.append("(?:\\u09DD|\\u09A2\\u09BC)");
                else if (cp == 0x09DF) sb.append("(?:\\u09DF|\\u09AF\\u09BC)");
                else sb.append(Pattern.quote(new String(Character.toChars(cp))));
            }
            if (first) sb.append("(?!)");               // the Find had only zero-width characters: matches nothing
        }
        if (o.mode == MODE_WORD) sb.append("(?![\\p{L}\\p{M}\\p{N}_])");
        return Pattern.compile(sb.toString(), flags);
    }

    /** \n -> newline, \t -> tab, \\ -> backslash. Any other backslash stays. */
    public static String unescape(String s) {
        if (s == null) return "";
        if (s.indexOf('\\') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char d = s.charAt(i + 1);
                if (d == 'n') { b.append('\n'); i++; continue; }
                if (d == 't') { b.append('\t'); i++; continue; }
                if (d == '\\') { b.append('\\'); i++; continue; }
            }
            b.append(c);
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ With (REGEX)

    /** Expands $0 $& $1..$99 ${name} $$ and the backslash codes (n t, double backslash, U L E, small u l) for one match. An unknown group stays as written. */
    static String expand(String w, Matcher m) {
        StringBuilder sb = new StringBuilder();
        int mode = 0, once = 0;   // mode: 1 upper, 2 lower; once: 1 upper, 2 lower (next letter only)
        int n = w.length();
        int i = 0;
        while (i < n) {
            char c = w.charAt(i);
            String piece = null;
            int adv = 1;
            if (c == '\\' && i + 1 < n) {
                char d = w.charAt(i + 1);
                adv = 2;
                if (d == 'n') piece = "\n";
                else if (d == 't') piece = "\t";
                else if (d == '\\') piece = "\\";
                else if (d == '$') piece = "$";
                else if (d == 'U') { mode = 1; piece = ""; }
                else if (d == 'L') { mode = 2; piece = ""; }
                else if (d == 'E') { mode = 0; once = 0; piece = ""; }
                else if (d == 'u') { once = 1; piece = ""; }
                else if (d == 'l') { once = 2; piece = ""; }
                else { piece = "\\"; adv = 1; }
            } else if (c == '$' && i + 1 < n) {
                char d = w.charAt(i + 1);
                adv = 2;
                if (d == '$') piece = "$";
                else if (d == '&') piece = m.group();
                else if (d >= '0' && d <= '9') {
                    int g = d - '0';
                    if (i + 2 < n && w.charAt(i + 2) >= '0' && w.charAt(i + 2) <= '9' && g * 10 + (w.charAt(i + 2) - '0') <= m.groupCount()) {
                        g = g * 10 + (w.charAt(i + 2) - '0');
                        adv = 3;
                    }
                    if (g <= m.groupCount()) { String s = m.group(g); piece = s == null ? "" : s; }
                    else { piece = "$" + d; }
                } else if (d == '{') {
                    int e = w.indexOf('}', i + 2);
                    if (e > i + 2) {
                        try {
                            String s = m.group(w.substring(i + 2, e));
                            piece = s == null ? "" : s;
                            adv = e + 1 - i;
                        } catch (IllegalArgumentException ex) { piece = null; adv = 1; }
                    }
                }
                if (piece == null) { piece = "$"; adv = 1; }
            }
            if (piece == null) {
                int cp = w.codePointAt(i);
                adv = Character.charCount(cp);
                piece = w.substring(i, i + adv);
            }
            i += adv;
            if (piece.length() == 0) continue;
            if (mode == 1) piece = piece.toUpperCase(Locale.ROOT);
            else if (mode == 2) piece = piece.toLowerCase(Locale.ROOT);
            if (once != 0) {
                int k = Character.charCount(piece.codePointAt(0));
                String h = piece.substring(0, k);
                h = once == 1 ? h.toUpperCase(Locale.ROOT) : h.toLowerCase(Locale.ROOT);
                piece = h + piece.substring(k);
                once = 0;
            }
            sb.append(piece);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ texts for the panel

    private static String vis(String s, int max) {
        if (s == null) return "";
        String t = s.replace("\n", "\u23CE").replace("\t", "\u21E5");
        if (t.length() > max) t = t.substring(0, max) + "\u2026";
        return t;
    }

    /** One preview line: ...before [found -> replacement] after... (new lines shown as a return sign). */
    public static String line(String text, Hit h, int ctx) {
        if (text == null || h == null) return "";
        int a = Math.max(0, h.start - ctx);
        int b = Math.min(text.length(), h.end + ctx);
        if (a > 0 && a < text.length() && Character.isLowSurrogate(text.charAt(a))) a--;
        if (b < text.length() && b > 0 && Character.isLowSurrogate(text.charAt(b))) b++;
        String f = h.found.length() == 0 ? "\u2205" : vis(h.found, 40);
        String r = h.repl.length() == 0 ? "\u2205" : vis(h.repl, 40);
        return (a > 0 ? "\u2026" : "") + vis(text.substring(a, h.start), 60) + "[" + f + " \u2192 " + r + "]" + vis(text.substring(h.end, b), 60) + (b < text.length() ? "\u2026" : "");
    }

    /**
     * v0.26.0 (R3): the same preview line as line(), but in 4 pieces {before, found, replacement, after}, so the panel can draw the found text bold and
     * the replacement in the accent colour. before + "[" + found + " \u2192 " + replacement + "]" + after == line(text, h, ctx).
     */
    public static String[] lineParts(String text, Hit h, int ctx) {
        if (text == null || h == null) return new String[] { "", "", "", "" };
        int a = Math.max(0, h.start - ctx);
        int b = Math.min(text.length(), h.end + ctx);
        if (a > 0 && a < text.length() && Character.isLowSurrogate(text.charAt(a))) a--;
        if (b < text.length() && b > 0 && Character.isLowSurrogate(text.charAt(b))) b++;
        String f = h.found.length() == 0 ? "\u2205" : vis(h.found, 40);
        String r = h.repl.length() == 0 ? "\u2205" : vis(h.repl, 40);
        return new String[] {
            (a > 0 ? "\u2026" : "") + vis(text.substring(a, h.start), 60),
            f, r,
            vis(text.substring(h.end, b), 60) + (b < text.length() ? "\u2026" : "") };
    }

    /** Status line under the fields. */
    public static String info(Result r) {
        if (r == null) return "";
        if (r.err != null) return r.err;
        if (r.blank) return "Type what to find";
        if (r.count == 0) return "No match";
        return r.count == 1 ? "1 match" : r.count + " matches";
    }

    // ------------------------------------------------------------------ options <-> one number (pref rp_opt)

    public static int pack(Opt o) {
        if (o == null) return pack(new Opt());
        int mode = o.mode < 0 || o.mode > 2 ? 0 : o.mode;
        return mode | (o.matchCase ? 4 : 0) | (o.bangla ? 8 : 0) | (o.escapes ? 16 : 0) | (o.keepCase ? 32 : 0);
    }

    public static Opt unpack(int v) {
        Opt o = new Opt();
        if (v < 0) return o;
        int mode = v & 3;
        o.mode = mode > 2 ? 0 : mode;
        o.matchCase = (v & 4) != 0;
        o.bangla = (v & 8) != 0;
        o.escapes = (v & 16) != 0;
        o.keepCase = (v & 32) != 0;
        return o;
    }
}
