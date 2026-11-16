import com.mgb.keyboard.typing.ReplaceEngine;
import com.mgb.keyboard.typing.ReplaceRules;
import com.mgb.keyboard.typing.ReplaceSets;

/** v0.25.0 (R2) every ready-made set compiles (no bad line, no error) and does what its name says on samples. */
public class ReplaceSetsTest {
    static int ok = 0, bad = 0;
    static void eq(String name, Object a, Object b) {
        if (String.valueOf(a).equals(String.valueOf(b))) ok++; else { bad++; System.out.println("FAIL " + name + ": got [" + a + "] want [" + b + "]"); }
    }
    static String run(String set, String text) {
        ReplaceSets.Def d = ReplaceSets.get(set);
        if (d == null) { bad++; System.out.println("FAIL missing set " + set); return ""; }
        return ReplaceRules.run(text, ReplaceRules.parse(d.text).rules, d.opt()).out;
    }

    public static void main(String[] a) {
        // every rule of every set compiles on a sample that touches many scripts
        String probe = "abc ABC 123 ١٢٣ ۱۲۳ 한국어 日本語 中文 Привет";
        for (ReplaceSets.Def d : ReplaceSets.all()) {
            ReplaceRules.Parsed p = ReplaceRules.parse(d.text);
            eq(d.name + " no bad lines", p.bad.size(), 0);
            eq(d.name + " has rules", p.active() > 0, true);
            eq(d.name + " under limit", p.tooMany, false);
            ReplaceRules.RunResult rr = ReplaceRules.run(probe, p.rules, d.opt());
            eq(d.name + " no error", rr.err, null);
        }
        eq("14 sets", ReplaceSets.all().size(), 14);
        eq("clean spaces", run("Clean text", "a  b ,c.   \n\n\n\nd"), "a b,c.\n\nd");
        eq("quotes", run("Quotes and dashes", "“hi” ‘x’ a—b a–b…"), "\"hi\" 'x' a-b a-b...");
        eq("korean mark", run("Korean clean-up", "안녕 ,반가워.만나서"), "안녕, 반가워. 만나서");
        eq("korean laugh", run("Korean clean-up", "ㅋㅋㅋㅋㅋㅋ ㅎㅎㅎㅎㅎ ㅠㅠㅠㅠㅠ"), "ㅋㅋㅋ ㅎㅎㅎ ㅠㅠ");
        eq("japanese", run("Japanese punctuation", "私は 学生です.元気?はい,そう!"), "私は学生です。元気？はい、そう！");
        eq("japanese keeps latin", run("Japanese punctuation", "Hello. OK, fine!"), "Hello. OK, fine!");
        eq("chinese", run("Chinese punctuation", "你好, 世界. 真的? 是的! 注意: 好; 啊"), "你好， 世界。 真的？ 是的！ 注意： 好； 啊");
        eq("chinese spaces", run("Chinese punctuation", "中 文 字"), "中文字");
        eq("chinese keeps latin", run("Chinese punctuation", "a, b. c? d!"), "a, b. c? d!");
        eq("fullwidth", run("Full-width to half-width", "２０２６！（ａ）？"), "2026!（ａ）?".replace("（", "(").replace("）", ")").replace("ａ", "ａ"));
        eq("russian yo", run("Russian typography", "Ёлка и ёж"), "Елка и еж");
        eq("russian dash", run("Russian typography", "он - друг"), "он — друг");
        eq("russian quotes", run("Russian typography", "он сказал \"да\""), "он сказал «да»");
        eq("russian straight", run("Russian quotes to straight", "«да» „нет“"), "\"да\" \"нет\"");
        eq("ar digits", run("Arabic digits to English", "٠١٢٣٤٥٦٧٨٩"), "0123456789");
        eq("fa digits", run("Persian digits to English", "۰۱۲۳۴۵۶۷۸۹"), "0123456789");
        eq("en to ar", run("English digits to Arabic", "2026-11"), "٢٠٢٦-١١");
        eq("en to fa", run("English digits to Persian", "2026"), "۲۰۲۶");
        eq("ar clean tatweel", run("Arabic clean-up", "مـــرحبا"), "مرحبا");
        eq("ar clean harakat", run("Arabic clean-up", "مَرْحَبًا"), "مرحبا");
        eq("ar clean letters", run("Arabic clean-up", "کتاب"), "كتاب");
        eq("ar punct", run("Arabic punctuation", "مرحبا, كيف حالك? نعم;"), "مرحبا، كيف حالك؟ نعم؛");
        eq("ar punct keeps latin", run("Arabic punctuation", "hi, ok?"), "hi, ok?");
        System.out.println("ReplaceSetsTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
