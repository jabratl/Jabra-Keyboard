package com.mgb.keyboard.hist;

/**
 * v0.20.0 (H1) typing history - WHERE may a field be recorded? Pure Java (no Android classes; the Android constants are copied as numbers),
 * tested by tools/HistPolicyTest.java. decide() gives OK or the first reason that blocks recording.
 *  - Real password fields (text / web / visible password, number password) are NEVER recorded (no setting can change that).
 *  - Email, URL (browser address / search bars) and number / phone / date fields: skipped while skipSens is ON (default ON).
 *  - Fields that ask for "no personalized learning" (incognito / private tabs): skipped while skipIncog is ON (default ON).
 *  - Apps the user excluded (hist_apps_off) and Jabra's own screens are never recorded. Pause and OFF block everything.
 */
public final class HistPolicy {
    private HistPolicy() { }

    public static final int OK = 0, OFF = 1, PAUSED = 2, OWN_APP = 3, PASSWORD = 4, SENSITIVE = 5, INCOGNITO = 6, APP_OFF = 7;

    // android.text.InputType / EditorInfo values
    static final int MASK_CLASS = 0x0000000f, MASK_VARIATION = 0x00000ff0;
    static final int CLASS_TEXT = 1, CLASS_NUMBER = 2;
    static final int TEXT_URI = 0x10, TEXT_EMAIL = 0x20, TEXT_PASSWORD = 0x80, TEXT_VISIBLE_PASSWORD = 0x90, TEXT_WEB_EMAIL = 0xd0, TEXT_WEB_PASSWORD = 0xe0;
    static final int NUMBER_PASSWORD = 0x10;
    static final int IME_NO_PERSONALIZED_LEARNING = 0x1000000;

    /** Keep-for levels (pref hist_days_lv 0..3). */
    public static final int[] DAYS = { 1, 3, 7, 30 };
    public static final String[] NAMES = { "1 day", "3 days", "7 days", "30 days" };
    public static final int DEF_LEVEL = 2;

    public static int level(int raw) { return (raw < 0 || raw >= DAYS.length) ? DEF_LEVEL : raw; }
    public static long ttlMs(int rawLevel) { return DAYS[level(rawLevel)] * 86400000L; }

    public static boolean isPassword(int inputType) {
        int cls = inputType & MASK_CLASS, v = inputType & MASK_VARIATION;
        if (cls == CLASS_TEXT) return v == TEXT_PASSWORD || v == TEXT_VISIBLE_PASSWORD || v == TEXT_WEB_PASSWORD;
        if (cls == CLASS_NUMBER) return v == NUMBER_PASSWORD;
        return false;
    }

    /** Email, URL, and every non-text field (number, phone, date, no editor). */
    public static boolean isSensitive(int inputType) {
        int cls = inputType & MASK_CLASS, v = inputType & MASK_VARIATION;
        if (cls != CLASS_TEXT) return true;
        return v == TEXT_URI || v == TEXT_EMAIL || v == TEXT_WEB_EMAIL;
    }

    public static boolean isIncognito(int imeOptions) { return (imeOptions & IME_NO_PERSONALIZED_LEARNING) != 0; }

    /** apps = package names separated by comma / semicolon / space / new line. */
    public static boolean listed(String apps, String pkg) {
        if (apps == null || pkg == null || pkg.isEmpty()) return false;
        for (String p : apps.split("[,;\\s]+")) if (p.equals(pkg)) return true;
        return false;
    }

    public static int decide(boolean on, boolean paused, boolean skipSens, boolean skipIncog,
                             int inputType, int imeOptions, String pkg, String ownPkg, String offApps) {
        if (!on) return OFF;
        if (paused) return PAUSED;
        if (pkg != null && pkg.equals(ownPkg)) return OWN_APP;
        if (isPassword(inputType)) return PASSWORD;
        if (skipSens && isSensitive(inputType)) return SENSITIVE;
        if (skipIncog && isIncognito(imeOptions)) return INCOGNITO;
        if (listed(offApps, pkg)) return APP_OFF;
        return OK;
    }

    public static String reasonText(int reason) {
        switch (reason) {
            case OK: return "Saving typing here";
            case OFF: return "Typing history is OFF";
            case PAUSED: return "Paused";
            case OWN_APP: return "Jabra's own screens are never saved";
            case PASSWORD: return "Password field (never saved)";
            case SENSITIVE: return "Email, URL or number field (skipped)";
            case INCOGNITO: return "Incognito / private field (skipped)";
            case APP_OFF: return "This app is excluded";
            default: return "Not saving";
        }
    }
}
