package com.mgb.keyboard.hist;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * v0.21.0 (H2) typing history PAGE - the pure rules (no Android classes, tested by tools/HistDaysTest.java).
 *  - Days: "Today", "Yesterday", then "Mon 3 Nov 2026" (phone time zone). Newest day first, newest entry first inside a day.
 *  - Search: every word must be found (case-insensitive) in the text OR in the app (package name or the app name the page shows).
 *  - App name: [Labeler] gives the real app name when Android lets us see the package; otherwise a cleaned package name ("com.whatsapp" -> "Whatsapp").
 *  - Export: plain text, oldest first, grouped by day. Texts for the empty states, toasts and buttons.
 */
public final class HistDays {
    private HistDays() { }

    /** The page gives the real app name for a package (or null / empty = unknown). */
    public interface Labeler { String label(String pkg); }

    /** One day block on the page. */
    public static final class Day {
        public final String label;
        public final List<HistLog.Entry> items = new ArrayList<HistLog.Entry>();
        Day(String label) { this.label = label; }
    }

    static final long DAY_MS = 86400000L;

    /** Local day number (days since 1970-01-01 in the given time zone). */
    public static long dayNumber(long time, TimeZone tz) {
        long off = tz == null ? 0L : tz.getOffset(time);
        return Math.floorDiv(time + off, DAY_MS);
    }

    public static String dayLabel(long time, long now, TimeZone tz) {
        long d = dayNumber(time, tz), n = dayNumber(now, tz);
        if (d == n) return "Today";
        if (d == n - 1) return "Yesterday";
        SimpleDateFormat f = new SimpleDateFormat("EEE d MMM yyyy", Locale.ENGLISH);
        if (tz != null) f.setTimeZone(tz);
        return f.format(new Date(time));
    }

