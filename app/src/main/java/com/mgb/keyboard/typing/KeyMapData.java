package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.16.4 (plan X, step X4): the data of the app page "Bangলা key map" (reference tables + examples + search). Pure Java, no Android
 * (tools/KeyMapDataTest.java). Every row and every example is CHECKED against phonetic/RidmikMap.convert in that test,
 * so the page can never show a letter / word that the engine does not really make.
 * Texts only: nothing here reads or writes a pref.
 */
public final class KeyMapData {
    private KeyMapData() { }

    /** Group titles in the order of the page. Rows exist for the first 4; the 5th shows the rules and the examples. */
    public static final String G_VOWELS = "Vowels + kar", G_CONS = "Consonants", G_SIGNS = "Signs", G_CONJ = "Conjuncts", G_RULES = "Rules + examples";
    public static final String[] GROUPS = { G_VOWELS, G_CONS, G_SIGNS, G_CONJ, G_RULES };

    /** One line of a table: what you see (bangla), what you type (roman), optional short note. */
    public static final class Row {
        public final String group, bangla, roman, note;
        Row(String group, String bangla, String roman, String note) { this.group = group; this.bangla = bangla; this.roman = roman; this.note = note; }
    }

    /** One example word: type [roman] to get [bangla]; [rule] = short reason. */
    public static final class Example {
        public final String bangla, roman, rule;
        Example(String bangla, String roman, String rule) { this.bangla = bangla; this.roman = roman; this.rule = rule; }
    }

    private static final String ZWNJ = "\u200C";
    private static final List<Row> ROWS = new ArrayList<Row>();
    private static final List<Example> EXAMPLES = new ArrayList<Example>();
    private static final List<String> RULES = new ArrayList<String>();

    private static void r(String g, String bangla, String roman, String note) { ROWS.add(new Row(g, bangla, roman, note)); }
    private static void r(String g, String bangla, String roman) { r(g, bangla, roman, ""); }
    private static void e(String bangla, String roman, String rule) { EXAMPLES.add(new Example(bangla, roman, rule)); }

