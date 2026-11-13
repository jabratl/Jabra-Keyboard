package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.17.62 (R2): the 3 typing modes (English, Bangla phonetic = Avro, Bangla fixed = Probhat) with a switch-off for EACH one (English too)
 * and a manual order (pref lang_order 0..5) for the language-key cycle and the space swipe. Pure Java, no Android (tools/LangModesTest.java).
 * State = {bangla, probhat} like LangOrder / LangSwipe (EN keeps the remembered probhat flag).
 * custom() = false (English on + order 0) means: the OLD code (LangOrder / LangSwipe) runs exactly as before; this class is used only when custom() is true.
 * At least one mode always stays on: all three switched off = English.
 */
public final class LangModes {
    private LangModes() { }

    public static final int EN = 0, AV = 1, PB = 2;
    private static final int[][] ORDERS = { {EN, AV, PB}, {EN, PB, AV}, {AV, EN, PB}, {AV, PB, EN}, {PB, EN, AV}, {PB, AV, EN} };
    public static final int ORDER_COUNT = ORDERS.length;

    public static int sanitizeOrder(int o) { return o >= 0 && o < ORDER_COUNT ? o : 0; }

    private static String nameOf(int m) { return m == EN ? "EN" : m == AV ? LangNames.AVRO : LangNames.PROBHAT; }

    /** Setting names, e.g. "EN > Bangla(avro) > Bangla(probhat)" (index = pref lang_order). */
    public static String[] orderNames() {
        String[] n = new String[ORDER_COUNT];
        for (int i = 0; i < ORDER_COUNT; i++) n[i] = nameOf(ORDERS[i][0]) + " > " + nameOf(ORDERS[i][1]) + " > " + nameOf(ORDERS[i][2]);
        return n;
    }

    /** False = default settings: use the old LangOrder / LangSwipe code. */
    public static boolean custom(boolean enOn, int order) { return !enOn || sanitizeOrder(order) != 0; }

    public static int modeOf(boolean bangla, boolean probhat) { return !bangla ? EN : probhat ? PB : AV; }

    /** {bangla, probhat} of a mode; EN keeps [lastProbhat]. */
    public static int[] state(int mode, boolean lastProbhat) {
        if (mode == EN) return new int[] { 0, lastProbhat ? 1 : 0 };
        return new int[] { 1, mode == PB ? 1 : 0 };
    }

    private static boolean on(int m, boolean en, boolean av, boolean pb) { return m == EN ? en : m == AV ? av : pb; }

    /** The switched-on modes in the chosen order (never empty: nothing on = {EN}). */
    public static int[] cycle(int order, boolean en, boolean av, boolean pb) {
        int[] o = ORDERS[sanitizeOrder(order)];
        List<Integer> l = new ArrayList<Integer>();
        for (int m : o) if (on(m, en, av, pb)) l.add(m);
        if (l.isEmpty()) l.add(EN);
        int[] r = new int[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    private static int indexOf(int[] c, int m) { for (int i = 0; i < c.length; i++) if (c[i] == m) return i; return -1; }

    /** The state to use when the current mode is switched off (or unchanged when it is on): first mode of the cycle. */
    public static int[] normalize(boolean bangla, boolean probhat, int order, boolean en, boolean av, boolean pb) {
        int[] c = cycle(order, en, av, pb);
        int cur = modeOf(bangla, probhat);
        if (indexOf(c, cur) >= 0) return state(cur, probhat);
        return state(c[0], probhat);
    }

    /** New state after [steps] steps (+ next, - previous). A switched-off current mode: + goes to the first, - to the last. */
    public static int[] step(boolean bangla, boolean probhat, int order, boolean en, boolean av, boolean pb, int steps) {
        int[] c = cycle(order, en, av, pb);
        int n = c.length;
        int cur = indexOf(c, modeOf(bangla, probhat));
        if (steps == 0) return cur >= 0 ? state(c[cur], probhat) : new int[] { bangla ? 1 : 0, probhat ? 1 : 0 };
        int idx = cur >= 0 ? ((cur + steps) % n + n) % n : (steps > 0 ? (steps - 1) % n : ((steps % n) + n) % n);
        return state(c[idx], probhat);
    }

    /**
     * New state after a TAP on the language key. tapMode 0 = EN <-> Bangla (when English is off: the next switched-on mode);
     * 1 or 2 = cycle in the manual order. fav (0 last used, 1 Avro, 2 Probhat) picks the Bangla layout when going EN -> Bangla.
     */
    public static int[] tap(boolean bangla, boolean probhat, int order, boolean en, boolean av, boolean pb, int tapMode, int fav) {
        int[] c = cycle(order, en, av, pb);
        if (c.length == 1) return state(c[0], probhat);
        if (tapMode == 1 || tapMode == 2 || !en) return step(bangla, probhat, order, en, av, pb, 1);
        if (bangla) return state(EN, probhat);
        int target = probhat ? PB : AV;
        if (fav == 1 && av) target = AV;
        else if (fav == 2 && pb) target = PB;
        if (!on(target, en, av, pb)) target = target == AV ? PB : AV;
        if (!on(target, en, av, pb)) return state(EN, probhat);
        return state(target, probhat);
    }
}