    /** "14:05" (24 hour, same on every phone). */
    public static String timeText(long time, TimeZone tz) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
        if (tz != null) f.setTimeZone(tz);
        return f.format(new Date(time));
    }

    /** Short app name from a package name: last part, first letter big. "" -> "Unknown app". */
    public static String cleanPackage(String pkg) {
        if (pkg == null) return "Unknown app";
        String p = pkg.trim();
        if (p.isEmpty()) return "Unknown app";
        String last = p;
        int i = p.lastIndexOf('.');
        if (i >= 0 && i < p.length() - 1) last = p.substring(i + 1);
        last = last.replace('_', ' ').replace('-', ' ').trim();
        if (last.isEmpty()) return p;
        return last.substring(0, 1).toUpperCase(Locale.ROOT) + last.substring(1);
    }

    /** App name shown on a row: the Labeler name when there is one, else the cleaned package name. */
    public static String appName(String pkg, Labeler lb) {
        String s = null;
        try { if (lb != null) s = lb.label(pkg); } catch (RuntimeException ex) { s = null; }
        if (s != null && !s.trim().isEmpty()) return s.trim();
        return cleanPackage(pkg);
    }

    /** Case-insensitive; every word of the query must be in the text, the package name or the shown app name. Empty query = everything. */
    public static boolean matches(HistLog.Entry e, String query, Labeler lb) {
        if (e == null) return false;
        if (query == null || query.trim().isEmpty()) return true;
        String hay = (e.text + "\n" + e.app + "\n" + appName(e.app, lb)).toLowerCase(Locale.ROOT);
        for (String w : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!w.isEmpty() && !hay.contains(w)) return false;
        }
        return true;
    }

    /** [oldestFirst] (as HistStore.entries() gives them) -> days, newest day first, newest entry first. Only entries that match the query. */
    public static List<Day> group(List<HistLog.Entry> oldestFirst, long now, TimeZone tz, String query, Labeler lb) {
        List<Day> out = new ArrayList<Day>();
        if (oldestFirst == null) return out;
        Day cur = null;
        long curNum = Long.MIN_VALUE;
        for (int i = oldestFirst.size() - 1; i >= 0; i--) {
            HistLog.Entry e = oldestFirst.get(i);
            if (!matches(e, query, lb)) continue;
            long dn = dayNumber(e.time, tz);
            if (cur == null || dn != curNum) { cur = new Day(dayLabel(e.time, now, tz)); curNum = dn; out.add(cur); }
            cur.items.add(e);
        }
        return out;
    }

    /** How many entries the days hold. */
    public static int count(List<Day> days) {
        int n = 0;
        if (days != null) for (Day d : days) n += d.items.size();
        return n;
    }

    // ---------- texts ----------

    /** Line above the list. [shown] = entries that match, [total] = all entries. */
    public static String summaryLine(int shown, int total, String query) {
        boolean q = query != null && !query.trim().isEmpty();
        if (total <= 0) return "No entries yet";
        if (!q) return total == 1 ? "1 entry" : total + " entries";
        return shown + " of " + total + (total == 1 ? " entry" : " entries");
    }

    /** Empty state, line 1. [on] = the switch, [total] = all entries, [query] = search text. */
    public static String emptyTitle(boolean on, int total, String query) {
        boolean q = query != null && !query.trim().isEmpty();
        if (total > 0 && q) return "Nothing found for \u201C" + query.trim() + "\u201D";
        if (!on) return "Typing history is OFF";
        return "Nothing saved yet";
    }

    /** Empty state, line 2. */
    public static String emptyHelp(boolean on, int total, String query) {
        boolean q = query != null && !query.trim().isEmpty();
        if (total > 0 && q) return "Try another word or part of the app name.";
        if (!on) return "Turn it on in Data & Backup. Nothing is saved until you do.";
        return "Type something with Jabra Keyboard in another app. Entries show up here (passwords and pasted text are never saved).";
    }

    public static String copiedToast() { return "Copied. It is also in your clipboard history now."; }
    public static String deletedToast() { return "Entry deleted"; }
    public static String undoLabel() { return "Undo"; }
    public static String restoredToast() { return "Entry is back"; }
    public static String openLabel(int n) { return n > 0 ? "Open typing history (" + n + ")" : "Open typing history"; }
    public static String hintLine() { return "Tap an entry to copy it. Hold an entry to delete it."; }

    public static String exportName(long now, TimeZone tz) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ENGLISH);
        if (tz != null) f.setTimeZone(tz);
        return "jabra_typing_history_" + f.format(new Date(now)) + ".txt";
    }

    /**
     * Plain text for the export file: entries of [oldestFirst] that match the query, still oldest first, grouped by day.
     * Format:  "== Mon 3 Nov 2026 ==" then "14:05  Whatsapp" and the text indented by two spaces (every line of it).
     */
    public static String exportText(List<HistLog.Entry> oldestFirst, long now, TimeZone tz, String query, Labeler lb) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        long curNum = Long.MIN_VALUE;
        if (oldestFirst != null) {
            for (HistLog.Entry e : oldestFirst) {
                if (!matches(e, query, lb)) continue;
                long dn = dayNumber(e.time, tz);
                if (dn != curNum) { if (n > 0) sb.append('\n'); sb.append("== ").append(dayLabel(e.time, now, tz)).append(" ==\n"); curNum = dn; }
                sb.append(timeText(e.time, tz)).append("  ").append(appName(e.app, lb)).append('\n');
                for (String ln : e.text.split("\\r?\\n", -1)) sb.append("  ").append(ln).append('\n');
                n++;
            }
        }
        StringBuilder head = new StringBuilder();
        head.append("Jabra Keyboard typing history\n");
        SimpleDateFormat ef = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);
        if (tz != null) ef.setTimeZone(tz);
        head.append("Exported ").append(ef.format(new Date(now))).append('\n');
        head.append(n).append(n == 1 ? " entry" : " entries").append('\n').append('\n');
        return head.toString() + sb.toString();
    }
}