    static {
        // ---- vowels (alone) ----
        r(G_VOWELS, "অ", "o"); r(G_VOWELS, "আ", "a"); r(G_VOWELS, "ই", "i"); r(G_VOWELS, "ঈ", "I"); r(G_VOWELS, "উ", "u"); r(G_VOWELS, "ঊ", "U");
        r(G_VOWELS, "ঋ", "rri"); r(G_VOWELS, "এ", "e"); r(G_VOWELS, "ঐ", "OI"); r(G_VOWELS, "ও", "O"); r(G_VOWELS, "ঔ", "OU");
        // ---- vowel signs (kar) after a consonant ----
        r(G_VOWELS, "ক", "ko", "o: no sign"); r(G_VOWELS, "কা", "ka"); r(G_VOWELS, "কি", "ki"); r(G_VOWELS, "কী", "kI"); r(G_VOWELS, "কু", "ku");
        r(G_VOWELS, "কূ", "kU"); r(G_VOWELS, "কৃ", "krri"); r(G_VOWELS, "কে", "ke"); r(G_VOWELS, "কৈ", "kOI"); r(G_VOWELS, "কো", "kO"); r(G_VOWELS, "কৌ", "kOU");

        // ---- consonants ----
        r(G_CONS, "ক", "k"); r(G_CONS, "খ", "kh"); r(G_CONS, "গ", "g"); r(G_CONS, "ঘ", "gh"); r(G_CONS, "ঙ", "Ng");
        r(G_CONS, "চ", "c"); r(G_CONS, "ছ", "ch"); r(G_CONS, "জ", "j"); r(G_CONS, "ঝ", "jh"); r(G_CONS, "ঞ", "NG");
        r(G_CONS, "ট", "T"); r(G_CONS, "ঠ", "Th"); r(G_CONS, "ড", "D"); r(G_CONS, "ঢ", "Dh"); r(G_CONS, "ণ", "N");
        r(G_CONS, "ত", "t"); r(G_CONS, "থ", "th"); r(G_CONS, "দ", "d"); r(G_CONS, "ধ", "dh"); r(G_CONS, "ন", "n");
        r(G_CONS, "প", "p"); r(G_CONS, "ফ", "f"); r(G_CONS, "ফ", "ph"); r(G_CONS, "ব", "b"); r(G_CONS, "ভ", "v"); r(G_CONS, "ভ", "bh"); r(G_CONS, "ম", "m");
        r(G_CONS, "য", "z"); r(G_CONS, "র", "r"); r(G_CONS, "ল", "l");
        r(G_CONS, "শ", "S"); r(G_CONS, "শ", "sh"); r(G_CONS, "ষ", "Sh"); r(G_CONS, "স", "s"); r(G_CONS, "হ", "h");
        r(G_CONS, "ড\u09BC", "R"); r(G_CONS, "ঢ\u09BC", "Rh"); r(G_CONS, "য\u09BC", "y");

        // ---- signs ----
        r(G_SIGNS, "ৎ", "TH"); r(G_SIGNS, "ং", "ng", "always, also before a vowel"); r(G_SIGNS, "ঃ", "HH");
        r(G_SIGNS, "ঁ", "qq"); r(G_SIGNS, "ঁ", "cb");
        r(G_SIGNS, "\u09CD" + ZWNJ, "hs", "hasanta; joins letters");

        // ---- conjuncts ----
        r(G_CONJ, "ক্ষ", "kkh"); r(G_CONJ, "ক্ষ", "kSh"); r(G_CONJ, "জ্ঞ", "gg"); r(G_CONJ, "ঞ্জ", "nj"); r(G_CONJ, "ঞ্চ", "nc");
        r(G_CONJ, "হ্ম", "hm"); r(G_CONJ, "ষ্ণ", "ShN"); r(G_CONJ, "ক্ষ্ণ", "kkhN");
        r(G_CONJ, "ক্ত", "kt"); r(G_CONJ, "ন্ত", "nt"); r(G_CONJ, "ন্দ", "nd"); r(G_CONJ, "ম্প", "mp"); r(G_CONJ, "স্ত", "st"); r(G_CONJ, "ল্ল", "ll");
        r(G_CONJ, "ক্র", "kr", "ra-phala"); r(G_CONJ, "ব্য", "by", "ya-phala"); r(G_CONJ, "ব্য", "bZ", "ya-phala"); r(G_CONJ, "শ্ব", "Sw", "ba-phala");

        // ---- example words (from the Ridmik practice text) ----
        e("টিটু", "TiTu", "T = ট");
        e("হটাৎ", "hoTaTH", "TH = ৎ");
        e("বাংলা", "bangla", "ng = ং");
        e("রঙিন", "roNgin", "Ng = ঙ");
        e("অঙ্গ", "oNggo", "Ng + g");
        e("মিঞা", "miNGa", "NG = ঞ");
        e("শ্বাশ্বত", "SwaSwoto", "w = ba-phala");
        e("ব্যবহার", "bybohar", "y = ya-phala");
        e("ব্যবহার", "bZbohar", "Z = ya-phala");
        e("অ্যানিমেশন", "oZanimeSon", "Z after a vowel");
        e("ঋণ", "rriN", "rri = ঋ");
        e("বৃত্ত", "brritto", "rri = ৃ");
        e("কর্ম", "korrmo", "rr = ref");
        e("উর্দি", "urrdi", "rr = ref");
        e("নির্মল", "nirrmol", "rr = ref");
        e("কর্তৃত্ব", "korrtrritw", "rr + rri + w");
        e("চাঁদ", "caqqd", "qq = ঁ");
        e("ছোঁয়া", "chOqqya", "qq = ঁ");
        e("দুঃখ", "duHHkh", "HH = ঃ");
        e("আল্লাহ্\u200C", "allahhs", "hs at the end + ZWNJ");
        e("গণতন্ত্র", "goNtontr", "only real conjuncts join");
        e("গণতন্ত্র", "goNotontro", "o keeps letters apart");
        e("শিক্ষা", "shikSha", "kSh = ক্ষ");
        e("শিক্ষা", "shikkha", "kkh = ক্ষ");
        e("লক্ষ্ণৌ", "lokkhNOU", "OU = ঔ");
        e("ছাত্র", "chatro", "tr");
        e("বৈষ্ণব", "bOIShNb", "OI = ঐ");
        e("সমুদ্র", "somudro", "dr");
        e("রিদ্মিক", "ridmik", "dm");
        e("ব্রহ্মপুত্র", "brohmputro", "hm");
        e("ময়মনসিংহ", "moymonosingoh", "ng = ং");
        e("শম্ভূগঞ্জ", "shomvUgonj", "nj = ঞ্জ");
        e("গাড়িতে", "gaRite", "R = ড়");

        // ---- rules (short, plain) ----
        RULES.add("Capital letters matter: T ট, t ত, D ড, d দ, N ণ, n ন, S শ, Sh ষ, s স, R ড়.");
        RULES.add("ng is always ং (bangla = বাংলা). For ঙ type Ng (roNgin = রঙিন).");
        RULES.add("TH = ৎ, HH = ঃ, qq or cb = ঁ (hoTaTH = হটাৎ, duHHkh = দুঃখ, caqqd = চাঁদ).");
        RULES.add("rr before a consonant makes ref (korrmo = কর্ম). rri = ঋ / ৃ (rriN = ঋণ).");
        RULES.add("Z or y after a consonant = ya-phala (bZbohar, bybohar = ব্যবহার). Z also works after a vowel (oZanimeSon = অ্যানিমেশন).");
        RULES.add("OI = ঐ ৈ and OU = ঔ ৌ (bOIShNb = বৈষ্ণব). Lowercase oi / ou are not these.");
        RULES.add("Letters join only when they make a real Bengali conjunct (goNtontr = গণতন্ত্র). hs forces a join; at the end of a word it adds an invisible ZWNJ (allahhs).");
        RULES.add("To go back to the old phonetic rules: Settings > Languages & layouts > \"" + LangNames.AVRO + " key map\" > Classic.");
    }

