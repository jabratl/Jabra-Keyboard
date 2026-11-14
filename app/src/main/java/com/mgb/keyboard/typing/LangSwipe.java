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
        return LangNames.name(probhat);   // v0.16.2: "Bang\u09B2\u09BE" / "\u09AC\u09BE\u0982\u09B2\u09BE"
    }

    /** v0.9.2 (R3): short name shown in the live bubble while swiping: "English" / "Avro" / "Probhat". */
    public static String label(boolean bangla, boolean probhat) {
        if (!bangla) return "English";
        return LangNames.bubbleName(probhat);   // v0.16.2
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

    /** v0.17.75 (S8 FIX): what to do after a setting was written so the EN key setting is never a dead switch. 0 = nothing, 1 = also turn Space swipe ON, 2 = also turn the EN key ON.
     *  key_lang OFF while Space swipe is OFF: the key would stay (no other way to switch) -> Space swipe is turned ON, so the key really goes.
     *  space_lang OFF while the EN key is OFF: nobody could switch language -> the EN key comes back. keyLang / spaceLang = values AFTER the write. */
    public static int syncAfter(String key, boolean value, boolean keyLang, boolean spaceLang) {
        if (value || key == null) return 0;
        if (key.equals("key_lang") && !spaceLang) return 1;
        if (key.equals("space_lang") && !keyLang) return 2;
        return 0;
    }

    // ---- v0.9.8 (V3): swipe feel ----
    public static final String[] STEP_NAMES = { "Small", "Normal", "Large" };
    public static final int STEP_DEFAULT = 1;

    /** v0.9.8: finger travel (dp) for one language step by level sp_step: 0 = 32, 1 = 44 (as before), 2 = 60. Anything else = 44. */
    public static float stepDp(int level) {
        if (level == 0) return 32f;
        if (level == 2) return 60f;
        return STEP_DP;
    }

    /** v0.9.8: the soft tick per language step is felt only while the space swipe is ON and sp_tick is ON. */
    public static boolean tickOn(boolean spaceLang, boolean spTick) {
        return spaceLang && spTick;
    }

    /**
     * v0.9.8: one swipe = one Session. The language lives in memory while the finger moves (no pref writes);
     * finish() (finger up) says what to save ONCE (null = nothing changed = no write); cancel() says which language to restore.
     */
    public static final class Session {
        private boolean active;
        private int sb, sp, cb, cp;

        public boolean active() { return active; }

        /** Call before the FIRST step of a swipe with the language that is on now. Later calls in the same swipe do nothing. */
        public void begin(boolean bangla, boolean probhat) {
            if (active) return;
            active = true;
            sb = cb = bangla ? 1 : 0;
            sp = cp = probhat ? 1 : 0;
        }

        /** Call after each step with the new language. */
        public void move(boolean bangla, boolean probhat) {
            if (!active) return;
            cb = bangla ? 1 : 0;
            cp = probhat ? 1 : 0;
        }

        /** Finger up: {bangla, probhat} to save once, or null when no swipe happened or the swipe ended where it began. Ends the session. */
        public int[] finish() {
            if (!active) return null;
            active = false;
            if (cb == sb && cp == sp) return null;
            return new int[] { cb, cp };
        }

        /** Swipe cancelled: {bangla, probhat} of the language the swipe started on (null = no swipe). Ends the session. Nothing was saved, so nothing to undo in prefs. */
        public int[] cancel() {
            if (!active) return null;
            active = false;
            return new int[] { sb, sp };
        }
    }
}
