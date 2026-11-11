package com.mgb.keyboard.emoji;

/**
 * v0.8.2 (E3) pure Java (no Android): the small cache file that remembers WHICH emoji this phone can draw.
 * Text format (UTF-8): line 1 "#JABRA-EMOJI-OK 1", line 2 = key (phone build + data Unicode version + emoji count), line 3 = one '0' / '1' per emoji, in file order.
 * decode() returns null on ANY problem (other phone build, other data file, cut-off or garbage file) = the check is simply run again.
 */
public final class EmojiVisible {
    private EmojiVisible() { }

    public static final String MAGIC = "#JABRA-EMOJI-OK 1";

    public static String key(String fingerprint, String unicodeVersion, int count) {
        String f = fingerprint == null ? "" : fingerprint.replace('\n', ' ').replace('\r', ' ');
        String u = unicodeVersion == null ? "" : unicodeVersion.replace('\n', ' ').replace('\r', ' ');
        return f + "|" + u + "|" + count;
    }

    public static int countTrue(boolean[] ok) {
        int n = 0;
        if (ok != null) for (boolean b : ok) if (b) n++;
        return n;
    }

    /** True when the check result is not believable (less than a quarter of the emoji drawable): then show everything and do not cache it. */
    public static boolean looksBroken(boolean[] ok) {
        return ok == null || (ok.length > 0 && countTrue(ok) * 4 < ok.length);
    }

    public static String encode(String key, boolean[] ok) {
        StringBuilder sb = new StringBuilder(ok.length + key.length() + 32);
        sb.append(MAGIC).append('\n').append(key).append('\n');
        for (boolean b : ok) sb.append(b ? '1' : '0');
        sb.append('\n');
        return sb.toString();
    }

    public static boolean[] decode(String text, String key, int count) {
        if (text == null || key == null || count < 0) return null;
        int a = text.indexOf('\n');
        if (a < 0 || !text.substring(0, a).equals(MAGIC)) return null;
        int b = text.indexOf('\n', a + 1);
        if (b < 0 || !text.substring(a + 1, b).equals(key)) return null;
        int c = text.indexOf('\n', b + 1);
        if (c < 0) return null;                       // cut-off file (no end of line)
        String bits = text.substring(b + 1, c);
        if (bits.length() != count) return null;
        boolean[] ok = new boolean[count];
        for (int i = 0; i < count; i++) {
            char ch = bits.charAt(i);
            if (ch == '1') ok[i] = true;
            else if (ch != '0') return null;
        }
        return ok;
    }
}
