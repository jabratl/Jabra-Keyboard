package com.mgb.keyboard.typing;

import java.util.HashSet;

/**
 * v0.9.1 (R2) feedback gate (pure Java, no Android, tested by tools/FeedbackGateTest.java).
 * Setting fb_down ON = the key buzz / click sound happens at finger-DOWN. The same key then must NOT buzz again at its click (finger-UP),
 * and a long-press hint must not add a second buzz. This class only remembers, per key id, "down already gave feedback".
 * Setting OFF: onDown never marks a key, so onClick / onHint answer true = exactly the old behaviour (feedback at click / at hint).
 * Main thread only (touch callbacks), so no locking.
 */
public final class FeedbackGate {
    private final HashSet<Integer> fed = new HashSet<Integer>();

    /** Finger down on key id. true = give feedback NOW. Always overwrites an old (stale) mark of this key. */
    public boolean onDown(int keyId, boolean enabled) {
        if (enabled) { fed.add(keyId); return true; }
        fed.remove(keyId);
        return false;
    }

    /** Key click (finger up inside the key). true = give feedback now (down did not). The mark is used up. */
    public boolean onClick(int keyId) {
        return !fed.remove(keyId);
    }

    /** Long-press hint fired. true = give feedback (down did not). The mark stays: the click never comes after a hint. */
    public boolean onHint(int keyId) {
        return !fed.contains(keyId);
    }

    /** Touch cancelled (finger slid off / system took the touch): forget the mark. */
    public void onCancel(int keyId) {
        fed.remove(keyId);
    }

    /** Forget everything (keyboard rebuilt). */
    public void reset() {
        fed.clear();
    }

    /** For tests. */
    public int marked() {
        return fed.size();
    }
}
