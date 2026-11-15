import com.mgb.keyboard.hist.HistInfo;

/** v0.20.0 (H1) HistInfo tests (computer only). */
public class HistInfoTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }

    public static void main(String[] a) {
        t("status off", HistInfo.statusLine(false, false, 2, 0).equals("Typing history: OFF (nothing is saved)"));
        t("status off with old data", HistInfo.statusLine(false, false, 2, 5).contains("5 old entries") && HistInfo.statusLine(false, false, 2, 1).contains("1 old entry is"));
        t("status on", HistInfo.statusLine(true, false, 2, 12).equals("Typing history: ON, kept 7 days, 12 entries"));
        t("status on singular", HistInfo.statusLine(true, false, 0, 1).equals("Typing history: ON, kept 1 day, 1 entry"));
        t("status paused", HistInfo.statusLine(true, true, 3, 0).contains("(paused)") && HistInfo.statusLine(true, true, 3, 0).contains("30 days"));
        t("keep label", HistInfo.keepLabel(1).equals("Keep typing history: 3 days") && HistInfo.keepLabel(77).contains("7 days"));
        t("clear none", HistInfo.clearLabel(false, 0).contains("nothing to clear") && HistInfo.clearLabel(true, -1).contains("nothing to clear"));
        t("clear idle", HistInfo.clearLabel(false, 4).equals("Clear typing history now"));
        t("clear armed", HistInfo.clearLabel(true, 4).equals("Sure? Delete 4 entries") && HistInfo.clearLabel(true, 1).equals("Sure? Delete 1 entry"));
        t("done", HistInfo.clearDone(0).equals("Nothing to clear.") && HistInfo.clearDone(1).equals("Cleared 1 entry.") && HistInfo.clearDone(9).equals("Cleared 9 entries."));
        String b = HistInfo.confirmBody();
        t("confirm says phone only", b.contains("this phone only"));
        t("confirm says plain text", b.contains("plain text"));
        t("confirm says unlocked phone can read", b.contains("unlocks the phone"));
        t("confirm says paste + voice not saved", b.contains("Pasted text") && b.contains("voice typing"));
        t("confirm says password never", b.contains("Password fields are never saved"));
        t("confirm says not in backup", b.contains("not part of Backup"));
        t("confirm says 2000", b.contains("2000"));
        t("title", !HistInfo.confirmTitle().isEmpty());
        t("privacy off", HistInfo.privacyLine(false, false, 2).contains("OFF"));
        t("privacy on", HistInfo.privacyLine(true, false, 3).contains("30 days") && HistInfo.privacyLine(true, false, 0).contains("1 day") && !HistInfo.privacyLine(true, false, 0).contains("1 days"));
        t("privacy paused", HistInfo.privacyLine(true, true, 2).contains("paused"));
        t("skip line", HistInfo.privacySkipLine(true, true).contains("password fields") && HistInfo.privacySkipLine(true, true).contains("URL / number fields: skipped") && HistInfo.privacySkipLine(true, true).contains("Incognito fields: skipped"));
        t("skip line recorded", HistInfo.privacySkipLine(false, false).contains("number fields: recorded") && HistInfo.privacySkipLine(false, false).contains("Incognito fields: recorded"));
        System.out.println("HistInfoTest: " + ok + " PASS / " + bad + " FAIL");
        if (bad > 0) System.exit(1);
    }
}
