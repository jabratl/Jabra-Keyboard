package com.mgb.keyboard.phonetic;

import java.util.HashMap;
import java.util.Map;

/**
 * Avro-style phonetic: roman letters -> Bangla.
 * Pure Java, NO Android classes, so it can be tested on any computer (see tools/AvroTest.java).
 * Own rule table (not copied from any other keyboard).
 */
public final class AvroPhonetic {
    private AvroPhonetic() { }

    private static final String HASANTA = "\u09CD";
    private static final int OTHER = 0;  // start, vowel sign, anusvar, other
    private static final int CONS = 1;   // last thing written is a consonant letter (can take kar / joins next consonant)
    private static final int PHALA = 2;  // last thing is ya-phala / ba-phala (takes kar, does not join next consonant)

    private static final Map<String, String[]> VOW = new HashMap<String, String[]>(); // roman -> {independent, kar}
    private static final Map<String, String> CON = new HashMap<String, String>();     // roman -> consonant
    private static final Map<String, String> AUTO = new HashMap<String, String>();    // whole word fixes

    private static void v(String r, String ind, String kar) { VOW.put(r, new String[]{ind, kar}); }
    private static void c(String r, String b) { CON.put(r, b); }

    static {
        // vowels
        v("a", "আ", "া"); v("aa", "আ", "া");
        v("i", "ই", "ি"); v("ii", "ঈ", "ী"); v("ee", "ঈ", "ী"); v("I", "ঈ", "ী");
        v("u", "উ", "ু"); v("oo", "উ", "ু"); v("uu", "ঊ", "ূ"); v("U", "ঊ", "ূ");
        v("e", "এ", "ে");
        v("o", "অ", "");          // inherent vowel: no sign after a consonant
        v("O", "ও", "ো");
        v("oi", "ঐ", "ৈ"); v("ou", "ঔ", "ৌ");
        v("rri", "ঋ", "ৃ");

        // consonants
        c("k", "ক"); c("kh", "খ"); c("g", "গ"); c("gh", "ঘ"); c("Ng", "ঙ");
        c("c", "চ"); c("ch", "ছ"); c("chh", "ছ"); c("j", "জ"); c("jh", "ঝ"); c("NG", "ঞ");
        c("T", "ট"); c("Th", "ঠ"); c("D", "ড"); c("Dh", "ঢ"); c("N", "ণ");
        c("t", "ত"); c("th", "থ"); c("d", "দ"); c("dh", "ধ"); c("n", "ন");
        c("p", "প"); c("ph", "ফ"); c("f", "ফ"); c("b", "ব"); c("bh", "ভ"); c("v", "ভ"); c("m", "ম");
        c("z", "য"); c("r", "র"); c("l", "ল");
        c("sh", "শ"); c("S", "শ"); c("Sh", "ষ"); c("s", "স"); c("h", "হ");
        c("R", "ড়"); c("Rh", "ঢ়"); c("q", "ক"); c("x", "ক্স");
        c("kkh", "ক্ষ"); c("GG", "জ্ঞ");

        // common words where the plain rules give a different spelling (exact whole-word match only)
        AUTO.put("valo", "ভালো"); AUTO.put("bhalo", "ভালো");
        AUTO.put("tomar", "তোমার"); AUTO.put("tomake", "তোমাকে"); AUTO.put("tomra", "তোমরা");
        AUTO.put("kono", "কোনো"); AUTO.put("bolo", "বলো"); AUTO.put("dekho", "দেখো");
        AUTO.put("to", "তো"); AUTO.put("boi", "বই"); AUTO.put("oi", "ওই");
    }

    private static boolean isToken(String t) {
        return VOW.containsKey(t) || CON.containsKey(t) || t.equals("ng") || t.equals("y") || t.equals("w");
    }

    /** Converts ONE roman word (letters only) to Bangla. Never throws for normal strings. */
    public static String convert(String roman) {
        if (roman == null || roman.length() == 0) return "";
        String auto = AUTO.get(roman);
        if (auto != null) return auto;

        StringBuilder s = new StringBuilder(roman);
        StringBuilder out = new StringBuilder();
        int kind = OTHER;
        int i = 0;
        while (i < s.length()) {
            String tok = null;
            for (int len = 3; len >= 1; len--) {
                if (i + len > s.length()) continue;
                String t = s.substring(i, i + len);
                if (isToken(t)) { tok = t; break; }
            }
            if (tok == null) {
                char ch = s.charAt(i);
                char lc = Character.toLowerCase(ch);
                if (lc != ch) { s.setCharAt(i, lc); continue; } // unknown capital: try the small letter
                out.append(ch); kind = OTHER; i++;
                continue;
            }
            i += tok.length();

            String[] vow = VOW.get(tok);
            if (vow != null) {
                out.append((kind == CONS || kind == PHALA) ? vow[1] : vow[0]);
                kind = OTHER;
                continue;
            }

            String val;
            if (tok.equals("ng")) {
                char nx = i < s.length() ? s.charAt(i) : '\0';
                if ("aeiouOIU".indexOf(nx) >= 0 && nx != '\0') {
                    val = "ঙ";                       // ng before a vowel = ঙ (rongin -> রঙিন)
                } else {
                    out.append("ং"); kind = OTHER;   // otherwise anusvar (bangla -> বাংলা)
                    continue;
                }
            } else if (tok.equals("y")) {
                if (kind == CONS) { out.append(HASANTA).append("য"); kind = PHALA; }   // ya-phala
                else { out.append("য়"); kind = CONS; }
                continue;
            } else if (tok.equals("w")) {
                if (kind == CONS) { out.append(HASANTA).append("ব"); kind = PHALA; }   // ba-phala
                else { out.append("ও"); kind = OTHER; }
                continue;
            } else {
                val = CON.get(tok);
            }
            if (kind == CONS) out.append(HASANTA);   // consonant + consonant = conjunct
            out.append(val);
            kind = CONS;
        }
        return out.toString();
    }
}
