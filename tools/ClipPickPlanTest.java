import com.mgb.keyboard.typing.ClipPickPlan;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** javac -encoding UTF-8 -d out $(find app -name '*.java') tools/ClipPickPlanTest.java && java -Dfile.encoding=UTF-8 -cp out ClipPickPlanTest */
public class ClipPickPlanTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL " + m); } }

    static String rep(String s, int n) { StringBuilder b = new StringBuilder(); for (int i = 0; i < n; i++) b.append(s); return b.toString(); }

    public static void main(String[] a) {
        String chap1 = "Chapter 1\n" + rep("The rain kept falling on the old town.\n", 12) + "The end of one.";
        String chap2 = "Chapter 2\n" + rep("Another long paragraph of story text here.\n", 30) + "Finally it stopped.";
        String shortT = "hello world";
        String bn = "\u0986\u09AE\u09BE\u09B0 \u09B8\u09CB\u09A8\u09BE\u09B0 \u09AC\u09BE\u0982\u09B2\u09BE \u0986\u09AE\u09BF \u09A4\u09CB\u09AE\u09BE\u09AF\u09BC \u09AD\u09BE\u09B2\u09CB\u09AC\u09BE\u09B8\u09BF";
        List<String> t = Arrays.asList(shortT, chap1, bn, chap2, shortT, "   ", null);
        List<Boolean> h = Arrays.asList(false, true, false, false, false, false, false);

        // ---- basics: blank / null skipped, same text once (newest wins), order kept
        List<ClipPickPlan.Entry> all = ClipPickPlan.plan(t, h, ClipPickPlan.F_ALL, ClipPickPlan.S_NEWEST);
        ok(all.size() == 4, "4 distinct non-blank clips, got " + all.size());
        ok(all.get(0).text.equals(shortT) && all.get(0).index == 0, "newest first, index 0 = Latest");
        ok(all.get(1).text.equals(chap1) && all.get(1).index == 1, "second");
        ok(all.get(3).text.equals(chap2) && all.get(3).index == 3, "duplicate at index 4 is dropped");
        ok(ClipPickPlan.plan(null, null, 0, 0).isEmpty(), "null list = empty");
        ok(ClipPickPlan.plan(new ArrayList<String>(), null, 0, 0).isEmpty(), "empty list = empty");

        // ---- filters
        List<ClipPickPlan.Entry> lg = ClipPickPlan.plan(t, h, ClipPickPlan.F_LONG, 0);
        ok(lg.size() == 2 && lg.get(0).text.equals(chap1) && lg.get(1).text.equals(chap2), "Long = chapters only");
        List<ClipPickPlan.Entry> sh = ClipPickPlan.plan(t, h, ClipPickPlan.F_SHORT, 0);
        ok(sh.size() == 2 && sh.get(0).text.equals(shortT), "Short = the small ones");
        List<ClipPickPlan.Entry> bg = ClipPickPlan.plan(t, h, ClipPickPlan.F_BANGLA, 0);
        ok(bg.size() == 1 && bg.get(0).text.equals(bn) && bg.get(0).bangla, "Bangla filter");
        List<ClipPickPlan.Entry> ht = ClipPickPlan.plan(t, h, ClipPickPlan.F_HTML, 0);
        ok(ht.size() == 1 && ht.get(0).text.equals(chap1) && ht.get(0).html, "HTML filter");
        ok(ClipPickPlan.plan(t, null, ClipPickPlan.F_HTML, 0).isEmpty(), "no html flags = HTML filter empty");
        ok(ClipPickPlan.plan(t, h, 99, 99).size() == 4, "bad filter / sort = All / Newest");

        // ---- sort
        List<ClipPickPlan.Entry> lo = ClipPickPlan.plan(t, h, ClipPickPlan.F_ALL, ClipPickPlan.S_LONGEST);
        ok(lo.get(0).text.equals(chap2) && lo.get(1).text.equals(chap1), "Longest first");
        ok(lo.get(lo.size() - 1).text.equals(shortT) || lo.get(lo.size() - 1).chars <= lo.get(lo.size() - 2).chars, "Longest descending");
        List<String> same = Arrays.asList("aaaa", "bbbb", "cccc");
        List<ClipPickPlan.Entry> st = ClipPickPlan.plan(same, null, 0, ClipPickPlan.S_LONGEST);
        ok(st.get(0).text.equals("aaaa") && st.get(2).text.equals("cccc"), "equal length keeps newest first");

        // ---- cycling
        ok(ClipPickPlan.nextFilter(0) == 1 && ClipPickPlan.nextFilter(4) == 0 && ClipPickPlan.nextFilter(-5) == 1, "filter cycles");
        ok(ClipPickPlan.nextSort(0) == 1 && ClipPickPlan.nextSort(1) == 0, "sort cycles");
        ok(ClipPickPlan.sanitizeFilter(-1) == 0 && ClipPickPlan.sanitizeSort(7) == 0, "sanitize");

        // ---- labels
        ClipPickPlan.Entry e1 = all.get(1);
        ok(e1.title.equals("Chapter 1"), "title = first line: " + e1.title);
        ok(e1.tail.equals("The end of one."), "tail = end of the long clip: " + e1.tail);
        ok(e1.head().startsWith("Chapter 1") && e1.head().contains("The end of one."), "head shows title and end");
        ok(e1.paras == 14, "paragraph count: " + e1.paras);
        ok(e1.info().contains("chars") && e1.info().contains("words") && e1.info().contains("para") && e1.info().contains("HTML"), "info: " + e1.info());
        ok(!e1.info().contains("Latest"), "only index 0 says Latest");
        ok(all.get(0).info().contains("Latest") && all.get(0).tail.isEmpty() && all.get(0).head().equals(shortT), "short latest clip: " + all.get(0).info());
        ok(all.get(2).info().contains("Bangla"), "Bangla tag");
        ok(all.get(0).info().startsWith("11 chars \u00B7 2 words"), "numbers: " + all.get(0).info());
        String big = rep("x", 1234);
        ok(ClipPickPlan.plan(Arrays.asList(big), null, 0, 0).get(0).info().startsWith("1,234 chars"), "thousands comma");
        ok(ClipPickPlan.plan(Arrays.asList("one"), null, 0, 0).get(0).info().contains("1 word") && !ClipPickPlan.plan(Arrays.asList("one"), null, 0, 0).get(0).info().contains("words"), "1 word singular");
        String longTitle = rep("w", 100) + "\nsecond line";
        ok(ClipPickPlan.plan(Arrays.asList(longTitle), null, 0, 0).get(0).title.length() <= ClipPickPlan.TITLE_MAX + 1, "long title cut");
        ok(ClipPickPlan.plan(Arrays.asList("\n\n  \nreal title here\nmore"), null, 0, 0).get(0).title.equals("real title here"), "blank leading lines skipped");

        // ---- cap
        List<String> many = new ArrayList<String>();
        for (int i = 0; i < 100; i++) many.add("clip number " + i);
        ok(ClipPickPlan.plan(many, null, 0, 0).size() == ClipPickPlan.MAX_SHOWN, "capped at MAX_SHOWN");

        // ---- counts
        ok(ClipPickPlan.count(t, h, ClipPickPlan.F_ALL) == 4, "count all");
        ok(ClipPickPlan.count(t, h, ClipPickPlan.F_LONG) == 2, "count long");
        ok(ClipPickPlan.count(t, h, ClipPickPlan.F_BANGLA) == 1, "count bangla");
        ok(ClipPickPlan.count(null, null, 0) == 0, "count null");

        System.out.println("ClipPickPlanTest: " + (total - fail) + "/" + total + (fail == 0 ? " OK" : " FAILED"));
        if (fail > 0) System.exit(1);
    }
}
