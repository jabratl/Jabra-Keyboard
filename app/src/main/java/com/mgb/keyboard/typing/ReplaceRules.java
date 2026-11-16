package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.25.0 (R2) many Find / Replace rules in a row. PURE JAVA (no Android), never throws.
 * Text form = ONE LINE per rule, the SAME syntax as the Formatter rules, so a list can be pasted both ways:
 *   find => with        plain text (mode of the SET: Text or Word)
 *   re: find => with    regex ($1, \n, \U...\E like ReplaceEngine)
 *   del: text           removes every LINE that contains the text
 *   del-re: pattern     same, with a regex
 *   # comment           kept, never run
 * The SET carries the flags (Opt: mode Text/Word, match case, escapes). Rules run top to bottom, each on the result of the one before.
 * A rule with an error is skipped and reported; the rest still run. Limits: 50 rules, 1 s for the whole run.
 * The regex work is done by ReplaceEngine.run (nothing is copied here).
 */
public final class ReplaceRules {
    private ReplaceRules() { }

    public static final int MAX_RULES = 50;
    public static final long BUDGET_MS = 1000;

    public static final int K_TEXT = 0, K_REGEX = 1, K_DEL = 2, K_DELRE = 3, K_COMMENT = 4;

    public static final class Rule {
        public final int kind;
        public final String find, with;
        public Rule(int kind, String find, String with) { this.kind = kind; this.find = find; this.with = with; }
    }

    public static final class Parsed {
        public final List<Rule> rules = new ArrayList<Rule>();   // includes comments (kept for serialize)
        public final List<Integer> bad = new ArrayList<Integer>(); // 1-based line numbers that could not be read
        public boolean tooMany = false;                           // more than MAX_RULES: the extra ones were dropped
        public int active() { int n = 0; for (Rule r : rules) if (r.kind != K_COMMENT) n++; return n; }
    }

    public static final class RunResult {
        public String out = "";
        public int[] counts = new int[0];     // per rule (same index as rules; comments = 0)
        public int changed = 0;               // total replacements
        public int errors = 0;                // rules skipped because of an error
        public String err = null;             // first error, "Rule N: ..."
    }

    private static String trim(String s) { return s.trim(); }

    private static String trimEnd(String s) {
        int e = s.length();
        while (e > 0 && Character.isWhitespace(s.charAt(e - 1))) e--;
        return s.substring(0, e);
    }

    public static Parsed parse(String src) {
        Parsed p = new Parsed();
        String[] lines = String.valueOf(src == null ? "" : src).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replace("\r", "");
            String tl = trim(line);
            if (tl.isEmpty()) continue;
            if (tl.charAt(0) == '#') { p.rules.add(new Rule(K_COMMENT, tl.substring(1).trim(), "")); continue; }
            if (p.active() >= MAX_RULES) { p.tooMany = true; break; }
            if (tl.startsWith("del-re:")) {
                String f = trim(tl.substring(7));
                if (f.isEmpty()) p.bad.add(i + 1); else p.rules.add(new Rule(K_DELRE, f, ""));
                continue;
            }
            if (tl.startsWith("del:")) {
                String f = trim(tl.substring(4));
                if (f.isEmpty()) p.bad.add(i + 1); else p.rules.add(new Rule(K_DEL, f, ""));
                continue;
            }
            int k = line.indexOf("=>");
            if (k < 0) { p.bad.add(i + 1); continue; }
            String f = trim(line.substring(0, k));
            String to = line.substring(k + 2);
            if (to.startsWith(" ")) to = to.substring(1);
            to = trimEnd(to);
            int kind = K_TEXT;
            if (f.startsWith("re:")) { kind = K_REGEX; f = trim(f.substring(3)); }
            if (f.isEmpty()) { p.bad.add(i + 1); continue; }
            p.rules.add(new Rule(kind, f, to));
        }
        return p;
    }

    public static String serialize(List<Rule> rules) {
        StringBuilder sb = new StringBuilder();
        for (Rule r : rules) {
            if (sb.length() > 0) sb.append('\n');
            switch (r.kind) {
                case K_COMMENT: sb.append("# ").append(r.find); break;
                case K_DEL: sb.append("del: ").append(r.find); break;
                case K_DELRE: sb.append("del-re: ").append(r.find); break;
                case K_REGEX: sb.append("re: ").append(r.find).append(" => ").append(r.with); break;
                default: sb.append(r.find).append(" => ").append(r.with); break;
            }
        }
        return sb.toString();
    }

    /** Runs the rules from top to bottom. o = the flags of the set (mode Text / Word, case, escapes). */
    public static RunResult run(String text, List<Rule> rules, ReplaceEngine.Opt o) {
        RunResult rr = new RunResult();
        String cur = text == null ? "" : text;
        rr.out = cur;
        rr.counts = new int[rules.size()];
        try {
            long end = System.currentTimeMillis() + BUDGET_MS;
            int idx = 0, active = 0;
            for (Rule r : rules) {
                int i = idx++;
                if (r.kind == K_COMMENT) continue;
                active++;
                if (active > MAX_RULES) { fail(rr, active, "more than " + MAX_RULES + " rules"); break; }
                if (System.currentTimeMillis() > end) { fail(rr, active, "time is up (the rest was skipped)"); break; }
                ReplaceEngine.Opt oo = o == null ? new ReplaceEngine.Opt() : o.copy();
                String find = r.find, with = r.with;
                if (r.kind == K_REGEX) { oo.mode = ReplaceEngine.MODE_REGEX; }
                else if (r.kind == K_DEL) { oo.mode = ReplaceEngine.MODE_REGEX; find = "^[^\\n]*" + java.util.regex.Pattern.quote(r.find) + "[^\\n]*(\\n|$)"; with = ""; }
                else if (r.kind == K_DELRE) { oo.mode = ReplaceEngine.MODE_REGEX; find = "^[^\\n]*(?:" + r.find + ")[^\\n]*(\\n|$)"; with = ""; }
                ReplaceEngine.Result res = ReplaceEngine.replaceAll(cur, find, with, oo);
                if (res.err != null) { fail(rr, active, res.err); continue; }
                if (res.blank) continue;
                rr.counts[i] = res.changed;
                rr.changed += res.changed;
                cur = res.out;
            }
        } catch (Throwable t) {
            rr.errors++;
            if (rr.err == null) rr.err = "Rules stopped: " + t.getClass().getSimpleName();
        }
        rr.out = cur;
        return rr;
    }

    private static void fail(RunResult rr, int n, String why) {
        rr.errors++;
        if (rr.err == null) rr.err = "Rule " + n + ": " + why;
    }

    /** One line of text for the panel: "3 rules, 5 changes" / "Rule 2: ..." */
    public static String info(RunResult rr, int activeRules) {
        if (rr.err != null) return rr.err + (rr.changed > 0 ? "  ·  " + rr.changed + " changes" : "");
        if (rr.changed == 0) return "No match  ·  " + activeRules + " rules";
        return rr.changed + (rr.changed == 1 ? " change" : " changes") + "  ·  " + activeRules + " rules";
    }
}
