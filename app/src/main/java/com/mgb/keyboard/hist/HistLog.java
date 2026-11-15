package com.mgb.keyboard.hist;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * v0.20.0 (H1) typing history - the pure in-memory log (no Android classes, tested by tools/HistLogTest.java).
 * One ENTRY = one typing run: time it started, app (package name) and the text. A run ends on Enter, when the field / app changes,
 * after IDLE_MS without typing, or when it reaches MAX_ENTRY_CHARS. While a run is open, Backspace removes the last characters of it,
 * so the saved text follows what you really kept (it only follows text typed in THIS run: older text is never edited here).
 * Paste-like input (one event longer than MAX_CHUNK characters) is never saved. Limits: MAX_ENTRIES and MAX_TOTAL_CHARS (oldest go first).
 * Saved text format (HistLog.serialize / parseOrNull): header line "JHIST1", then one line per entry "time TAB app TAB text" (text escaped).
 * Used only while the user turned Typing history ON (default OFF). Plain text on the phone only.
 */
public final class HistLog {
    public static final int MAX_ENTRIES = 2000;
    public static final int MAX_TOTAL_CHARS = 300000;
    public static final int MAX_ENTRY_CHARS = 500;
    public static final long IDLE_MS = 120000L;
    public static final int MAX_CHUNK = 64;
    public static final String HEADER = "JHIST1";

    private static final AtomicLong IDS = new AtomicLong(1);

    /** One saved typing run. id is stable while the app runs (not saved in the file). */
    public static final class Entry {
        public final long id;
        public final long time;
        public final String app;
        public final String text;
        Entry(long id, long time, String app, String text) { this.id = id; this.time = time; this.app = app; this.text = text; }
    }

    private final ArrayList<Entry> closed = new ArrayList<Entry>();
    private long totalChars = 0L;          // chars of all closed entries
    private boolean dirty = false;

    // the open run (null = none)
    private StringBuilder open = null;
    private String openApp = "";
    private long openTime = 0L;
    private long openLast = 0L;
    private long openId = 0L;

    // ---------- typing ----------

    /** Typed text s in [app] at time now. Returns true when the log changed. */
    public boolean type(String s, String app, long now) {
        if (s == null || s.isEmpty()) return false;
        String a = cleanApp(app);
        if (s.codePointCount(0, s.length()) > MAX_CHUNK) { closeRun(); return false; }   // paste-like: not saved, and the run ends (the field now holds text we did not record)
        if (open != null && (!a.equals(openApp) || now - openLast > IDLE_MS || open.length() + s.length() > MAX_ENTRY_CHARS)) closeRun();
        if (open == null) { open = new StringBuilder(); openApp = a; openTime = now; openId = IDS.getAndIncrement(); }
        open.append(s);
        openLast = now;
        dirty = true;
        return true;
    }

    /** Backspace removed n characters (code points) before the cursor in [app]. Only the open run is edited. */
    public boolean back(int n, String app, long now) {
        if (open == null || n <= 0) return false;
        if (!cleanApp(app).equals(openApp)) { closeRun(); return false; }
        int len = open.length();
        int cut = n;
        while (cut > 0 && len > 0) {
            int cp = Character.codePointBefore(open, len);
            len -= Character.charCount(cp);
            cut--;
        }
        open.setLength(len);
        openLast = now;
        dirty = true;
        if (len == 0) open = null;
        return true;
    }

    /** Hold-backspace removed one word (same rule as typing/WordDelete). */
    public boolean backWord(String app, long now) {
        if (open == null) return false;
        int len = open.length();
        int from = Math.max(0, len - com.mgb.keyboard.typing.WordDelete.WINDOW);
        int n = com.mgb.keyboard.typing.WordDelete.count(open.substring(from));
        if (n <= 0) return false;
        if (!cleanApp(app).equals(openApp)) { closeRun(); return false; }
        open.setLength(len - Math.min(n, len));
        openLast = now;
        dirty = true;
        if (open.length() == 0) open = null;
        return true;
    }

    /** Ends the open run (Enter, field change, keyboard closed). Empty / blank runs are dropped. */
    public void closeRun() {
        if (open == null) return;
        String t = open.toString().trim();
        if (!t.isEmpty()) { closed.add(new Entry(openId, openTime, openApp, t)); totalChars += t.length(); }
        open = null;
        dirty = true;
        enforce();
    }

    // ---------- limits / retention ----------

    private void enforce() {
        while (!closed.isEmpty() && (closed.size() > MAX_ENTRIES || totalChars + (open == null ? 0 : open.length()) > MAX_TOTAL_CHARS)) {
            Entry e = closed.remove(0);
            totalChars -= e.text.length();
            dirty = true;
        }
    }

    /** Removes entries older than ttlMs (by start time; the open run by last typing). ttlMs <= 0 = nothing. Returns how many were removed. */
    public int prune(long now, long ttlMs) {
        if (ttlMs <= 0L) return 0;
        long cutoff = now - ttlMs;
        int n = 0;
        while (!closed.isEmpty() && closed.get(0).time < cutoff) { Entry e = closed.remove(0); totalChars -= e.text.length(); n++; }
        // entries are in time order, but a merged / odd file may not be: scan the rest too
        for (int i = closed.size() - 1; i >= 0; i--) {
            Entry e = closed.get(i);
            if (e.time < cutoff) { closed.remove(i); totalChars -= e.text.length(); n++; }
        }
        if (open != null && openLast < cutoff) { open = null; n++; }
        if (n > 0) dirty = true;
        return n;
    }

    // ---------- reading ----------

