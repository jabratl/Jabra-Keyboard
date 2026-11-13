package com.mgb.keyboard.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.17.3 (K3) one row. share = "" (own keys) or "nr" / "bot": a REFERENCE row that shows the first row of the layer
 * of that name (shared number row / bottom row); its own key list is ignored and may be empty.
 */
public final class RowDef {
    public static final int H_MIN = 50, H_MAX = 200;
    public int heightPct = 100;
    public String share = "";
    public List<KeyDef> keys = new ArrayList<KeyDef>();

    public RowDef copy() {
        RowDef r = new RowDef();
        r.heightPct = heightPct; r.share = share;
        for (KeyDef k : keys) r.keys.add(k.copy());
        return r;
    }

    public boolean isRef() { return share.length() > 0; }

    public float totalWeight() { float t = 0; for (KeyDef k : keys) t += k.weight; return t; }

    public String sig() {
        StringBuilder b = new StringBuilder("R").append(heightPct).append(',').append(share).append('{');
        for (KeyDef k : keys) b.append(k.sig()).append(';');
        return b.append('}').toString();
    }
}
