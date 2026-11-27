package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * v0.40.0 PAIR LIST ("+ Pair") of the Find & Replace panel (idea from the Bulk Replace tool). PURE JAVA (no Android), never throws.
 * The panel keeps a list of rules in MEMORY while the keyboard runs (the typed texts are not written to disk, like Find / With).
 * "Save as Set" is the only way to keep the list: it is written as a normal Set (the user's own action).
 * make() turns the Find / With texts + the panel switches into ONE ReplaceRules.Rule that is guaranteed to survive serialize() > parse():
 *  - Find is trimmed, With loses trailing spaces (what parse() does anyway); no line breaks; Find has no "=>" and does not start with a keyword ("re:", "del:", "word:", "#" ...).
 *  - List switch ON (Find = a|b|c) becomes the variants form "a | b | c". v0.56.0: two or more pieces split by ";" do the same without the switch (a single ";" piece stays text).
 *  - Word mode = "word:", Regex mode = "re:" (the pattern is tested here), Keep switch = "keep:".
 */
public final class ReplacePairList {
    private ReplacePairList() { }

    public static final int MAX = ReplaceRules.MAX_RULES;

    public static final class Made {
        public final ReplaceRules.Rule rule;
        public final String error;     // null = ok
        Made(ReplaceRules.Rule rule, String error) { this.rule = rule; this.error = error; }
    }

    private static final String[] RESERVED = { "re:", "del:", "del-re:", "word:", "keep:", "smart:", "#" };

    private static Made err(String e) { return new Made(null, e); }

    public static Made make(String find0, String with0, int mode, boolean keep, boolean list) { return make(find0, with0, mode, keep, list, false); }

    /** v0.41.0: smart = the pair gets "smart:" (smart matching; plain-text pairs only, ignored for Regex). */
    public static Made make(String find0, String with0, int mode, boolean keep, boolean list, boolean smart) {
        try {
            String find = find0 == null ? "" : find0.trim();
            String with = with0 == null ? "" : with0;
            int e = with.length();
            while (e > 0 && Character.isWhitespace(with.charAt(e - 1))) e--;
            with = with.substring(0, e);
            if (with.trim().isEmpty() && mode != ReplaceEngine.MODE_REGEX && find.indexOf("=>") > 0) {   // v0.58.8: one line "Gaam;geme;gome => game" typed in Find (With empty) = Find + With
                int k = find.indexOf("=>");
                with = find.substring(k + 2).trim();
                find = find.substring(0, k).trim();
            }
            if (find.isEmpty()) return err("Type what to find first");
            if (find.indexOf('\n') >= 0 || find.indexOf('\r') >= 0 || with.indexOf('\n') >= 0 || with.indexOf('\r') >= 0)
                return err("A pair cannot hold a line break (use the \\n switch and type \\n)");
            if (find.contains("=>")) return err("Find cannot contain =>");
            boolean regex = mode == ReplaceEngine.MODE_REGEX;
            if (regex) {
                try { Pattern.compile(find); } catch (PatternSyntaxException ex) { return err("Pattern error: " + (ex.getDescription() == null ? "?" : ex.getDescription())); }
                return new Made(new ReplaceRules.Rule(ReplaceRules.K_REGEX, find, with, false, keep), null);
            }
            boolean semi = !list && find.indexOf(';') >= 0;   // v0.56.0 (NV2): "Lin Fen; Lin Fong; Lin Fan" = three wrong forms of one name
            if (list || semi) {   // a|b|c (List switch) or a;b;c  ->  a | b | c
                List<String> parts = new ArrayList<String>();
                for (String x : find.split(list ? "[;|]" : ";", -1)) { String t = x.trim(); if (t.length() > 0 && !parts.contains(t)) parts.add(t); }
                if (parts.isEmpty() && list) return err("Type what to find first");
                if (list || parts.size() >= 2) {   // ONE piece with a ";" ("&amp;") stays plain text
                    StringBuilder sb = new StringBuilder();
                    for (String t : parts) { if (sb.length() > 0) sb.append(" | "); sb.append(t); }
                    find = sb.toString();
                }
            }
            String low = find.toLowerCase(java.util.Locale.ROOT);
            for (String k : RESERVED) if (low.startsWith(k)) return err("Find cannot start with \"" + k + "\"");
            return new Made(new ReplaceRules.Rule(ReplaceRules.K_TEXT, find, with, mode == ReplaceEngine.MODE_WORD, keep, smart), null);
        } catch (Throwable t) { return err("Could not make this pair"); }
    }

    /** Index of a rule with the same kind + Find (case-sensitive, flags ignored), -1 = none. */
    public static int indexOf(List<ReplaceRules.Rule> list, ReplaceRules.Rule r) {
        if (list == null || r == null) return -1;
        for (int i = 0; i < list.size(); i++) { ReplaceRules.Rule q = list.get(i); if (q != null && q.kind == r.kind && q.find.equals(r.find)) return i; }
        return -1;
    }

    /** "3× cat → dog" / "cat → dog" (count 0) / "(word) ..." (whole word) / "(re) ..." / "(keep) ...". Cut to max characters. */
    public static String label(ReplaceRules.Rule r, int count, int max) {
        if (r == null) return "";
        StringBuilder sb = new StringBuilder();
        if (count > 0) sb.append(count).append("×  ");
        if (r.kind == ReplaceRules.K_REGEX) sb.append("(re) ");
        else if (r.word) sb.append("(word) ");
        if (r.keep) sb.append("(keep) ");
        if (r.smart) sb.append("(smart) ");
        sb.append(r.find.replace('\n', ' ')).append(" → ").append(r.with.length() == 0 ? "(delete)" : r.with.replace('\n', ' '));
        String s = sb.toString();
        if (max > 3 && s.length() > max) {
            int c = max - 1;
            if (Character.isHighSurrogate(s.charAt(c - 1))) c--;
            s = s.substring(0, c) + "…";
        }
        return s;
    }

    /** "Pairs 1", "Pairs 2" ... the first name that is not used yet (names are compared without case). */
    public static String nextName(Collection<String> existing) {
        for (int i = 1; i < 1000; i++) {
            String n = "Pairs " + i;
            boolean used = false;
            if (existing != null) for (String x : existing) if (x != null && x.equalsIgnoreCase(n)) { used = true; break; }
            if (!used) return n;
        }
        return "Pairs";
    }

    /** The list as set text (one rule per line). */
    public static String text(List<ReplaceRules.Rule> list) { return ReplaceRules.serialize(list == null ? new ArrayList<ReplaceRules.Rule>() : list); }
}
