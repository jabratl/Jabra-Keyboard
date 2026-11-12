package com.mgb.keyboard.typing;

import com.mgb.keyboard.vault.VaultList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.16.10 (X10): pure helpers for the NEW Vault list (panel_ui = 1). No Android, nothing stored, data format untouched.
 * The panel asks this class for the card title, the 1-line preview, the chip labels and the select-bar summary.
 */
public final class VaultView {
    private VaultView() { }

    public static final int PREVIEW_DEFAULT = 80;

    /** First non-blank line of a text, trimmed ("" when the text is null / blank). */
    public static String firstLine(String text) {
        if (text == null) return "";
        String[] lines = text.split("\\r?\\n");
        for (String s : lines) { String t = s.trim(); if (t.length() > 0) return t; }
        return "";
    }

    /** Card title: the entry title, else line 1 of the text, else "(empty)". Never null. */
    public static String title(VaultList.Entry e) {
        if (e == null) return "(empty)";
        String t = e.title == null ? "" : e.title.trim();
        if (t.length() == 0) t = firstLine(e.text);
        return t.length() == 0 ? "(empty)" : t;
    }

    /** Collapse all white space (new lines, tabs, runs of spaces) into single spaces and trim. */
    public static String flat(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * 1-line preview, at most n characters (adds "…" when cut; never splits an emoji / surrogate pair).
     * When the title came from line 1 of the text, that first line is skipped so it is not shown twice.
     */
    public static String preview(VaultList.Entry e, int n) {
        if (e == null) return "";
        if (n < 1) n = PREVIEW_DEFAULT;
        String text = e.text == null ? "" : e.text;
        String t = e.title == null ? "" : e.title.trim();
        if (t.length() == 0) {
            String fl = firstLine(text);
            int at = text.indexOf(fl);
            if (fl.length() > 0 && at >= 0) text = text.substring(at + fl.length());
        }
        return cut(flat(text), n);
    }

    /** Cut to n chars with an ellipsis, keeping surrogate pairs whole. */
    public static String cut(String s, int n) {
        if (s == null) return "";
        if (n < 1 || s.length() <= n) return s;
        int end = n;
        if (end > 0 && Character.isHighSurrogate(s.charAt(end - 1))) end--;
        return s.substring(0, end).trim() + "\u2026";
    }

    /** Small tag shown before the preview: "Template", "Note", "Term", "" for a plain snippet. */
    public static String tag(String kind) {
        if (VaultList.TEMPLATE.equals(kind)) return "Template";
        if (VaultList.NOTE.equals(kind)) return "Note";
        if (VaultList.TERM.equals(kind)) return "Term";
        return "";
    }

    /** Preview line with the tag: "Note · text". Empty tag = the preview alone. */
    public static String line2(VaultList.Entry e, int n) {
        if (e == null) return "";
        String tg = tag(e.kind);
        String p = preview(e, n);
        if (tg.length() == 0) return p;
        return p.length() == 0 ? tg : tg + " \u00b7 " + p;
    }

    /** Chip text: "Name 3", a tick in front when selected. Folder "" shows as "Unfiled". */
    public static String chipLabel(String name, int count, boolean selected) {
        String n = name == null || name.length() == 0 ? "Unfiled" : name;
        return (selected ? "\u2713 " : "") + n + " " + Math.max(0, count);
    }

    /** Select-mode summary: "3 selected" (0 -> "Tap items to select"). */
    public static String selectSummary(int picked) {
        if (picked <= 0) return "Tap items to select";
        return picked + " selected";
    }

    /** Header text over the list: "12 items" / "1 item" / "No items". */
    public static String countLine(int n) {
        if (n <= 0) return "No items";
        return n == 1 ? "1 item" : n + " items";
    }

    /** Group a ready (already searched + sorted) list by folder; folders keep the order of first appearance, "" = Unfiled. */
    public static LinkedHashMap<String, List<VaultList.Entry>> groupByFolder(List<VaultList.Entry> list) {
        LinkedHashMap<String, List<VaultList.Entry>> m = new LinkedHashMap<String, List<VaultList.Entry>>();
        if (list == null) return m;
        for (VaultList.Entry e : list) {
            if (e == null) continue;
            String f = e.folder == null ? "" : e.folder;
            List<VaultList.Entry> g = m.get(f);
            if (g == null) { g = new ArrayList<VaultList.Entry>(); m.put(f, g); }
            g.add(e);
        }
        return m;
    }

    /** Names of the long-press actions sheet, in order. Select mode has no sheet (the bottom bar is used). */
    public static List<String> sheetActions(VaultList.Entry e, boolean selectMode) {
        List<String> a = new ArrayList<String>();
        if (selectMode || e == null) return a;
        a.add("Edit");
        a.add("Move");
        a.add("Delete");
        a.add("Select");
        return a;
    }

    /** Bottom action bar in select mode: "All" only when something is not picked yet, Move / Delete only when something is picked. */
    public static List<String> barActions(int picked, int shown) {
        List<String> a = new ArrayList<String>();
        if (picked < shown) a.add("All");
        if (picked > 0) { a.add("Move"); if (picked == 1) a.add("Edit"); a.add("Delete"); }
        a.add("Done");
        return a;
    }

    /** Keep only ids that still exist in the list (after a delete / move the selection must not point at old ids). */
    public static List<Integer> liveIds(List<Integer> picked, List<VaultList.Entry> shown) {
        List<Integer> out = new ArrayList<Integer>();
        if (picked == null || shown == null) return out;
        Map<Integer, Boolean> have = new LinkedHashMap<Integer, Boolean>();
        for (VaultList.Entry e : shown) if (e != null) have.put(e.id, Boolean.TRUE);
        for (Integer id : picked) if (id != null && have.containsKey(id) && !out.contains(id)) out.add(id);
        return out;
    }
}
