package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * v0.57.0 (FX1): logic of the ONE-SCREEN "Fix names, per novel" view (design = Fix_names_per_novel_mock.html). PURE JAVA (no Android), never throws.
 * One list per novel: a pair is a row (wrong forms => right form, topic chip, hit count, on/off switch), a locked name (Never change) is a row too.
 * Rows come from the saved "wrong forms" sets of the novel (plugin glossary sets + the user's "My pairs" sets); nothing new is stored except the optional topic tag line
 * "# topic: Characters" inside a My pairs set (a comment line: the Replace engine ignores it, old versions ignore it too).
 * The preview uses the SAME engine as the real fix (ReplaceRules.run), so what you see is what is replaced.
 */
public final class FixNames {
    private FixNames() { }

    public static final String TOPIC_TAG = "topic:";
    public static final int MAX_ROWS = 400;
    public static final String[] DEFAULT_TOPICS = {"Characters", "Places", "Skills"};

    /** One saved pair of the active novel. */
    public static final class Row {
        public String set = "", topic = "", to = "";
        public int idx = -1;                 // index of the rule inside its set text (comments counted)
        public boolean mine = false;
        public ReplaceRules.Rule rule;
        public List<String> forms = new ArrayList<String>();
        /** Stable id for the on/off switch (memory only). */
        public String key() { return set + "\u0001" + (rule == null ? "" : rule.find); }
        /** "Lin Fen; Lin Fong" */
        public String formsText() { StringBuilder sb = new StringBuilder(); for (String f : forms) { if (sb.length() > 0) sb.append("; "); sb.append(f); } return sb.toString(); }
    }

    /** One piece of the preview: a replaced match (lock = false) or a locked name (lock = true). */
    public static final class Seg {
        public int start, end;
        public String found = "", to = "";
        public boolean lock = false, draft = false;
    }

    public static final class Scan {
        public final List<Seg> segs = new ArrayList<Seg>();
        public int[] counts = new int[0];    // per row (hits of that row alone on the text now; a switched-off row still shows its count)
        public int hits = 0;                 // matches that "Fix" would replace now (saved pairs that are on; the pair being typed is not counted)
        public int draftHits = 0;
        public int locked = 0;
    }

    /** "Lin Fen; Lin Fong | Lin Fan" -> [Lin Fen, Lin Fong, Lin Fan] (trimmed, no empty ones, no repeats in any case). */
    public static List<String> forms(String field) {
        List<String> out = new ArrayList<String>();
        try {
            if (field == null) return out;
            for (String x : field.split("[;|]", -1)) {
                String t = x.trim();
                if (t.isEmpty()) continue;
                boolean dup = false;
                for (String o : out) if (o.equalsIgnoreCase(t)) { dup = true; break; }
                if (!dup) out.add(t);
            }
        } catch (RuntimeException e) { /* what was read stays */ }
        return out;
    }

    private static boolean isTag(ReplaceRules.Rule r) {
        return r != null && r.kind == ReplaceRules.K_COMMENT && r.find != null && r.find.toLowerCase(Locale.ROOT).startsWith(TOPIC_TAG);
    }

    private static String tagOf(ReplaceRules.Rule r) { return r.find.substring(TOPIC_TAG.length()).trim(); }

    /**
     * The rows of one novel. names / texts = the saved sets (same order). Only "wrong forms" sets (plugin) and My pairs sets are used (raw-to-English sets stay in Sets).
     * Plain-text rules only (regex / delete rules are not names). At most MAX_ROWS.
     */
    public static List<Row> rows(String novel, List<String> names, List<String> texts) {
        List<Row> out = new ArrayList<Row>();
        try {
            if (novel == null || names == null || texts == null) return out;
            for (int s = 0; s < names.size() && s < texts.size(); s++) {
                String n = names.get(s);
                if (!NovelBook.belongs(n, novel)) continue;
                boolean mine = NovelBook.isMine(n);
                if (!mine && !"wrong".equals(GlossaryImport.kindOf(n))) continue;
                String topic = mine ? NovelBook.MY_TOPIC : GlossaryImport.topicOf(n);
                ReplaceRules.Parsed p = ReplaceRules.parse(texts.get(s));
                for (int i = 0; i < p.rules.size(); i++) {
                    ReplaceRules.Rule r = p.rules.get(i);
                    if (isTag(r)) { if (mine) topic = tagOf(r).isEmpty() ? NovelBook.MY_TOPIC : tagOf(r); continue; }
                    if (r.kind != ReplaceRules.K_TEXT || out.size() >= MAX_ROWS) continue;
                    Row w = new Row();
                    w.set = n; w.idx = i; w.mine = mine; w.rule = r; w.topic = topic; w.to = r.with;
                    List<String> vs = ReplaceRules.variants(r.find);
                    if (vs.isEmpty()) w.forms.add(r.find); else w.forms.addAll(vs);
                    out.add(w);
                }
            }
        } catch (RuntimeException e) { /* what was read stays */ }
        return out;
    }

    /** Topics that have rows, in order of first appearance. */
    public static List<String> topicsOf(List<Row> rows) {
        List<String> out = new ArrayList<String>();
        if (rows != null) for (Row r : rows) if (!r.topic.isEmpty() && !out.contains(r.topic)) out.add(r.topic);
        return out;
    }

    /** Topic chips of the "new pair" box: the topics the novel already has (without "My pairs"), else Characters / Places / Skills. At most 5. */
    public static List<String> topicChoices(List<Row> rows) {
        List<String> out = new ArrayList<String>();
        for (String t : topicsOf(rows)) if (!NovelBook.MY_TOPIC.equals(t) && out.size() < 5) out.add(t);
        if (out.isEmpty()) for (String t : DEFAULT_TOPICS) out.add(t);
        return out;
    }

    /** Count of rows per chip: chip ALL = every row; a topic = rows of that topic. */
    public static int countFor(List<Row> rows, String chip) {
        int c = 0;
        if (rows != null) for (Row r : rows) if (NovelBook.ALL.equals(chip) || r.topic.equalsIgnoreCase(chip)) c++;
        return c;
    }

    /** Row indexes to show: filtered by chip (ALL or a topic) and by the search words (any form, right form or topic), rows with hits first, stable inside each group. */
    public static List<Integer> visible(List<Row> rows, int[] counts, String chip, String query) {
        List<Integer> hit = new ArrayList<Integer>(), none = new ArrayList<Integer>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (rows != null) for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (!NovelBook.ALL.equals(chip) && !r.topic.equalsIgnoreCase(chip)) continue;
            if (!q.isEmpty() && (r.formsText() + " " + r.to + " " + r.topic).toLowerCase(Locale.ROOT).indexOf(q) < 0) continue;
            if (counts != null && i < counts.length && counts[i] > 0) hit.add(i); else none.add(i);
        }
        hit.addAll(none);
        return hit;
    }

    /** The saved pairs that are switched on, as engine rules (order = row order). off = Row.key() values. */
    public static List<ReplaceRules.Rule> activeRules(List<Row> rows, Set<String> off) {
        List<ReplaceRules.Rule> out = new ArrayList<ReplaceRules.Rule>();
        if (rows != null) for (Row r : rows) if (off == null || !off.contains(r.key())) out.add(r.rule);
        return out;
    }

    private static ReplaceEngine.Opt opt(List<String> protect) {
        ReplaceEngine.Opt o = new ReplaceEngine.Opt();
        o.matchCase = false;
        o.protect = protect;
        return o;
    }

    /** The pair being typed as an engine rule (null = nothing valid yet). */
    public static ReplaceRules.Rule draftRule(List<String> draftForms, String draftTo) {
        try {
            if (draftForms == null || draftForms.isEmpty() || draftTo == null || draftTo.trim().isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            for (String f : draftForms) { if (sb.length() > 0) sb.append("; "); sb.append(f); }
            return ReplacePairList.make(sb.toString(), draftTo.trim(), ReplaceEngine.MODE_TEXT, false, false).rule;
        } catch (RuntimeException e) { return null; }
    }

    private static void collect(List<Seg> into, ReplaceRules.RunResult rr, boolean draft) {
        for (ReplaceEngine.Hit h : rr.edits) { Seg s = new Seg(); s.start = h.start; s.end = h.end; s.found = h.found; s.to = h.repl; s.draft = draft; into.add(s); }
    }

    /**
     * The live preview. Locked names win, then the saved pairs (row order), then the pair being typed (dashed in the UI). Overlapping pieces: the first one stays.
     * protect = the Never change list of the novel (these names are shown with a lock and never replaced).
     */
    public static Scan scan(String text, List<Row> rows, Set<String> off, List<String> protect, List<String> draftForms, String draftTo) {
        Scan sc = new Scan();
        String t = text == null ? "" : text;
        int n = rows == null ? 0 : rows.size();
        sc.counts = new int[n];
        try {
            List<Seg> all = new ArrayList<Seg>();
            if (protect != null) for (String l : protect) {
                if (l == null || l.isEmpty()) continue;
                String low = t.toLowerCase(Locale.ROOT), lp = l.toLowerCase(Locale.ROOT);
                if (low.length() != t.length()) continue;   // odd case mapping: skip the lock marks, the engine still protects
                int at = 0;
                while ((at = low.indexOf(lp, at)) >= 0) { Seg s = new Seg(); s.start = at; s.end = at + l.length(); s.found = t.substring(s.start, s.end); s.lock = true; all.add(s); at = s.end; }
            }
            ReplaceEngine.Opt o = opt(protect);
            for (int i = 0; i < n; i++) {
                Row r = rows.get(i);
                List<ReplaceRules.Rule> one = new ArrayList<ReplaceRules.Rule>();
                one.add(r.rule);
                ReplaceRules.RunResult rr = ReplaceRules.run(t, one, o);
                sc.counts[i] = rr.counts.length > 0 ? rr.counts[0] : 0;
                if (off == null || !off.contains(r.key())) { collect(all, rr, false); sc.hits += rr.counts.length > 0 ? rr.counts[0] : 0; }
            }
            ReplaceRules.Rule d = draftRule(draftForms, draftTo);
            if (d != null) {
                List<ReplaceRules.Rule> one = new ArrayList<ReplaceRules.Rule>();
                one.add(d);
                ReplaceRules.RunResult rr = ReplaceRules.run(t, one, o);
                sc.draftHits = rr.counts.length > 0 ? rr.counts[0] : 0;
                collect(all, rr, true);
            }
            Collections.sort(all, new Comparator<Seg>() {
                @Override public int compare(Seg a, Seg b) { return a.start != b.start ? Integer.compare(a.start, b.start) : Integer.compare(b.end - b.start, a.end - a.start); }
            });
            int end = 0;
            for (Seg s : all) {
                if (s.start < end) continue;
                sc.segs.add(s); end = s.end;
                if (s.lock) sc.locked++;
            }
        } catch (RuntimeException e) { /* a partial preview is fine */ }
        return sc;
    }

    /**
     * v0.58.0 (FX2): the raw names of a novel for the "Show raw names" switch: key = right form in lower case, value = the raw terms of the plugin's "raw to English" sets
     * ("林风" for "Lin Feng", several raw terms joined by ", "). Plain-text rules only. Never throws.
     */
    public static Map<String, String> rawOf(String novel, List<String> names, List<String> texts) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        try {
            if (novel == null || names == null || texts == null) return out;
            for (int s = 0; s < names.size() && s < texts.size(); s++) {
                String n = names.get(s);
                if (!NovelBook.belongs(n, novel) || NovelBook.isMine(n) || !"raw".equals(GlossaryImport.kindOf(n))) continue;
                ReplaceRules.Parsed p = ReplaceRules.parse(texts.get(s));
                for (ReplaceRules.Rule r : p.rules) {
                    if (r.kind != ReplaceRules.K_TEXT || r.with == null || r.find == null) continue;
                    String k = r.with.trim().toLowerCase(Locale.ROOT);
                    if (k.isEmpty()) continue;
                    String cur = out.get(k);
                    if (cur == null) out.put(k, r.find.trim());
                    else if (!(", " + cur + ", ").contains(", " + r.find.trim() + ", ")) out.put(k, cur + ", " + r.find.trim());
                }
            }
        } catch (RuntimeException e) { /* what was read stays */ }
        return out;
    }

    /** "Fix 3 in text" / "Nothing to fix". */
    public static String fixLabel(int hits) { return hits > 0 ? "Fix " + hits + " in text" : "Nothing to fix"; }

    /**
     * Puts one pair line ("Lin Fen | Lin Fong => Lin Feng") into the text of a My pairs set under a topic tag line.
     * Same Find = replaced in place (its topic stays where it is unless it moved: then the old line is removed and the new one goes under the new topic).
     * New = appended after the last pair of the same topic, or after a new "# topic: X" line at the end. A topic that is "My pairs" or empty writes no tag.
     * More than max pairs: the line is returned in overflow of the result. Comment lines other than tags stay untouched.
     */
    public static NovelBook.Merged mergeWithTopic(String existing, String ruleLine, String topic, int max) {
        NovelBook.Merged m = new NovelBook.Merged();
        try {
            String r = ruleLine == null ? "" : ruleLine.trim();
            int ar = r.indexOf("=>");
            if (ar <= 0) { m.text = existing == null ? "" : existing; return m; }
            String key = r.substring(0, ar).trim();
            String tp = topic == null ? "" : topic.trim();
            if (NovelBook.MY_TOPIC.equalsIgnoreCase(tp)) tp = "";
            List<String> lines = new ArrayList<String>();
            if (existing != null) for (String l0 : existing.split("\n", -1)) { String l = l0.replace("\r", ""); if (!l.trim().isEmpty()) lines.add(l); }
            int count = 0, at = -1;
            List<String> topicOf = new ArrayList<String>();   // topic of every line ("" = no tag above it)
            String cur = "";
            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i).trim();
                if (l.charAt(0) == '#') {
                    String body = l.substring(1).trim();
                    if (body.toLowerCase(Locale.ROOT).startsWith(TOPIC_TAG)) { cur = body.substring(TOPIC_TAG.length()).trim(); if (NovelBook.MY_TOPIC.equalsIgnoreCase(cur)) cur = ""; }
                    topicOf.add(cur); continue;
                }
                topicOf.add(cur); count++;
                int k = l.indexOf("=>");
                if (k > 0 && l.substring(0, k).trim().equals(key)) at = i;
            }
            if (at >= 0 && topicOf.get(at).equalsIgnoreCase(tp)) { lines.set(at, r); m.updated = 1; }
            else {
                if (at >= 0) { lines.remove(at); topicOf.remove(at); count--; m.updated = 1; }
                if (count >= max) { m.overflow.add(r); }
                else {
                    int last = -1;
                    for (int i = 0; i < lines.size(); i++) if (lines.get(i).trim().charAt(0) != '#' && topicOf.get(i).equalsIgnoreCase(tp)) last = i;
                    if (last >= 0) lines.add(last + 1, r);
                    else { if (!tp.isEmpty() || (!topicOf.isEmpty() && !topicOf.get(topicOf.size() - 1).isEmpty())) lines.add("# " + TOPIC_TAG + " " + (tp.isEmpty() ? NovelBook.MY_TOPIC : tp)); lines.add(r); }
                    if (m.updated == 0) m.added = 1;
                }
            }
            StringBuilder sb = new StringBuilder();
            for (String l : lines) sb.append(l).append('\n');
            m.text = sb.toString();
        } catch (RuntimeException e) { m.text = existing == null ? "" : existing; }
        return m;
    }
}
