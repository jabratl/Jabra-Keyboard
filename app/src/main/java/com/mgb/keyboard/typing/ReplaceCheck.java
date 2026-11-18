package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * v0.40.0 CHECK PAIRS: warnings about a rule list BEFORE it runs (idea from the Bulk Replace tool "Check pairs"). PURE JAVA (no Android), never throws, reads only.
 * What it finds (plain-text rules and variants "a | b => c"; comments / del: rules are not checked, except that a bad del-re: / re: pattern is reported):
 *  - DUPLICATE   the same Find is in two rules (another With = the later rule never finds it, same With = it is only a repeat)
 *  - CHAIN       a rule makes a text that a LATER rule changes again ("a => b" then "b => c"); in Word mode only a whole word counts
 *  - SHORT       a short (3 letters or less) or very common word that is NOT whole-word: "cat" would also hit "category". Fix = withWholeWord() adds "word:" to those rules
 *  - NEVER-CHANGE (v0.41.0)  a Find that is on the Never-change list (Opt.protect): the rule can never replace it
 *  - EMPTY WITH  the rule deletes the text;  SAME       both sides are the same;  PATTERN  a re: / del-re: pattern that does not compile
 * Rule numbers are 1-based and count only active rules (comments are skipped), the same numbers as ReplaceRules.info() ("Rule 3: ...").
 * At most MAX_WARN warnings are returned (the last entry then says how many more).
 */
public final class ReplaceCheck {
    private ReplaceCheck() { }

    public static final int MAX_WARN = 12;

    public static final class Warn {
        public final int rule;            // 1-based number of the rule (0 = the list as a whole)
        public final String text;
        public final boolean shortWord;   // true = withWholeWord() can fix it
        public final boolean minor;       // true = only a hint (empty With = deletes, both sides the same): shown by Check, but it never stops "Replace all"
        Warn(int rule, String text, boolean shortWord, boolean minor) { this.rule = rule; this.text = text; this.shortWord = shortWord; this.minor = minor; }
    }

    // common English words that are longer than 3 letters but are still bad as a "contains" match
    private static final Set<String> COMMON = new HashSet<String>();
    static {
        String[] w = { "the", "and", "for", "that", "this", "with", "from", "have", "will", "they", "them", "then", "than", "there", "their", "what",
            "when", "where", "which", "while", "would", "could", "should", "about", "into", "over", "your", "been", "were", "also", "some", "such", "only", "very" };
        for (String x : w) COMMON.add(x);
    }

    private static final Pattern SHORT_SCRIPT = Pattern.compile("^[\\p{IsLatin}\\p{IsCyrillic}][\\p{IsLatin}\\p{IsCyrillic}'\u2019-]*$");

    private static String clip(String s, int n) {
        String t = s == null ? "" : s.replace('\n', ' ');
        return t.length() > n ? t.substring(0, n) + "…" : t;
    }

    private static String norm(String s, boolean matchCase) { return matchCase ? s : s.toLowerCase(java.util.Locale.ROOT); }

    /** The finds of one plain-text rule: the variants, or the single Find. */
    private static List<String> alts(ReplaceRules.Rule r) {
        List<String> v = ReplaceRules.variants(r.find);
        if (v.isEmpty()) { v = new ArrayList<String>(); v.add(r.find); }
        return v;
    }

    private static boolean wholeWord(ReplaceRules.Rule r, ReplaceEngine.Opt o) {
        return r.word || (o != null && o.mode == ReplaceEngine.MODE_WORD);
    }

    /** True when this find would be risky as a "contains" match: 2-3 letters (Latin / Cyrillic, a single letter is a character fix and is left alone) or a very common word. */
    static boolean risky(String a) {
        try {
            if (a == null) return false;
            String t = a.trim();
            if (t.isEmpty() || !SHORT_SCRIPT.matcher(t).matches()) return false;
            int len = t.codePointCount(0, t.length());
            return (len >= 2 && len <= 3) || COMMON.contains(t.toLowerCase(java.util.Locale.ROOT));
        } catch (RuntimeException e) { return false; }
    }

    private static boolean riskyRule(ReplaceRules.Rule r, ReplaceEngine.Opt o) {
        if (r.kind != ReplaceRules.K_TEXT || wholeWord(r, o)) return false;
        for (String a : alts(r)) if (risky(a)) return true;
        return false;
    }

    /** Does text contain find (as a whole word when whole = true)? */
    private static boolean has(String text, String find, boolean whole, boolean matchCase) {
        if (find.length() == 0) return false;
        if (!whole) return norm(text, matchCase).contains(norm(find, matchCase));
        try {
            int fl = matchCase ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            return Pattern.compile("(?<![\\p{L}\\p{M}\\p{N}_])" + Pattern.quote(find) + "(?![\\p{L}\\p{M}\\p{N}_])", fl).matcher(text).find();
        } catch (RuntimeException e) { return false; }
    }

