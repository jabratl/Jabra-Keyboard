import com.mgb.keyboard.hist.HistPolicy;

/** v0.20.0 (H1) HistPolicy tests (computer only). Android InputType numbers are written out on purpose. */
public class HistPolicyTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }

    static final int TEXT = 1, NUMBER = 2, PHONE = 3, DATETIME = 4;
    static final int FLAG_NO_PERS = 0x1000000;

    static int d(boolean on, boolean pause, boolean sens, boolean incog, int type, int ime, String pkg, String apps) {
        return HistPolicy.decide(on, pause, sens, incog, type, ime, pkg, "com.mgb.keyboard", apps);
    }

    public static void main(String[] a) {
        // normal text
        t("plain text ok", d(true, false, true, true, TEXT, 0, "com.chat", "") == HistPolicy.OK);
        t("multi-line text ok", d(true, false, true, true, TEXT | 0x20000, 0, "com.chat", "") == HistPolicy.OK);
        t("web edit text ok", d(true, false, true, true, TEXT | 0xa0, 0, "com.browser", "") == HistPolicy.OK);
        t("email subject is normal text", d(true, false, true, true, TEXT | 0x30, 0, "com.mail", "") == HistPolicy.OK);
        // switches
        t("off", d(false, false, true, true, TEXT, 0, "com.chat", "") == HistPolicy.OFF);
        t("paused", d(true, true, true, true, TEXT, 0, "com.chat", "") == HistPolicy.PAUSED);
        t("off wins over pause", d(false, true, true, true, TEXT, 0, "c", "") == HistPolicy.OFF);
        // own app
        t("own app never", d(true, false, false, false, TEXT, 0, "com.mgb.keyboard", "") == HistPolicy.OWN_APP);
        // passwords: always
        int[] pw = { TEXT | 0x80, TEXT | 0x90, TEXT | 0xe0, NUMBER | 0x10 };
        for (int p : pw) {
            t("password blocked (sens ON) " + p, d(true, false, true, true, p, 0, "c", "") == HistPolicy.PASSWORD);
            t("password blocked (sens OFF, incog OFF) " + p, d(true, false, false, false, p, 0, "c", "") == HistPolicy.PASSWORD);
        }
        t("password flags with other bits", d(true, false, false, false, TEXT | 0x80 | 0x80000 | 0x8000, 0, "c", "") == HistPolicy.PASSWORD);
        // sensitive
        int[] sens = { TEXT | 0x10, TEXT | 0x20, TEXT | 0xd0, NUMBER, NUMBER | 0x2000, PHONE, DATETIME, 0 };
        for (int s : sens) {
            t("sensitive skipped when ON " + s, d(true, false, true, false, s, 0, "c", "") == HistPolicy.SENSITIVE);
            t("sensitive recorded when OFF " + s, d(true, false, false, false, s, 0, "c", "") == HistPolicy.OK);
        }
        t("number password is still a password when sens OFF", d(true, false, false, true, NUMBER | 0x10, 0, "c", "") == HistPolicy.PASSWORD);
        // incognito
        t("incognito skipped when ON", d(true, false, true, true, TEXT, FLAG_NO_PERS, "com.browser", "") == HistPolicy.INCOGNITO);
        t("incognito recorded when OFF", d(true, false, true, false, TEXT, FLAG_NO_PERS, "com.browser", "") == HistPolicy.OK);
        t("other ime flags are not incognito", d(true, false, true, true, TEXT, 0x6 | 0x10000000, "com.browser", "") == HistPolicy.OK);
        t("isIncognito", HistPolicy.isIncognito(FLAG_NO_PERS | 6) && !HistPolicy.isIncognito(6));
        // excluded apps
        t("excluded app", d(true, false, true, true, TEXT, 0, "com.bank", "com.bank,com.x") == HistPolicy.APP_OFF);
        t("excluded app (new lines / spaces)", d(true, false, true, true, TEXT, 0, "com.x", "com.bank\ncom.x ") == HistPolicy.APP_OFF);
        t("not excluded", d(true, false, true, true, TEXT, 0, "com.bank2", "com.bank,com.x") == HistPolicy.OK);
        t("prefix is not a match", !HistPolicy.listed("com.bank.pro", "com.bank"));
        t("listed null / empty", !HistPolicy.listed(null, "a") && !HistPolicy.listed("a", null) && !HistPolicy.listed("a", "") && !HistPolicy.listed("", "a"));
        t("null package is fine", d(true, false, true, true, TEXT, 0, null, "") == HistPolicy.OK);
        // order: password before app list
        t("password reason first", d(true, false, true, true, TEXT | 0x80, FLAG_NO_PERS, "com.bank", "com.bank") == HistPolicy.PASSWORD);
        // levels
        t("levels = 1 3 7 30 days", HistPolicy.DAYS.length == 4 && HistPolicy.DAYS[0] == 1 && HistPolicy.DAYS[1] == 3 && HistPolicy.DAYS[2] == 7 && HistPolicy.DAYS[3] == 30);
        t("names match", HistPolicy.NAMES.length == 4 && "1 day".equals(HistPolicy.NAMES[0]) && "30 days".equals(HistPolicy.NAMES[3]));
        t("default level = 7 days", HistPolicy.DAYS[HistPolicy.DEF_LEVEL] == 7);
        t("bad level -> default", HistPolicy.level(-1) == HistPolicy.DEF_LEVEL && HistPolicy.level(4) == HistPolicy.DEF_LEVEL && HistPolicy.level(3) == 3 && HistPolicy.level(0) == 0);
        t("ttl 7 days", HistPolicy.ttlMs(2) == 7L * 86400000L && HistPolicy.ttlMs(99) == 7L * 86400000L && HistPolicy.ttlMs(3) == 30L * 86400000L);
        // texts
        boolean allText = true;
        for (int r = 0; r <= 8; r++) { String s = HistPolicy.reasonText(r); if (s == null || s.isEmpty()) allText = false; }
        t("reason texts", allText && HistPolicy.reasonText(HistPolicy.PASSWORD).contains("never"));
        System.out.println("HistPolicyTest: " + ok + " PASS / " + bad + " FAIL");
        if (bad > 0) System.exit(1);
    }
}
