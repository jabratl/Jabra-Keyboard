package com.mgb.keyboard.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.17.3 (K3) undo / redo of layout snapshots (pure Java). Max {@link #MAX} undo steps (oldest dropped).
 * Models are never changed by the editor in place (LayoutOps returns new ones), so a snapshot is just the reference.
 * Each state carries a label of the change that led to it, so the Undo button can say "Undo: merge q + w".
 */
public final class LayoutHistory {
    public static final int MAX = 50;
    private final List<LayoutDef> states = new ArrayList<LayoutDef>();
    private final List<String> labels = new ArrayList<String>();
    private int pos;

    public LayoutHistory(LayoutDef start) { reset(start); }

    /** Forget everything and start again from this model. */
    public void reset(LayoutDef start) {
        states.clear(); labels.clear();
        states.add(start); labels.add("");
        pos = 0;
    }

    public LayoutDef current() { return states.get(pos); }

    /** Records a new state after a change. Returns false (nothing recorded) for null or an identical model. */
    public boolean commit(LayoutDef m, String label) {
        if (m == null) return false;
        LayoutDef cur = states.get(pos);
        if (m == cur || m.equals(cur)) return false;
        while (states.size() > pos + 1) { states.remove(states.size() - 1); labels.remove(labels.size() - 1); }
        states.add(m); labels.add(label == null ? "" : label);
        pos++;
        while (pos > MAX) { states.remove(0); labels.remove(0); pos--; }
        return true;
    }

    public boolean canUndo() { return pos > 0; }

    public boolean canRedo() { return pos + 1 < states.size(); }

    /** Goes one step back; returns the model now current, or null when there is nothing to undo. */
    public LayoutDef undo() { if (!canUndo()) return null; pos--; return states.get(pos); }

    public LayoutDef redo() { if (!canRedo()) return null; pos++; return states.get(pos); }

    /** Label of the change an Undo would take back ("" when none). */
    public String undoLabel() { return canUndo() ? labels.get(pos) : ""; }

    public String redoLabel() { return canRedo() ? labels.get(pos + 1) : ""; }

    public int undoSteps() { return pos; }

    public int redoSteps() { return states.size() - 1 - pos; }
}
