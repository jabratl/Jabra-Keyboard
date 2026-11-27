package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.54.0 (GL1): reads the text the Jabra Novel Assistant plugin copies with "Copy for keyboard" and turns it into Replace sets + Never change terms.
 * Format (sections in this order, every line is a normal Replace rule, "#" lines are comments):
 *   ## jabra-glossary v1 | Novel A | newest names
 *   ## wrong forms        (word: Lin Fong | Lin Fen => Lin Feng)
 *   ## raw to English     (林风 => Lin Feng)
 *   ## never change       (one term per line)
 * One set holds at most 50 rules (ReplaceRules.MAX_RULES), so a long section becomes several sets: "G Novel A · raw 1", "G Novel A · raw 2" ...
 * The names are the same every time, so pasting again REPLACES the old glossary sets (the caller removes every set that starts with prefix + " · ").
 * PURE JAVA, never throws. Tested by GlossaryImportTest.
 */
public final class GlossaryImport {
    private GlossaryImport() { }

    public static final int CHUNK = 50;
    public static final String SEP = " \u00B7 ";
    private static final int PREFIX_MAX = 26;

    public static final class Plan {
        public String title = "";
        public String prefix = "G";                                // "G Novel A": every set of this glossary starts with prefix + SEP
        public final List<String> setNames = new ArrayList<String>();
        public final List<String> setTexts = new ArrayList<String>();
        public final List<Boolean> setCase = new ArrayList<Boolean>();   // matchCase flag of each set
        public final List<String> never = new ArrayList<String>();
        public int wrong = 0, raw = 0;       // rule lines read
        public int bad = 0;                  // lines that are not a rule (ignored)
        public int droppedSets = 0;          // sets that did not fit under maxSets
        public int droppedRules = 0;         // rules inside those sets
    }

    /** True when the text carries the plugin header. */
    public static boolean looksLike(String text) {
        if (text == null) return false;
        int n = Math.min(text.length(), 400);
        return text.substring(0, n).toLowerCase().contains("## jabra-glossary");
    }

    /** A saved set that was made from a glossary (name "G ... · ..."). */
    public static boolean isGlossarySet(String name) {
        return name != null && name.startsWith("G ") && name.contains(SEP);
    }

    public static Plan parse(String text, int maxSets) {
        Plan p = new Plan();
        try {
            List<String> wrong = new ArrayList<String>(), raw = new ArrayList<String>();
            int sec = 0;   // 1 wrong, 2 raw, 3 never
            String[] lines = String.valueOf(text == null ? "" : text).split("\n", -1);
            for (String l0 : lines) {
                String l = l0.replace("\r", "").trim();
                if (l.isEmpty()) continue;
                String low = l.toLowerCase();
                if (low.startsWith("## jabra-glossary")) {
                    String[] f = l.split("\\|");
                    if (f.length > 1) p.title = f[1].trim();
                    continue;
                }
                if (low.startsWith("##")) {
                    if (low.contains("wrong")) sec = 1;
                    else if (low.contains("raw")) sec = 2;
                    else if (low.contains("never")) sec = 3;
                    continue;
                }
                if (l.charAt(0) == '#') continue;
                if (sec == 3) { if (l.length() <= 60) p.never.add(l); else p.bad++; continue; }
                int k = l.indexOf("=>");
                if (k <= 0) { p.bad++; continue; }
                if (sec == 2) raw.add(l); else wrong.add(l);   // lines before any section = wrong forms
            }
            p.wrong = wrong.size(); p.raw = raw.size();
            String t = p.title.replaceAll("[\\r\\n=|]", " ").trim();
            if (t.length() > PREFIX_MAX - 2) t = t.substring(0, PREFIX_MAX - 2).trim();
            p.prefix = t.isEmpty() ? "G" : "G " + t;
            int cap = Math.max(0, maxSets);
            chunk(p, wrong, "wrong", false, cap);
            chunk(p, raw, "raw", true, cap);
        } catch (Throwable t) { /* a half result is returned */ }
        return p;
    }

    private static void chunk(Plan p, List<String> rules, String word, boolean matchCase, int cap) {
        if (rules.isEmpty()) return;
        int parts = (rules.size() + CHUNK - 1) / CHUNK;
        for (int i = 0; i < parts; i++) {
            int a = i * CHUNK, b = Math.min(rules.size(), a + CHUNK);
            if (p.setNames.size() >= cap) { p.droppedSets++; p.droppedRules += b - a; continue; }
            StringBuilder sb = new StringBuilder();
            String name = p.prefix + SEP + word + (parts > 1 ? " " + (i + 1) : "");
            sb.append("# ").append(name).append(" (from the Jabra Novel Assistant glossary)\n");
            for (int j = a; j < b; j++) sb.append(rules.get(j)).append('\n');
            p.setNames.add(name); p.setTexts.add(sb.toString()); p.setCase.add(matchCase);
        }
    }

    /** One line for the screen. */
    public static String summary(Plan p, int neverAdded, int neverDup, int neverFull) {
        StringBuilder s = new StringBuilder();
        s.append(p.title.isEmpty() ? "Glossary" : p.title).append(": ").append(p.setNames.size()).append(p.setNames.size() == 1 ? " set" : " sets")
         .append(" (").append(p.wrong).append(" wrong forms, ").append(p.raw).append(" raw terms)");
        if (!p.never.isEmpty()) s.append(", Never change +").append(neverAdded);
        if (neverDup > 0) s.append(" (").append(neverDup).append(" already there)");
        if (neverFull > 0) s.append(", ").append(neverFull).append(" not added: list full");
        if (p.droppedSets > 0) s.append(". ").append(p.droppedSets).append(" sets (").append(p.droppedRules).append(" rules) did not fit: delete old sets first");
        if (p.bad > 0) s.append(". ").append(p.bad).append(" lines ignored");
        return s.toString();
    }
}
