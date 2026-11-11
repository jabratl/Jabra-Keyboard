package com.mgb.keyboard.vault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v0.4.4 PERSONAL TEXT VAULT engine (pure Java, no Android classes, tested by tools/VaultTest.java).
 *
 * A permanent text library, separate from the auto-trimmed clipboard history.
 * Entry = id + kind + folder + title + text + made / used / count.
 * Kinds: snippet, template (v0.4.4); note, term (v0.4.5). For a TERM: title = the term, text = its meaning.
 * Folders = list of names. Entries without a folder have folder "" (called "Unfiled" on screen).
 *
 * Saved as JSON text {"ver":1,"next":N,"folders":[..],"entries":[{id,kind,folder,title,text,made,used,count}]}
 * (file filesDir/vault_v1.json, written by VaultStore.kt with AtomicFile). parse() returns null when the text is
 * damaged or has a NEWER ver, so the store never overwrites a file it could not read.
 */
public final class VaultList {

    public static final int VER = 1;
    public static final int MAX_ENTRIES = 500;
    public static final int MAX_LEN = 20000;
    public static final int MAX_TITLE = 60;
    public static final int MAX_FOLDERS = 30;
    public static final int MAX_FOLDER_NAME = 30;

    public static final String SNIPPET = "snippet", TEMPLATE = "template", NOTE = "note", TERM = "term";
    public static final String[] KINDS = { SNIPPET, TEMPLATE, NOTE, TERM };

    /** Ready-made folder set offered once as a button (user decision; nothing is added automatically). */
    public static final String[] NOVEL_FOLDERS = { "Character Names", "Glossary", "Common Instructions", "Translation Prompts" };

    /** Result codes of add / edit / folder methods. */
    public static final int OK = 0, EMPTY = 1, TOO_LONG = 2, FULL = 3, EXISTS = 4, NOT_FOUND = 5, BAD_KIND = 6;

    /** Sort modes. */
    public static final int NEWEST = 0, AZ = 1, USED = 2;

    public static final class Entry {
        public int id;
        public String kind = SNIPPET;
        public String folder = "";
        public String title = "";
        public String text = "";
        public long made;
        public long used;
        public int count;
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private final List<String> folders = new ArrayList<String>();
    private int next = 1;

    public int size() { return entries.size(); }
    public List<String> folders() { return new ArrayList<String>(folders); }
    public boolean hasFolder(String f) { return f != null && folders.contains(f); }

    public static boolean isKind(String k) {
        if (k == null) return false;
        for (String x : KINDS) if (x.equals(k)) return true;
        return false;
    }

    // ------------------------------------------------------------------ entries

    /** The id of the entry added by the last successful add(), 0 if none. */
    private int lastId = 0;
    public int lastAddedId() { return lastId; }

    /** First line of the text, cut to a short title (used when the title is empty). */
    public static String autoTitle(String text) {
        if (text == null) return "";
        String t = text.trim();
        int nl = t.indexOf('\n');
        if (nl >= 0) t = t.substring(0, nl).trim();
        if (t.length() > 40) t = t.substring(0, 40).trim();
        return t;
    }

    private static String cleanTitle(String s) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').replace('\r', ' ').trim();
        if (t.length() > MAX_TITLE) t = t.substring(0, MAX_TITLE).trim();
        return t;
    }

    /**
     * Add an entry on top. Empty title = auto title from the text (not for a term: a term needs its own title).
     * Folder that does not exist = "" (Unfiled). Returns OK / EMPTY / TOO_LONG / FULL / BAD_KIND.
     */
    public int add(String kind, String folder, String title, String text, long now) {
        lastId = 0;
        if (!isKind(kind)) return BAD_KIND;
        if (text == null || text.trim().length() == 0) return EMPTY;
        if (text.length() > MAX_LEN) return TOO_LONG;
        if (entries.size() >= MAX_ENTRIES) return FULL;
        String ti = cleanTitle(title);
        if (TERM.equals(kind) && ti.length() == 0) return EMPTY;
        if (ti.length() == 0) ti = autoTitle(text);
        Entry e = new Entry();
        e.id = next++;
        e.kind = kind;
        e.folder = hasFolder(folder) ? folder : "";
        e.title = ti;
        e.text = text;
        e.made = now;
        entries.add(0, e);
        lastId = e.id;
        return OK;
    }

