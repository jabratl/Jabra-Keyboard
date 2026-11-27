package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.54.0 (GL1): reads the text the Jabra Novel Assistant plugin copies with "Copy for keyboard" and turns it into Replace sets + Never change terms.
 * Format (sections in this order, every line is a normal Replace rule, "#" lines are comments):
 *   ## jabra-glossary v1 | Novel A | newest names
 *   ## wrong forms        (word: Lin Fong | Lin Fen => Lin Feng)
 *   ## raw to English     (林风 => Lin Feng)
 *   ## never change       (one term per line)
 * One set holds at most 50 rules (ReplaceRules.MAX_RULES), so a long section becomes several sets: "G Novel A · raw 1", "G Novel A · raw 2" ...
 * The names are the same every time, so pasting again REPLACES the old glossary sets (the caller removes every set that starts with prefix + " · ").
 * v0.55.0 (NV1): a section header may carry the term type of the plugin: "## wrong forms | Characters", "## raw to English | Places". Every (type, kind) pair becomes its own set
 * ("G Novel A · Characters · wrong 1"). No type = the old names ("G Novel A · wrong"), shown under the topic "Terms". Novel = the glossary title.
 * PURE JAVA, never throws. Tested by GlossaryImportTest + NovelBookTest.
 */
public final class GlossaryImport {
    private GlossaryImport() { }

    public static final int CHUNK = 50;
    public static final String SEP = " \u00B7 ";
    private static final int PREFIX_MAX = 26;

    public static final class Plan {
        public String title = "";
        public String novel = "";                                 // v0.55.0: cleaned title = the novel this glossary belongs to ("" = no title)
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
            Map<String, List<String>> wrong = new LinkedHashMap<String, List<String>>(), raw = new LinkedHashMap<String, List<String>>();
            int sec = 0;   // 1 wrong, 2 raw, 3 never
            String topic = "";
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
                    String head = low, tp = "";
                    int bar = l.indexOf('|');
                    if (bar >= 0) { head = low.substring(0, bar); tp = cleanTopic(l.substring(bar + 1)); }
                    if (head.contains("wrong")) sec = 1;
                    else if (head.contains("raw")) sec = 2;
                    else if (head.contains("never")) sec = 3;
                    topic = tp;
                    continue;
                }
                if (l.charAt(0) == '#') continue;
                if (sec == 3) { if (l.length() <= 60) p.never.add(l); else p.bad++; continue; }
                int k = l.indexOf("=>");
                if (k <= 0) { p.bad++; continue; }
                Map<String, List<String>> dst = (sec == 2) ? raw : wrong;   // lines before any section = wrong forms
                List<String> bucket = dst.get(topic);
                if (bucket == null) { bucket = new ArrayList<String>(); dst.put(topic, bucket); }
                bucket.add(l);
                if (sec == 2) p.raw++; else p.wrong++;
            }
            String t = p.title.replaceAll("[\\r\\n=|]", " ").replace(SEP.trim(), "-").replaceAll("\\s+", " ").trim();
            p.novel = t.length() > PREFIX_MAX - 2 ? t.substring(0, PREFIX_MAX - 2).trim() : t;
            p.prefix = p.novel.isEmpty() ? "G" : "G " + p.novel;
            int cap = Math.max(0, maxSets);
            for (Map.Entry<String, List<String>> e : wrong.entrySet()) chunk(p, e.getValue(), e.getKey(), "wrong", false, cap);
            for (Map.Entry<String, List<String>> e : raw.entrySet()) chunk(p, e.getValue(), e.getKey(), "raw", true, cap);
        } catch (Throwable t) { /* a half result is returned */ }
        return p;
    }

    private static void chunk(Plan p, List<String> rules, String topic, String word, boolean matchCase, int cap) {
        if (rules.isEmpty()) return;
        int parts = (rules.size() + CHUNK - 1) / CHUNK;
        for (int i = 0; i < parts; i++) {
            int a = i * CHUNK, b = Math.min(rules.size(), a + CHUNK);
            if (p.setNames.size() >= cap) { p.droppedSets++; p.droppedRules += b - a; continue; }
            StringBuilder sb = new StringBuilder();
            String name = p.prefix + SEP + (topic.isEmpty() ? "" : topic + SEP) + word + (parts > 1 ? " " + (i + 1) : "");
            sb.append("# ").append(name).append(" (from the Jabra Novel Assistant glossary)\n");
            for (int j = a; j < b; j++) sb.append(rules.get(j)).append('\n');
            p.setNames.add(name); p.setTexts.add(sb.toString()); p.setCase.add(matchCase);
        }
    }

    public static final int TOPIC_MAX = 14;
    public static final String NO_TOPIC = "Terms";

    /** A topic name from the plugin: no separators, one line, at most TOPIC_MAX characters, first letter upper case. */
    public static String cleanTopic(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[\\r\\n=|#]", " ").replace(SEP.trim(), "-").replaceAll("\\s+", " ").trim();
        if (t.length() > TOPIC_MAX) t = t.substring(0, TOPIC_MAX).trim();
        if (!t.isEmpty()) t = t.substring(0, 1).toUpperCase() + t.substring(1);
        return t;
    }

    /** The novel a glossary set belongs to ("G Novel A, Characters, wrong 1" gives "Novel A"), or "" when the name is not a glossary set / has no title. */
    public static String novelOf(String setName) {
        if (!isGlossarySet(setName)) return "";
        String head = setName.substring(0, setName.indexOf(SEP));
        return head.length() > 2 ? head.substring(2).trim() : "";
    }

    private static String[] tail(String setName) {
        if (!isGlossarySet(setName)) return new String[0];
        String rest = setName.substring(setName.indexOf(SEP) + SEP.length());
        return rest.split(java.util.regex.Pattern.quote(SEP), -1);
    }

    /** Topic of a glossary set: the middle part, or "Terms" for the old names without a type. "" = not a glossary set. */
    public static String topicOf(String setName) {
        String[] t = tail(setName);
        if (t.length == 0) return "";
        return t.length >= 2 && !t[0].trim().isEmpty() ? t[0].trim() : NO_TOPIC;
    }

    /** "wrong" or "raw" (the last part of the name without its number). "" = not a glossary set. */
    public static String kindOf(String setName) {
        String[] t = tail(setName);
        if (t.length == 0) return "";
        String k = t[t.length - 1].trim().toLowerCase();
        return k.startsWith("raw") ? "raw" : "wrong";
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
