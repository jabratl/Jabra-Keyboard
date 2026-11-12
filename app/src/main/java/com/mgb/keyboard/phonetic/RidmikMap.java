package com.mgb.keyboard.phonetic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * v0.16.3 (plan X, step X3): Ridmik key map for the "Bangলা" (phonetic) mode. Roman letters -> Bangla.
 * Own tables made from the Ridmik key map screenshots + the "বাংলা লেখা চর্চা" text. Pure Java, no Android (tools/RidmikMapTest.java).
 * AvroPhonetic.java is NOT touched: pref bn_map (1 = this class, 0 = Classic Avro rules) decides which one MgbIme calls.
 *
 * Rules (the whole word is converted again after every key, like AvroPhonetic):
 *  - Longest token first (3 letters): rri kkh chh | rr ng Ng NG gh jh kh ch Th Dh th dh ph bh sh Sh Rh nj nc gg OI OU TH HH qq cb hs | single letters.
 *  - Vowels: o a i I u U rri e OI O OU. After a consonant they become the vowel sign (kar); "o" after a consonant = nothing (it only ends the conjunct).
 *    Lowercase oi / ou are NOT diphthongs here: o + i = অই (Ridmik uses OI / OU). Extra aliases aa ii ee oo uu are NOT in this map.
 *  - ng = ং always (also before a vowel), Ng = ঙ, NG = ঞ, TH = ৎ, HH = ঃ, qq or cb = ঁ, hs = ্ (at the end of the word + ZWNJ).
 *  - rr + consonant = ref (র্ and the consonant follows without a second hasanta); rr before a vowel / at the end = plain র. rri = ঋ / ৃ.
 *  - y = য় (ya-phala after a consonant), Z = ্য after anything (oZanimeSon), z = য, w = ba-phala after a consonant (ও otherwise).
 *  - kkh / kSh = ক্ষ, gg = জ্ঞ, nj = ঞ্জ, nc = ঞ্চ (own tokens, NOT ন্জ / ন্চ).
 *  - Consonant + consonant joins ONLY when the pair (or 3-letter cluster) is a real Bengali conjunct (table below).
 *    So goNtontr = গণতন্ত্র (ণ ত do not join), brohmputro = ব্রহ্মপুত্র (হ্ম then প stays apart). "o" or a not-listed pair keeps letters apart; hs forces a join.
 *  - Unknown capital letter falls back to the small letter; every other character is copied as it is. Never throws.
 * Extras kept from the Classic rules because the Ridmik table does not forbid them: chh = ছ, f = ফ, q = ক, x = ক্স.
 */
public final class RidmikMap {
    private RidmikMap() { }

    private static final String H = "\u09CD";       // hasanta
    private static final String ZWNJ = "\u200C";
    private static final int OTHER = 0;   // start, vowel, sign, other
    private static final int CONS = 1;    // last thing written is a consonant (takes kar, may join the next one)
    private static final int PHALA = 2;   // last thing is ya-phala / ba-phala (takes kar, never joins)
    private static final int SIGN = 3;    // last thing is ং ঃ ঁ

    private static final Map<String, String[]> VOW = new HashMap<String, String[]>();   // roman -> {independent, kar}
    private static final Map<String, String[]> CON = new HashMap<String, String[]>();   // roman -> {letters written, base letters for the conjunct table}
    private static final Set<String> PAIRS = new HashSet<String>();
    private static final Set<String> TRIPLES = new HashSet<String>();

    private static void v(String r, String ind, String kar) { VOW.put(r, new String[] { ind, kar }); }
    private static void c(String r, String b) { CON.put(r, new String[] { b, b }); }
    private static void cc(String r, String written, String base) { CON.put(r, new String[] { written, base }); }

    private static void pairs(String first, String seconds) {
        for (int i = 0; i < seconds.length(); i++) PAIRS.add(first + seconds.charAt(i));
    }

