import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * v0.10.22 (W8) makes assets/words/<lang>.txt from a frequency list (pure Java, no Android; tested by tools/WordBuildTest.java).
 * Input  : text, one entry per line, "word" or "word<space>count", MOST FREQUENT FIRST (FrequencyWords en_50k.txt / bn_50k.txt).
 * Output : the JABRA-WORDS 1 format that typing/WordIndex reads (#JABRA-WORDS 1 / #lang / #count / words).
 * Rules  : the old starter words stay ON TOP (same order); then the list in its order. Dedupe ignoring case. Latin: lower-case,
 *          a-z and an inner apostrophe only, >= 2 letters. Bangla: Bengali block only, no digits, starts with a letter (not a sign),
 *          >= 1 char ("ও" is a word). Tokenizer fragments (don, didn, ll, ve ...) dropped. Max word length 30. Stops at maxWords or maxBytes (output size), whichever comes first.
 * Run    : sh tools/build_words.sh en en_50k.txt   |   sh tools/build_words.sh bn bn_50k.txt
 */
public final class WordBuild {
    public static final int MAX_WORD = 30;

    /** First token of a line ("you 28787591" -> "you"); "" when blank. Strips a BOM. */
    static String token(String line) {
        if (line == null) return "";
        String s = line;
        if (!s.isEmpty() && s.charAt(0) == '﻿') s = s.substring(1);
        s = s.trim();
        if (s.isEmpty()) return "";
        int i = 0;
        while (i < s.length() && !Character.isWhitespace(s.charAt(i))) i++;
        return s.substring(0, i);
    }

    /** Pieces left when a subtitle tokenizer splits "don't" / "I'll" at the apostrophe: not words, never suggested. */
    static final Set<String> FRAGMENTS = new HashSet<String>(java.util.Arrays.asList(
        "ll", "ve", "re", "don", "didn", "doesn", "isn", "wasn", "aren", "weren", "hasn", "haven", "hadn",
        "couldn", "wouldn", "shouldn", "mustn", "needn", "ain", "mightn", "shan"));

    /** Clean Latin word or null when it is not acceptable. */
    static String cleanEn(String t) {
        if (t == null) return null;
        String w = t.toLowerCase(Locale.ROOT).replace('’', '\'');
        int n = w.length();
        if (n < 2 || n > MAX_WORD) return null;
        if (FRAGMENTS.contains(w)) return null;
        int letters = 0;
        for (int i = 0; i < n; i++) {
            char c = w.charAt(i);
            if (c >= 'a' && c <= 'z') { letters++; continue; }
            if (c == '\'' && i > 0 && i < n - 1) continue;
            return null;
        }
        return letters >= 2 ? w : null;
    }

    /** Clean Bangla word or null. */
    static String cleanBn(String t) {
        if (t == null) return null;
        int n = t.length();
        if (n < 1 || n > MAX_WORD) return null;
        for (int i = 0; i < n; i++) {
            char c = t.charAt(i);
            if (c < 0x0980 || c > 0x09FF) return null;
            if (c >= 0x09E6 && c <= 0x09EF) return null;      // Bengali digits
            if (c >= 0x09F2 && c <= 0x09FB) return null;      // currency / numerators
        }
        char f = t.charAt(0);
        boolean letter = (f >= 0x0985 && f <= 0x09B9) || (f >= 0x09DC && f <= 0x09DF) || f == 0x09CE;
        return letter ? t : null;
    }

    static boolean isBn(String lang) { return "bn".equals(lang); }

    /** Builds the whole output file text. [starter] = old file text or "" (its words go first). */
    public static String build(String lang, String freqText, String starterText, int maxWords, int maxBytes) {
        List<String> out = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        long bytes = 0;
        int headRoom = 64;
        // 1) starter words (already clean in their file, only dedupe + minimal check)
        if (starterText != null) {
            for (String l : starterText.split("\n", -1)) {
                if (l.endsWith("\r")) l = l.substring(0, l.length() - 1);
                String w = l.trim();
                if (w.isEmpty() || w.charAt(0) == '#' || w.length() > MAX_WORD) continue;
                if (out.size() >= maxWords) break;
                if (seen.add(w.toLowerCase(Locale.ROOT))) { out.add(w); bytes += utf8(w) + 1; }
            }
        }
        // 2) frequency list
        if (freqText != null) {
            for (String l : freqText.split("\n", -1)) {
                if (out.size() >= maxWords) break;
                String t = token(l);
                if (t.isEmpty()) continue;
                String w = isBn(lang) ? cleanBn(t) : cleanEn(t);
                if (w == null) continue;
                if (seen.contains(w.toLowerCase(Locale.ROOT))) continue;
                long add = utf8(w) + 1;
                if (bytes + add + headRoom > maxBytes) break;
                seen.add(w.toLowerCase(Locale.ROOT));
                out.add(w);
                bytes += add;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("#JABRA-WORDS 1\n#lang ").append(lang).append("\n#count ").append(out.size()).append('\n');
        for (String w : out) sb.append(w).append('\n');
        return sb.toString();
    }

    static int utf8(String s) { return s.getBytes(StandardCharsets.UTF_8).length; }

    static String read(File f) throws Exception {
        if (f == null || !f.exists()) return "";
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } finally { in.close(); }
    }

    /** args: lang freqFile starterFile outFile [maxWords] [maxBytes] */
    public static void main(String[] a) throws Exception {
        if (a.length < 4) { System.out.println("usage: WordBuild <en|bn> <freq.txt> <starter words file> <out file> [maxWords] [maxBytes]"); System.exit(2); }
        int mw = a.length > 4 ? Integer.parseInt(a[4]) : 30000;
        int mb = a.length > 5 ? Integer.parseInt(a[5]) : 400 * 1024;
        String text = build(a[0], read(new File(a[1])), read(new File(a[2])), mw, mb);
        FileOutputStream o = new FileOutputStream(a[3]);
        try { o.write(text.getBytes(StandardCharsets.UTF_8)); } finally { o.close(); }
        int cnt = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') cnt++;
        System.out.println("wrote " + a[3] + ": " + (cnt - 3) + " words, " + utf8(text) + " bytes");
    }
}
