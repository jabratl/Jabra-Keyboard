package com.mgb.keyboard.typing;

/** v0.9.10 (V5): pure ring buffer of timing samples (last 100 per kind). No Android, no allocation per sample. */
public final class KeyTiming {
    public static final int KEY = 0, HANDLER = 1, BUBBLE = 2, LANG = 3, BUILD = 4;
    public static final int KINDS = 5, CAP = 100;
    public static final String[] NAMES = { "Key: down > commit", "Key: tap handler", "Bubble show", "Language switch", "Keyboard build" };

    private final long[][] buf = new long[KINDS][CAP];
    private final int[] cnt = new int[KINDS];    // total added (capped use = min(cnt, CAP))
    private final int[] pos = new int[KINDS];

    public synchronized void add(int kind, long nanos) {
        if (kind < 0 || kind >= KINDS || nanos < 0) return;
        buf[kind][pos[kind]] = nanos;
        pos[kind] = (pos[kind] + 1) % CAP;
        if (cnt[kind] < Integer.MAX_VALUE) cnt[kind]++;
    }

    public synchronized int count(int kind) { return ok(kind) ? Math.min(cnt[kind], CAP) : 0; }

    private boolean ok(int kind) { return kind >= 0 && kind < KINDS; }

    private long[] copy(int kind) {
        int n = Math.min(cnt[kind], CAP);
        long[] a = new long[n];
        System.arraycopy(buf[kind], 0, a, 0, n);   // order does not matter for avg / p95 / max
        return a;
    }

    public synchronized long avgNanos(int kind) {
        if (!ok(kind)) return 0;
        long[] a = copy(kind);
        if (a.length == 0) return 0;
        long s = 0;
        for (long v : a) s += v;
        return s / a.length;
    }

    public synchronized long maxNanos(int kind) {
        if (!ok(kind)) return 0;
        long m = 0;
        for (long v : copy(kind)) if (v > m) m = v;
        return m;
    }

    /** 95th percentile (nearest rank). 0 when empty. */
    public synchronized long p95Nanos(int kind) {
        if (!ok(kind)) return 0;
        long[] a = copy(kind);
        if (a.length == 0) return 0;
        java.util.Arrays.sort(a);
        int idx = (int) Math.ceil(a.length * 0.95) - 1;
        if (idx < 0) idx = 0;
        if (idx >= a.length) idx = a.length - 1;
        return a[idx];
    }

    public synchronized void reset() {
        for (int k = 0; k < KINDS; k++) { cnt[k] = 0; pos[k] = 0; }
    }

    public static String ms(long nanos) {
        long t = Math.round(nanos / 100000.0);   // tenths of ms
        return (t / 10) + "." + (t % 10);
    }

    /** One display line, e.g. "Bubble show: avg 1.2 / p95 3.0 / max 5.1 ms (n=40)". */
    public synchronized String line(int kind) {
        if (!ok(kind)) return "";
        int n = count(kind);
        if (n == 0) return NAMES[kind] + ": no samples yet";
        return NAMES[kind] + ": avg " + ms(avgNanos(kind)) + " / p95 " + ms(p95Nanos(kind)) + " / max " + ms(maxNanos(kind)) + " ms (n=" + n + ")";
    }
}
