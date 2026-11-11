package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v0.5.3 pure settings migration (no Android classes, tested by tools/HealthTest.java).
 * schema_v (pref, int) says which step the stored settings are at. CURRENT = what this code writes.
 * Every step is IDEMPOTENT (running it twice changes nothing more) and only ever REMOVES a value that can never be valid
 * (wrong type / out of range for a whitelisted key). Removing a key = the key's default = the old behaviour.
 * The caller (HealthRun in HealthPanel.kt) saves a backup first, applies the removals, and writes schema_v LAST.
 */
public final class Migrate {
    private Migrate() { }

    /** schema 1 = v0.4.0 - v0.5.2 (backups wrote it). schema 2 = v0.5.3 (value check of the whitelisted keys). */
    public static final int CURRENT = 2;

    public static final class Result {
        public final int from;
        public final int to;
        public final List<String> remove = new ArrayList<String>();   // pref keys to remove
        public final List<String> notes = new ArrayList<String>();    // one line per removed key
        Result(int from, int to) { this.from = from; this.to = to; }
        public boolean changes() { return !remove.isEmpty(); }
    }

    /** from < 1 or garbage counts as 1. A newer schema (from > CURRENT) is left alone (to = from, no changes). */
    public static Result plan(Map<String, ?> prefs, int from) {
        int f = from < 1 ? 1 : from;
        Result r = new Result(f, f > CURRENT ? f : CURRENT);
        if (f >= CURRENT || prefs == null) return r;
        if (f < 2) step2(prefs, r);
        return r;
    }

    /** Step 1 -> 2: whitelisted keys (ProfileCodec.KEYS) with a wrong value type, or a value no code can use, are removed. */
    private static void step2(Map<String, ?> prefs, Result r) {
        for (String k : ProfileCodec.KEYS) {
            Object v = prefs.get(k);
            if (v == null) continue;
            char t = ProfileCodec.typeOf(k);
            boolean bad = false;
            if (t == 'b') bad = !(v instanceof Boolean);
            else if (t == 'i') {
                if (!(v instanceof Integer)) bad = true;
                else {
                    int n = (Integer) v;
                    if (k.equals("theme") && (n < 0 || n > 20)) bad = true;
                    else if ((k.equals("onehand") || k.equals("lang_tap") || k.equals("lang_fav")) && (n < 0 || n > 2)) bad = true;
                }
            } else if (t == 's') {
                if (!(v instanceof String)) bad = true;
                else if (k.equals("bn_layout") && !v.equals("avro") && !v.equals("probhat")) bad = true;
            }
            if (bad) { r.remove.add(k); r.notes.add(k + ": value not usable, back to default"); }
        }
    }
}