    static {
        v("o", "অ", ""); v("a", "আ", "া"); v("i", "ই", "ি"); v("I", "ঈ", "ী"); v("u", "উ", "ু"); v("U", "ঊ", "ূ");
        v("rri", "ঋ", "ৃ"); v("e", "এ", "ে"); v("OI", "ঐ", "ৈ"); v("O", "ও", "ো"); v("OU", "ঔ", "ৌ");

        c("k", "ক"); c("kh", "খ"); c("g", "গ"); c("gh", "ঘ"); c("Ng", "ঙ");
        c("c", "চ"); c("ch", "ছ"); c("chh", "ছ"); c("j", "জ"); c("jh", "ঝ"); c("NG", "ঞ");
        c("T", "ট"); c("Th", "ঠ"); c("D", "ড"); c("Dh", "ঢ"); c("N", "ণ");
        c("t", "ত"); c("th", "থ"); c("d", "দ"); c("dh", "ধ"); c("n", "ন");
        c("p", "প"); c("ph", "ফ"); c("f", "ফ"); c("b", "ব"); c("bh", "ভ"); c("v", "ভ"); c("m", "ম");
        c("z", "য"); c("r", "র"); c("l", "ল");
        c("sh", "শ"); c("S", "শ"); c("Sh", "ষ"); c("s", "স"); c("h", "হ");
        cc("R", "\u09A1\u09BC", "\u09DC");
        cc("Rh", "\u09A2\u09BC", "\u09DD");
        c("q", "ক");
        cc("x", "ক্স", "কস");
        cc("kkh", "ক্ষ", "কষ"); cc("gg", "জ্ঞ", "জঞ"); cc("nj", "ঞ্জ", "ঞজ"); cc("nc", "ঞ্চ", "ঞচ");

        // valid 2-letter conjuncts (first letter : second letters). ra / ya after any consonant are handled by a rule below.
        pairs("ক", "কটতনবমলষস"); pairs("গ", "গধনবমল"); pairs("ঘ", "ন"); pairs("ঙ", "কখগঘম");
        pairs("চ", "চছঞ"); pairs("জ", "জঝঞব"); pairs("ঞ", "চছজঝ"); pairs("ট", "টম"); pairs("ড", "ড");
        pairs("ণ", "টঠডঢণম"); pairs("ত", "তথনবম"); pairs("থ", "ব"); pairs("দ", "গঘদধবভম"); pairs("ধ", "নবম");
        pairs("ন", "টঠডতথদধনবমস"); pairs("প", "টতনপলস"); pairs("ফ", "টল"); pairs("ব", "জদধবভল");
        pairs("ম", "নপফবভমল"); pairs("ল", "কগটডপফবমল"); pairs("শ", "চছনবমল"); pairs("ষ", "কটঠণপফবম");
        pairs("স", "কখটতথনপফবমল"); pairs("হ", "ণনমলব");
        // valid 3-letter conjuncts that are not "valid pair + ra / ya" (those are added by the rule)
        String[] t3 = { "কষণ", "কষম", "ঙকষ", "নতব", "ততব", "নদব" };
        for (String s : t3) TRIPLES.add(s);
    }

    /** ra-phala / ya-phala may follow any real consonant except these. */
    private static boolean phalaFirst(char f) {
        return f != 'র' && f != '\u09DC' && f != '\u09DD' && f != '\u09DF' && f != 'য' && f != 'ৎ';
    }

    /** May the next consonant (base letters cl) join the current conjunct (base letters prev)? */
    private static boolean joinOk(String prev, String cl) {
        int pl = prev.length(), cn = cl.length();
        String nc = prev + cl;
        if (pl == 1 && cn == 1) {
            if (PAIRS.contains(nc)) return true;
            return (cl.equals("র") || cl.equals("য")) && phalaFirst(prev.charAt(0));
        }
        if (pl == 1 && cn == 2) return TRIPLES.contains(nc);
        if (pl == 2 && cn == 1) {
            if (TRIPLES.contains(nc)) return true;
            return (cl.equals("র") || cl.equals("য")) && PAIRS.contains(prev);
        }
        return false;
    }

    private static boolean isToken(String t) {
        return VOW.containsKey(t) || CON.containsKey(t) || t.equals("ng") || t.equals("y") || t.equals("w") || t.equals("Z")
            || t.equals("rr") || t.equals("TH") || t.equals("HH") || t.equals("qq") || t.equals("cb") || t.equals("hs");
    }

    private static String tokenAt(CharSequence s, int i) {
        for (int len = 3; len >= 1; len--) {
            if (i + len > s.length()) continue;
            String t = s.subSequence(i, i + len).toString();
            if (isToken(t)) return t;
        }
        return null;
    }

