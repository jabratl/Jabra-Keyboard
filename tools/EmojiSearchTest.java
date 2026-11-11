import com.mgb.keyboard.emoji.EmojiDb;
import com.mgb.keyboard.emoji.EmojiSearch;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

/** v0.8.3 (E4) tests for emoji/EmojiSearch: small hand made list + the real data file (if present). */
public class EmojiSearchTest {
    static int ok = 0, bad = 0;
    static void check(boolean c, String m) { if (c) ok++; else { bad++; System.out.println("FAIL: " + m); } }

    static final String SMALL = "#JABRA-EMOJI 1\n#unicode 18.0\n#count 6\n#mods 1F44D\n"
        + "@G\tSmileys & Emotion\n@S\tface-smiling\n"
        + "\uD83D\uDE00\tgrinning face\t-\t1.0\n"
        + "\uD83D\uDE03\tgrinning face with big eyes\t-\t1.0\n"
        + "@S\tface-affection\n"
        + "\uD83D\uDE0D\tsmiling face with heart-eyes\t-\t1.0\n"
        + "@G\tPeople & Body\n@S\thand-fingers-closed\n"
        + "\uD83D\uDC4D\tthumbs up\t-\t1.0\n"
        + "@G\tFlags\n@S\tcountry-flag\n"
        + "\uD83C\uDDE7\uD83C\uDDE9\tflag: Bangladesh\t-\t2.0\n"
        + "\uD83C\uDDE7\uD83C\uDDEA\tflag: Belgium\t-\t2.0\n";

    public static void main(String[] a) throws Exception {
        EmojiDb db = EmojiDb.parse(SMALL);
        EmojiSearch s = new EmojiSearch(db);

        String[] w = EmojiSearch.words("Flag: C\u00F4te d'Ivoire heart-eyes");
        check(w.length == 6 && w[0].equals("flag") && w[2].equals("d") && w[3].equals("ivoire") && w[5].equals("eyes"), "words split " + java.util.Arrays.toString(w));
        check(EmojiSearch.words(null).length == 0 && EmojiSearch.words("  ").length == 0, "words null / blank");

        List<Integer> r = s.search("grin", null, 10);
        check(r.size() == 2 && r.get(0) == 0 && r.get(1) == 1, "prefix grin " + r);
        r = s.search("GRINNING Big", null, 10);
        check(r.size() == 1 && r.get(0) == 1, "AND + case " + r);
        r = s.search("face", null, 10);
        check(r.size() == 3, "face finds 3 " + r);
        r = s.search("heart", null, 10);
        check(r.size() == 1 && r.get(0) == 2, "heart (from heart-eyes) " + r);
        r = s.search("hand", null, 10);
        check(r.size() == 1 && r.get(0) == 3, "subgroup word only: hand " + r);
        r = s.search("country", null, 10);
        check(r.size() == 2, "subgroup country -> both flags " + r);
        r = s.search("bang", null, 10);
        check(r.size() == 1 && r.get(0) == 4, "flag by country " + r);
        check(s.search("flag be", null, 10).size() == 1 && s.search("flag be", null, 10).get(0) == 5, "flag be = Belgium " + s.search("flag be", null, 10));
        check(s.search("zzz", null, 10).isEmpty(), "no match");
        check(s.search("", null, 10).isEmpty() && s.search("   ", null, 10).isEmpty() && s.search(null, null, 10).isEmpty(), "empty query");
        check(s.search("face", null, 0).isEmpty(), "max 0");
        check(s.search("face", null, 2).size() == 2, "max 2");
        check(s.search("rin", null, 10).isEmpty(), "match is a PREFIX, not a middle part");

        // ranking: exact name word before prefix before subgroup-only
        r = s.search("face", null, 10);
        check(r.get(0) == 0 && r.get(1) == 1 && r.get(2) == 2, "face order is file order inside a score " + r);
        r = s.search("smile", null, 10);
        check(r.size() == 1 && r.get(0) == 2, "smile -> smiling (weak match) " + r);
        r = s.search("smiling", null, 10);
        check(r.size() == 3 && r.get(0) == 2, "smiling: name word first, then the face-smiling subgroup " + r);
        check(s.search("smi", null, 10).size() == 3 && s.search("smi", null, 10).get(0) == 2, "smi: plain prefix, name first");
        check(s.search("thumb", null, 10).size() == 1 && s.search("thumbs", null, 10).size() == 1, "thumb / thumbs");
        check(s.search("hearts", null, 10).size() == 1, "hearts -> heart-eyes (weak)");
        r = s.search("grinn", null, 10);
        check(r.size() == 2, "grinn prefix");

        // phone cannot draw -> hidden
        boolean[] okv = { true, false, true, true, true, true };
        r = s.search("grin", okv, 10);
        check(r.size() == 1 && r.get(0) == 0, "hidden emoji not returned " + r);
        boolean[] shortOk = { false };
        check(s.search("grin", shortOk, 10).size() == 1, "short ok array: index 1 unknown = shown");
        r = s.search("a very long query a very long query a very long query", null, 10);
        check(r.isEmpty(), "long query cut, no crash");

        // real file
        File f = new File("app/src/main/assets/emoji/emoji_v1.txt");
        if (f.exists()) {
            EmojiDb real = EmojiDb.parse(new String(Files.readAllBytes(f.toPath()), "UTF-8"));
            long t0 = System.nanoTime();
            EmojiSearch rs = new EmojiSearch(real);
            long tb = (System.nanoTime() - t0) / 1000000;
            t0 = System.nanoTime();
            for (int k = 0; k < 100; k++) rs.search("heart", null, 120);
            long ts = (System.nanoTime() - t0) / 100000;
            System.out.println("real file: index build " + tb + " ms, one search ~" + ts + " microseconds");
            check(rs.search("heart", null, 500).size() > 20, "real: heart many");
            List<Integer> h = rs.search("heart", null, 500);
            check(real.name(h.get(0)).toLowerCase().contains("heart"), "real: first heart result has heart in the name");
            check(rs.search("bangladesh", null, 10).size() == 1, "real: bangladesh flag");
            check(rs.search("thumbs", null, 10).size() >= 2, "real: thumbs");
            check(rs.search("pizza", null, 10).size() == 1, "real: pizza");
            check(rs.search("smile", null, 50).size() >= 5, "real: smile");
            check(rs.search("cat", null, 10).size() >= 5, "real: cat");
            check(rs.search("grinning", null, 10).size() >= 5, "real: grinning");
            boolean allOk = true;
            for (int i = 0; i < real.size(); i++) if (EmojiSearch.words(real.name(i)).length == 0) allOk = false;
            check(allOk, "real: every emoji has at least one name word");
            // every emoji can be found by its own first name word
            int miss = 0;
            for (int i = 0; i < real.size(); i++) {
                String fw = EmojiSearch.words(real.name(i))[0];
                if (!rs.search(fw, null, 5000).contains(i)) miss++;
            }
            check(miss == 0, "real: every emoji found by its first name word, missing " + miss);
        } else System.out.println("SKIP real file (run from repo root)");

        System.out.println("EmojiSearchTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
