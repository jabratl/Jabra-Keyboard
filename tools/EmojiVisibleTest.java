import com.mgb.keyboard.emoji.EmojiVisible;

/** v0.8.2 (E3) tests for emoji/EmojiVisible (cache of which emoji the phone can draw). */
public class EmojiVisibleTest {
    static int ok = 0, bad = 0;
    static void check(boolean c, String m) { if (c) ok++; else { bad++; System.out.println("FAIL: " + m); } }

    public static void main(String[] a) {
        String key = EmojiVisible.key("google/phone/x:14/ABC", "18.0", 6);
        check(key.equals("google/phone/x:14/ABC|18.0|6"), "key " + key);
        check(EmojiVisible.key(null, null, 3).equals("||3"), "key null-safe");
        check(!EmojiVisible.key("a\nb", "1\r2", 1).contains("\n") && !EmojiVisible.key("a\nb", "1\r2", 1).contains("\r"), "key has no line break");

        boolean[] v = { true, false, true, true, false, true };
        String t = EmojiVisible.encode(key, v);
        check(t.startsWith("#JABRA-EMOJI-OK 1\n"), "magic first");
        check(t.endsWith("101101\n"), "bits written " + t);
        boolean[] r = EmojiVisible.decode(t, key, 6);
        check(r != null && r.length == 6, "round trip length");
        boolean same = r != null;
        for (int i = 0; same && i < 6; i++) if (r[i] != v[i]) same = false;
        check(same, "round trip values");

        check(EmojiVisible.decode(t, "other|18.0|6", 6) == null, "other phone build = null");
        check(EmojiVisible.decode(t, EmojiVisible.key("google/phone/x:14/ABC", "17.0", 6), 6) == null, "other Unicode version = null");
        check(EmojiVisible.decode(t, key, 7) == null, "other count = null");
        check(EmojiVisible.decode(t, key, 5) == null, "smaller count = null");
        check(EmojiVisible.decode(null, key, 6) == null, "null text");
        check(EmojiVisible.decode(t, null, 6) == null, "null key");
        check(EmojiVisible.decode(t, key, -1) == null, "negative count");
        check(EmojiVisible.decode("", key, 6) == null, "empty text");
        check(EmojiVisible.decode("garbage", key, 6) == null, "garbage");
        check(EmojiVisible.decode(t.substring(0, t.length() - 1), key, 6) == null, "cut-off (no last newline)");
        check(EmojiVisible.decode(t.substring(0, t.length() - 4), key, 6) == null, "cut-off (short bits)");
        check(EmojiVisible.decode(t.replace("101101", "10x101"), key, 6) == null, "bad char in bits");
        check(EmojiVisible.decode(t.replace("#JABRA-EMOJI-OK 1", "#JABRA-EMOJI-OK 2"), key, 6) == null, "other format number");
        check(EmojiVisible.decode(key + "\n101101\n", key, 6) == null, "no magic line");

        boolean[] none = new boolean[0];
        String te = EmojiVisible.encode("k|1|0", none);
        boolean[] re = EmojiVisible.decode(te, "k|1|0", 0);
        check(re != null && re.length == 0, "empty list round trip");

        boolean[] big = new boolean[1923];
        for (int i = 0; i < big.length; i++) big[i] = i % 7 != 0;
        String tb = EmojiVisible.encode("k|18.0|1923", big);
        boolean[] rb = EmojiVisible.decode(tb, "k|18.0|1923", 1923);
        boolean sb = rb != null;
        for (int i = 0; sb && i < big.length; i++) if (rb[i] != big[i]) sb = false;
        check(sb, "1923 round trip");
        check(tb.length() < 2100, "cache file is about 2 KB: " + tb.length());

        check(EmojiVisible.countTrue(v) == 4 && EmojiVisible.countTrue(null) == 0, "countTrue");
        check(!EmojiVisible.looksBroken(v), "4 of 6 is believable");
        check(EmojiVisible.looksBroken(new boolean[8]), "all false = broken");
        check(EmojiVisible.looksBroken(new boolean[] { true, false, false, false, false, false, false, false, false }), "1 of 9 = broken");
        check(!EmojiVisible.looksBroken(new boolean[] { true, true, false, false, false, false, false, false }), "2 of 8 = still believable");
        check(EmojiVisible.looksBroken(null), "null = broken");
        check(!EmojiVisible.looksBroken(new boolean[0]), "empty = not broken");

        System.out.println("EmojiVisibleTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
