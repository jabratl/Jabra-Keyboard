import com.mgb.keyboard.emoji.EmojiDb;
import com.mgb.keyboard.emoji.EmojiTone;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;

/**
 * v0.8.0.1 (E1 data drop): checks the REAL data file app/src/main/assets/emoji/emoji_v1.txt (made from Unicode's emoji-test.txt).
 * Run from the repo root after the generator test:
 * javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/emoji/*.java tools/EmojiAssetTest.java ; java -Dfile.encoding=UTF-8 -cp out EmojiAssetTest
 * If the file is missing the test says SKIP (nothing fails): the app then uses the old panel.
 */
public class EmojiAssetTest {
    static int ok = 0, bad = 0;
    static void check(boolean c, String m) { if (c) ok++; else { bad++; System.out.println("FAIL: " + m); } }
    static String u(int... cps) { return new String(cps, 0, cps.length); }
    static int find(EmojiDb db, String e) { for (int i = 0; i < db.size(); i++) if (db.emoji(i).equals(e)) return i; return -1; }

    public static void main(String[] a) throws Exception {
        File f = new File("app/src/main/assets/emoji/emoji_v1.txt");
        if (!f.exists()) { System.out.println("SKIP: no real emoji file (old panel stays)"); return; }
        EmojiDb db;
        FileInputStream in = new FileInputStream(f);
        try { db = EmojiDb.parseOrNull(in); } finally { in.close(); }
        check(db != null, "real file parses");
        if (db == null) { System.out.println("EmojiAssetTest: 0 ok, 1 FAIL"); System.exit(1); }
        check(f.length() < 150 * 1024, "file under 150 KB: " + f.length());
        check(db.size() >= 1800 && db.size() <= 2200, "emoji count " + db.size());
        check(db.groupCount() == 9, "9 groups " + db.groupCount());
        String[] g = { "Smileys & Emotion", "People & Body", "Animals & Nature", "Food & Drink", "Travel & Places", "Activities", "Objects", "Symbols", "Flags" };
        for (int i = 0; i < g.length && i < db.groupCount(); i++) check(db.groupName(i).equals(g[i]), "group " + i + " = " + db.groupName(i));
        Set<String> seen = new HashSet<String>();
        int tones = 0;
        for (int i = 0; i < db.size(); i++) {
            check(seen.add(db.emoji(i)), "no duplicate " + db.name(i));
            check(db.name(i).length() > 0, "name " + i);
            if (db.toneable(i)) {
                tones++;
                for (int t = 0; t < 5; t++) check(!db.tone(i, t).equals(db.emoji(i)) && db.tone(i, t).indexOf(EmojiTone.swatch(t)) >= 0, "tone " + t + " of " + db.name(i));
            }
        }
        check(tones > 300 && tones < 400, "toneable count " + tones);
        int grin = find(db, u(0x1F600));
        check(grin == 0 && !db.toneable(grin), "first emoji = grinning face, no tone");
        int wave = find(db, u(0x1F44B));
        check(wave >= 0 && db.toneable(wave) && db.tone(wave, 4).equals(u(0x1F44B, 0x1F3FF)), "waving hand tone 4");
        int pep = find(db, u(0x1F9D1, 0x200D, 0x1F91D, 0x200D, 0x1F9D1));
        check(pep >= 0 && db.toneable(pep) && db.tone(pep, 1).equals(u(0x1F9D1, 0x1F3FC, 0x200D, 0x1F91D, 0x200D, 0x1F9D1, 0x1F3FC)), "people holding hands tone 1");
        int fam = find(db, u(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467));
        check(fam >= 0 && !db.toneable(fam), "family has no tones");
        check(find(db, u(0x1F1E7, 0x1F1E9)) >= 0, "flag Bangladesh present");
        check(find(db, u(0x1F3FB)) < 0, "tone swatch is not an emoji entry");
        // v0.8.1: the grid panel reads groupStart(g)..groupEnd(g) and uses the first emoji of each group as the tab icon
        int prevEnd = 0;
        for (int gi = 0; gi < db.groupCount(); gi++) {
            check(db.groupStart(gi) == prevEnd && db.groupEnd(gi) > db.groupStart(gi), "group " + gi + " range is contiguous and not empty");
            check(db.group(db.groupStart(gi)) == gi && db.group(db.groupEnd(gi) - 1) == gi, "group " + gi + " first / last emoji belong to it");
            check(db.emoji(db.groupStart(gi)).length() > 0, "group " + gi + " tab icon");
            prevEnd = db.groupEnd(gi);
        }
        check(prevEnd == db.size(), "groups cover all emoji");
        System.out.println("EmojiAssetTest: " + ok + " ok, " + bad + " FAIL (" + db.size() + " emoji, " + tones + " toneable, Unicode " + db.unicodeVersion() + ")");
        if (bad > 0) System.exit(1);
    }
}
