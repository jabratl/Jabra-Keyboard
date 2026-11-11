package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.5.4 pure toolbar usage counter (tested by tools/ToolUsageTest.java).
 * tb_use = "id:count,id:count". count(id) on a tap; reorder(visibleIds, use) = first item stays first, the TOP 3 used
 * (count >= 1) of the rest move right behind it (most used first), everything else keeps the user's order.
 */
public final class ToolUsage {
    private ToolUsage() { }

    public static final int TOP = 3;
    public static final int MAX_COUNT = 1000;

    public static Map<String, Integer> parse(String csv) {
        Map<String, Integer> m = new LinkedHashMap<String, Integer>();
        if (csv == null) return m;
        for (String p : csv.split(",")) {
            int c = p.indexOf(':');
            if (c <= 0) continue;
            String id = p.substring(0, c).trim();
            if (!ToolbarOrder.known(id) || m.containsKey(id)) continue;
            try { int n = Integer.parseInt(p.substring(c + 1).trim()); if (n > 0) m.put(id, Math.min(n, 100000)); } catch (NumberFormatException e) { /* skip */ }
        }
        return m;
    }

    public static String serialize(Map<String, Integer> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : m.entrySet()) { if (sb.length() > 0) sb.append(','); sb.append(e.getKey()).append(':').append(e.getValue()); }
        return sb.toString();
    }

    /** One tap. Unknown id = text unchanged. When a count passes MAX_COUNT every count is halved (old habits fade). */
    public static String count(String csv, String id) {
        if (!ToolbarOrder.known(id)) return csv == null ? "" : csv;
        Map<String, Integer> m = parse(csv);
        Integer o = m.get(id);
        int n = (o == null ? 0 : o) + 1;
        m.put(id, n);
        if (n > MAX_COUNT) {
            Map<String, Integer> h = new LinkedHashMap<String, Integer>();
            for (Map.Entry<String, Integer> e : m.entrySet()) if (e.getValue() / 2 > 0) h.put(e.getKey(), e.getValue() / 2);
            m = h;
        }
        return serialize(m);
    }

    public static List<String> reorder(List<String> ids, String csv) {
        List<String> out = new ArrayList<String>();
        if (ids == null || ids.isEmpty()) return out;
        Map<String, Integer> m = parse(csv);
        if (m.isEmpty() || ids.size() < 3) { out.addAll(ids); return out; }
        List<String> rest = new ArrayList<String>(ids.subList(1, ids.size()));
        List<String> top = new ArrayList<String>();
        for (int k = 0; k < TOP; k++) {
            String best = null; int bn = 0;
            for (String id : rest) { Integer n = m.get(id); if (n != null && n > bn) { bn = n; best = id; } }   // first in user order wins a tie
            if (best == null) break;
            top.add(best); rest.remove(best);
        }
        out.add(ids.get(0)); out.addAll(top); out.addAll(rest);
        return out;
    }
}
