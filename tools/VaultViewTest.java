import com.mgb.keyboard.typing.VaultView;
import com.mgb.keyboard.vault.VaultList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/vault/VaultList.java app/src/main/java/com/mgb/keyboard/typing/VaultView.java tools/VaultViewTest.java && java -cp out VaultViewTest */
public class VaultViewTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL " + m); } }
    static VaultList.Entry e(int id, String kind, String folder, String title, String text) {
        VaultList.Entry x = new VaultList.Entry(); x.id = id; x.kind = kind; x.folder = folder; x.title = title; x.text = text; return x;
    }
    public static void main(String[] a) {
        VaultList.Entry s = e(1, VaultList.SNIPPET, "Work", "Greeting", "Hello,\nhow are you?");
        VaultList.Entry n = e(2, VaultList.NOTE, "", "", "First line\nsecond line\n  third");
        VaultList.Entry t = e(3, VaultList.TERM, "Glossary", "মেঘ", "cloud");
        VaultList.Entry tp = e(4, VaultList.TEMPLATE, "Work", "Mail", "Dear {name}");
        ok(VaultView.title(s).equals("Greeting"), "title = entry title");
        ok(VaultView.title(n).equals("First line"), "empty title -> line 1 of text");
        ok(VaultView.title(e(5, "snippet", "", "   ", "\n\n  hi  ")).equals("hi"), "blank title + leading blank lines -> first real line");
        ok(VaultView.title(e(6, "snippet", "", "", "")).equals("(empty)"), "nothing at all -> (empty)");
        ok(VaultView.title(null).equals("(empty)"), "null entry safe");
        ok(VaultView.title(e(7, "snippet", "", null, null)).equals("(empty)"), "null fields safe");
        ok(VaultView.firstLine(null).equals(""), "firstLine null");
        ok(VaultView.firstLine("a\r\nb").equals("a"), "firstLine CRLF");
        ok(VaultView.flat(" a \n\t b   c ").equals("a b c"), "flat collapses white space");
        ok(VaultView.flat(null).equals(""), "flat null");
        ok(VaultView.preview(s, 80).equals("Hello, how are you?"), "preview joins lines");
        ok(VaultView.preview(n, 80).equals("second line third"), "preview skips line 1 when it is the title");
        ok(VaultView.preview(s, 5).equals("Hello\u2026") || VaultView.preview(s, 5).equals("Hello,\u2026".substring(0, 5)) || VaultView.preview(s, 5).endsWith("\u2026"), "preview cut adds ellipsis");
        ok(VaultView.preview(s, 5).length() <= 6, "preview cut length <= n + 1");
        ok(VaultView.preview(s, 0).equals("Hello, how are you?"), "n = 0 uses default");
        ok(VaultView.preview(null, 10).equals(""), "preview null entry");
        ok(VaultView.preview(e(8, "snippet", "", "T", null), 10).equals(""), "preview null text");
        ok(VaultView.cut("abc", 3).equals("abc"), "cut exactly n kept");
        ok(VaultView.cut("abcd", 3).equals("abc\u2026"), "cut n + 1 gets ellipsis");
        ok(VaultView.cut(null, 3).equals(""), "cut null");
        String emoji = "ab\uD83D\uDE00cd"; // ab + grinning face + cd
        String c3 = VaultView.cut(emoji, 3);
        ok(c3.equals("ab\u2026"), "cut never splits a surrogate pair");
        ok(VaultView.cut(emoji, 4).equals("ab\uD83D\uDE00\u2026"), "cut keeps a whole emoji");
        ok(VaultView.tag(VaultList.NOTE).equals("Note"), "tag note");
        ok(VaultView.tag(VaultList.TEMPLATE).equals("Template"), "tag template");
        ok(VaultView.tag(VaultList.TERM).equals("Term"), "tag term");
        ok(VaultView.tag(VaultList.SNIPPET).equals(""), "tag snippet empty");
        ok(VaultView.tag(null).equals(""), "tag null");
        ok(VaultView.line2(n, 80).equals("Note \u00b7 second line third"), "line2 with tag");
        ok(VaultView.line2(s, 80).equals("Hello, how are you?"), "line2 snippet no tag");
        ok(VaultView.line2(e(9, VaultList.NOTE, "", "T", ""), 10).equals("Note"), "line2 tag only when no text");
        ok(VaultView.line2(null, 10).equals(""), "line2 null");
        ok(VaultView.chipLabel("Work", 3, false).equals("Work 3"), "chip plain");
        ok(VaultView.chipLabel("Work", 3, true).equals("\u2713 Work 3"), "chip selected");
        ok(VaultView.chipLabel("", 2, false).equals("Unfiled 2"), "chip unfiled");
        ok(VaultView.chipLabel(null, -4, false).equals("Unfiled 0"), "chip null name + negative count");
        ok(VaultView.selectSummary(0).equals("Tap items to select"), "summary 0");
        ok(VaultView.selectSummary(3).equals("3 selected"), "summary 3");
        ok(VaultView.selectSummary(-1).equals("Tap items to select"), "summary negative");
        ok(VaultView.countLine(0).equals("No items") && VaultView.countLine(1).equals("1 item") && VaultView.countLine(20).equals("20 items"), "countLine");
        List<VaultList.Entry> all = Arrays.asList(s, n, t, tp);
        Map<String, List<VaultList.Entry>> g = VaultView.groupByFolder(all);
        ok(g.size() == 3, "3 folder groups");
        ok(new ArrayList<String>(g.keySet()).equals(Arrays.asList("Work", "", "Glossary")), "group order = first appearance");
        ok(g.get("Work").size() == 2 && g.get("Work").get(1) == tp, "group keeps list order inside");
        ok(VaultView.groupByFolder(null).isEmpty(), "group null list");
        ok(VaultView.groupByFolder(Arrays.asList((VaultList.Entry) null, s)).size() == 1, "group skips null entries");
        ok(VaultView.groupByFolder(Arrays.asList(e(10, "snippet", null, "x", "y"))).containsKey(""), "null folder = Unfiled");
        ok(VaultView.sheetActions(s, false).equals(Arrays.asList("Edit", "Move", "Delete", "Select")), "sheet actions");
        ok(VaultView.sheetActions(s, true).isEmpty(), "no sheet in select mode");
        ok(VaultView.sheetActions(null, false).isEmpty(), "no sheet for null entry");
        ok(VaultView.barActions(0, 5).equals(Arrays.asList("All", "Done")), "bar: nothing picked");
        ok(VaultView.barActions(1, 5).equals(Arrays.asList("All", "Move", "Edit", "Delete", "Done")), "bar: one picked");
        ok(VaultView.barActions(2, 5).equals(Arrays.asList("All", "Move", "Delete", "Done")), "bar: two picked (no Edit)");
        ok(VaultView.barActions(5, 5).equals(Arrays.asList("Move", "Delete", "Done")), "bar: all picked (no All)");
        ok(VaultView.barActions(0, 0).equals(Arrays.asList("Done")), "bar: empty list");
        List<Integer> live = VaultView.liveIds(Arrays.asList(1, 2, 99, 2, null), all);
        ok(live.equals(Arrays.asList(1, 2)), "liveIds drops missing, duplicates, null");
        ok(VaultView.liveIds(null, all).isEmpty() && VaultView.liveIds(Arrays.asList(1), null).isEmpty(), "liveIds null safe");
        // real VaultList round trip: titles / previews from the real view()
        VaultList l = new VaultList();
        l.add(VaultList.SNIPPET, "", "", "Line one\nLine two", 1L);
        l.add(VaultList.NOTE, "", "Memo", "body text", 2L);
        List<VaultList.Entry> v = l.view(null, null, "", VaultList.NEWEST);
        ok(v.size() == 2, "real list has 2 entries");
        boolean good = true;
        for (VaultList.Entry x : v) { if (VaultView.title(x).length() == 0 || VaultView.line2(x, 40) == null) good = false; }
        ok(good, "real entries all get a title + line2");
        System.out.println((total - fail) + "/" + total + " passed");
        if (fail > 0) System.exit(1);
    }
}
