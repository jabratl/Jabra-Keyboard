package com.mgb.keyboard.typing;

import com.mgb.keyboard.phonetic.RidmikMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * v0.16.5 (plan X, step X5): logic of the "Practice" page (বাংলা লেখা চর্চা). Pure Java, no Android (tools/PracticePlanTest.java).
 * Deck = the example words of the key map page (KeyMapData.examples, no duplicates) + a built-in list of everyday words.
 * Every deck word has a [hint] roman that the REAL engine (RidmikMap.convert) turns into exactly that word (checked by the test),
 * so a word can never be impossible to type. check() compares what the engine makes from the typed roman with the target.
 * Reads / writes no pref: the page keeps kp_best.
 */
public final class PracticePlan {
    private PracticePlan() { }

    public static final int EMPTY = -1, WRONG = 0, RIGHT = 1;

    /** One word to type: [bangla] = target, [hint] = a roman that gives it, [rule] = short reason (may be ""). */
    public static final class Item {
        public final String bangla, hint, rule;
        Item(String bangla, String hint, String rule) { this.bangla = bangla; this.hint = hint; this.rule = rule; }
    }

    /** Result of one check: [state] RIGHT / WRONG / EMPTY, [made] = the Bangla the engine made from the typed text (screen text). */
    public static final class Result {
        public final int state;
        public final String made;
        Result(int state, String made) { this.state = state; this.made = made; }
    }

    private static final String ZWNJ = "‌";
    private static final List<Item> DECK = new ArrayList<Item>();

    private static void w(String bangla, String hint) { DECK.add(new Item(bangla, hint, "")); }

    static {
        Set<String> seen = new HashSet<String>();
        for (KeyMapData.Example x : KeyMapData.examples()) {
            if (seen.add(x.bangla)) DECK.add(new Item(x.bangla, x.roman, x.rule));
        }
        String[][] extra = {
            {"আমি", "ami"}, {"তুমি", "tumi"}, {"বাংলাদেশ", "bangladeS"}, {"আকাশ", "akaS"}, {"বাতাস", "batas"}, {"নদী", "nodI"},
            {"পাখি", "pakhi"}, {"মানুষ", "manuSh"}, {"ভালোবাসা", "valObasa"}, {"বই", "boi"}, {"স্কুল", "skul"}, {"ছেলে", "chele"},
            {"মেয়ে", "meye"}, {"সূর্য", "sUrrzo"}, {"ফুল", "phul"}, {"জল", "jol"}, {"ঘর", "ghor"}, {"রাত", "rat"},
            {"সকাল", "sokal"}, {"কলম", "kolom"}, {"খাতা", "khata"}, {"পরিবার", "poribar"}, {"শিক্ষক", "shikkhok"}, {"বাড়ি", "baRi"},
            {"গাছ", "gach"}, {"নীল", "nIl"}, {"লাল", "lal"}, {"সবুজ", "sobuj"}, {"চট্টগ্রাম", "coTTogram"}, {"কবিতা", "kobita"},
            {"গান", "gan"}, {"সুন্দর", "sundor"}, {"ধন্যবাদ", "dhonZobad"}, {"ঋতু", "rritu"}, {"ঐতিহ্য", "OItihZo"}, {"কৃষক", "krriShok"},
            {"বন্ধু", "bondhu"}, {"মা", "ma"}, {"বাবা", "baba"}, {"শহর", "Sohor"}, {"গ্রাম", "gram"}, {"পৃথিবী", "prrithibI"},
            {"আনন্দ", "anondo"}, {"সাঁতার", "saqqtar"}, {"কাঁদা", "kaqqda"}, {"রাজধানী", "rajdhanI"}, {"যুদ্ধ", "zuddho"},
            {"পদ্মা", "podma"}, {"মেঘনা", "meghona"}, {"ঈদ", "Id"}, {"সংগীত", "songgIt"}
        };
        for (String[] e : extra) if (seen.add(e[0])) w(e[0], e[1]);
    }

    /** The whole deck (copy). */
    public static List<Item> deck() { return new ArrayList<Item>(DECK); }

    public static int size() { return DECK.size(); }

    /** Item at [index]; any index is accepted (wraps around, negative too). */
    public static Item at(int index) {
        int n = DECK.size();
        return DECK.get(((index % n) + n) % n);
    }

    /**
     * Index of the next word: from [seed] (same seed = same answer, so tests are stable), never the same as [prev] (when the deck has 2+ words).
     * [prev] = -1 for the first word. Always 0..size-1.
     */
    public static int next(int prev, long seed) {
        int n = DECK.size();
        long x = seed * 6364136223846793005L + 1442695040888963407L;
        x ^= (x >>> 33);
        int i = (int) ((x & 0x7fffffffL) % n);
        if (n > 1 && i == prev) i = (i + 1) % n;
        return i;
    }

    /** Text without invisible ZWNJ and with ড় ঢ় য় split in 2 characters, so keyboards and the engine compare equal. null = "". */
    static String clean(String s) {
        if (s == null) return "";
        return KeyMapData.norm(s.replace(ZWNJ, ""));
    }

    /** Check what the user typed (roman letters, as typed on the Bangলা keyboard) against the target Bangla word. Never throws. */
    public static Result check(String target, String typedRoman) {
        try {
            String typed = typedRoman == null ? "" : typedRoman.trim();
            if (typed.isEmpty()) return new Result(EMPTY, "");
            String made = RidmikMap.convert(typed);
            if (made == null) made = "";
            return new Result(clean(made).equals(clean(target)) ? RIGHT : WRONG, made);
        } catch (Throwable t) {
            return new Result(WRONG, "");
        }
    }

    /**
     * Streak update after a word: RIGHT = streak + 1, WRONG / skip = 0. Returns { streak, best } (best never goes down, never negative).
     */
    public static int[] streak(int streak, int best, boolean right) {
        int s = right ? Math.max(0, streak) + 1 : 0;
        int b = Math.max(Math.max(0, best), s);
        return new int[] { s, b };
    }

    /** One line for the screen: "Streak 3   Best 7". */
    public static String streakText(int streak, int best) {
        return "Streak " + Math.max(0, streak) + "   Best " + Math.max(0, best);
    }
}
