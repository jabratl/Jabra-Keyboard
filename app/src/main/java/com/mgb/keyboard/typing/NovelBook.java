package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.55.0 (NV1): one glossary per NOVEL. PURE JAVA (no Android), never throws.
 * A novel = the title of a pasted glossary. Its saved sets are the sets named "G <novel> · ..." (see GlossaryImport), its Never change list is its own pref (key = neverKey).
 * Registry pref rp_novels = one novel name per line; the active novel is pref rp_novel_cur. Novels that only exist as old glossary sets (before v0.55.0) are found from the set names, so nothing needs a migration.
 * No active novel = the app works exactly as before (all sets, the general Never change list rp_protect).
 */
public final class NovelBook {
    private NovelBook() { }

    public static final int MAX_NOVELS = 12;
    public static final int MAX_SETS_PER_NOVEL = 40;   // sets of ONE novel (a set holds 50 rules); the old 40 for everything is gone for glossary sets
    public static final int MAX_USER_SETS = 40;        // the user's own (non glossary) sets
    public static final int MAX_ALL_SETS = 400;        // hard cap of the stored map
    public static final String ALL = "All", MINE = "Mine", READY = "Ready-made";
    public static final String MY_TOPIC = "My pairs";   // v0.56.0 (NV2): pairs the user saves for a novel; a new glossary paste never removes them
    public static final int RULES_PER_SET = 50;

    /** Name of the n-th (1 = first) "My pairs" set of a novel. */
    public static String mySetName(String novel, int n) {
        return "G " + novel.trim() + GlossaryImport.SEP + MY_TOPIC + GlossaryImport.SEP + "wrong" + (n > 1 ? " " + n : "");
    }

    public static boolean isMine(String setName) { return MY_TOPIC.equals(GlossaryImport.topicOf(setName)); }

    /** Result of merge(): the new set text, how many rules are new / replaced, and the lines that did not fit under max rules. */
    public static final class Merged {
        public String text = "";
        public int added = 0, updated = 0;
        public final List<String> overflow = new ArrayList<String>();
    }

    private static String key(String rule) {
        int k = rule.indexOf("=>");
        return (k < 0 ? rule : rule.substring(0, k)).trim();
    }

    /** Puts rule lines into a set text. Same Find (left of =>) = replaced in place, new = appended; comment lines stay; more than max rules = the rest is returned in overflow. */
    public static Merged merge(String existing, List<String> rules, int max) {
        Merged m = new Merged();
        try {
            List<String> lines = new ArrayList<String>();
            int count = 0;
            if (existing != null) for (String l0 : existing.split("\n", -1)) {
                String l = l0.replace("\r", "");
                if (l.trim().isEmpty()) continue;
                lines.add(l);
                if (l.trim().charAt(0) != '#') count++;
            }
            if (rules != null) for (String r0 : rules) {
                String r = r0 == null ? "" : r0.trim();
                if (r.isEmpty() || r.indexOf("=>") <= 0) continue;
                int at = -1;
                for (int i = 0; i < lines.size(); i++) { String l = lines.get(i).trim(); if (l.charAt(0) != '#' && key(l).equals(key(r))) { at = i; break; } }
                if (at >= 0) { lines.set(at, r); m.updated++; }
                else if (count >= max) m.overflow.add(r);
                else { lines.add(r); count++; m.added++; }
            }
            StringBuilder sb = new StringBuilder();
            for (String l : lines) sb.append(l).append('\n');
            m.text = sb.toString();
        } catch (RuntimeException e) { /* what is done stays */ }
        return m;
    }

    /** Registry text -> clean list (trimmed, no empty lines, no repeats in any case, at most MAX_NOVELS). */
    public static List<String> parse(String text) {
        List<String> out = new ArrayList<String>();
        try {
            if (text == null) return out;
            for (String line : text.split("\n", -1)) {
                String t = line.replace('\r', ' ').trim();
                if (t.isEmpty() || has(out, t) || out.size() >= MAX_NOVELS) continue;
                out.add(t);
            }
        } catch (RuntimeException e) { /* what was read stays */ }
        return out;
    }

    public static String text(List<String> list) {
        StringBuilder sb = new StringBuilder();
        if (list != null) for (String t : list) { if (sb.length() > 0) sb.append('\n'); sb.append(t); }
        return sb.toString();
    }

    public static boolean has(List<String> list, String name) {
        if (list == null || name == null) return false;
        for (String x : list) if (x != null && x.equalsIgnoreCase(name.trim())) return true;
        return false;
    }

