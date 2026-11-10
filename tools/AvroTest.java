import com.mgb.keyboard.phonetic.AvroPhonetic;

/**
 * Run on a computer:
 *   javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/phonetic/AvroPhonetic.java tools/AvroTest.java
 *   java -cp out AvroTest
 */
public class AvroTest {
    static int fail = 0, total = 0;
    static void t(String in, String want) {
        total++;
        String got = AvroPhonetic.convert(in);
        if (!got.equals(want)) { fail++; System.out.println("FAIL " + in + " -> " + got + "  (want " + want + ")"); }
    }
    public static void main(String[] a) {
        t("ami", "আমি"); t("kaj", "কাজ"); t("tumi", "তুমি"); t("ma", "মা"); t("baba", "বাবা");
        t("bangla", "বাংলা"); t("bangladesh", "বাংলাদেশ"); t("Dhaka", "ঢাকা");
        t("sundor", "সুন্দর"); t("prem", "প্রেম"); t("ondhokar", "অন্ধকার"); t("kormo", "কর্ম");
        t("rokkha", "রক্ষা"); t("shikkha", "শিক্ষা"); t("sthan", "স্থান"); t("biGGan", "বিজ্ঞান");
        t("bijoy", "বিজয়"); t("hoy", "হয়"); t("bakyo", "বাক্য"); t("swadhIn", "স্বাধীন");
        t("chhobi", "ছবি"); t("achhi", "আছি"); t("ache", "আছে"); t("hocche", "হচ্ছে");
        t("rongin", "রঙিন"); t("ekhon", "এখন"); t("kemon", "কেমন"); t("rriShi", "ঋষি");
        t("valo", "ভালো"); t("bhalo", "ভালো"); t("tomar", "তোমার"); t("to", "তো"); t("boi", "বই");
        t("", "");
        // odd input must never crash (we only check it returns something)
        String[] odd = {"xyz", "QWERTY", "AAAA", "ngngng", "kkkkkkkk", "rrrr", "1234", "a-b"};
        for (String o : odd) { total++; try { if (AvroPhonetic.convert(o) == null) { fail++; System.out.println("FAIL null for " + o); } } catch (Throwable e) { fail++; System.out.println("FAIL crash for " + o + ": " + e); } }
        total++; try { if (AvroPhonetic.convert(null).length() != 0) fail++; } catch (Throwable e) { fail++; System.out.println("FAIL null input"); }
        System.out.println((total - fail) + "/" + total + " passed");
        if (fail > 0) System.exit(1);
    }
}