    /** All entries, oldest first (the open run is the last one, as it is now). A copy: safe to keep. */
    public List<Entry> entries() {
        ArrayList<Entry> r = new ArrayList<Entry>(closed);
        if (open != null) { String t = open.toString().trim(); if (!t.isEmpty()) r.add(new Entry(openId, openTime, openApp, t)); }
        return r;
    }

    public int size() {
        int n = closed.size();
        if (open != null && open.toString().trim().length() > 0) n++;
        return n;
    }

    /** Removes one entry by id (also the open run). Returns true when found. */
    public boolean remove(long id) {
        if (open != null && openId == id) { open = null; dirty = true; return true; }
        for (int i = 0; i < closed.size(); i++) {
            if (closed.get(i).id == id) { totalChars -= closed.get(i).text.length(); closed.remove(i); dirty = true; return true; }
        }
        return false;
    }

    /**
     * v0.21.0 (H2): puts back an entry that was just removed (Undo on the history page). Same id, same time, inserted in time order.
     * Returns false for a null / blank entry or when an entry with this id is already there. The limits still apply.
     */
    public boolean restore(Entry e) {
        if (e == null || e.text == null || e.text.trim().isEmpty()) return false;
        if (open != null && openId == e.id) return false;
        for (Entry c : closed) if (c.id == e.id) return false;
        int i = closed.size();
        while (i > 0 && closed.get(i - 1).time > e.time) i--;
        closed.add(i, new Entry(e.id, e.time, cleanApp(e.app), e.text));
        totalChars += e.text.length();
        dirty = true;
        enforce();
        return true;
    }

    /** v0.23.0 (H3): removes every entry (and the open run) of one app. Returns how many entries were removed. */
    public int removeApp(String app) {
        if (app == null || app.isEmpty()) return 0;
        int n = 0;
        if (open != null && app.equals(openApp)) { open = null; n++; }
        for (int i = closed.size() - 1; i >= 0; i--) {
            if (app.equals(closed.get(i).app)) { totalChars -= closed.get(i).text.length(); closed.remove(i); n++; }
        }
        if (n > 0) dirty = true;
        return n;
    }

    /** v0.23.0 (H3): how many entries (the open run too) belong to one app. */
    public int countApp(String app) {
        if (app == null || app.isEmpty()) return 0;
        int n = (open != null && app.equals(openApp)) ? 1 : 0;
        for (Entry e : closed) if (app.equals(e.app)) n++;
        return n;
    }

    /** Removes everything. Returns how many entries were removed. */
    public int clear() {
        int n = size();
        closed.clear(); totalChars = 0L; open = null; dirty = true;
        return n;
    }

    /** Puts the entries of an older (loaded) log in front of this one, keeps time order, applies the limits. */
    public void mergeOlder(HistLog older) {
        if (older == null || older.closed.isEmpty()) return;
        ArrayList<Entry> all = new ArrayList<Entry>(older.closed.size() + closed.size());
        for (Entry e : older.closed) all.add(new Entry(IDS.getAndIncrement(), e.time, e.app, e.text));
        all.addAll(closed);
        // stable insertion sort by time (lists are nearly sorted already)
        for (int i = 1; i < all.size(); i++) {
            Entry x = all.get(i); int j = i - 1;
            while (j >= 0 && all.get(j).time > x.time) { all.set(j + 1, all.get(j)); j--; }
            all.set(j + 1, x);
        }
        closed.clear(); closed.addAll(all);
        totalChars = 0L; for (Entry e : closed) totalChars += e.text.length();
        dirty = true;
        enforce();
    }

    public boolean isDirty() { return dirty; }
    public void markClean() { dirty = false; }
    public void markDirty() { dirty = true; }

    // ---------- file text ----------

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        for (Entry e : entries()) {
            sb.append(e.time).append('\t').append(cleanApp(e.app)).append('\t').append(esc(e.text)).append('\n');
        }
        return sb.toString();
    }

    /** null = not a history file (wrong / missing header). Bad lines are skipped. */
    public static HistLog parseOrNull(String text) {
        if (text == null) return null;
        String[] lines = text.split("\n", -1);
        if (lines.length == 0 || !HEADER.equals(lines[0].replace("\r", ""))) return null;
        HistLog log = new HistLog();
        for (int i = 1; i < lines.length; i++) {
            String ln = lines[i];
            if (ln.isEmpty()) continue;
            int a = ln.indexOf('\t');
            int b = a < 0 ? -1 : ln.indexOf('\t', a + 1);
            if (a <= 0 || b < 0) continue;
            long t;
            try { t = Long.parseLong(ln.substring(0, a)); } catch (NumberFormatException ex) { continue; }
            String app = ln.substring(a + 1, b);
            String body = unesc(ln.substring(b + 1)).trim();
            if (body.isEmpty()) continue;
            if (body.length() > MAX_ENTRY_CHARS * 2) body = body.substring(0, MAX_ENTRY_CHARS * 2);
            log.closed.add(new Entry(IDS.getAndIncrement(), t, cleanApp(app), body));
            log.totalChars += body.length();
        }
        log.enforce();
        log.dirty = false;
        return log;
    }

    static String cleanApp(String a) {
        if (a == null) return "";
        return a.replace('\t', '_').replace('\n', '_').replace('\r', '_');
    }

    static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else sb.append(c);
        }
        return sb.toString();
    }

    static String unesc(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char d = s.charAt(++i);
                if (d == 'n') sb.append('\n');
                else if (d == 'r') sb.append('\r');
                else if (d == 't') sb.append('\t');
                else if (d == '\\') sb.append('\\');
                else { sb.append(c).append(d); }
            } else sb.append(c);
        }
        return sb.toString();
    }
}