    /** The registry plus every novel that is only known from saved glossary set names. Order: registry first. */
    public static List<String> all(List<String> registry, Iterable<String> setNames) {
        List<String> out = new ArrayList<String>();
        try {
            if (registry != null) for (String r : registry) if (r != null && !r.isEmpty() && !has(out, r)) out.add(r);
            if (setNames != null) for (String n : setNames) {
                String nv = GlossaryImport.novelOf(n);
                if (!nv.isEmpty() && !has(out, nv) && out.size() < MAX_NOVELS + 8) out.add(nv);
            }
        } catch (RuntimeException e) { /* partial list */ }
        return out;
    }

    /** Adds a novel to the registry (no repeat). Returns false when it is empty or the list is full. */
    public static boolean add(List<String> registry, String name) {
        String t = name == null ? "" : name.trim();
        if (t.isEmpty() || has(registry, t)) return !t.isEmpty();
        if (registry.size() >= MAX_NOVELS) return false;
        registry.add(t);
        return true;
    }

    /** Pref key of the Never change list of one novel. */
    public static String neverKey(String novel) {
        String n = novel == null ? "" : novel.trim().toLowerCase();
        return "rp_nv_" + Integer.toHexString(n.hashCode());
    }

    /** Does this saved set belong to the novel? */
    public static boolean belongs(String setName, String novel) {
        String nv = GlossaryImport.novelOf(setName);
        return !nv.isEmpty() && novel != null && nv.equalsIgnoreCase(novel.trim());
    }

    /** Names of the sets of one novel, in stored order. */
    public static List<String> setsOf(Iterable<String> setNames, String novel) {
        List<String> out = new ArrayList<String>();
        if (setNames != null) for (String n : setNames) if (belongs(n, novel)) out.add(n);
        return out;
    }

    /** How many sets are NOT glossary sets (the user's own). */
    public static int userCount(Iterable<String> setNames) {
        int c = 0;
        if (setNames != null) for (String n : setNames) if (!GlossaryImport.isGlossarySet(n)) c++;
        return c;
    }

    /** Topics of one novel with the number of sets in each, in order of first appearance. */
    public static Map<String, Integer> topics(Iterable<String> setNames, String novel) {
        Map<String, Integer> m = new LinkedHashMap<String, Integer>();
        for (String n : setsOf(setNames, novel)) {
            String t = GlossaryImport.topicOf(n);
            Integer c = m.get(t);
            m.put(t, c == null ? 1 : c + 1);
        }
        return m;
    }

    /**
     * Should a saved set show in the Sets screen? novel "" = no active novel: everything shows.
     * With an active novel: its own sets (All or its topic chip); the user's own sets only under Mine, ready-made sets only under Ready-made; sets of OTHER novels never show.
     * chip: ALL, a topic, MINE or READY. isReady = the set is a ready-made one (not saved by the user).
     */
    public static boolean visible(String setName, boolean isReady, String novel, String chip) {
        if (novel == null || novel.isEmpty()) return true;
        if (isReady) return READY.equals(chip);                                   // v0.55.1: ready-made and own sets only under their own chip, so "All" is just this novel
        if (!GlossaryImport.isGlossarySet(setName)) return MINE.equals(chip);
        if (!belongs(setName, novel)) return false;
        return ALL.equals(chip) || GlossaryImport.topicOf(setName).equalsIgnoreCase(chip);
    }

    /** Plain words for a glossary set: "Fix wrong names", "Raw to English", with the part number when the topic has several ("#2") and the topic in front when withTopic. */
    public static String title(String setName, boolean withTopic) {
        if (!GlossaryImport.isGlossarySet(setName)) return setName == null ? "" : setName;
        if (isMine(setName)) {
            String l2 = setName.substring(setName.lastIndexOf(GlossaryImport.SEP) + GlossaryImport.SEP.length()).trim();
            int j = l2.length();
            while (j > 0 && Character.isDigit(l2.charAt(j - 1))) j--;
            return "My pairs" + (j < l2.length() && j > 0 ? " #" + l2.substring(j) : "");
        }
        String base = "raw".equals(GlossaryImport.kindOf(setName)) ? "Raw to English" : "Fix wrong names";
        String last = setName.substring(setName.lastIndexOf(GlossaryImport.SEP) + GlossaryImport.SEP.length()).trim();
        int i = last.length();
        while (i > 0 && Character.isDigit(last.charAt(i - 1))) i--;
        if (i < last.length() && i > 0) base += " #" + last.substring(i);
        return withTopic ? GlossaryImport.topicOf(setName) + " \u00B7 " + base : base;
    }

    /** "5 sets · 12 never change" for a novel row. */
    public static String rowSub(int sets, int never) {
        return sets + (sets == 1 ? " set" : " sets") + " \u00B7 " + never + " never change";
    }
}
