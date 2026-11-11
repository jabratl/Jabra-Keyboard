package com.mgb.keyboard.emoji;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.8.4 (E5) conservative text emoticons (kaomoji) for the extra emoji tab. Pure Java, no Android; tested by tools/KaomojiTest.java.
 * Only BMP characters (no emoji, no surrogate pairs). Some fonts miss a few symbols, so the panel keeps an entry only when
 * the phone can draw EVERY non-ASCII character of it (filter() + a CharCheck made from Paint.hasGlyph). Spaces inside an entry are
 * normal, so kaomoji are NEVER stored in pref emoji_recent (it splits on spaces).
 */
public final class Kaomoji {
    private Kaomoji() { }

    /** Asked once per distinct character of an entry; true = the phone can draw it. */
    public interface CharCheck { boolean canDraw(String oneChar); }

    public static final String[] ALL = {
        "\u00AF\\_(\u30C4)_/\u00AF",   // shrug
        "(\u256F\u00B0\u25A1\u00B0)\u256F\uFE35 \u253B\u2501\u253B",   // table flip
        "\u252C\u2500\u252C \u30CE( \u309C-\u309C\u30CE)",   // table back
        "( \u0361\u00B0 \u035C\u0296 \u0361\u00B0)",   // lenny
        "(\u2022\u203F\u2022)",   // smile dot
        "(^_^)",   // happy
        "(^o^)",   // happy open
        "\\(^o^)/",   // hooray
        "(^_^)v",   // peace
        "(^_-)",   // wink
        "(>_<)",   // squint
        "(T_T)",   // cry
        "(;_;)",   // cry small
        "(-_-)",   // meh
        "(-_-;)",   // sweat
        "(o_o)",   // stare
        "(O_O)",   // shock
        "(*_*)",   // dazed
        "(._.)",   // sad dot
        "(=_=)",   // tired
        "(\u2665\u203F\u2665)",   // love
        "(\uFF3E\u03C9\uFF3E)",   // cute
        "\u30FD(\u2022\u203F\u2022)\u30CE",   // cheer
        "m(_ _)m",   // bow
        "orz",   // despair
        "<3",   // heart
        "XD",   // laugh
        ":-)",   // smile
        ":D",   // grin
        ";)",   // wink2
        ":P",   // tongue
        ":'("   // tear
    };

    /** Entries whose every non-ASCII, non-space character passes the check, in list order. A null check keeps all. Never throws. */
    public static List<String> filter(CharCheck check) {
        List<String> out = new ArrayList<String>();
        for (String k : ALL) {
            boolean good = true;
            if (check != null) {
                for (int i = 0; i < k.length() && good; i++) {
                    char c = k.charAt(i);
                    if (c < 128 || c == ' ') continue;
                    boolean r;
                    try { r = check.canDraw(String.valueOf(c)); } catch (Throwable t) { r = true; }
                    if (!r) good = false;
                }
            }
            if (good) out.add(k);
        }
        return out;
    }
}
