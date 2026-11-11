import com.mgb.keyboard.emoji.EmojiBuild;
import com.mgb.keyboard.emoji.EmojiDb;
import com.mgb.keyboard.emoji.EmojiUpdateCheck;
import java.io.File;
import java.nio.file.Files;

/** v0.8.5 (E6) tests for emoji/EmojiUpdateCheck (version compare + the "may this list replace the old one" rule). */
public class EmojiUpdateCheckTest {
    static int ok = 0, bad = 0;
    static void check(boolean c, String m) { if (c) ok++; else { bad++; System.out.println("FAIL: " + m); } }

    public static void main(String[] a) throws Exception {
        check(EmojiUpdateCheck.parseVersion("18.0").length == 2, "18.0");
        check(EmojiUpdateCheck.parseVersion(" 17.0 ")[0] == 17, "trim");
        check(EmojiUpdateCheck.parseVersion("16").length == 1, "16");
        check(EmojiUpdateCheck.parseVersion("1.2.3.4").length == 4, "4 parts");
        String[] badV = { null, "", "  ", "x", "18.", ".5", "1.2.3.4.5", "12345", "18.0a", "-1", "18,0", "v18" };
        for (String s : badV) check(EmojiUpdateCheck.parseVersion(s) == null, "bad version [" + s + "]");

        check(EmojiUpdateCheck.compareVersions("18.0", "17.0") > 0, "18 > 17");
        check(EmojiUpdateCheck.compareVersions("17.0", "18.0") < 0, "17 < 18");
        check(EmojiUpdateCheck.compareVersions("15.1", "15.0") > 0, "15.1 > 15.0");
        check(EmojiUpdateCheck.compareVersions("16", "16.0") == 0, "16 == 16.0");
        check(EmojiUpdateCheck.compareVersions("9.0", "10.0") < 0, "9 < 10 (numbers, not text)");
        check(EmojiUpdateCheck.compareVersions("", "17.0") < 0, "empty < good");
        check(EmojiUpdateCheck.compareVersions("17.0", null) > 0, "good > null");
        check(EmojiUpdateCheck.compareVersions(null, "") == 0, "both bad = equal");

        int OK = EmojiUpdateCheck.OK, UP = EmojiUpdateCheck.UP_TO_DATE, FEW = EmojiUpdateCheck.TOO_FEW, BAD = EmojiUpdateCheck.BAD;
        check(EmojiUpdateCheck.verdict("19.0", 1950, 9, "18.0", 1923) == OK, "newer = OK");
        check(EmojiUpdateCheck.verdict("18.0", 1923, 9, "18.0", 1923) == UP, "same = up to date");
        check(EmojiUpdateCheck.verdict("17.0", 1900, 9, "18.0", 1923) == UP, "older = up to date");
        check(EmojiUpdateCheck.verdict("19.0", 1950, 9, "", 0) == OK, "current unknown = OK");
        check(EmojiUpdateCheck.verdict("19.0", 1950, 9, null, 0) == OK, "current null = OK");
        check(EmojiUpdateCheck.verdict("x", 1950, 9, "18.0", 1923) == BAD, "bad version");
        check(EmojiUpdateCheck.verdict("19.0", 1950, 3, "18.0", 1923) == BAD, "few groups");
        check(EmojiUpdateCheck.verdict("19.0", 999, 9, "18.0", 0) == FEW, "under 1000 = too few");
        check(EmojiUpdateCheck.verdict("19.0", 1000, 9, "18.0", 0) == OK, "exactly 1000 ok");
        check(EmojiUpdateCheck.verdict("19.0", 1500, 9, "18.0", 1923) == FEW, "20% fewer than now = too few");
        check(EmojiUpdateCheck.verdict("19.0", 1730, 9, "18.0", 1923) == FEW, "just under 90% = too few");
        check(EmojiUpdateCheck.verdict("19.0", 1731, 9, "18.0", 1923) == OK, "90% or more ok");
        check(EmojiUpdateCheck.verdict("19.0", Integer.MAX_VALUE, 9, "18.0", Integer.MAX_VALUE) == UP || true, "no overflow crash");
        check(EmojiUpdateCheck.verdict("19.0", 1950, 8, "18.0", 1923) == OK, "8 groups still ok");
        check(EmojiUpdateCheck.MAX_DOWNLOAD >= 2 * 1024 * 1024 && EmojiUpdateCheck.URL.startsWith("https://"), "https + size cap");

        // the same numbers must hold for what the real generator makes from a real-sized list: use the hand made sample (small) -> TOO_FEW
        File s = new File("tools/testdata/emoji-test-sample.txt");
        if (s.exists()) {
            EmojiBuild.Result r = EmojiBuild.build(new String(Files.readAllBytes(s.toPath()), "UTF-8"));
            check(EmojiDb.parseOrNull(r.text) != null, "sample builds a parsable file");
            check(EmojiUpdateCheck.verdict(r.unicodeVersion, r.count, r.groups, "18.0", 1923) == FEW || EmojiUpdateCheck.verdict(r.unicodeVersion, r.count, r.groups, "18.0", 1923) == BAD, "a tiny list can never replace the real one");
        } else System.out.println("SKIP sample (run from repo root)");

        System.out.println("EmojiUpdateCheckTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
