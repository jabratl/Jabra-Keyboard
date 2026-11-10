package com.mgb.keyboard.typing;

import com.mgb.keyboard.clip.ClipList;
import java.util.ArrayList;
import java.util.List;

/**
 * v0.2.5.3: select mode state for the clipboard (pure Java, no Android).
 * Items are compared by IDENTITY (==), like ClipList (no id field). Nothing is saved.
 */
public final class ClipSelect {
    private boolean on = false;
    private final List<ClipList.Item> sel = new ArrayList<>();

    public boolean isOn() { return on; }
    /** Enter select mode (nothing selected). */
    public void enter() { on = true; }
    /** Leave select mode and forget the selection. */
    public void leave() { on = false; sel.clear(); }
    /** Long-press: enter select mode with this item selected. */
    public void enterWith(ClipList.Item it) { on = true; sel.clear(); if (it != null) sel.add(it); }
    public int count() { return sel.size(); }
    public boolean isSelected(ClipList.Item it) { return indexOf(it) >= 0; }
    /** Toggle one item. Only works in select mode. Returns true if it is selected afterwards. */
    public boolean toggle(ClipList.Item it) {
        if (!on || it == null) return false;
        int i = indexOf(it);
        if (i >= 0) { sel.remove(i); return false; }
        sel.add(it);
        return true;
    }
    /** Select all given items (adds the missing ones). Only in select mode. */
    public void selectAll(List<ClipList.Item> all) {
        if (!on || all == null) return;
        for (ClipList.Item it : all) if (it != null && indexOf(it) < 0) sel.add(it);
    }
    /** Unselect everything but stay in select mode. */
    public void clear() { sel.clear(); }
    /** Copy of the selection (safe to pass to ClipList.setPinned / removeAll). */
    public List<ClipList.Item> selection() { return new ArrayList<>(sel); }
    /** Drop selected items that are not in [existing] any more (after delete / clear). */
    public void prune(List<ClipList.Item> existing) {
        if (existing == null) { sel.clear(); return; }
        for (int i = sel.size() - 1; i >= 0; i--) if (!containsId(existing, sel.get(i))) sel.remove(i);
    }
    /** How many selected items are pinned. */
    public int pinnedCount() { int n = 0; for (ClipList.Item it : sel) if (it.pinned) n++; return n; }

    private int indexOf(ClipList.Item it) {
        for (int i = 0; i < sel.size(); i++) if (sel.get(i) == it) return i;
        return -1;
    }
    private static boolean containsId(List<ClipList.Item> l, ClipList.Item it) {
        for (ClipList.Item x : l) if (x == it) return true;
        return false;
    }
}
