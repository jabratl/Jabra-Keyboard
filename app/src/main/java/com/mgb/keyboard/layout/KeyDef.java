package com.mgb.keyboard.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v0.17.3 (K3) one key of a custom layout (pure Java). Treat as immutable outside {@link LayoutOps}:
 * every LayoutOps call works on a deep copy and returns a NEW model.
 * kind CHAR = types out[0] (out[1] on shift); ACTION = runs the function named by action; SPACER = empty spot.
 */
public final class KeyDef {
    public static final int CHAR = 0, ACTION = 1, SPACER = 2;
    public static final float W_MIN = 0.25f, W_MAX = 8f;

    public String id;
    public int kind;
    public float weight = 1f;
    public String[] out = {"", ""};
    public String[] label = {"", ""};
    public String lp = "";
    public List<String> lpList = new ArrayList<String>();
    /** swipe up / down / left / right (index 0..3). */
    public String[] sw = {"", "", "", ""};
    public String action = "";
    public String combo = "";
    public String labelIcon = "";
    public boolean bold, wideHint, noPopup, repeat;

    public KeyDef(String id, int kind) { this.id = id; this.kind = kind; }

    public static KeyDef spacer(String id, float w) { KeyDef k = new KeyDef(id, SPACER); k.weight = w; return k; }

    public static KeyDef ch(String id, String out0, float w) { KeyDef k = new KeyDef(id, CHAR); k.out[0] = out0; k.weight = w; return k; }

    public static KeyDef act(String id, String action, float w) { KeyDef k = new KeyDef(id, ACTION); k.action = action; k.weight = w; return k; }

    public KeyDef copy() {
        KeyDef k = new KeyDef(id, kind);
        k.weight = weight;
        k.out = out.clone(); k.label = label.clone(); k.lp = lp;
        k.lpList = new ArrayList<String>(lpList);
        k.sw = sw.clone(); k.action = action; k.combo = combo; k.labelIcon = labelIcon;
        k.bold = bold; k.wideHint = wideHint; k.noPopup = noPopup; k.repeat = repeat;
        return k;
    }

    /** R1: shift output. out[1] if set; else upper case of out[0] when it is Latin letters; else out[0]. */
    public String shiftOut() {
        if (kind != CHAR) return "";
        if (out[1].length() > 0) return out[1];
        return LayoutText.isLatinLetters(out[0]) ? out[0].toUpperCase(Locale.ROOT) : out[0];
    }

    /** Canonical one-line text of the key (used by equals / history / tests). */
    public String sig() {
        StringBuilder b = new StringBuilder();
        b.append(id).append('|').append(kind).append('|').append(weight);
        for (String s : out) b.append('|').append(s);
        for (String s : label) b.append('|').append(s);
        b.append('|').append(lp).append("|[");
        for (String s : lpList) b.append(s).append('\u0001');
        b.append("]");
        for (String s : sw) b.append('|').append(s);
        b.append('|').append(action).append('|').append(combo).append('|').append(labelIcon);
        b.append('|').append(bold ? 1 : 0).append(wideHint ? 1 : 0).append(noPopup ? 1 : 0).append(repeat ? 1 : 0);
        return b.toString();
    }
}