    public static List<Warn> check(List<ReplaceRules.Rule> rules, ReplaceEngine.Opt o) {
        List<Warn> out = new ArrayList<Warn>();
        try {
            if (rules == null) return out;
            boolean mc = o != null && o.matchCase;
            List<ReplaceRules.Rule> act = new ArrayList<ReplaceRules.Rule>();
            for (ReplaceRules.Rule r : rules) if (r != null && r.kind != ReplaceRules.K_COMMENT) act.add(r);
            Map<String, Integer> seen = new LinkedHashMap<String, Integer>();   // normalized find -> 1-based rule number
            int more = 0;
            for (int i = 0; i < act.size(); i++) {
                ReplaceRules.Rule r = act.get(i);
                int n = i + 1;
                List<Warn> w = new ArrayList<Warn>();
                if (r.kind == ReplaceRules.K_REGEX || r.kind == ReplaceRules.K_DELRE) {
                    try { Pattern.compile(r.find, mc ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)); }
                    catch (PatternSyntaxException e) { w.add(new Warn(n, "Rule " + n + ": the pattern is not valid (" + (e.getDescription() == null ? "?" : e.getDescription()) + ").", false, false)); }
                } else if (r.kind == ReplaceRules.K_TEXT) {
                    List<String> al = alts(r);
                    boolean isVar = al.size() > 1 || ReplaceRules.variants(r.find).size() > 1;
                    String first = clip(al.get(0), 20);
                    if (r.with.length() == 0) w.add(new Warn(n, "Rule " + n + ": \"" + first + "\" will be deleted (empty With).", false, true));
                    else if (!isVar && r.find.equals(r.with)) w.add(new Warn(n, "Rule " + n + ": both sides are the same, nothing changes.", false, true));
                    if (o != null && o.protect != null && !o.protect.isEmpty()) {   // v0.41.0: a Find that is on the never-change list is never replaced
                        for (String a : al) if (ReplaceProtect.contains(o.protect, a)) { w.add(new Warn(n, "Rule " + n + ": \"" + clip(a, 20) + "\" is on the Never-change list, so it is never replaced.", false, false)); break; }
                    }
                    if (riskyRule(r, o)) w.add(new Warn(n, "Rule " + n + ": \"" + first + "\" is short or a common word, it can also hit inside longer words. Use word:", true, false));
                    for (String a : al) {
                        String key = norm(a, mc);
                        Integer prev = seen.get(key);
                        if (prev != null) {
                            ReplaceRules.Rule pr = act.get(prev - 1);
                            if (pr.with.equals(r.with)) w.add(new Warn(n, "Rule " + n + ": \"" + clip(a, 20) + "\" is already in rule " + prev + " (same With).", false, false));
                            else w.add(new Warn(n, "Rule " + n + ": \"" + clip(a, 20) + "\" is also in rule " + prev + " with another With. Rule " + prev + " runs first, so this one will not find it.", false, false));
                        } else seen.put(key, n);
                    }
                    if (r.with.length() > 0) {
                        for (int j = i + 1; j < act.size(); j++) {
                            ReplaceRules.Rule q = act.get(j);
                            if (q.kind != ReplaceRules.K_TEXT) continue;
                            boolean whole = wholeWord(q, o);
                            for (String a : alts(q)) {
                                if (has(r.with, a, whole, mc)) {
                                    w.add(new Warn(n, "Rule " + n + " makes \"" + clip(a, 20) + "\", which rule " + (j + 1) + " changes again.", false, false));
                                    break;
                                }
                            }
                        }
                    }
                }
                for (Warn x : w) { if (out.size() < MAX_WARN) out.add(x); else more++; }
            }
            if (more > 0) out.add(new Warn(0, "+ " + more + " more warning" + (more == 1 ? "" : "s") + ".", false, false));
        } catch (Throwable t) { /* a check must never stop the keyboard: what was found so far is returned */ }
        return out;
    }

    /** How many warnings are not only hints (these are the ones that make "Replace all" ask once more). */
    public static int serious(List<Warn> ws) {
        int n = 0;
        if (ws != null) for (Warn w : ws) if (!w.minor) n++;
        return n;
    }

    /** How many warnings can be fixed by whole-word (withWholeWord). */
    public static int shortCount(List<Warn> ws) {
        int n = 0;
        if (ws != null) for (Warn w : ws) if (w.shortWord) n++;
        return n;
    }

    /** A copy of the list where every risky short rule got "word:" (the others are the same objects). Never changes the input. */
    public static List<ReplaceRules.Rule> withWholeWord(List<ReplaceRules.Rule> rules, ReplaceEngine.Opt o) {
        List<ReplaceRules.Rule> out = new ArrayList<ReplaceRules.Rule>();
        if (rules == null) return out;
        for (ReplaceRules.Rule r : rules) {
            if (r != null && riskyRule(r, o)) out.add(new ReplaceRules.Rule(r.kind, r.find, r.with, true, r.keep, r.smart));
            else out.add(r);
        }
        return out;
    }

    /** One short line for the info bar: "No problems" / "3 warnings: <first>". */
    public static String line(List<Warn> ws) {
        if (ws == null || ws.isEmpty()) return "Check: no problems found";
        int n = 0;
        for (Warn w : ws) if (w.rule > 0) n++;
        return "Check: " + n + (n == 1 ? " warning" : " warnings") + ". " + ws.get(0).text;
    }

    /** All warnings, one per line (for a long list view). */
    public static String lines(List<Warn> ws) {
        StringBuilder sb = new StringBuilder();
        if (ws != null) for (Warn w : ws) { if (sb.length() > 0) sb.append('\n'); sb.append("• ").append(w.text); }
        return sb.toString();
    }
}
