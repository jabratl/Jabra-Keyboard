package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.28.0 (R3b) small pure helpers of the Find & Replace panel. No Android, never throws.
 *  - UndoStack: the last MAX Replace taps (result text + original text), memory only, newest on top. Never written to disk.
 *  - formatterText(): a rule set as text for the Formatter's Rules page ("Rules from clipboard"). Same syntax, so only a comment header is added.
 */
public final class ReplaceExtras {
    private ReplaceExtras() { }

    public static final int MAX_UNDO = 3;

    public static final class Step {
        public final String res, orig;
        Step(String res, String orig) { this.res = res == null ? "" : res; this.orig = orig == null ? "" : orig; }
    }

    public static final class UndoStack {
        private final List<Step> l = new ArrayList<Step>();

        /** Adds a replace. A step that changed nothing (res equals orig) is ignored. The oldest step is dropped above MAX_UNDO. */
        public void push(String res, String orig) {
            if (res == null || orig == null || res.equals(orig)) return;
            l.add(new Step(res, orig));
            while (l.size() > MAX_UNDO) l.remove(0);
        }

        public boolean canUndo() { return !l.isEmpty(); }
        public int size() { return l.size(); }

        /** The newest step (stays in the stack until drop()), or null. */
        public Step peek() { return l.isEmpty() ? null : l.get(l.size() - 1); }

        /** Removes the newest step (after a successful undo). */
        public void drop() { if (!l.isEmpty()) l.remove(l.size() - 1); }

        public void clear() { l.clear(); }
    }

    /** First [n] chars, never cutting a surrogate pair in half (emoji, rare Chinese / Japanese characters). null = "". n <= 0 = "". */
    public static String head(String s, int n) {
        if (s == null || n <= 0) return "";
        if (s.length() <= n) return s;
        int e = n;
        if (Character.isHighSurrogate(s.charAt(e - 1)) && Character.isLowSurrogate(s.charAt(e))) e--;
        return s.substring(0, e);
    }

    /** Last [n] chars, never cutting a surrogate pair in half. null = "". n <= 0 = "". */
    public static String tail(String s, int n) {
        if (s == null || n <= 0) return "";
        if (s.length() <= n) return s;
        int b = s.length() - n;
        if (Character.isLowSurrogate(s.charAt(b)) && Character.isHighSurrogate(s.charAt(b - 1))) b++;
        return s.substring(b);
    }

    /** Label of the Undo button: "Undo" for one step, "Undo (2)" for more. */
    public static String undoLabel(int n) { return n > 1 ? "Undo (" + n + ")" : "Undo"; }

    /**
     * The set as Formatter rules text. Header = 2 comment lines (the Formatter and the sets both skip # lines).
     * The Formatter has its own switches, so the header says which flags the set used. Empty / null rules = "" (nothing to copy).
     */
    /** v0.40.0: "a | b | c => x" becomes three lines "a => x" ... (plain-text rules only; re: / del: / # lines and lines without variants come back unchanged). */
    static String expandVariants(String line) {
        try {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("del:") || t.startsWith("del-re:") || t.startsWith("re:") || t.startsWith("keep: re:") || t.startsWith("word: re:")) return line;
            int k = line.indexOf("=>");
            if (k < 0) return line;
            String f = line.substring(0, k).trim();
            String pre = "";
            while (true) {
                if (f.startsWith("word:")) { pre += "word: "; f = f.substring(5).trim(); }
                else if (f.startsWith("keep:")) { pre += "keep: "; f = f.substring(5).trim(); }
                else break;
            }
            if (f.startsWith("re:")) return line;
            java.util.List<String> vs = ReplaceRules.variants(f);
            if (vs.size() < 2) return line;
            String to = line.substring(k);
            StringBuilder sb = new StringBuilder();
            for (String v : vs) { if (sb.length() > 0) sb.append('\n'); sb.append(pre).append(v).append(' ').append(to); }
            return sb.toString();
        } catch (RuntimeException e) { return line; }
    }

    public static String formatterText(String name, String rulesText, boolean matchCase, boolean word) {
        if (rulesText == null || rulesText.trim().isEmpty()) return "";
        String n = name == null ? "" : name.replace('\n', ' ').replace('\r', ' ').trim();
        StringBuilder b = new StringBuilder();
        b.append("# From Find & Replace set: ").append(n.isEmpty() ? "(no name)" : n).append('\n');
        b.append("# Set options: match case ").append(matchCase ? "ON" : "OFF").append(", whole word ").append(word ? "ON" : "OFF").append('\n');
        // v0.29.0: the Formatter does not know "word:" / "keep:" in front of a rule: they are removed (the rule stays, only the flag is lost)
        String[] ls = rulesText.split("\n", -1);
        boolean lost = false, vexp = false;
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < ls.length; i++) {
            String l = ls[i];
            String t = l.trim();
            if (!t.startsWith("#")) {
                String x = t;
                while (x.startsWith("word:") || x.startsWith("keep:")) { x = x.substring(5).trim(); lost = true; }
                if (!x.equals(t)) l = x;
            }
            // v0.40.0: the Formatter does not know variants "a | b => c": one line per variant (same result)
            String xl = expandVariants(l);
            if (!xl.equals(l)) { l = xl; vexp = true; }
            body.append(l);
            if (i < ls.length - 1) body.append('\n');
        }
        if (vexp) b.append("# Note: lines with variants (a | b => c) were split into one line per variant\n");
        if (lost) b.append("# Note: word: / keep: flags of single rules were dropped (the Formatter has no such flags)\n");
        String bs = body.toString();
        b.append(bs.endsWith("\n") ? bs : bs + "\n");
        return b.toString();
    }

    /**
     * v0.31.0: text to look at a rule set BEFORE loading it: "name: 3 changes, 5 rules on 340 chars" + the first 6 rules with how often each one matched.
     * Nothing is changed anywhere. Never throws ("" on any problem).
     */
    public static String setPreview(String name, String rulesText, ReplaceEngine.Opt o, String text) {
        try {
            ReplaceRules.Parsed p = ReplaceRules.parse(rulesText);
            int act = p.active();
            String n = name == null || name.trim().isEmpty() ? "(no name)" : name.trim();
            if (act == 0) return n + ": no rules";
            String t = text == null ? "" : text;
            StringBuilder b = new StringBuilder();
            if (t.isEmpty()) return b.append(n).append(": ").append(act).append(act == 1 ? " rule" : " rules").append(" (no text in the field to try it on)").toString();
            ReplaceRules.RunResult rr = ReplaceRules.run(t, p.rules, o);
            b.append(n).append(": ").append(ReplaceRules.info(rr, act)).append(" on ").append(t.length()).append(" chars");
            int shown = 0, more = 0, idx = 0;
            for (ReplaceRules.Rule r : p.rules) {
                int i = idx++;
                if (r.kind == ReplaceRules.K_COMMENT) continue;
                if (shown >= 6) { more++; continue; }
                shown++;
                String one = ReplaceRules.serialize(java.util.Collections.singletonList(r)).replace('\n', ' ');
                b.append('\n').append(i < rr.counts.length ? rr.counts[i] : 0).append("x  ").append(head(one, 36));
            }
            if (more > 0) b.append("\n+").append(more).append(" more rules");
            return b.toString();
        } catch (Throwable e) { return ""; }
    }
}
