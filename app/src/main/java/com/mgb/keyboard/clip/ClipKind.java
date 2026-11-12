package com.mgb.keyboard.clip;

import com.mgb.keyboard.typing.ClipSensitive;

/**
 * v0.10.24 (C1) what kind of text a clip is, for the small tag on a clipboard row / card (pure Java, no Android; tested by tools/ClipKindTest.java).
 * Only a LABEL: nothing is changed, stored or sent. Plain text and anything unsure = NONE (no tag, so normal clips stay clean).
 * Order: Link, Email, Code (short one-time code), Phone, Bangla (most letters are Bangla). One-token rules only: a sentence that contains a link is NONE.
 */
public final class ClipKind {
    private ClipKind() { }

    public static final int NONE = 0, LINK = 1, EMAIL = 2, CODE = 3, PHONE = 4, BANGLA = 5;
    public static final String[] NAMES = { "", "Link", "Email", "Code", "Phone", "Bangla" };
    public static final int MAX_LOOK = 2000;   // longer clips are NONE (no scanning of big text)

    public static String name(int kind) { return kind >= 0 && kind < NAMES.length ? NAMES[kind] : ""; }

    private static boolean noSpace(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isWhitespace(s.charAt(i))) return false;
        return true;
    }

    static boolean isLink(String t) {
        if (!noSpace(t) || t.length() < 4) return false;
        String l = t.toLowerCase(java.util.Locale.ROOT);
        if (l.startsWith("http://") || l.startsWith("https://")) return l.length() > 8;
        if (l.startsWith("www.")) return l.length() > 6;
        return false;
    }

    static boolean isEmail(String t) {
        if (!noSpace(t)) return false;
        int at = t.indexOf('@');
        if (at <= 0 || at != t.lastIndexOf('@')) return false;
        String dom = t.substring(at + 1);
        int dot = dom.lastIndexOf('.');
        return dot > 0 && dot < dom.length() - 1;
    }

    static boolean isPhone(String t) {
        int digits = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') digits++;
            else if (c == '+' && i == 0) continue;
            else if (c == ' ' || c == '-' || c == '(' || c == ')') continue;
            else return false;
        }
        return digits >= 7 && digits <= 15;
    }

    static boolean isBangla(String t) {
        int letters = 0, bn = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetter(c)) { letters++; if (c >= 0x0980 && c <= 0x09FF) bn++; }
            else if (c >= 0x0980 && c <= 0x09FF && !(c >= 0x09E6 && c <= 0x09EF)) bn++;   // vowel signs, hasanta (not letters by isLetter)
        }
        return bn >= 3 && bn * 2 >= letters;
    }

    public static int kind(String text) {
        if (text == null) return NONE;
        String t = text.trim();
        if (t.isEmpty() || t.length() > MAX_LOOK) return NONE;
        if (isLink(t)) return LINK;
        if (isEmail(t)) return EMAIL;
        if (t.length() <= 10 && ClipSensitive.looksLikeCode(t)) return CODE;
        if (isPhone(t)) return PHONE;
        if (isBangla(t)) return BANGLA;
        return NONE;
    }
}
