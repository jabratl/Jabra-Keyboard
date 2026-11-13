package com.mgb.keyboard.layout;

import com.mgb.keyboard.probhat.ProbhatLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * v0.17.4 (K4) warnings of a custom layout (pure Java, no Android). Saving is NEVER blocked by a warning
 * (only {@link #fatal} blocks, same gate as LayoutOps.fatal). check() never throws.
 *
 * W_DUP        same tap output on 2+ keys of one layer (normal and shift compared separately; Latin letters case-insensitive)
 * W_OVERLAP    a swipe / long-press text equals the tap output of ANOTHER key in the layer
 * W_MISSING    layer has no backspace / enter / space / shift / symbols key (en av pb; symbol pages: backspace / enter / space)
 * W_NARROW     a key is narrower than weight 0.5
 * W_WIDE       a row is more than 25 % narrower (in weight) than the widest row of the layer
 * W_EMPTY_ROW  a row has only empty spots
 * W_COVERAGE   en / av: an a-z letter has no key; pb: a Bangla letter / vowel sign of ProbhatLayout can no longer be typed
 * W_PHONETIC   av: a key types a non-Latin letter and so bypasses the phonetic buffer
 * W_TOUCH      a key is narrower than 24 dp at the given screen width
 * W_NOEXIT     no way to symbols (letter layers) / back to letters (symbol pages) / to the language switch (2+ languages ON)
 * W_ROWSPREAD  key count differs by more than 4 between rows (the bottom row with the space bar is not counted)
 */
public final class LayoutCheck {
    private LayoutCheck() { }

    public static final int INFO = 0, LOW = 1, MED = 2, HIGH = 3;
    public static final String W_DUP = "W_DUP", W_OVERLAP = "W_OVERLAP", W_MISSING = "W_MISSING", W_NARROW = "W_NARROW",
            W_WIDE = "W_WIDE", W_EMPTY_ROW = "W_EMPTY_ROW", W_COVERAGE = "W_COVERAGE", W_PHONETIC = "W_PHONETIC",
            W_TOUCH = "W_TOUCH", W_NOEXIT = "W_NOEXIT", W_ROWSPREAD = "W_ROWSPREAD";
    public static final float NARROW = 0.5f, MIN_KEY_DP = 24f, WIDE_RATIO = 0.25f;
    public static final int SPREAD_MAX = 4;

    /** Facts the check cannot read from the model. */
    public static final class Env {
        public float screenDp = 360f;
        public int langsOn = 1;
        public Env() { }
        public Env(float screenDp, int langsOn) { this.screenDp = screenDp; this.langsOn = langsOn; }
    }

    public static final class Warn {
        public final String code;
        public final int severity;
        public final String layer;
        /** Keys involved, in reading order (the first is the one that keeps its content on auto-fix). */
        public final List<String> keyIds = new ArrayList<String>();
        /** Missing items: letters (W_COVERAGE), action ids (W_MISSING / W_NOEXIT). */
        public final List<String> items = new ArrayList<String>();
        public final String text;
        public boolean fixable;

        Warn(String code, int severity, String layer, String text) { this.code = code; this.severity = severity; this.layer = layer; this.text = text; }

        @Override public String toString() { return code + "[" + severity + "] " + layer + ": " + text + " " + keyIds + " " + items; }
    }

    public static List<String> fatal(LayoutDef m) { return LayoutOps.fatal(m); }

    public static int maxSeverity(List<Warn> w) {
        int s = -1;
        if (w != null) for (Warn x : w) if (x.severity > s) s = x.severity;
        return s;
    }

    public static List<Warn> check(LayoutDef m) { return check(m, new Env()); }

    public static List<Warn> check(LayoutDef m, Env env) {
        List<Warn> out = new ArrayList<Warn>();
        try {
            if (m == null || LayoutOps.valid(m) != null) return out;
            if (env == null) env = new Env();
            for (LayerDef l : m.layers) layer(m, l.id, env, out);
        } catch (Throwable e) { /* never throws */ }
        return out;
    }

    private static boolean letterLayer(String id) { return id.equals("en") || id.equals("av") || id.equals("pb"); }

    private static boolean symLayer(String id) { return id.length() == 2 && id.charAt(0) == 's'; }

    private static String key(String s, boolean fold) {
        return fold && LayoutText.isLatinLetters(s) ? s.toLowerCase(Locale.ROOT) : s;
    }

    private static void layer(LayoutDef m, String id, Env env, List<Warn> out) {
        if (LayoutText.isShareId(id)) return;      // shared rows are checked inside the layers that show them
        List<RowDef> rows = m.effectiveRows(id);
        List<KeyDef> keys = new ArrayList<KeyDef>();
        for (RowDef r : rows) keys.addAll(r.keys);

        dup(id, keys, out);
        overlap(id, keys, out);
        missing(id, keys, env, out);
        narrow(id, keys, out);
        shape(id, rows, env, out);
        coverage(id, keys, out);
        if (id.equals("av")) phonetic(id, keys, out);
        touch(id, rows, env, out);
    }

    // ---------- duplicates ----------
    private static void dup(String layer, List<KeyDef> keys, List<Warn> out) {
        for (int shift = 0; shift < 2; shift++) {
            Map<String, List<String>> by = new LinkedHashMap<String, List<String>>();
            for (KeyDef k : keys) {
                if (k.kind != KeyDef.CHAR) continue;
                String t = shift == 0 ? k.out[0] : k.shiftOut();
                if (t.length() == 0) continue;
                String kk = key(t, shift == 0);   // only the normal state folds q / Q; shift outputs are compared exactly
                List<String> l = by.get(kk);
                if (l == null) { l = new ArrayList<String>(); by.put(kk, l); }
                l.add(k.id);
            }
            for (Map.Entry<String, List<String>> e : by.entrySet()) {
                if (e.getValue().size() < 2) continue;
                if (shift == 1 && sameKeysAsNormal(keys, e.getValue(), e.getKey())) continue;
                Warn w = new Warn(W_DUP, MED, layer, "\"" + e.getKey() + "\" is on " + e.getValue().size() + " keys" + (shift == 1 ? " (shift)" : ""));
                w.keyIds.addAll(e.getValue());
                w.items.add(e.getKey());
                w.fixable = true;
                out.add(w);
            }
        }
    }

    /** A shift duplicate that is only the same pair of keys already reported for the normal state is not reported twice. */
    private static boolean sameKeysAsNormal(List<KeyDef> keys, List<String> ids, String text) {
        Set<String> a = new LinkedHashSet<String>(ids);
        Map<String, List<String>> by = new HashMap<String, List<String>>();
        for (KeyDef k : keys) {
            if (k.kind != KeyDef.CHAR || k.out[0].length() == 0) continue;
            String kk = key(k.out[0], true);
            List<String> l = by.get(kk);
            if (l == null) { l = new ArrayList<String>(); by.put(kk, l); }
            l.add(k.id);
        }
        for (List<String> l : by.values()) if (l.size() >= 2 && new LinkedHashSet<String>(l).equals(a)) return true;
        return false;
    }

    // ---------- overlap ----------
    private static void overlap(String layer, List<KeyDef> keys, List<Warn> out) {
        Map<String, String> tap = new HashMap<String, String>();
        for (KeyDef k : keys) {
            if (k.kind != KeyDef.CHAR) continue;
            if (k.out[0].length() > 0 && !tap.containsKey(k.out[0])) tap.put(k.out[0], k.id);
        }
        for (KeyDef k : keys) {
            if (k.kind == KeyDef.SPACER) continue;
            List<String> vals = new ArrayList<String>();
            vals.add(k.lp);
            vals.addAll(k.lpList);
            for (String s : k.sw) vals.add(s);
            Set<String> seen = new LinkedHashSet<String>();
            for (String v : vals) {
                if (v.length() == 0 || v.charAt(0) == '@' && v.length() > 1) continue;
                String other = tap.get(v);
                if (other != null && !other.equals(k.id) && seen.add(v)) {
                    Warn w = new Warn(W_OVERLAP, LOW, layer, "\"" + v + "\" is a swipe / long-press of one key and the tap of another");
                    w.keyIds.add(k.id); w.keyIds.add(other); w.items.add(v);
                    out.add(w);
                }
            }
        }
    }

    // ---------- missing / exit ----------
    private static boolean has(List<KeyDef> keys, String action, String at) {
        for (KeyDef k : keys) {
            if (k.kind == KeyDef.ACTION && k.action.equals(action)) return true;
            if (k.kind == KeyDef.SPACER) continue;
            if (k.lp.equals(at)) return true;
            for (String s : k.lpList) if (s.equals(at)) return true;
            for (String s : k.sw) if (s.equals(at)) return true;
        }
        return false;
    }

    private static void missing(String layer, List<KeyDef> keys, Env env, List<Warn> out) {
        boolean letters = letterLayer(layer);
        if (!letters && !symLayer(layer)) return;
        String[][] need = letters
                ? new String[][]{{"backspace", "@del_word"}, {"enter", "\u0000"}, {"space", "\u0000"}, {"shift", "@caps"}}
                : new String[][]{{"backspace", "@del_word"}, {"enter", "\u0000"}, {"space", "\u0000"}};
        for (String[] n : need) {
            if (!has(keys, n[0], n[1])) {
                Warn w = new Warn(W_MISSING, MED, layer, "no " + n[0] + " key in this layer");
                w.items.add(n[0]);
                w.fixable = true;
                out.add(w);
            }
        }
        if (!has(keys, "sym", "@sym")) {
            Warn w = new Warn(W_NOEXIT, HIGH, layer, letters ? "no key opens the symbols page" : "no key goes back to the letters");
            w.items.add("sym");
            w.fixable = true;
            out.add(w);
        }
        if (letters && env.langsOn >= 2 && !has(keys, "lang", "@lang")) {
            Warn w = new Warn(W_NOEXIT, HIGH, layer, "no key switches the language (2+ languages are ON)");
            w.items.add("lang");
            w.fixable = true;
            out.add(w);
        }
    }

    // ---------- width, shape ----------
    private static void narrow(String layer, List<KeyDef> keys, List<Warn> out) {
        for (KeyDef k : keys) {
            if (k.kind == KeyDef.SPACER || k.weight >= NARROW) continue;
            Warn w = new Warn(W_NARROW, LOW, layer, "key is very narrow (" + k.weight + ")");
            w.keyIds.add(k.id);
            w.fixable = true;
            out.add(w);
        }
    }

    private static boolean onlySpacers(RowDef r) {
        for (KeyDef k : r.keys) if (k.kind != KeyDef.SPACER) return false;
        return true;
    }

    private static boolean hasSpace(RowDef r) {
        for (KeyDef k : r.keys) if (k.kind == KeyDef.ACTION && k.action.equals("space")) return true;
        return false;
    }

    private static void shape(String layer, List<RowDef> rows, Env env, List<Warn> out) {
        float max = 0f;
        for (RowDef r : rows) max = Math.max(max, r.totalWeight());
        int lo = Integer.MAX_VALUE, hi = 0, counted = 0;
        for (int i = 0; i < rows.size(); i++) {
            RowDef r = rows.get(i);
            if (onlySpacers(r)) out.add(new Warn(W_EMPTY_ROW, INFO, layer, "row " + (i + 1) + " has only empty spots"));
            if (max > 0f && (max - r.totalWeight()) / max > WIDE_RATIO) {
                Warn w = new Warn(W_WIDE, INFO, layer, "row " + (i + 1) + " is much narrower than the widest row");
                if (r.keys.size() > 0) w.keyIds.add(r.keys.get(0).id);
                out.add(w);
            }
            if (hasSpace(r)) continue;
            int n = 0;
            for (KeyDef k : r.keys) if (k.kind != KeyDef.SPACER) n++;
            lo = Math.min(lo, n); hi = Math.max(hi, n); counted++;
        }
        if (counted >= 2 && hi - lo > SPREAD_MAX) out.add(new Warn(W_ROWSPREAD, INFO, layer, "rows have very different key counts (" + lo + " to " + hi + ")"));
    }

    private static void touch(String layer, List<RowDef> rows, Env env, List<Warn> out) {
        if (!(env.screenDp > 0f)) return;
        for (RowDef r : rows) {
            float tot = r.totalWeight();
            if (!(tot > 0f)) continue;
            for (KeyDef k : r.keys) {
                if (k.kind == KeyDef.SPACER) continue;
                if (env.screenDp * k.weight / tot < MIN_KEY_DP) {
                    Warn w = new Warn(W_TOUCH, INFO, layer, "key is narrower than " + (int) MIN_KEY_DP + " dp on this screen");
                    w.keyIds.add(k.id);
                    out.add(w);
                }
            }
        }
    }

    // ---------- coverage, phonetic ----------
    private static void typed(KeyDef k, Set<String> into) {
        if (k.kind == KeyDef.CHAR) { into.add(k.out[0]); into.add(k.shiftOut()); }
        if (k.kind == KeyDef.SPACER) return;
        into.add(k.lp);
        into.addAll(k.lpList);
        for (String s : k.sw) into.add(s);
    }

    private static void coverage(String layer, List<KeyDef> keys, List<Warn> out) {
        boolean latin = layer.equals("en") || layer.equals("av"), pb = layer.equals("pb");
        if (!latin && !pb) return;
        Set<String> have = new LinkedHashSet<String>();
        for (KeyDef k : keys) typed(k, have);
        Set<String> low = new LinkedHashSet<String>();
        for (String s : have) low.add(key(s, true));
        List<String> miss = new ArrayList<String>();
        if (latin) {
            for (char c = 'a'; c <= 'z'; c++) if (!low.contains(String.valueOf(c))) miss.add(String.valueOf(c));
        } else {
            Set<String> want = new LinkedHashSet<String>();
            for (char c = 'a'; c <= 'z'; c++) {
                String n = ProbhatLayout.map(c), s = ProbhatLayout.map(Character.toUpperCase(c));
                if (n != null) want.add(n);
                if (s != null) want.add(s);
            }
            for (String e : ProbhatLayout.EXTRAS) want.add(e);
            for (String w : want) if (!have.contains(w)) miss.add(w);
        }
        if (miss.size() > 0) {
            Warn w = new Warn(W_COVERAGE, HIGH, layer, miss.size() + " letter(s) can no longer be typed");
            w.items.addAll(miss);
            w.fixable = true;
            out.add(w);
        }
    }

    private static void phonetic(String layer, List<KeyDef> keys, List<Warn> out) {
        for (KeyDef k : keys) {
            if (k.kind != KeyDef.CHAR) continue;
            boolean bad = false;
            for (int i = 0; i < k.out[0].length(); ) {
                int cp = k.out[0].codePointAt(i);
                i += Character.charCount(cp);
                if (Character.isLetter(cp) && cp >= 0x250) bad = true;
            }
            if (bad) {
                Warn w = new Warn(W_PHONETIC, INFO, layer, "key types a non-Latin letter and bypasses the phonetic buffer");
                w.keyIds.add(k.id);
                out.add(w);
            }
        }
    }

    // ---------- auto-fix (one undo step: the caller commits the returned model once) ----------
    private static KeyDef find(LayoutDef m, String id) {
        for (LayerDef l : m.layers) for (RowDef r : l.rows) for (KeyDef k : r.keys) if (k.id.equals(id)) return k;
        return null;
    }

    private static boolean hasContent(KeyDef k) {
        if (k.out[0].length() + k.out[1].length() + k.lp.length() > 0 || k.lpList.size() > 0) return true;
        for (String s : k.sw) if (s.length() > 0) return true;
        return false;
    }

    /** The WIDEST empty spot of the layer (shared rows included; ties = the first), or null. A key that was just emptied is wider than a half-key gap, so it gets its key back. */
    private static String firstSpacer(LayoutDef m, String layer) {
        String best = null;
        float bw = -1f;
        for (RowDef r : m.effectiveRows(layer)) for (KeyDef k : r.keys) if (k.kind == KeyDef.SPACER && k.weight > bw) { best = k.id; bw = k.weight; }
        return best;
    }

    /**
     * Safe fix of a warning, or the SAME instance when there is none (W_DUP: clear the later duplicates; W_MISSING / W_NOEXIT:
     * put the key into the widest empty spot or after the last key; W_COVERAGE: put the missing letters into empty spots;
     * W_NARROW: widen to 0.5). Never returns a model that the safety gate (fatal) would refuse when the old one passed.
     */
    public static LayoutDef autoFix(LayoutDef m, Warn w) {
        try {
            if (m == null || w == null || !w.fixable || LayoutOps.valid(m) != null) return m;
            LayoutDef r = m;
            if (w.code.equals(W_DUP)) {
                for (int i = 1; i < w.keyIds.size(); i++) {
                    String id = w.keyIds.get(i);
                    KeyDef k = find(r, id);
                    if (k == null || k.kind != KeyDef.CHAR) continue;
                    r = LayoutOps.setOut(r, id, 0, "");
                    r = LayoutOps.setOut(r, id, 1, "");
                    KeyDef k2 = find(r, id);
                    if (k2 != null && !hasContent(k2)) r = LayoutOps.makeSpacer(r, id);
                }
            } else if (w.code.equals(W_NARROW)) {
                for (String id : w.keyIds) r = LayoutOps.setWeight(r, id, NARROW);
            } else if (w.code.equals(W_MISSING) || w.code.equals(W_NOEXIT)) {
                for (String a : w.items) r = putAction(r, w.layer, a);
            } else if (w.code.equals(W_COVERAGE)) {
                for (String t : w.items) {
                    String id = spot(r, w.layer);
                    if (id == null) break;
                    r = LayoutOps.fill(r, id, t);
                }
            } else return m;
            if (r == m) return m;
            if (LayoutOps.valid(r) != null) return m;
            if (LayoutOps.fatal(m).isEmpty() && !LayoutOps.fatal(r).isEmpty()) return m;
            return r;
        } catch (Throwable e) {
            return m;
        }
    }

    /** Id of a free empty spot of the layer: an existing one, else a new spot after the last key of the last row. */
    private static String spot(LayoutDef m, String layer) {
        return firstSpacer(m, layer);
    }

    private static LayoutDef putAction(LayoutDef m, String layer, String action) {
        String id = firstSpacer(m, layer);
        LayoutDef r = m;
        if (id == null) {
            List<RowDef> rows = m.effectiveRows(layer);
            if (rows.size() == 0) return m;
            RowDef last = rows.get(rows.size() - 1);
            if (last.keys.size() == 0) return m;
            String after = last.keys.get(last.keys.size() - 1).id;
            r = LayoutOps.insertAfter(m, after);
            if (r == m) return m;
            KeyDef nk = null;
            for (RowDef rr : r.effectiveRows(layer)) for (KeyDef k : rr.keys) if (k.kind == KeyDef.SPACER && find(m, k.id) == null) nk = k;
            if (nk == null) return m;
            id = nk.id;
        }
        r = LayoutOps.setAction(r, id, action);
        KeyDef k = find(r, id);
        if (k != null && k.kind == KeyDef.ACTION) {
            if (k.weight < 1f) r = LayoutOps.setWeight(r, id, 1f);
            if (action.equals("backspace")) r = LayoutOps.setFlags(r, id, false, false, false, true);
        }
        return r;
    }
}