    /** True when an entry already has exactly this text (used for "Already saved"; terms are not checked by the panel). */
    public boolean hasText(String text) {
        if (text == null) return false;
        for (Entry e : entries) if (e.text.equals(text)) return true;
        return false;
    }

    public Entry find(int id) {
        for (Entry e : entries) if (e.id == id) return e;
        return null;
    }

    /** Edit title / text / kind / folder of one entry (same id, same list position). */
    public int edit(int id, String kind, String folder, String title, String text) {
        Entry e = find(id);
        if (e == null) return NOT_FOUND;
        if (!isKind(kind)) return BAD_KIND;
        if (text == null || text.trim().length() == 0) return EMPTY;
        if (text.length() > MAX_LEN) return TOO_LONG;
        String ti = cleanTitle(title);
        if (TERM.equals(kind) && ti.length() == 0) return EMPTY;
        if (ti.length() == 0) ti = autoTitle(text);
        e.kind = kind;
        e.folder = hasFolder(folder) ? folder : "";
        e.title = ti;
        e.text = text;
        return OK;
    }

    public boolean remove(int id) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id == id) { entries.remove(i); return true; }
        return false;
    }

    /** Remove many; returns how many were removed. */
    public int removeAll(List<Integer> ids) {
        int n = 0;
        if (ids == null) return 0;
        for (Integer id : ids) if (id != null && remove(id.intValue())) n++;
        return n;
    }

    /** Move entries to a folder ("" = Unfiled). Unknown folder = nothing moved. Returns how many moved. */
    public int move(List<Integer> ids, String folder) {
        if (ids == null || folder == null) return 0;
        if (folder.length() > 0 && !hasFolder(folder)) return 0;
        int n = 0;
        for (Integer id : ids) {
            Entry e = id == null ? null : find(id.intValue());
            if (e != null) { e.folder = folder; n++; }
        }
        return n;
    }

    /** Count a use (tap = paste). */
    public void noteUse(int id, long now) {
        Entry e = find(id);
        if (e == null) return;
        e.count++;
        e.used = now;
    }

    // ------------------------------------------------------------------ folders

    private static String cleanFolder(String s) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').replace('\r', ' ').trim();
        return t;
    }

    /** OK / EMPTY / TOO_LONG / EXISTS (same name, any case) / FULL. */
    public int addFolder(String name) {
        String n = cleanFolder(name);
        if (n.length() == 0) return EMPTY;
        if (n.length() > MAX_FOLDER_NAME) return TOO_LONG;
        for (String f : folders) if (f.equalsIgnoreCase(n)) return EXISTS;
        if (folders.size() >= MAX_FOLDERS) return FULL;
        folders.add(n);
        return OK;
    }

    /** Delete a folder. Its entries are NOT deleted: they become Unfiled. Returns how many entries were moved, -1 = no such folder. */
    public int removeFolder(String name) {
        if (!folders.remove(name)) return -1;
        int n = 0;
        for (Entry e : entries) if (name.equals(e.folder)) { e.folder = ""; n++; }
        return n;
    }

    /** Adds the 4 Novel Translation folders that are missing. Returns how many were added. */
    public int addPresetFolders() {
        int n = 0;
        for (String f : NOVEL_FOLDERS) if (addFolder(f) == OK) n++;
        return n;
    }

    public int countIn(String folder) {
        int n = 0;
        for (Entry e : entries) if (folder.equals(e.folder)) n++;
        return n;
    }

    // ------------------------------------------------------------------ views

    /**
     * Entries to show. kind: null = all kinds. "snippet" also matches "template" (one Snippets chip).
     * folder: null = all folders, "" = Unfiled only, else that folder. query: every word must be in title + text + folder (no case).
     * sort: NEWEST (list order, newest on top) / AZ (title) / USED (count, then last used).
     */
    public List<Entry> view(String kind, String folder, String query, int sort) {
        List<Entry> out = new ArrayList<Entry>();
        String[] words = words(query);
        for (Entry e : entries) {
            if (kind != null && !kindMatch(kind, e.kind)) continue;
            if (folder != null && !folder.equals(e.folder)) continue;
            if (words.length > 0) {
                String hay = (e.title + "\n" + e.text + "\n" + e.folder).toLowerCase(Locale.ROOT);
                boolean all = true;
                for (String w : words) if (hay.indexOf(w) < 0) { all = false; break; }
                if (!all) continue;
            }
            out.add(e);
        }
        if (sort == AZ) {
            Collections.sort(out, new Comparator<Entry>() {
                public int compare(Entry a, Entry b) { return a.title.toLowerCase(Locale.ROOT).compareTo(b.title.toLowerCase(Locale.ROOT)); }
            });
        } else if (sort == USED) {
            Collections.sort(out, new Comparator<Entry>() {
                public int compare(Entry a, Entry b) {
                    if (a.count != b.count) return b.count - a.count;
                    return a.used == b.used ? 0 : (a.used < b.used ? 1 : -1);
                }
            });
        }
        return out;
    }

    private static boolean kindMatch(String filter, String kind) {
        if (SNIPPET.equals(filter)) return SNIPPET.equals(kind) || TEMPLATE.equals(kind);
        return filter.equals(kind);
    }

    private static String[] words(String q) {
        if (q == null) return new String[0];
        String t = q.trim().toLowerCase(Locale.ROOT);
        if (t.length() == 0) return new String[0];
        return t.split("\\s+");
    }

    // ------------------------------------------------------------------ v0.4.5: Glossary + Notes helpers

    /** What a TAP pastes: a term pastes the TERM (title); every other kind pastes its text. */
    public static String pasteText(Entry e) {
        if (e == null) return "";
        return TERM.equals(e.kind) ? e.title : e.text;
    }

    /** The meaning of a term (its text); other kinds have none. */
    public static String meaning(Entry e) {
        if (e == null || !TERM.equals(e.kind)) return "";
        return e.text;
    }

    /** How many entries of this kind ("snippet" counts templates too; null = all). */
    public int countKind(String kind) {
        int n = 0;
        for (Entry e : entries) if (kind == null || kindMatch(kind, e.kind)) n++;
        return n;
    }

    /** Screen label of a kind. */
    public static String kindLabel(String kind) {
        if (SNIPPET.equals(kind)) return "Snippet";
        if (TEMPLATE.equals(kind)) return "Template";
        if (NOTE.equals(kind)) return "Note";
        if (TERM.equals(kind)) return "Term";
        return "?";
    }

    /** Next kind in the order snippet -> template -> note -> term -> snippet (edit screen button). */
    public static String nextKind(String kind) {
        for (int i = 0; i < KINDS.length; i++) if (KINDS[i].equals(kind)) return KINDS[(i + 1) % KINDS.length];
        return SNIPPET;
    }

    // ------------------------------------------------------------------ JSON

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ver\":").append(VER).append(",\"next\":").append(next).append(",\"folders\":[");
        for (int i = 0; i < folders.size(); i++) { if (i > 0) sb.append(','); sb.append(q(folders.get(i))); }
        sb.append("],\"entries\":[");
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(e.id)
              .append(",\"kind\":").append(q(e.kind))
              .append(",\"folder\":").append(q(e.folder))
              .append(",\"title\":").append(q(e.title))
              .append(",\"text\":").append(q(e.text))
              .append(",\"made\":").append(e.made)
              .append(",\"used\":").append(e.used)
              .append(",\"count\":").append(e.count).append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Parse saved text. Returns null if damaged / not JSON / "ver" missing or newer than this code. Empty text is NOT handled here (store = new list). */
    public static VaultList parse(String json) {
        try {
            if (json == null) return null;
            Object root = new P(json).root();
            if (!(root instanceof Map)) return null;
            Map<?, ?> m = (Map<?, ?>) root;
            Object v = m.get("ver");
            if (!(v instanceof Long) || ((Long) v).longValue() < 1 || ((Long) v).longValue() > VER) return null;
            VaultList l = new VaultList();
            Object fs = m.get("folders");
            if (fs instanceof List) for (Object o : (List<?>) fs) if (o instanceof String) l.addFolder((String) o);
            Object es = m.get("entries");
            int maxId = 0;
            if (es instanceof List) {
                for (Object o : (List<?>) es) {
                    if (!(o instanceof Map) || l.entries.size() >= MAX_ENTRIES) continue;
                    Map<?, ?> em = (Map<?, ?>) o;
                    Entry e = new Entry();
                    e.id = (int) num(em.get("id"));
                    String kind = str(em.get("kind"));
                    e.kind = isKind(kind) ? kind : SNIPPET;
                    String folder = str(em.get("folder"));
                    e.folder = l.hasFolder(folder) ? folder : "";
                    e.text = str(em.get("text"));
                    if (e.text.length() > MAX_LEN) e.text = e.text.substring(0, MAX_LEN);
                    if (e.text.trim().length() == 0) continue;
                    e.title = cleanTitle(str(em.get("title")));
                    if (e.title.length() == 0) e.title = autoTitle(e.text);
                    e.made = num(em.get("made"));
                    e.used = num(em.get("used"));
                    e.count = (int) num(em.get("count"));
                    if (e.id <= 0 || l.find(e.id) != null) continue;   // bad or duplicate id: skip (never invent data)
                    if (e.id > maxId) maxId = e.id;
                    l.entries.add(e);
                }
            }
            long nx = num(m.get("next"));
            l.next = (int) Math.max(nx, maxId + 1L);
            if (l.next < 1) l.next = 1;
            return l;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String str(Object o) { return o instanceof String ? (String) o : ""; }
    private static long num(Object o) { return o instanceof Long ? ((Long) o).longValue() : (o instanceof Double ? (long) ((Double) o).doubleValue() : 0L); }

    static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Tiny JSON reader (same idea as BackupCodec): objects, arrays, strings, numbers (integers as Long), true / false / null. */
    private static final class P {
        final String s; int i = 0;
        P(String s) { this.s = s; }
        Object root() { Object o = val(); ws(); if (i != s.length()) throw new IllegalStateException("trailing"); return o; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        Object val() {
            ws();
            if (i >= s.length()) throw new IllegalStateException("eof");
            char c = s.charAt(i);
            if (c == '{') {
                i++; Map<String, Object> m = new LinkedHashMap<String, Object>(); ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws(); String k = str(); ws(); expect(':'); m.put(k, val()); ws();
                    char d = s.charAt(i++);
                    if (d == '}') return m;
                    if (d != ',') throw new IllegalStateException("obj");
                }
            }
            if (c == '[') {
                i++; List<Object> l = new ArrayList<Object>(); ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(val()); ws();
                    char d = s.charAt(i++);
                    if (d == ']') return l;
                    if (d != ',') throw new IllegalStateException("arr");
                }
            }
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw new IllegalStateException("bad");
            String n = s.substring(st, i);
            if (n.indexOf('.') < 0 && n.indexOf('e') < 0 && n.indexOf('E') < 0) return Long.valueOf(Long.parseLong(n));
            return Double.valueOf(Double.parseDouble(n));
        }
        void expect(char c) { if (s.charAt(i++) != c) throw new IllegalStateException("expect"); }
        String str() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char d = s.charAt(i++);
                    switch (d) {
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: sb.append(d);
                    }
                } else sb.append(c);
            }
        }
    }
}
