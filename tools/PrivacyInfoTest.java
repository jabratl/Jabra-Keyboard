import com.mgb.keyboard.typing.PrivacyInfo;

/** v0.10.10 (W4) PrivacyInfo tests (computer only). */
public class PrivacyInfoTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }

    public static void main(String[] a) {
        // bytes
        t("0 B", "0 B".equals(PrivacyInfo.bytes(0)));
        t("1 B", "1 B".equals(PrivacyInfo.bytes(1)));
        t("1023 B", "1023 B".equals(PrivacyInfo.bytes(1023)));
        t("1024 = 1 KB", "1 KB".equals(PrivacyInfo.bytes(1024)));
        t("1536 = 2 KB", "2 KB".equals(PrivacyInfo.bytes(1536)));
        t("12 KB", "12 KB".equals(PrivacyInfo.bytes(12 * 1024)));
        t("500 KB", "500 KB".equals(PrivacyInfo.bytes(500 * 1024)));
        t("just under 1 MB shows MB", "1.0 MB".equals(PrivacyInfo.bytes(1048575)));
        t("1 MB", "1.0 MB".equals(PrivacyInfo.bytes(1048576)));
        t("1.5 MB", "1.5 MB".equals(PrivacyInfo.bytes(1572864)));
        t("25 MB", "25.0 MB".equals(PrivacyInfo.bytes(25L * 1048576L)));
        t("1 GB", "1.0 GB".equals(PrivacyInfo.bytes(1073741824L)));
        t("2.5 GB", "2.5 GB".equals(PrivacyInfo.bytes(2684354560L)));
        t("negative = ?", "?".equals(PrivacyInfo.bytes(-1)));
        t("uses dot, not comma", !PrivacyInfo.bytes(1572864).contains(","));
        // total
        t("total sums", PrivacyInfo.total(new long[] { 100, 200, 300 }) == 600L);
        t("total skips unknown", PrivacyInfo.total(new long[] { 100, -1, 50 }) == 150L);
        t("total null", PrivacyInfo.total(null) == 0L);
        t("total empty", PrivacyInfo.total(new long[0]) == 0L);
        // storageLine
        t("storage normal", "Vault: 12 KB".equals(PrivacyInfo.storageLine("Vault", 12 * 1024)));
        t("storage none yet", "Learned words: none yet".equals(PrivacyInfo.storageLine("Learned words", -1)));
        t("storage zero bytes", "Clips: 0 B".equals(PrivacyInfo.storageLine("Clips", 0)));
        t("storage null label", PrivacyInfo.storageLine(null, 10).startsWith("Data: "));
        t("storage empty label", PrivacyInfo.storageLine("", 10).startsWith("Data: "));
        // retention
        t("ret 0", "never".equals(PrivacyInfo.retention(0)));
        t("ret 1", "after 1 hour".equals(PrivacyInfo.retention(1)));
        t("ret 2", "after 1 day".equals(PrivacyInfo.retention(2)));
        t("ret 3", "after 7 days".equals(PrivacyInfo.retention(3)));
        t("ret bad = never", "never".equals(PrivacyInfo.retention(9)) && "never".equals(PrivacyInfo.retention(-1)));
        // clipLine
        t("clip off", PrivacyInfo.clipLine(false, 2, true).contains("OFF") && !PrivacyInfo.clipLine(false, 2, true).contains("guard"));
        t("clip on never off guard", PrivacyInfo.clipLine(true, 0, false).equals("Clipboard history: ON, unpinned clips deleted never; one-time code guard OFF"));
        t("clip on 1 day guard on", PrivacyInfo.clipLine(true, 2, true).contains("after 1 day") && PrivacyInfo.clipLine(true, 2, true).contains("2 minutes"));
        // learnLine
        t("learn on", PrivacyInfo.learnLine(true).contains("ON") && PrivacyInfo.learnLine(true).contains("this phone"));
        t("learn off", PrivacyInfo.learnLine(false).contains("OFF"));
        // permission lines
        String[] p = PrivacyInfo.permissionLines();
        t("6 permission lines", p.length == 6);
        boolean nonEmpty = true; for (String s : p) if (s == null || s.trim().isEmpty()) nonEmpty = false;
        t("all permission lines have text", nonEmpty);
        t("internet line says only on tap", p[0].startsWith("Internet") && p[0].contains("only when you tap"));
        t("internet line covers emoji list", p[0].contains("Update emoji list"));
        t("mentions vibrate", p[2].startsWith("Vibrate"));
        t("password line", p[4].startsWith("Password fields"));
        t("no account / cloud line", p[5].contains("No account"));
        // clearLabel / clearDone
        t("clear none", PrivacyInfo.clearLabel(false, 0).contains("nothing to clear") && PrivacyInfo.clearLabel(true, 0).contains("nothing to clear"));
        t("clear idle", "Clear clipboard history now".equals(PrivacyInfo.clearLabel(false, 5)));
        t("clear armed plural", "Sure? Delete 5 clips (pinned stay)".equals(PrivacyInfo.clearLabel(true, 5)));
        t("clear armed singular", "Sure? Delete 1 clip (pinned stay)".equals(PrivacyInfo.clearLabel(true, 1)));
        t("clear negative = none", PrivacyInfo.clearLabel(false, -3).contains("nothing to clear"));
        t("done 0", "Nothing to clear.".equals(PrivacyInfo.clearDone(0)));
        t("done negative", "Nothing to clear.".equals(PrivacyInfo.clearDone(-2)));
        t("done 1", "Cleared 1 clip. Pinned clips were kept.".equals(PrivacyInfo.clearDone(1)));
        t("done many", "Cleared 12 clips. Pinned clips were kept.".equals(PrivacyInfo.clearDone(12)));
        System.out.println("PrivacyInfoTest: " + ok + " PASS / " + bad + " FAIL");
        if (bad > 0) System.exit(1);
    }
}