    private static final class St {
        final StringBuilder out = new StringBuilder();
        int kind = OTHER;
        String cluster = "";
        boolean force, ref;
    }

    /** Writes one consonant (val = letters written, base = letters for the conjunct table). */
    private static void put(St st, String val, String base) {
        if (st.ref) { st.out.append(val); st.ref = false; }
        else if (st.force) { st.out.append(val); st.force = false; }
        else if (st.kind == CONS && joinOk(st.cluster, base)) {
            st.out.append(H).append(val);
            st.cluster = st.cluster + base;
            st.kind = CONS;
            return;
        } else st.out.append(val);
        st.cluster = base;
        st.kind = CONS;
    }

    /** Converts ONE roman word to Bangla. Never throws. */
    public static String convert(String roman) {
        if (roman == null || roman.length() == 0) return "";
        try { return run(roman); } catch (RuntimeException e) { return roman; }
    }

    private static String run(String roman) {
        StringBuilder s = new StringBuilder(roman);
        St st = new St();
        int i = 0;
        while (i < s.length()) {
            String tok = tokenAt(s, i);
            if (tok == null) {
                char ch = s.charAt(i);
                char lc = Character.toLowerCase(ch);
                if (lc != ch) { s.setCharAt(i, lc); continue; }   // unknown capital: try the small letter
                st.out.append(ch); st.kind = OTHER; st.cluster = ""; st.force = false; st.ref = false;
                i++;
                continue;
            }
            i += tok.length();

            String[] vow = VOW.get(tok);
            if (vow != null) {
                if (st.kind == CONS || st.kind == PHALA) st.out.append(vow[1]);
                else if (st.kind == SIGN && tok.equals("o")) { /* silent: ং + o + consonant (singoh) */ }
                else st.out.append(vow[0]);
                st.kind = OTHER; st.cluster = ""; st.force = false; st.ref = false;
                continue;
            }
            if (tok.equals("ng")) { st.out.append("ং"); st.kind = SIGN; st.cluster = ""; st.force = false; st.ref = false; continue; }
            if (tok.equals("HH")) { st.out.append("ঃ"); st.kind = SIGN; st.cluster = ""; st.force = false; st.ref = false; continue; }
            if (tok.equals("qq") || tok.equals("cb")) { st.out.append("ঁ"); st.kind = SIGN; st.cluster = ""; st.force = false; st.ref = false; continue; }
            if (tok.equals("TH")) { st.out.append("ৎ"); st.kind = OTHER; st.cluster = ""; st.force = false; st.ref = false; continue; }
            if (tok.equals("hs")) {
                st.out.append(H);
                if (i >= s.length()) st.out.append(ZWNJ); else st.force = true;
                st.kind = OTHER; st.cluster = ""; st.ref = false;
                continue;
            }
            if (tok.equals("rr")) {
                String nx = tokenAt(s, i);
                if (nx != null && CON.containsKey(nx)) {   // ref: র্ then the next consonant without a second hasanta
                    st.out.append("র").append(H);
                    st.ref = true; st.force = false; st.kind = OTHER; st.cluster = "";
                    continue;
                }
                tok = "r";   // before a vowel / at the end: plain র
            }
            if (tok.equals("y")) {
                if (st.kind == CONS && !st.ref && !st.force) { st.out.append(H).append("য"); st.kind = PHALA; st.cluster = ""; }
                else put(st, "\u09AF\u09BC", "\u09DF");
                continue;
            }
            if (tok.equals("w")) {
                if (st.kind == CONS && !st.ref && !st.force) { st.out.append(H).append("ব"); st.kind = PHALA; st.cluster = ""; }
                else { st.out.append("ও"); st.kind = OTHER; st.cluster = ""; st.force = false; st.ref = false; }
                continue;
            }
            if (tok.equals("Z")) {
                if (st.out.length() > 0 && !st.ref && !st.force) { st.out.append(H).append("য"); st.kind = PHALA; st.cluster = ""; }
                else put(st, "য", "য");
                continue;
            }
            String[] cv = CON.get(tok);
            if (cv != null) put(st, cv[0], cv[1]);
        }
        return st.out.toString();
    }
}
