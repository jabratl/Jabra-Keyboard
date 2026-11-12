package com.mgb.keyboard.typing;

import java.util.List;
import java.util.Map;

/**
 * v0.10.19 (W6.2) decisions for "auto-profile per app". Pure Java (no Android), tested by tools/AppProfileApplyTest.java.
 * The Kotlin hook (MgbIme.autoProfile) only asks these questions, so the risky part stays tiny.
 */
public final class AppProfileApply {
    private AppProfileApply() { }

    /**
     * Which profile name to apply for this app now, or null for "do nothing":
     * no package / no mapping / same profile as the one applied last time for this app visit = null.
     */
    public static String pick(AppProfileMap map, String pkg, String lastApplied) {
        if (map == null || pkg == null || pkg.length() == 0) return null;
        String n = map.lookup(pkg);
        if (n == null || n.length() == 0) return null;
        if (lastApplied != null && lastApplied.equalsIgnoreCase(n)) return null;
        return n;
    }

    /** Finds a profile by name (case-insensitive): your saved ones first, then the ready-made ones. null = missing (the mapping is ignored). */
    public static ProfileCodec.Profile find(String name, List<ProfileCodec.Profile> saved, List<ProfileCodec.Profile> ready) {
        if (name == null || name.length() == 0) return null;
        if (saved != null) for (ProfileCodec.Profile p : saved) if (p.name.equalsIgnoreCase(name)) return p;
        if (ready != null) for (ProfileCodec.Profile p : ready) if (p.name.equalsIgnoreCase(name)) return p;
        return null;
    }

    /**
     * True when applying these profile values would change at least one pref. all = SharedPreferences.getAll().
     * A null value means "remove the key": it changes something only when the key is set now. Numbers compare by value.
     * Used to skip the apply (and the keyboard rebuild) when nothing would change, so there is no flicker.
     */
    public static boolean differs(Map<String, Object> values, Map<String, ?> all) {
        if (values == null) return false;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object now = all == null ? null : all.get(e.getKey());
            Object want = e.getValue();
            if (want == null) { if (now != null) return true; continue; }
            if (now == null) return true;
            if (want instanceof Number && now instanceof Number) { if (((Number) want).intValue() != ((Number) now).intValue()) return true; }
            else if (!want.equals(now)) return true;
        }
        return false;
    }
}
