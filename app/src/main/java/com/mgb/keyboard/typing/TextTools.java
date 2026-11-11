package com.mgb.keyboard.typing;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * v0.4.6 text tools (pure Java, no Android classes, tested by tools/TextToolsTest.java).
 * counts / caseConvert / clean / repeat / dateTime. Every method is safe for null and never throws.
 * Bangla text passes through unchanged by the case tools (Bangla has no upper / lower case).
 */
public final class TextTools {

    private TextTools() { }

    public static final int UPPER = 0, LOWER = 1, TITLE = 2, SENTENCE = 3;
    public static final int MAX_REPEAT = 50;
    public static final int MAX_OUT = 50000;   // repeat() never returns more characters than this

    public static final String[] SEPARATORS = { "\n", " ", "\n\n", "" };
    public static final String[] SEPARATOR_NAMES = { "New line", "Space", "Blank line", "None" };

    public static final String[] DATE_FORMATS = { "dd/MM/yyyy", "yyyy-MM-dd", "dd MMM yyyy", "EEEE, dd MMMM yyyy", "HH:mm", "yyyy-MM-dd HH:mm" };

    // ------------------------------------------------------------------ counts

    /** {words, characters, characters without whitespace, sentences, lines, paragraphs}. Characters = code points. */
    public static int[] counts(String text) {
        int[] r = new int[6];
        if (text == null || text.length() == 0) return r;
        int n = text.length();
        boolean inWord = false;
        int cp, i = 0;
        int sentences = 0;
        boolean pendingEnd = false, anyLetter = false;
        while (i < n) {
            cp = text.codePointAt(i);
            i += Character.charCount(cp);
            r[1]++;
            boolean ws = Character.isWhitespace(cp);
            if (!ws) {
                r[2]++;
                if (!inWord) { r[0]++; inWord = true; }
            } else inWord = false;
            if (cp == '.' || cp == '!' || cp == '?' || cp == 0x0964 /* danda */) {
                if (anyLetter) pendingEnd = true;
            } else if (!ws) {
                if (pendingEnd) { sentences++; pendingEnd = false; }
                anyLetter = true;
            }
        }
        if (pendingEnd) sentences++;
        else if (anyLetter) sentences++;   // text without an end mark = 1 sentence
        r[3] = sentences;
        // lines: number of line parts (a trailing newline does not add an empty line)
        String t = text.endsWith("\n") ? text.substring(0, n - 1) : text;
        int lines = 1;
        for (int k = 0; k < t.length(); k++) if (t.charAt(k) == '\n') lines++;
        r[4] = lines;
        // paragraphs: blocks of non-blank lines
        int paras = 0;
        boolean inPara = false;
        for (String ln : text.split("\n", -1)) {
            if (ln.trim().length() > 0) { if (!inPara) { paras++; inPara = true; } } else inPara = false;
        }
        r[5] = paras;
        return r;
    }

    public static String countsLine(String text) {
        int[] c = counts(text);
        return "Words " + c[0] + " · Characters " + c[1] + " (no spaces " + c[2] + ") · Sentences " + c[3] + " · Lines " + c[4] + " · Paragraphs " + c[5];
    }

    // ------------------------------------------------------------------ case

    public static String caseConvert(String text, int mode) {
        if (text == null) return "";
        switch (mode) {
            case UPPER: return text.toUpperCase(Locale.ROOT);
            case LOWER: return text.toLowerCase(Locale.ROOT);
            case TITLE: {
                String low = text.toLowerCase(Locale.ROOT);
                StringBuilder sb = new StringBuilder(low.length());
                boolean start = true;
                for (int i = 0; i < low.length(); ) {
                    int cp = low.codePointAt(i);
                    i += Character.charCount(cp);
                    if (Character.isWhitespace(cp) || cp == '-' || cp == '(' || cp == '"' || cp == '“') { sb.appendCodePoint(cp); start = true; }
                    else if (start && Character.isLetter(cp)) { sb.appendCodePoint(Character.toTitleCase(cp)); start = false; }
                    else { sb.appendCodePoint(cp); start = false; }
                }
                return sb.toString();
            }
            case SENTENCE: {
                String low = text.toLowerCase(Locale.ROOT);
                StringBuilder sb = new StringBuilder(low.length());
                boolean start = true;
                for (int i = 0; i < low.length(); ) {
                    int cp = low.codePointAt(i);
                    i += Character.charCount(cp);
                    if (start && Character.isLetter(cp)) { sb.appendCodePoint(Character.toTitleCase(cp)); start = false; }
                    else {
                        sb.appendCodePoint(cp);
                        if (cp == '.' || cp == '!' || cp == '?' || cp == 0x0964 || cp == '\n') start = true;
                        else if (!Character.isWhitespace(cp) && cp != '"' && cp != '\'' && cp != '(' && cp != '“') start = false;
                    }
                }
                return sb.toString();
            }
            default: return text;
        }
    }

    // ------------------------------------------------------------------ clean

    /**
     * extraSpaces: runs of spaces / tabs inside a line become one space (line breaks are kept).
     * trailing: spaces / tabs at the end of every line are removed.
     * blankLines: 2 or more blank lines in a row become ONE blank line.
     */
    public static String clean(String text, boolean extraSpaces, boolean blankLines, boolean trailing) {
        if (text == null) return "";
        String t = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = t.split("\n", -1);
        StringBuilder sb = new StringBuilder(t.length());
        int blankRun = 0;
        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i];
            if (extraSpaces) ln = ln.replaceAll("[ \\t\\u00A0]{2,}", " ");
            if (trailing) ln = ln.replaceAll("[ \\t\\u00A0]+$", "");
            boolean blank = ln.trim().length() == 0;
            if (blankLines && blank) {
                blankRun++;
                if (blankRun > 1) continue;
            } else blankRun = 0;
            if (sb.length() > 0 || i > 0) sb.append('\n');
            sb.append(ln);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ repeat

    /** n copies joined by sep. n is limited to 1..MAX_REPEAT; the result never gets longer than MAX_OUT (fewer copies are made instead). */
    public static String repeat(String text, int n, String sep) {
        if (text == null || text.length() == 0) return "";
        if (n < 1) n = 1;
        if (n > MAX_REPEAT) n = MAX_REPEAT;
        String s = sep == null ? "" : sep;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            int add = text.length() + (i > 0 ? s.length() : 0);
            if (sb.length() + add > MAX_OUT) break;
            if (i > 0) sb.append(s);
            sb.append(text);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ date / time

    public static String dateTime(int preset, long millis, TimeZone tz) {
        try {
            if (preset < 0 || preset >= DATE_FORMATS.length) preset = 0;
            SimpleDateFormat f = new SimpleDateFormat(DATE_FORMATS[preset], Locale.ENGLISH);
            if (tz != null) f.setTimeZone(tz);
            return f.format(new java.util.Date(millis));
        } catch (Throwable t) {
            return "";
        }
    }
}