    // ---- read access (copies, the caller cannot change the tables) ----
    public static List<Row> rows() { return new ArrayList<Row>(ROWS); }
    public static List<Example> examples() { return new ArrayList<Example>(EXAMPLES); }
    public static List<String> rules() { return new ArrayList<String>(RULES); }

    /** Rows of one group (title from GROUPS); unknown group = empty list. */
    public static List<Row> rowsOf(String group) {
        List<Row> out = new ArrayList<Row>();
        if (group == null) return out;
        for (Row x : ROWS) if (x.group.equals(group)) out.add(x);
        return out;
    }

    // ---- text helpers ----
    /** The engine writes ড় ঢ় য় as 2 characters, a keyboard may give 1: compare after splitting. null stays null. */
    public static String norm(String s) {
        if (s == null) return null;
        return s.replace("\u09DC", "\u09A1\u09BC").replace("\u09DD", "\u09A2\u09BC").replace("\u09DF", "\u09AF\u09BC");
    }

    /** Text for the screen: no invisible ZWNJ; a mark that cannot stand alone (ঁ ং ঃ ্ and the kar signs) gets a dotted circle in front. */
    public static String shown(String bangla) {
        if (bangla == null) return "";
        String s = bangla.replace(ZWNJ, "");
        if (s.isEmpty()) return s;
        char c = s.charAt(0);
        boolean mark = (c >= '\u0981' && c <= '\u0983') || c == '\u09BC' || (c >= '\u09BE' && c <= '\u09CD') || c == '\u09D7';
        return mark ? "\u25CC" + s : s;
    }

    // ---- search ----
    /**
     * Rows that match [query]. Empty / null query = all rows. A row matches when
     *  - its roman is the query (exact, case matters: Ng is not ng), listed FIRST, or
     *  - its roman or its note contains the query (case ignored), or
     *  - its Bangla text contains the query (Bangla typed or pasted).
     * Order inside each part = table order. Never null.
     */
    public static List<Row> search(String query) {
        List<Row> out = new ArrayList<Row>();
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) { out.addAll(ROWS); return out; }
        String nq = norm(q);
        String lq = q.toLowerCase();
        for (Row x : ROWS) if (x.roman.equals(q)) out.add(x);
        for (Row x : ROWS) {
            if (x.roman.equals(q)) continue;
            if (x.roman.toLowerCase().contains(lq) || x.note.toLowerCase().contains(lq) || norm(x.bangla).contains(nq)) out.add(x);
        }
        return out;
    }

    /** Examples that match [query] in the same way (roman exact first, then roman / rule / Bangla contains). Empty query = all. */
    public static List<Example> searchExamples(String query) {
        List<Example> out = new ArrayList<Example>();
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) { out.addAll(EXAMPLES); return out; }
        String nq = norm(q);
        String lq = q.toLowerCase();
        for (Example x : EXAMPLES) if (x.roman.equals(q)) out.add(x);
        for (Example x : EXAMPLES) {
            if (x.roman.equals(q)) continue;
            if (x.roman.toLowerCase().contains(lq) || x.rule.toLowerCase().contains(lq) || norm(x.bangla).contains(nq)) out.add(x);
        }
        return out;
    }

    /** Rules that contain [query] (case ignored; Bangla compared after norm). Empty query = all rules. */
    public static List<String> searchRules(String query) {
        List<String> out = new ArrayList<String>();
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) { out.addAll(RULES); return out; }
        String lq = q.toLowerCase(), nq = norm(q);
        for (String x : RULES) if (x.toLowerCase().contains(lq) || norm(x).contains(nq)) out.add(x);
        return out;
    }
}
