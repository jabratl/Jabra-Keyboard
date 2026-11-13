package com.mgb.keyboard.layout;

import java.util.ArrayList;
import java.util.List;

/** v0.17.3 (K3) the whole custom layout = layers. Pure Java; use {@link LayoutOps} to change it (returns new models). */
public final class LayoutDef {
    public static final int MAX_KEYS_LAYER = 80, MAX_ROWS = 8, MAX_LAYERS = 10;   // v0.17.19 (L3): 8 -> 10 (bot_av, bot_pb)
    public List<LayerDef> layers = new ArrayList<LayerDef>();

    public LayoutDef copy() {
        LayoutDef m = new LayoutDef();
        for (LayerDef l : layers) m.layers.add(l.copy());
        return m;
    }

    public LayerDef layer(String id) {
        for (LayerDef l : layers) if (l.id.equals(id)) return l;
        return null;
    }

    /** Rows of a layer with reference rows resolved (a "bot" / "nr" reference shows the first row of that layer). */
    public List<RowDef> effectiveRows(String layerId) {
        List<RowDef> out = new ArrayList<RowDef>();
        LayerDef l = layer(layerId);
        if (l == null) return out;
        for (RowDef r : l.rows) {
            if (!r.isRef()) { out.add(r); continue; }
            LayerDef s = layer(r.share);
            if (s != null && s != l && s.rows.size() > 0 && !s.rows.get(0).isRef()) out.add(s.rows.get(0));
        }
        return out;
    }

    /** v0.17.19 (L3) id of the bottom-row layer that letter layer [layerId] shows (bot / bot_av / bot_pb), or "" when it has no bottom row. */
    public String botRef(String layerId) {
        LayerDef l = layer(layerId);
        if (l == null) return "";
        for (RowDef r : l.rows) if (r.isRef() && LayoutText.isBottomId(r.share)) return r.share;
        return "";
    }

    public int totalKeys() { int n = 0; for (LayerDef l : layers) n += l.keyCount(); return n; }

    public String sig() {
        StringBuilder b = new StringBuilder("M");
        for (LayerDef l : layers) b.append(l.sig());
        return b.toString();
    }

    @Override public boolean equals(Object o) { return o instanceof LayoutDef && ((LayoutDef) o).sig().equals(sig()); }

    @Override public int hashCode() { return sig().hashCode(); }
}
