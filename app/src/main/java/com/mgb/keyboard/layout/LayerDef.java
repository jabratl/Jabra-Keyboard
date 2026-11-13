package com.mgb.keyboard.layout;

import java.util.ArrayList;
import java.util.List;

/** v0.17.3 (K3) one layer (en, av, pb, s1-s3, nr, bot) = a list of rows. */
public final class LayerDef {
    public String id;
    public List<RowDef> rows = new ArrayList<RowDef>();

    public LayerDef(String id) { this.id = id; }

    public LayerDef copy() {
        LayerDef l = new LayerDef(id);
        for (RowDef r : rows) l.rows.add(r.copy());
        return l;
    }

    /** Number of own keys (reference rows count 0). */
    public int keyCount() { int n = 0; for (RowDef r : rows) if (!r.isRef()) n += r.keys.size(); return n; }

    public String sig() {
        StringBuilder b = new StringBuilder("L").append(id).append('[');
        for (RowDef r : rows) b.append(r.sig());
        return b.append(']').toString();
    }
}
