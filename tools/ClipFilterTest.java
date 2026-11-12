import com.mgb.keyboard.clip.ClipList;
import com.mgb.keyboard.typing.ClipFilter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** javac -encoding UTF-8 -d out $(find app -name '*.java') tools/ClipFilterTest.java && java -Dfile.encoding=UTF-8 -cp out ClipFilterTest */
public class ClipFilterTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL " + m); } }

    /** "!text" = pinned clip. */
    static List<ClipList.Item> mk(String... texts) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String t : texts) {
            if (sb.length() > 0) sb.append('\n');
            boolean p = t.startsWith("!");
            sb.append(p ? '1' : '0').append(',').append(URLEncoder.encode(p ? t.substring(1) : t, "UTF-8"));
        }
        return ClipList.parse(sb.toString()).view("");
    }

    static String joined(List<ClipList.Item> l) { StringBuilder s = new StringBuilder(); for (ClipList.Item i : l) s.append(i.text).append('|'); return s.toString(); }

    public static void main(String[] a) throws Exception {
        // ---- numbers
        String[] nums = { "123", "482913", "1,250.50", "+880 1711-000000", "(02) 5555 1234", "$12.99", "\u09E7\u09E8\u09E9", "\u09F3 500", "12/05/2026", "10:30", "50%", "  42  ", "1\n2\n3", "\u20AC9,99" };
        for (String s : nums) ok(ClipFilter.isNumber(s), "number: " + s);
        String[] notNums = { "", "   ", "abc", "12abc", "a1", "+", "-", "...", "()", "https://x.com/1", "12 apples", "1e5", "12#", "\u09E7\u09E8a" };
        for (String s : notNums) ok(!ClipFilter.isNumber(s), "not number: " + s);
        ok(!ClipFilter.isNumber(null), "null not number");
        StringBuilder big = new StringBuilder(); for (int i = 0; i < 2100; i++) big.append('7');
        ok(!ClipFilter.isNumber(big.toString()), "huge digit text is not scanned (Text)");

        // ---- chips basics
        ok(ClipFilter.COUNT == 5 && ClipFilter.LABELS.length == 5, "5 chips");
        ok(ClipFilter.label(0).equals("All") && ClipFilter.label(1).equals("Pinned") && ClipFilter.label(2).equals("Text") && ClipFilter.label(3).equals("Links") && ClipFilter.label(4).equals("Numbers"), "labels in order");
        ok(ClipFilter.sanitize(-1) == 0 && ClipFilter.sanitize(5) == 0 && ClipFilter.sanitize(99) == 0 && ClipFilter.sanitize(Integer.MIN_VALUE) == 0, "bad chip = All");
        ok(ClipFilter.sanitize(3) == 3 && ClipFilter.sanitize(4) == 4, "good chip kept");
        ok(ClipFilter.label(77).equals("All"), "label of bad chip");

        // ---- filter on a mixed list
        List<ClipList.Item> l = mk("!Hello world", "https://example.com/a", "482913", "name@mail.com", "!www.site.org", "1,250.50", "\u09AC\u09BE\u0982\u09B2\u09BE \u09B2\u09C7\u0996\u09BE", "line one\nline two", "+880 1711-000000", "http://x.y");
        ok(l.size() == 10, "mk built 10 items");
        ok(joined(ClipFilter.filter(l, ClipFilter.ALL)).equals(joined(l)), "All = same order");
        ok(ClipFilter.filter(l, ClipFilter.ALL) != l, "All returns a NEW list");
        ok(joined(ClipFilter.filter(l, ClipFilter.PINNED)).equals("Hello world|www.site.org|"), "Pinned");
        ok(joined(ClipFilter.filter(l, ClipFilter.LINKS)).equals("www.site.org|https://example.com/a|http://x.y|"), "Links (pinned link counts too, pinned first like the list)");
        ok(joined(ClipFilter.filter(l, ClipFilter.NUMBERS)).equals("482913|1,250.50|+880 1711-000000|"), "Numbers");
        ok(joined(ClipFilter.filter(l, ClipFilter.TEXT)).equals("Hello world|name@mail.com|\u09AC\u09BE\u0982\u09B2\u09BE \u09B2\u09C7\u0996\u09BE|line one\nline two|"), "Text (e-mail + Bangla + multi-line)");
        int sum = ClipFilter.count(l, 2) + ClipFilter.count(l, 3) + ClipFilter.count(l, 4);
        ok(sum == l.size(), "Text + Links + Numbers = every clip (no overlap, none lost)");
        for (ClipList.Item it : l) {
            int hits = 0;
            for (int c = 2; c <= 4; c++) if (ClipFilter.matches(it, c)) hits++;
            ok(hits == 1, "exactly one of Text/Links/Numbers: " + it.text);
            ok(ClipFilter.matches(it, ClipFilter.ALL), "All matches " + it.text);
        }
        ok(ClipFilter.count(l, ClipFilter.PINNED) == 2 && ClipFilter.count(l, ClipFilter.ALL) == 10, "counts");
        ok(joined(ClipFilter.filter(l, 99)).equals(joined(l)), "bad chip filters as All");

        // ---- order + identity kept (select mode compares by identity)
        List<ClipList.Item> f = ClipFilter.filter(l, ClipFilter.LINKS);
        ok(f.get(0) == l.get(1) && f.get(1) == l.get(2) && f.get(2) == l.get(9), "same item objects, same order");

        // ---- null / empty safety
        ok(ClipFilter.filter(null, ClipFilter.ALL).isEmpty(), "null list");
        ok(ClipFilter.filter(new ArrayList<ClipList.Item>(), ClipFilter.PINNED).isEmpty(), "empty list");
        List<ClipList.Item> withNull = new ArrayList<ClipList.Item>(l); withNull.add(null); withNull.add(0, null);
        ok(ClipFilter.filter(withNull, ClipFilter.ALL).size() == 10, "null items dropped");
        ok(ClipFilter.count(null, 1) == 0 && ClipFilter.count(withNull, 1) == 2, "count null safe");
        ok(!ClipFilter.matches((ClipList.Item) null, 0), "null item matches nothing");
        ok(ClipFilter.matches((String) null, false, ClipFilter.ALL), "null text under All");
        ok(!ClipFilter.matches((String) null, false, ClipFilter.TEXT) && !ClipFilter.matches((String) null, false, ClipFilter.LINKS) && !ClipFilter.matches((String) null, false, ClipFilter.NUMBERS), "null text under the others");
        ok(ClipFilter.matches((String) null, true, ClipFilter.PINNED), "pinned flag alone decides Pinned");

        // ---- links edge cases
        ok(ClipFilter.isLink("https://a.bc") && ClipFilter.isLink("HTTP://X.COM/1") && ClipFilter.isLink("www.abc.com"), "links");
        ok(!ClipFilter.isLink("see https://a.bc now") && !ClipFilter.isLink("example.com") && !ClipFilter.isLink(""), "sentence with a link / bare domain / empty = not a link");
        ok(ClipFilter.matches("see https://a.bc now", false, ClipFilter.TEXT), "sentence with a link is Text");
        ok(!ClipFilter.matches("12345", false, ClipFilter.TEXT) && ClipFilter.matches("12345", false, ClipFilter.NUMBERS), "digits are Numbers, not Text");
        ok(ClipFilter.matches("ab12", false, ClipFilter.TEXT) && !ClipFilter.matches("ab12", false, ClipFilter.NUMBERS), "letters + digits are Text");

        // ---- texts
        ok(ClipFilter.emptyText(0, false).startsWith("Nothing copied yet"), "empty All = old hint");
        ok(ClipFilter.emptyText(0, true).equals("No match") && ClipFilter.emptyText(3, true).equals("No match"), "search = No match");
        for (int c = 1; c <= 4; c++) ok(!ClipFilter.emptyText(c, false).isEmpty() && !ClipFilter.emptyText(c, false).equals(ClipFilter.emptyText(0, false)), "empty text chip " + c);
        ok(ClipFilter.emptyText(-3, false).equals(ClipFilter.emptyText(0, false)), "bad chip empty text = All");
        ok(ClipFilter.selectedText(0).equals("Tap clips to select") && ClipFilter.selectedText(-2).equals("Tap clips to select"), "selected 0 / negative");
        ok(ClipFilter.selectedText(1).equals("1 selected") && ClipFilter.selectedText(12).equals("12 selected"), "selected n");

        System.out.println("ClipFilterTest: " + (total - fail) + " ok, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
