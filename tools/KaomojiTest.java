import com.mgb.keyboard.emoji.Kaomoji;
import java.util.HashSet;
import java.util.List;

/** v0.8.4 (E5) tests for emoji/Kaomoji (the list + the per-character filter). */
public class KaomojiTest {
    static int ok = 0, bad = 0;
    static void check(boolean c, String m) { if (c) ok++; else { bad++; System.out.println("FAIL: " + m); } }

    public static void main(String[] a) {
        String[] all = Kaomoji.ALL;
        check(all.length >= 25 && all.length <= 60, "size " + all.length);
        HashSet<String> seen = new HashSet<String>();
        boolean clean = true, shortEnough = true, bmp = true, hasTrim = true;
        for (String k : all) {
            if (k.length() == 0 || k.indexOf('\n') >= 0 || k.indexOf('\t') >= 0 || k.indexOf('\r') >= 0) clean = false;
            if (k.length() > 24) shortEnough = false;
            if (!k.equals(k.trim())) hasTrim = false;
            for (int i = 0; i < k.length(); i++) if (Character.isSurrogate(k.charAt(i))) bmp = false;
            check(seen.add(k), "duplicate " + k);
        }
        check(clean, "no empty / newline / tab");
        check(shortEnough, "each entry <= 24 chars");
        check(bmp, "BMP only, no emoji pairs");
        check(hasTrim, "no leading / trailing space");

        List<String> everything = Kaomoji.filter(null);
        check(everything.size() == all.length, "null check keeps all");
        List<String> yes = Kaomoji.filter(new Kaomoji.CharCheck() { public boolean canDraw(String s) { return true; } });
        check(yes.size() == all.length && yes.get(0).equals(all[0]), "all drawable keeps all, same order");
        List<String> no = Kaomoji.filter(new Kaomoji.CharCheck() { public boolean canDraw(String s) { return false; } });
        check(no.size() > 0 && no.size() < all.length, "nothing drawable: only pure ASCII entries stay " + no.size());
        boolean ascii = true;
        for (String k : no) for (int i = 0; i < k.length(); i++) if (k.charAt(i) >= 128) ascii = false;
        check(ascii, "ASCII-only entries stay");
        check(no.contains("orz") && no.contains(":-)") && no.contains("<3"), "plain text emoticons always stay");
        // one missing character hides only the entries that use it
        List<String> noBox = Kaomoji.filter(new Kaomoji.CharCheck() { public boolean canDraw(String s) { return !s.equals("\u253B"); } });
        check(noBox.size() == all.length - 1, "one missing char hides exactly the table flip: " + (all.length - noBox.size()) + " hidden");
        List<String> thrower = Kaomoji.filter(new Kaomoji.CharCheck() { public boolean canDraw(String s) { throw new RuntimeException("x"); } });
        check(thrower.size() == all.length, "a throwing check = keep (show)");
        // each non-ASCII char is asked about as ONE char
        final boolean[] oneChar = { true };
        Kaomoji.filter(new Kaomoji.CharCheck() { public boolean canDraw(String s) { if (s.length() != 1) oneChar[0] = false; return true; } });
        check(oneChar[0], "check gets one char at a time");

        System.out.println("KaomojiTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
