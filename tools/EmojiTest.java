import com.mgb.keyboard.emoji.EmojiData;
import com.mgb.keyboard.emoji.RecentEmoji;
import java.util.*;

/** javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/emoji/*.java tools/EmojiTest.java ; java -cp out EmojiTest */
public class EmojiTest {
    static int ok = 0, bad = 0;
    static void check(boolean c, String m) { if (c) ok++; else { bad++; System.out.println("FAIL: " + m); } }

    public static void main(String[] a) {
        check(EmojiData.count() == EmojiData.ICONS.length, "icons count");
        check(EmojiData.count() == EmojiData.NAMES.length, "names count");
        for (int i = 0; i < EmojiData.count(); i++) {
            String[] l = EmojiData.list(i);
            check(l.length >= 20, "category " + i + " has " + l.length);
            Set<String> seen = new HashSet<String>();
            for (String s : l) {
                check(s.length() > 0, "empty item in " + i);
                check(seen.add(s), "duplicate " + s + " in " + i);
                check(s.indexOf(' ') < 0, "space inside item");
                int cp0 = s.codePointAt(0); check(cp0 > 0x2000 || cp0 == 0xA9 || cp0 == 0xAE, "not an emoji start: " + s);
            }
        }
        check(EmojiData.list(-1).length == 0 && EmojiData.list(99).length == 0, "bad index");
        String r = "";
        r = RecentEmoji.add(r, "😀");
        r = RecentEmoji.add(r, "👍");
        check(r.equals("👍 😀"), "newest first: " + r);
        r = RecentEmoji.add(r, "😀");
        check(r.equals("😀 👍"), "move to front: " + r);
        check(RecentEmoji.parse(r).size() == 2, "parse size");
        for (int i = 0; i < 50; i++) r = RecentEmoji.add(r, "e" + i);
        check(RecentEmoji.parse(r).size() == RecentEmoji.MAX, "max items");
        check(RecentEmoji.parse(r).get(0).equals("e49"), "newest kept");
        check(RecentEmoji.add(null, "x").equals("x"), "null stored");
        check(RecentEmoji.add("a b", "").equals("a b"), "empty emoji ignored");
        check(RecentEmoji.add("a b", "c d").equals("a b"), "emoji with space ignored");
        check(RecentEmoji.parse("a  a b").size() == 2, "dedupe/ignore double space");
        System.out.println("EmojiTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
