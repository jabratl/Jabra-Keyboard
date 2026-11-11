package com.mgb.keyboard.typing;

/**
 * v0.8.9 space-key language swipe (pure Java, no Android, tested by tools/LangSwipeTest.java).
 * Swipe left / right on the space key (v0.9.3: no hold needed; ARM_MS = 0) = previous / next language among the ones that are switched on.
 * Order: EN, Avro, Probhat (a layout switched off in "Languages & layouts" is skipped). The list wraps round.
 * State = (bangla, probhat) like LangOrder: EN = bangla false (probhat = remembered last Bangla layout).
 */
public final class LangSwipe {
    private LangSwipe() { }

    public static final long ARM_MS = 0L;       // v0.9.3: user request: NO hold, a swipe on space switches language at once (was 200 ms in v0.8.9 - v0.9.2)
    public static final float STEP_DP = 44f;    // finger travel for one language step (a cursor step is 16 dp)

    /** The languages in swipe order: {bangla, probhat} each. EN keeps the remembered probhat flag. */
    public static int[][] cycle(boolean probhat, boolean avroOn, boolean probhatOn) {
        int n = 1 + (avroOn ? 1 : 0) + (probhatOn ? 1 : 0);
        int[][] c = new int[n][];
        int i = 0;
        c[i++] = new int[] { 0, probhat ? 1 : 0 };
        if (avroOn) c[i++] = new int[] { 1, 0 };
        if (probhatOn) c[i++] = new int[] { 1, 1 };
        return c;
    }

    /** New state after [steps] swipe steps (positive = next, negative = previous, 0 = unchanged). A hidden current layout counts as EN. */
    public static int[] step(boolean bangla, boolean probhat, boolean avroOn, boolean probhatOn, int steps) {
        int[][] c = cycle(probhat, avroOn, probhatOn);
        int cur = 0;
        if (bangla) for (int i = 1; i < c.length; i++) if ((c[i][1] == 1) == probhat) cur = i;
        if (steps == 0 || c.length == 1) return new int[] { c[cur][0], c[cur][1] };
        int idx = ((cur + steps) % c.length + c.length) % c.length;
        return new int[] { c[idx][0], c[idx][1] };
    }

    public static String name(boolean bangla, boolean probhat) {
        if (!bangla) return "English";
        return probhat ? "\u09AC\u09BE\u0982\u09B2\u09BE (Probhat)" : "\u09AC\u09BE\u0982\u09B2\u09BE (Avro)";
    }

    /** v0.9.2 (R3): short name shown in the live bubble while swiping: "English" / "Avro" / "Probhat". */
    public static String label(boolean bangla, boolean probhat) {
        if (!bangla) return "English";
        return probhat ? "Probhat" : "Avro";
    }

    /** v0.9.2 (R3): the live bubble is drawn only when the space swipe is ON and sp_preview is ON. */
    public static boolean previewOn(boolean spaceLang, boolean spPreview) {
        return spaceLang && spPreview;
    }

    /** Text on the space key while the swipe feature is ON, e.g. "◂ English ▸". */
    public static String spaceLabel(boolean bangla, boolean probhat) {
        return "\u25C2 " + name(bangla, probhat) + " \u25B8";
    }

    /** The language (EN) key is hidden only when the user turned it off AND the space swipe is ON (otherwise there would be no way to switch). */
    public static boolean langKeyVisible(boolean keyLang, boolean spaceLang) {
        return keyLang || !spaceLang;
    }
}
