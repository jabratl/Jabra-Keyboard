package com.mgb.keyboard.layout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * v0.17.3 (K3) pure edit operations of the custom keyboard layout (no Android, tested on a computer).
 *
 * RULES (every method):
 *  - NEVER throws (null / unknown id / wrong index / NaN = refused).
 *  - A refused or useless change returns the SAME model instance (callers can test "m2 == m1" = nothing happened).
 *  - A change returns a NEW model (deep copy); the old one is never touched (undo = keep the old one).
 *  - The result is always structurally valid ({@link #valid} == null): weights 0.25..8, texts cleaned (R2 / R3),
 *    ids unique, max 80 keys per layer, max 8 rows per layer, every own row keeps at least 1 key.
 *  - Ops allow "bad but valid" layouts (no backspace ...): warnings are LayoutCheck's job (K4), blocking is
 *    {@link #fatal}'s job (safety gate before saving).
 */
public final class LayoutOps {
    private LayoutOps() { }

    // ---------- finders ----------
    private static final class Ctx { LayoutDef c; LayerDef L; RowDef R; KeyDef K; int li, ri, ki; }

    private static Ctx ctx(LayoutDef m, String id) {
        if (m == null || id == null) return null;
        for (int a = 0; a < m.layers.size(); a++) {
            LayerDef l = m.layers.get(a);
            for (int b = 0; b < l.rows.size(); b++) {
                RowDef r = l.rows.get(b);
                if (r.isRef()) continue;
                for (int c = 0; c < r.keys.size(); c++) {
                    if (id.equals(r.keys.get(c).id)) {
                        Ctx x = new Ctx();
                        x.c = m.copy(); x.li = a; x.ri = b; x.ki = c;
                        x.L = x.c.layers.get(a); x.R = x.L.rows.get(b); x.K = x.R.keys.get(c);
                        return x;
                    }
                }
            }
        }
        return null;
    }

    private static Ctx ctxRow(LayoutDef m, String layerId, int row) {
        if (m == null || layerId == null) return null;
        for (int a = 0; a < m.layers.size(); a++) {
            if (!m.layers.get(a).id.equals(layerId)) continue;
            if (row < 0 || row >= m.layers.get(a).rows.size()) return null;
            Ctx x = new Ctx();
            x.c = m.copy(); x.li = a; x.ri = row;
            x.L = x.c.layers.get(a); x.R = x.L.rows.get(row);
            return x;
        }
        return null;
    }

    private static Set<String> ids(LayoutDef m) {
        Set<String> s = new HashSet<String>();
        for (LayerDef l : m.layers) for (RowDef r : l.rows) for (KeyDef k : r.keys) s.add(k.id);
        return s;
    }

    /** Smallest free id "k1", "k2" ... (not used anywhere in the layout). */
    public static String newId(LayoutDef m) { return fresh(m == null ? new HashSet<String>() : ids(m)); }

    private static String fresh(Set<String> used) {
        int n = 1;
        while (used.contains("k" + n)) n++;
        String id = "k" + n;
        used.add(id);
        return id;
    }

    // ---------- normalising ----------
    /** Weight rounded to steps of 0.25 and clamped 0.25..8 (NaN = 1). */
    public static float qw(float w) {
        if (Float.isNaN(w)) return 1f;
        if (w < KeyDef.W_MIN) return KeyDef.W_MIN;
        if (w > KeyDef.W_MAX) return KeyDef.W_MAX;
        float q = Math.round(w * 4f) / 4f;
        return q < KeyDef.W_MIN ? KeyDef.W_MIN : (q > KeyDef.W_MAX ? KeyDef.W_MAX : q);
    }

    private static float cw(float w) {
        if (Float.isNaN(w)) return 1f;
        return w < KeyDef.W_MIN ? KeyDef.W_MIN : (w > KeyDef.W_MAX ? KeyDef.W_MAX : w);
    }

    private static void normKey(KeyDef k) {
        if (k.kind < 0 || k.kind > 2) k.kind = KeyDef.CHAR;
        k.weight = cw(k.weight);
        for (int i = 0; i < 2; i++) {
            k.out[i] = LayoutText.clean(k.out[i], LayoutText.MAX_OUT);
            k.label[i] = LayoutText.clean(k.label[i], LayoutText.MAX_LABEL);
        }
        k.lp = LayoutText.cleanSwipe(k.lp, LayoutText.MAX_LP);
        List<String> lpl = new ArrayList<String>();
        for (String s : k.lpList) {
            String t = LayoutText.cleanSwipe(s, LayoutText.MAX_LP);
            if (t.length() > 0 && lpl.size() < LayoutText.MAX_LPLIST) lpl.add(t);
        }
        k.lpList = lpl;
        for (int i = 0; i < 4; i++) k.sw[i] = LayoutText.cleanSwipe(k.sw[i], LayoutText.MAX_OUT);
        k.action = LayoutText.cleanWord(k.action, 16);
        if (!LayoutText.ACTIONS.contains(k.action)) k.action = "";
        k.combo = LayoutText.clean(k.combo, LayoutText.MAX_COMBO);
        k.labelIcon = LayoutText.cleanWord(k.labelIcon, 16);
        if (k.kind == KeyDef.ACTION && k.action.length() == 0) k.kind = KeyDef.SPACER;
        if (k.kind == KeyDef.SPACER) {
            k.out = new String[]{"", ""}; k.label = new String[]{"", ""}; k.lp = ""; k.lpList = new ArrayList<String>();
            k.sw = new String[]{"", "", "", ""}; k.action = ""; k.combo = ""; k.labelIcon = "";
            k.bold = false; k.wideHint = false; k.noPopup = false; k.repeat = false;
        } else if (k.kind == KeyDef.CHAR) {
            k.action = ""; k.combo = "";
        } else {
            k.out = new String[]{"", ""};
            if (!k.action.equals("combo")) k.combo = "";
        }
    }

    /** Normalises a model in place (it must be a private copy) and returns it. */
    private static LayoutDef fin(LayoutDef m) {
        Set<String> seen = new HashSet<String>();
        List<KeyDef> dupes = new ArrayList<KeyDef>();
        for (LayerDef l : m.layers) {
            for (RowDef r : l.rows) {
                if (r.heightPct < RowDef.H_MIN) r.heightPct = RowDef.H_MIN;
                if (r.heightPct > RowDef.H_MAX) r.heightPct = RowDef.H_MAX;
                if (!LayoutText.isShareId(r.share)) r.share = "";
                for (KeyDef k : r.keys) {
                    normKey(k);
                    k.id = LayoutText.cleanId(k.id);
                    if (k.id.length() == 0 || !seen.add(k.id)) dupes.add(k);
                }
            }
        }
        for (KeyDef k : dupes) k.id = fresh(seen);
        return m;
    }

    /** v0.17.4 (K4): a normalised COPY of any model (texts cleaned, weights / heights clamped, duplicate ids renamed); null stays null. Used by the codec. */
    public static LayoutDef normalize(LayoutDef m) { return m == null ? null : fin(m.copy()); }

    // ---------- validation ----------
    /** Structural check. null = valid, else a short reason (used by tests and by the load gate). */
    public static String valid(LayoutDef m) {
        if (m == null) return "null";
        if (m.layers.size() > LayoutDef.MAX_LAYERS) return "too many layers";
        Set<String> lay = new HashSet<String>();
        Set<String> seen = new HashSet<String>();
        for (LayerDef l : m.layers) {
            if (!LayoutText.LAYERS.contains(l.id) || !lay.add(l.id)) return "bad layer id " + l.id;
            if (l.rows.size() > LayoutDef.MAX_ROWS) return "too many rows in " + l.id;
            if (l.keyCount() > LayoutDef.MAX_KEYS_LAYER) return "too many keys in " + l.id;
            for (RowDef r : l.rows) {
                if (r.heightPct < RowDef.H_MIN || r.heightPct > RowDef.H_MAX) return "bad row height";
                if (r.isRef()) {
                    if (!LayoutText.isShareId(r.share)) return "bad share";
                    if (r.share.equals(l.id)) return "row shares itself";
                    continue;
                }
                if (r.keys.size() == 0) return "empty row in " + l.id;
                for (KeyDef k : r.keys) {
                    String why = validKey(k);
                    if (why != null) return why + " (" + k.id + ")";
                    if (!seen.add(k.id)) return "duplicate id " + k.id;
                }
            }
        }
        for (LayerDef l : m.layers) for (RowDef r : l.rows) if (r.isRef() && m.layer(r.share) == null) return "missing shared layer " + r.share;
        return null;
    }

    private static String validKey(KeyDef k) {
        if (k.id == null || k.id.length() == 0 || !LayoutText.cleanId(k.id).equals(k.id)) return "bad id";
        if (k.kind < 0 || k.kind > 2) return "bad kind";
        if (Float.isNaN(k.weight) || k.weight < KeyDef.W_MIN || k.weight > KeyDef.W_MAX) return "bad weight";
        for (int i = 0; i < 2; i++) {
            if (!LayoutText.clean(k.out[i], LayoutText.MAX_OUT).equals(k.out[i])) return "bad out";
            if (!LayoutText.clean(k.label[i], LayoutText.MAX_LABEL).equals(k.label[i])) return "bad label";
        }
        if (!LayoutText.cleanSwipe(k.lp, LayoutText.MAX_LP).equals(k.lp)) return "bad lp";
        if (k.lpList.size() > LayoutText.MAX_LPLIST) return "bad lpList";
        for (String s : k.lpList) if (s.length() == 0 || !LayoutText.cleanSwipe(s, LayoutText.MAX_LP).equals(s)) return "bad lpList item";
        for (int i = 0; i < 4; i++) if (!LayoutText.cleanSwipe(k.sw[i], LayoutText.MAX_OUT).equals(k.sw[i])) return "bad swipe";
        if (k.kind == KeyDef.ACTION && !LayoutText.ACTIONS.contains(k.action)) return "bad action";
        if (k.kind != KeyDef.ACTION && k.action.length() > 0) return "action on non-action key";
        if (k.kind == KeyDef.SPACER && (k.out[0].length() + k.out[1].length() + k.lp.length() + k.sw[0].length() > 0)) return "spacer has content";
        return null;
    }

    /**
     * Safety gate (6.8 S1 / S4): reasons why this layout must NOT be used or saved. Empty list = OK.
     * Only these block; every other problem is just a warning (LayoutCheck, K4).
     */
    public static List<String> fatal(LayoutDef m) {
        List<String> f = new ArrayList<String>();
        String v = valid(m);
        if (v != null) { f.add("invalid: " + v); return f; }
        if (m.layers.size() == 0) { f.add("no layers"); return f; }
        for (LayerDef l : m.layers) {
            List<RowDef> er = m.effectiveRows(l.id);
            int n = 0;
            for (RowDef r : er) n += r.keys.size();
            if (er.size() == 0 || n == 0) f.add("layer " + l.id + " is empty");
        }
        LayerDef en = m.layer("en");
        if (en == null) { f.add("no English layer"); return f; }
        boolean ch = false, space = false, back = false, sym = false;
        for (RowDef r : m.effectiveRows("en")) {
            for (KeyDef k : r.keys) {
                if (k.kind == KeyDef.CHAR && k.out[0].length() > 0) ch = true;
                if (k.kind == KeyDef.ACTION) {
                    if (k.action.equals("space")) space = true;
                    if (k.action.equals("backspace")) back = true;
                    if (k.action.equals("sym")) sym = true;
                }
                for (int i = 0; i < 4; i++) if (k.sw[i].equals("@sym")) sym = true;
                if (k.lp.equals("@sym")) sym = true;
            }
        }
        if (!ch) f.add("English layer has no letter key");
        if (!space) f.add("English layer has no space key");
        if (!back) f.add("English layer has no backspace key");
        if (!sym) f.add("English layer has no way to open symbols");
        return f;
    }

    // ---------- one key: content ----------
    public static LayoutDef setWeight(LayoutDef m, String id, float w) {
        Ctx x = ctx(m, id); if (x == null) return m;
        float q = qw(w);
        if (q == x.K.weight) return m;
        x.K.weight = q; return fin(x.c);
    }

    /** idx 0 = normal, 1 = shift. Text on a spacer / action key turns it into a normal key; empty text there is refused. */
    public static LayoutDef setOut(LayoutDef m, String id, int idx, String text) {
        Ctx x = ctx(m, id); if (x == null || idx < 0 || idx > 1) return m;
        String t = LayoutText.clean(text, LayoutText.MAX_OUT);
        if (x.K.kind != KeyDef.CHAR && t.length() == 0) return m;
        if (x.K.kind == KeyDef.CHAR && x.K.out[idx].equals(t)) return m;
        x.K.kind = KeyDef.CHAR; x.K.action = "";
        x.K.out[idx] = t; return fin(x.c);
    }

    public static LayoutDef setLabel(LayoutDef m, String id, int idx, String text) {
        Ctx x = ctx(m, id); if (x == null || idx < 0 || idx > 1 || x.K.kind == KeyDef.SPACER) return m;
        String t = LayoutText.clean(text, LayoutText.MAX_LABEL);
        if (x.K.label[idx].equals(t)) return m;
        x.K.label[idx] = t; return fin(x.c);
    }

    public static LayoutDef setLp(LayoutDef m, String id, String text) {
        Ctx x = ctx(m, id); if (x == null || x.K.kind == KeyDef.SPACER) return m;
        String t = LayoutText.cleanSwipe(text, LayoutText.MAX_LP);
        if (x.K.lp.equals(t)) return m;
        x.K.lp = t; return fin(x.c);
    }

    public static LayoutDef setLpList(LayoutDef m, String id, List<String> list) {
        Ctx x = ctx(m, id); if (x == null || x.K.kind == KeyDef.SPACER) return m;
        x.K.lpList = list == null ? new ArrayList<String>() : new ArrayList<String>(list);
        LayoutDef r = fin(x.c);
        return r.equals(m) ? m : r;
    }

    /** dir 0 up, 1 down, 2 left, 3 right. Text or an "@action" of the whitelist. */
    public static LayoutDef setSwipe(LayoutDef m, String id, int dir, String text) {
        Ctx x = ctx(m, id); if (x == null || dir < 0 || dir > 3 || x.K.kind == KeyDef.SPACER) return m;
        String t = LayoutText.cleanSwipe(text, LayoutText.MAX_OUT);
        if (x.K.sw[dir].equals(t)) return m;
        x.K.sw[dir] = t; return fin(x.c);
    }

    /** Turns the key into an ACTION key (action must be in LayoutText.ACTIONS, else refused). */
    public static LayoutDef setAction(LayoutDef m, String id, String action) {
        Ctx x = ctx(m, id); if (x == null || action == null || !LayoutText.ACTIONS.contains(action)) return m;
        if (x.K.kind == KeyDef.ACTION && x.K.action.equals(action)) return m;
        x.K.kind = KeyDef.ACTION; x.K.action = action;
        return fin(x.c);
    }

    /** PC-mode combo key (e.g. "ctrl+67"): ACTION "combo" with the combo text. */
    public static LayoutDef setCombo(LayoutDef m, String id, String combo) {
        Ctx x = ctx(m, id); if (x == null) return m;
        String t = LayoutText.clean(combo, LayoutText.MAX_COMBO);
        if (t.length() == 0) return m;
        x.K.kind = KeyDef.ACTION; x.K.action = "combo"; x.K.combo = t;
        return fin(x.c);
    }

    public static LayoutDef setFlags(LayoutDef m, String id, boolean bold, boolean wideHint, boolean noPopup, boolean repeat) {
        Ctx x = ctx(m, id); if (x == null || x.K.kind == KeyDef.SPACER) return m;
        if (x.K.bold == bold && x.K.wideHint == wideHint && x.K.noPopup == noPopup && x.K.repeat == repeat) return m;
        x.K.bold = bold; x.K.wideHint = wideHint; x.K.noPopup = noPopup; x.K.repeat = repeat;
        return fin(x.c);
    }

    public static LayoutDef setLabelIcon(LayoutDef m, String id, String icon) {
        Ctx x = ctx(m, id); if (x == null || x.K.kind == KeyDef.SPACER) return m;
        String t = LayoutText.cleanWord(icon, 16);
        if (x.K.labelIcon.equals(t)) return m;
        x.K.labelIcon = t; return fin(x.c);
    }

    /** Empty spot: wipes everything except id and width. */
    public static LayoutDef makeSpacer(LayoutDef m, String id) {
        Ctx x = ctx(m, id); if (x == null || x.K.kind == KeyDef.SPACER) return m;
        x.K.kind = KeyDef.SPACER; return fin(x.c);
    }

    /** Fills an empty spot with a normal key that types out0 (spacer only). */
    public static LayoutDef fill(LayoutDef m, String id, String out0) {
        Ctx x = ctx(m, id); if (x == null || x.K.kind != KeyDef.SPACER) return m;
        x.K.kind = KeyDef.CHAR; x.K.out[0] = LayoutText.clean(out0, LayoutText.MAX_OUT);
        return fin(x.c);
    }

    // ---------- one key: position ----------
    public static LayoutDef moveLeft(LayoutDef m, String id) { return swapNeighbour(m, id, -1); }

    public static LayoutDef moveRight(LayoutDef m, String id) { return swapNeighbour(m, id, 1); }

    private static LayoutDef swapNeighbour(LayoutDef m, String id, int d) {
        Ctx x = ctx(m, id); if (x == null) return m;
        int j = x.ki + d;
        if (j < 0 || j >= x.R.keys.size()) return m;
        KeyDef t = x.R.keys.get(j); x.R.keys.set(j, x.K); x.R.keys.set(x.ki, t);
        return fin(x.c);
    }

    public static LayoutDef moveUp(LayoutDef m, String id) { return moveRow(m, id, -1); }

    public static LayoutDef moveDown(LayoutDef m, String id) { return moveRow(m, id, 1); }

    /** Moves the key into the row above / below at the same index (clamped). Refused when its row would become empty. */
    private static LayoutDef moveRow(LayoutDef m, String id, int d) {
        Ctx x = ctx(m, id); if (x == null) return m;
        int t = x.ri + d;
        if (t < 0 || t >= x.L.rows.size() || x.R.keys.size() < 2) return m;
        RowDef to = x.L.rows.get(t);
        if (to.isRef()) return m;
        x.R.keys.remove(x.ki);
        to.keys.add(Math.min(x.ki, to.keys.size()), x.K);
        return fin(x.c);
    }

    /** New empty spot (weight 1) before the key. Refused at 80 keys in the layer. */
    public static LayoutDef insertBefore(LayoutDef m, String id) { return insert(m, id, 0); }

    public static LayoutDef insertAfter(LayoutDef m, String id) { return insert(m, id, 1); }

    private static LayoutDef insert(LayoutDef m, String id, int off) {
        Ctx x = ctx(m, id); if (x == null || x.L.keyCount() >= LayoutDef.MAX_KEYS_LAYER) return m;
        x.R.keys.add(x.ki + off, KeyDef.spacer(fresh(ids(x.c)), 1f));
        return fin(x.c);
    }

    /** Deletes the key; a row always keeps at least 1 key (else refused). */
    public static LayoutDef delete(LayoutDef m, String id) {
        Ctx x = ctx(m, id); if (x == null || x.R.keys.size() < 2) return m;
        x.R.keys.remove(x.ki);
        return fin(x.c);
    }

    // ---------- merge / split ----------
    /** True when merging key id with its right neighbour moves the neighbour's letter into long-press (the editor says so). */
    public static boolean mergeMovesSecond(LayoutDef m, String id) {
        Ctx x = ctx(m, id); if (x == null || x.ki + 1 >= x.R.keys.size()) return false;
        KeyDef a = x.R.keys.get(x.ki), b = x.R.keys.get(x.ki + 1);
        return a.kind != KeyDef.SPACER && b.kind == KeyDef.CHAR && b.out[0].length() > 0 && a.lp.length() == 0;
    }

    /**
     * Merges the key with its right neighbour: width = sum (max 8). Content comes from the FIRST key; the second key's
     * letter becomes the first's long-press when the first has none. Two spacers = one spacer. A spacer first = the
     * second key's content. The first key's id stays.
     */
    public static LayoutDef merge(LayoutDef m, String id) {
        Ctx x = ctx(m, id); if (x == null || x.ki + 1 >= x.R.keys.size()) return m;
        KeyDef a = x.R.keys.get(x.ki), b = x.R.keys.get(x.ki + 1);
        float w = cw(a.weight + b.weight);
        KeyDef r;
        if (a.kind == KeyDef.SPACER) { r = b.copy(); r.id = a.id; }
        else {
            r = a.copy();
            if (b.kind == KeyDef.CHAR && b.out[0].length() > 0 && a.lp.length() == 0) r.lp = b.out[0];
        }
        r.weight = w;
        x.R.keys.set(x.ki, r); x.R.keys.remove(x.ki + 1);
        return fin(x.c);
    }

    /** Splits one key in two halves (min 0.25 each, else refused): first keeps everything, second = new empty spot. */
    public static LayoutDef split(LayoutDef m, String id) {
        Ctx x = ctx(m, id); if (x == null || x.L.keyCount() >= LayoutDef.MAX_KEYS_LAYER) return m;
        float h = x.K.weight / 2f;
        if (h < KeyDef.W_MIN) return m;
        x.K.weight = h;
        x.R.keys.add(x.ki + 1, KeyDef.spacer(fresh(ids(x.c)), h));
        return fin(x.c);
    }

    // ---------- rows ----------
    /** New row (one empty wide spot) after row index afterRow (-1 = at the top). Refused at 8 rows / 80 keys. */
    public static LayoutDef addRow(LayoutDef m, String layerId, int afterRow) {
        Ctx x = ctxLayer(m, layerId); if (x == null) return m;
        if (x.L.rows.size() >= LayoutDef.MAX_ROWS || x.L.keyCount() >= LayoutDef.MAX_KEYS_LAYER) return m;
        RowDef r = new RowDef();
        r.keys.add(KeyDef.spacer(fresh(ids(x.c)), KeyDef.W_MAX));
        int at = Math.max(0, Math.min(x.L.rows.size(), afterRow + 1));
        x.L.rows.add(at, r);
        return fin(x.c);
    }

    private static Ctx ctxLayer(LayoutDef m, String layerId) {
        if (m == null || layerId == null) return null;
        for (int a = 0; a < m.layers.size(); a++) {
            if (m.layers.get(a).id.equals(layerId)) {
                Ctx x = new Ctx(); x.c = m.copy(); x.li = a; x.L = x.c.layers.get(a); return x;
            }
        }
        return null;
    }

    public static LayoutDef deleteRow(LayoutDef m, String layerId, int row) {
        Ctx x = ctxRow(m, layerId, row); if (x == null || x.L.rows.size() < 2) return m;
        x.L.rows.remove(row);
        return fin(x.c);
    }

    /** Copy of a row (new key ids) inserted right after it. */
    public static LayoutDef duplicateRow(LayoutDef m, String layerId, int row) {
        Ctx x = ctxRow(m, layerId, row); if (x == null) return m;
        if (x.L.rows.size() >= LayoutDef.MAX_ROWS || x.L.keyCount() + (x.R.isRef() ? 0 : x.R.keys.size()) > LayoutDef.MAX_KEYS_LAYER) return m;
        RowDef copy = x.R.copy();
        Set<String> used = ids(x.c);
        for (KeyDef k : copy.keys) k.id = fresh(used);
        x.L.rows.add(row + 1, copy);
        return fin(x.c);
    }

    // ---------- v0.17.19 (L3): own bottom row per language ----------
    /**
     * Gives letter layer [layerId] (av or pb) its OWN copy of the shared bottom row (layer bot_av / bot_pb) and points that language at it.
     * English always uses the shared row. Key ids of the copy get the suffix _av / _pb (ids stay unique; the danda-dot rule knows the suffix).
     * Refused (same instance): en, unknown layer, copy already there, no reference to "bot", layer limit.
     */
    public static LayoutDef separateBottom(LayoutDef m, String layerId) {
        if (m == null || layerId == null) return m;
        String own = LayoutText.botLayerOf(layerId);
        if (own.equals("bot") || m.layer(own) != null) return m;
        LayerDef src = m.layer("bot");
        if (src == null || src.rows.isEmpty() || src.rows.get(0).isRef() || !"bot".equals(m.botRef(layerId))) return m;
        if (m.layers.size() >= LayoutDef.MAX_LAYERS) return m;
        LayoutDef c = m.copy();
        RowDef row = c.layer("bot").rows.get(0).copy();
        String sfx = layerId.equals("av") ? "_av" : "_pb";
        Set<String> used = ids(c);
        for (KeyDef k : row.keys) {
            String nid = LayoutText.cleanId(k.id + sfx);
            if (nid.length() == 0 || used.contains(nid)) nid = fresh(used); else used.add(nid);
            k.id = nid;
        }
        LayerDef nl = new LayerDef(own);
        nl.rows.add(row);
        c.layers.add(nl);
        for (RowDef r : c.layer(layerId).rows) if (r.isRef() && r.share.equals("bot")) r.share = own;
        return fin(c);
    }

    /** Points letter layer [layerId] back at the shared bottom row and drops its own copy. Refused (same instance) when it has no own copy. */
    public static LayoutDef shareBottom(LayoutDef m, String layerId) {
        if (m == null || layerId == null) return m;
        String own = LayoutText.botLayerOf(layerId);
        if (own.equals("bot") || m.layer(own) == null || m.layer("bot") == null) return m;
        LayoutDef c = m.copy();
        for (RowDef r : c.layer(layerId).rows) if (r.isRef() && r.share.equals(own)) r.share = "bot";
        boolean used = false;
        for (LayerDef l : c.layers) for (RowDef r : l.rows) if (r.isRef() && r.share.equals(own)) used = true;
        if (!used) { LayerDef g = c.layer(own); c.layers.remove(g); }
        return fin(c);
    }

    public static LayoutDef setRowHeight(LayoutDef m, String layerId, int row, int pct) {
        Ctx x = ctxRow(m, layerId, row); if (x == null) return m;
        int p = Math.max(RowDef.H_MIN, Math.min(RowDef.H_MAX, pct));
        if (p == x.R.heightPct) return m;
        x.R.heightPct = p; return fin(x.c);
    }

    public static LayoutDef swapRows(LayoutDef m, String layerId, int a, int b) {
        Ctx x = ctxLayer(m, layerId); if (x == null) return m;
        int n = x.L.rows.size();
        if (a < 0 || b < 0 || a >= n || b >= n || a == b) return m;
        RowDef t = x.L.rows.get(a); x.L.rows.set(a, x.L.rows.get(b)); x.L.rows.set(b, t);
        return fin(x.c);
    }

    /** All keys of the row get the same width (the row's total width is kept, 0.25 steps). */
    public static LayoutDef autoBalanceRow(LayoutDef m, String layerId, int row) {
        Ctx x = ctxRow(m, layerId, row); if (x == null || x.R.isRef() || x.R.keys.size() == 0) return m;
        float each = qw(x.R.totalWeight() / x.R.keys.size());
        boolean same = true;
        for (KeyDef k : x.R.keys) if (k.weight != each) same = false;
        if (same) return m;
        for (KeyDef k : x.R.keys) k.weight = each;
        return fin(x.c);
    }

    /** Every key of the row gets the width of the given key. */
    public static LayoutDef applyWidthToRow(LayoutDef m, String id) {
        Ctx x = ctx(m, id); if (x == null) return m;
        boolean same = true;
        for (KeyDef k : x.R.keys) if (k.weight != x.K.weight) same = false;
        if (same) return m;
        float w = x.K.weight;
        for (KeyDef k : x.R.keys) k.weight = w;
        return fin(x.c);
    }

    /** Copies width + look flags + icon (not the letters) from one key to another. */
    public static LayoutDef copyKeyStyle(LayoutDef m, String fromId, String toId) {
        Ctx f = ctx(m, fromId); if (f == null) return m;
        Ctx x = ctx(m, toId); if (x == null || fromId.equals(toId)) return m;
        x.K.weight = f.K.weight; x.K.bold = f.K.bold; x.K.wideHint = f.K.wideHint;
        x.K.noPopup = f.K.noPopup; x.K.repeat = f.K.repeat; x.K.labelIcon = f.K.labelIcon;
        LayoutDef r = fin(x.c);
        return r.equals(m) ? m : r;
    }

    // ---------- reset (defaults = a LayoutDef, e.g. LayoutDefaults in K4) ----------
    /** Replaces one row by the same row of the defaults (new ids where they would clash). */
    public static LayoutDef resetRow(LayoutDef m, String layerId, int row, LayoutDef defaults) {
        Ctx x = ctxRow(m, layerId, row); if (x == null || defaults == null || valid(defaults) != null) return m;
        LayerDef dl = defaults.layer(layerId);
        if (dl == null || row >= dl.rows.size()) return m;
        x.L.rows.remove(row);
        RowDef nr = dl.rows.get(row).copy();
        Set<String> used = ids(x.c);
        for (KeyDef k : nr.keys) if (used.contains(k.id)) k.id = fresh(used); else used.add(k.id);
        x.L.rows.add(row, nr);
        LayoutDef r = fin(x.c);
        return r.equals(m) ? m : r;
    }

    /** Replaces a whole layer by the layer of the defaults (new ids where they would clash; added when missing). */
    public static LayoutDef resetLayer(LayoutDef m, String layerId, LayoutDef defaults) {
        if (m == null || layerId == null || defaults == null || valid(defaults) != null) return m;
        LayerDef dl = defaults.layer(layerId);
        if (dl == null) return m;
        LayoutDef c = m.copy();
        int at = -1;
        for (int i = 0; i < c.layers.size(); i++) if (c.layers.get(i).id.equals(layerId)) at = i;
        if (at < 0 && c.layers.size() >= LayoutDef.MAX_LAYERS) return m;
        if (at >= 0) c.layers.remove(at);
        LayerDef nl = dl.copy();
        Set<String> used = ids(c);
        for (RowDef r : nl.rows) for (KeyDef k : r.keys) if (used.contains(k.id)) k.id = fresh(used); else used.add(k.id);
        c.layers.add(at < 0 ? c.layers.size() : at, nl);
        LayoutDef r = fin(c);
        return r.equals(m) ? m : r;
    }

    // ---------- bulk (multi-select) ----------
    public static LayoutDef setAllWeight(LayoutDef m, List<String> idList, float w) {
        LayoutDef cur = m;
        if (idList == null) return m;
        for (String id : idList) cur = setWeight(cur, id, w);
        return cur;
    }

    public static LayoutDef makeSpacerAll(LayoutDef m, List<String> idList) {
        LayoutDef cur = m;
        if (idList == null) return m;
        for (String id : idList) cur = makeSpacer(cur, id);
        return cur;
    }

    /** Deletes every listed key that can be deleted (a row keeps at least 1 key; the rest are skipped). */
    public static LayoutDef deleteAll(LayoutDef m, List<String> idList) {
        LayoutDef cur = m;
        if (idList == null) return m;
        for (String id : idList) cur = delete(cur, id);
        return cur;
    }
}
