package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.26.0 (R3, step 3f) RECENT FIND / WITH PAIRS of the Find & Replace panel. PURE JAVA (no Android), never throws.
 * PRIVACY: this is the ONLY place where typed Find / With texts are stored on the phone. OFF by default (pref rpr_on).
 *  - Stored in pref rpr_list as one text: pairs separated by "|", find and with separated by ";".
 *    The characters % | ; and every control character are written as %XXXX (4 hex digits), so the text is plain and XML-safe.
 *  - Prefix "rpr_" is NEVER part of Backup / Restore (BackupPlan.isSkippedPref).
 *  - Newest first, at most MAX (10), the same pair moves to the top, a pair with an empty Find is never stored,
 *    each text is cut to ReplacePads.FIELD_MAX characters.
 */
public final class RecentPairs {
    private RecentPairs() { }

    public static final int MAX = 10;
    public static final String PREF_ON = "rpr_on";
    public static final String PREF_LIST = "rpr_list";

    public static final class Pair {
        public final String find, with;
        public Pair(String find, String with) { this.find = find == null ? "" : find; this.with = with == null ? "" : with; }
        @Override public boolean equals(Object o) { return o instanceof Pair && ((Pair) o).find.equals(find) && ((Pair) o).with.equals(with); }
        @Override public int hashCode() { return find.hashCode() * 31 + with.hashCode(); }
    }

    // ------------------------------------------------------------------ rules

    /** Record a pair only when: the switch is ON, the field is not a password field, Find is not empty, and no rule set is active. */
    public static boolean shouldRecord(boolean on, boolean passwordField, String find, boolean setActive) {
        return on && !passwordField && !setActive && find != null && find.length() > 0;
    }

    /** New list = pair on top, same pair removed from below, at most MAX. Empty Find = list unchanged. Never changes the input list. */
    public static List<Pair> add(List<Pair> list, String find, String with) {
        List<Pair> out = new ArrayList<Pair>();
        try {
            if (find == null || find.length() == 0) { if (list != null) out.addAll(list); return cap(out); }
            Pair p = new Pair(cut(find), cut(with));
            out.add(p);
            if (list != null) for (Pair q : list) { if (q != null && !q.equals(p) && q.find.length() > 0) out.add(q); }
        } catch (Throwable t) { if (list != null) { out.clear(); out.addAll(list); } }
        return cap(out);
    }

    private static List<Pair> cap(List<Pair> l) {
        while (l.size() > MAX) l.remove(l.size() - 1);
        return l;
    }

    private static String cut(String s) {
        if (s == null) return "";
        int max = ReplacePads.FIELD_MAX;
        if (s.length() <= max) return s;
        int e = max;
        if (Character.isHighSurrogate(s.charAt(e - 1))) e--;
        return s.substring(0, e);
    }

    // ------------------------------------------------------------------ text <-> list

    private static boolean esc(char c) { return c == '%' || c == '|' || c == ';' || c < 0x20 || c == 0x7F || c == '\uFFFE' || c == '\uFFFF' || c == '\u2028' || c == '\u2029'; }

    static String enc(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc(c)) { String h = Integer.toHexString(c).toUpperCase(); sb.append('%'); for (int k = h.length(); k < 4; k++) sb.append('0'); sb.append(h); }
            else sb.append(c);
        }
        return sb.toString();
    }

    static String dec(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '%' && i + 5 <= s.length()) {
                try { sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 5; continue; } catch (Throwable t) { /* keep the % as it is */ }
            }
            sb.append(c); i++;
        }
        return sb.toString();
    }

    public static String serialize(List<Pair> list) {
        StringBuilder sb = new StringBuilder();
        try {
            if (list == null) return "";
            int n = 0;
            for (Pair p : list) {
                if (p == null || p.find.length() == 0) continue;
                if (n >= MAX) break;
                if (n > 0) sb.append('|');
                sb.append(enc(cut(p.find))).append(';').append(enc(cut(p.with)));
                n++;
            }
        } catch (Throwable t) { return ""; }
        return sb.toString();
    }

    /** Broken / unknown text = the readable pairs only (never throws, never more than MAX). */
    public static List<Pair> parse(String stored) {
        List<Pair> out = new ArrayList<Pair>();
        try {
            if (stored == null || stored.length() == 0) return out;
            int from = 0;
            while (from <= stored.length() && out.size() < MAX) {
                int bar = stored.indexOf('|', from);
                String item = bar < 0 ? stored.substring(from) : stored.substring(from, bar);
                int semi = item.indexOf(';');
                if (semi > 0 && item.indexOf(';', semi + 1) < 0) {
                    String f = dec(item.substring(0, semi));
                    if (f.length() > 0) out.add(new Pair(cut(f), cut(dec(item.substring(semi + 1)))));
                }
                if (bar < 0) break;
                from = bar + 1;
            }
        } catch (Throwable t) { /* what was read stays */ }
        return out;
    }

    // ------------------------------------------------------------------ texts

    /** One list row: find -> with (new lines shown as a return sign, long text cut). */
    public static String label(Pair p, int max) {
        if (p == null) return "";
        String f = vis(p.find, max), w = p.with.length() == 0 ? "\u2205" : vis(p.with, max);
        return f + "  \u2192  " + w;
    }

    private static String vis(String s, int max) {
        String t = s.replace("\n", "\u23CE").replace("\t", "\u21E5");
        if (max > 0 && t.length() > max) t = ReplaceExtras.head(t, max) + "\u2026";   // v0.28.0: never cut a surrogate pair
        return t;
    }

    /** Text of the switch in Data & Backup. Says clearly that the texts are stored. */
    public static String switchHelp() {
        return "When ON, the Find and With texts of every Replace you do are stored as plain text on this phone (last " + MAX + " pairs). "
             + "Turn it OFF and they are deleted. They are never part of Backup. Password fields are never recorded.";
    }

    public static String statusLine(boolean on, int n) {
        if (!on) return "Recent pairs is OFF. Nothing is stored.";
        return n == 0 ? "Recent pairs is ON. No pair stored yet." : "Recent pairs is ON. " + n + (n == 1 ? " pair" : " pairs") + " stored on this phone.";
    }

    /** Message of the panel's Recent list when there is nothing to show. */
    public static String emptyText(boolean on) {
        return on ? "No pair yet. Every Replace you do is added here." : "Recent pairs is OFF. Turn it on in the app: Data & Backup > Recent Find & Replace.";
    }
}
