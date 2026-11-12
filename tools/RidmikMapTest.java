import com.mgb.keyboard.phonetic.AvroPhonetic;
import com.mgb.keyboard.phonetic.RidmikMap;

/**
 * v0.16.3 (plan X, X3): Ridmik key map engine.
 * Every example of "বাংলা লেখা চর্চা.txt" + every letter row of the 2 key map screenshots + rules + odd input.
 * Expected texts are compared after splitting precomposed ড় ঢ় য় (the txt mixes both spellings).
 */
public class RidmikMapTest {
    static int ok = 0, bad = 0;
    static String norm(String s) { return s == null ? null : s.replace("\u09DC", "\u09A1\u09BC").replace("\u09DD", "\u09A2\u09BC").replace("\u09DF", "\u09AF\u09BC"); }
    static void d(String roman, String want) {
        String got = RidmikMap.convert(roman);
        if (norm(got).equals(norm(want))) ok++;
        else { bad++; System.out.println("FAIL " + roman + " -> " + got + " (want " + want + ")"); }
    }
    static void t(String name, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + name); } }

    public static void main(String[] a) {
        // ---- 1) every example of the txt file (garite is a typo there: r = র, ড়  = R, see gaRite below) ----
        d("TiTu", "টিটু");
        d("hoTaTH", "হটাৎ");
        d("bangla", "বাংলা");
        d("oNggo", "অঙ্গ");
        d("miNGa", "মিঞা");
        d("SwaSwoto", "শ্বাশ্বত");
        d("SwaSwt", "শ্বাশ্বত");
        d("bybohar", "ব্যবহার");
        d("bzbohar", "ব্যবহার");
        d("bZbohar", "ব্যবহার");
        d("oZanimeSon", "অ্যানিমেশন");
        d("rriN", "ঋণ");
        d("brritto", "বৃত্ত");
        d("korrmo", "কর্ম");
        d("urrdi", "উর্দি");
        d("nirrmol", "নির্মল");
        d("caqqd", "চাঁদ");
        d("chOqqya", "ছোঁয়া");
        d("duHHkh", "দুঃখ");
        d("allahhs", "আল্লাহ্‌");
        d("goNtontr", "গণতন্ত্র");
        d("goNotontro", "গণতন্ত্র");
        d("kkh", "ক্ষ");
        d("kSh", "ক্ষ");
        d("kkhN", "ক্ষ্ণ");
        d("gg", "জ্ঞ");
        d("NGc", "ঞ্চ");
        d("ShN", "ষ্ণ");
        d("hm", "হ্ম");
        d("nj", "ঞ্জ");
        d("nc", "ঞ্চ");
        d("lokkhNOU", "লক্ষ্ণৌ");
        d("korrtrritw", "কর্তৃত্ব");
        d("shikSha", "শিক্ষা");
        d("shikkha", "শিক্ষা");
        d("chatro", "ছাত্র");
        d("bOIShNb", "বৈষ্ণব");
        d("somudro", "সমুদ্র");
        d("ridmik", "রিদ্মিক");
        d("brohmputro", "ব্রহ্মপুত্র");
        d("moymonosingoh", "ময়মনসিংহ");
        d("shomvUgonj", "শম্ভূগঞ্জ");
        // ---- 2) the key map screenshots: one check per row ----
        d("o", "অ");
        d("a", "আ");
        d("i", "ই");
        d("I", "ঈ");
        d("u", "উ");
        d("U", "ঊ");
        d("rri", "ঋ");
        d("e", "এ");
        d("OI", "ঐ");
        d("O", "ও");
        d("OU", "ঔ");
        d("ko", "ক");
        d("ka", "কা");
        d("ki", "কি");
        d("kI", "কী");
        d("ku", "কু");
        d("kU", "কূ");
        d("krri", "কৃ");
        d("ke", "কে");
        d("kOI", "কৈ");
        d("kO", "কো");
        d("kOU", "কৌ");
        d("k", "ক");
        d("kh", "খ");
        d("g", "গ");
        d("gh", "ঘ");
        d("Ng", "ঙ");
        d("c", "চ");
        d("ch", "ছ");
        d("j", "জ");
        d("jh", "ঝ");
        d("NG", "ঞ");
        d("T", "ট");
        d("Th", "ঠ");
        d("D", "ড");
        d("Dh", "ঢ");
        d("N", "ণ");
        d("t", "ত");
        d("th", "থ");
        d("d", "দ");
        d("dh", "ধ");
        d("n", "ন");
        d("p", "প");
        d("f", "ফ");
        d("ph", "ফ");
        d("b", "ব");
        d("v", "ভ");
        d("bh", "ভ");
        d("m", "ম");
        d("z", "য");
        d("r", "র");
        d("l", "ল");
        d("S", "শ");
        d("sh", "শ");
        d("Sh", "ষ");
        d("s", "স");
        d("h", "হ");
        d("R", "ড়");
        d("Rh", "ঢ়");
        d("y", "য়");
        d("TH", "ৎ");
        d("ng", "ং");
        d("HH", "ঃ");
        d("qq", "ঁ");
        d("cb", "ঁ");
        d("hs", "্‌");
        d("kkh", "ক্ষ");
        d("gg", "জ্ঞ");
        // ---- 3) rules + extra words ----
        d("gaRite", "গাড়িতে");
        d("garite", "গারিতে");
        d("roNgin", "রঙিন");
        d("bangla", "বাংলা");
        d("bhokti", "ভক্তি");
        d("bondhu", "বন্ধু");
        d("uttor", "উত্তর");
        d("protidin", "প্রতিদিন");
        d("korra", "করা");
        d("karr", "কার");
        d("kahsm", "কা্ম");
        d("ami", "আমি");
        d("kaj", "কাজ");
        d("tumi", "তুমি");
        d("onjoli", "অঞ্জলি");
        d("shokti", "শক্তি");
        d("oNgko", "অঙ্ক");
        d("Kh", "খ");
        d("K", "ক");
        d("x", "ক্স");
        d("q", "ক");
        d("qqq", "ঁক");
        d("HHH", "ঃহ");
        d("rrrr", "রর");
        d("boi", "বই");
        d("moy", "ময়");
        d("kOI", "কৈ");
        d("ou", "অউ");
        d("kaaj", "কাআজ");
        d("sthan", "স্থান");
        d("prem", "প্রেম");
        d("sundor", "সুন্দর");
        d("bakyo", "বাক্য");
        d("biggan", "বিজ্ঞান");
        d("swadhIn", "স্বাধীন");
        d("hocche", "হচ্ছে");
        d("ondhokar", "অন্ধকার");
        d("kormo", "করম");   // no ref without rr (Classic Avro gave কর্ম)
        d("123", "123");
        d("k1", "ক1");
        d("a-b", "আ-ব");
        // ---- 4) odd input never crashes and never returns null ----
        String[] odd = { null, "", " ", "1", "!!", "rrrr", "qqq", "HHH", "hshs", "ngng", "TH", "OIOU", "rri", "rrri", "ZZZ", "yyy", "www", "kkkkkk", "NGNGNG", "hs", "rr", "Z", "\u0985", "k\u0985k", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ" };
        for (String s : odd) { String g = null; boolean thrown = false; try { g = RidmikMap.convert(s); } catch (Throwable e) { thrown = true; } t("odd no crash " + s, !thrown && g != null); }
        t("null -> empty", RidmikMap.convert(null).equals("") && RidmikMap.convert("").equals(""));
        // ---- 5) hasanta: ZWNJ only at the end of a word ----
        t("hs end has ZWNJ", RidmikMap.convert("allahhs").endsWith("\u09CD\u200C"));
        t("hs middle no ZWNJ", !RidmikMap.convert("kahsm").contains("\u200C"));
        // ---- 6) the Classic engine is untouched and still different where the rules differ ----
        t("Classic rongin still রঙিন", AvroPhonetic.convert("rongin").equals("রঙিন"));
        t("Classic bangla still বাংলা", AvroPhonetic.convert("bangla").equals("বাংলা"));
        t("Ridmik rongin differs (ng = ং)", !RidmikMap.convert("rongin").equals(AvroPhonetic.convert("rongin")));
        t("Ridmik ng before vowel = ং", RidmikMap.convert("rongin").contains("ং"));
        System.out.println("RidmikMapTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
