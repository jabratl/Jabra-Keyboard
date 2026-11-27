import com.mgb.keyboard.typing.FixNames;
import com.mgb.keyboard.typing.GlossaryImport;
import com.mgb.keyboard.typing.NovelBook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** v0.57.0 (FX1): rows, live preview, hit counts, locks, on/off, topic tag merge of the one-screen Fix names view. */
public class FixNamesTest {
    static int ok = 0, bad = 0;
    static void eq(String name, Object a, Object b) {
        if (String.valueOf(a).equals(String.valueOf(b))) ok++; else { bad++; System.out.println("FAIL " + name + ": got [" + a + "] want [" + b + "]"); }
    }
    static final String S = " \u00B7 ";

    public static void main(String[] x) {
        String text = "Lin Fen bowed at Lin Fen Pavilion. Lin Fong smiled, and Lin Fan left for Blue Peek. Master Wu used Sword Chi twice.";
        List<String> names = new ArrayList<String>(Arrays.asList(
            "G Saga" + S + "Characters" + S + "wrong", "G Saga" + S + "Places" + S + "wrong", "G Saga" + S + "Skills" + S + "raw", "G Other" + S + "Characters" + S + "wrong"));
        List<String> texts = new ArrayList<String>(Arrays.asList(
            "# c\nLin Fen | Lin Fong => Lin Feng\n", "Blue Peek => Blue Peak\nSword Chi => Sword Qi\n", "\u5251 => Sword\n", "Wu => Other\n"));
        List<FixNames.Row> rows = FixNames.rows("Saga", names, texts);
        eq("3 rows (raw + other novel skipped)", rows.size(), 3);
        eq("forms", rows.get(0).formsText(), "Lin Fen; Lin Fong");
        eq("topic", rows.get(1).topic, "Places");
        eq("topics", FixNames.topicsOf(rows), "[Characters, Places]");
        eq("choices no mine", FixNames.topicChoices(rows), "[Characters, Places]");
        eq("choices default", FixNames.topicChoices(new ArrayList<FixNames.Row>()), "[Characters, Places, Skills]");
        eq("count all", FixNames.countFor(rows, NovelBook.ALL), 3);
        eq("count Places", FixNames.countFor(rows, "Places"), 2);

        List<String> protect = new ArrayList<String>(Arrays.asList("Lin Fen Pavilion"));
        Set<String> off = new HashSet<String>();
        FixNames.Scan sc = FixNames.scan(text, rows, off, protect, new ArrayList<String>(), "");
        eq("row0 count (pavilion locked)", sc.counts[0], 2);
        eq("row1 count", sc.counts[1], 1);
        eq("row2 count", sc.counts[2], 1);
        eq("hits", sc.hits, 4);
        eq("locked", sc.locked, 1);
        eq("fix label", FixNames.fixLabel(sc.hits), "Fix 4 in text");
        eq("nothing label", FixNames.fixLabel(0), "Nothing to fix");
        boolean sorted = true;
        for (int i = 1; i < sc.segs.size(); i++) if (sc.segs.get(i).start < sc.segs.get(i - 1).end) sorted = false;
        eq("segs sorted, not overlapping", sorted, true);

        List<Integer> vis = FixNames.visible(rows, sc.counts, NovelBook.ALL, "");
        eq("all visible", vis.size(), 3);
        eq("search any form", FixNames.visible(rows, sc.counts, NovelBook.ALL, "lin fong").size(), 1);
        eq("search right form", FixNames.visible(rows, sc.counts, NovelBook.ALL, "sword qi").size(), 1);
        eq("filter topic", FixNames.visible(rows, sc.counts, "Places", "").size(), 2);

        off.add(rows.get(1).key());
        FixNames.Scan s2 = FixNames.scan(text, rows, off, protect, new ArrayList<String>(), "");
        eq("off row still counted", s2.counts[1], 1);
        eq("off row not in hits", s2.hits, 3);
        eq("active rules", FixNames.activeRules(rows, off).size(), 2);

        FixNames.Scan s3 = FixNames.scan(text, rows, new HashSet<String>(), protect, FixNames.forms("Lin Fan; Lin Fan Wu"), "Lin Feng");
        eq("draft hits", s3.draftHits, 1);
        eq("draft hits not in Fix count", s3.hits, 4);
        int dr = 0; for (FixNames.Seg s : s3.segs) if (s.draft) dr++;
        eq("draft seg shown", dr, 1);
        eq("forms dedupe", FixNames.forms("A; a ;B|b; ;C").size(), 3);
        eq("no draft without right form", FixNames.scan(text, rows, off, protect, FixNames.forms("Lin Fan"), "").draftHits, 0);
        eq("empty text", FixNames.scan("", rows, off, protect, null, null).hits, 0);
        eq("null rows", FixNames.scan(text, null, null, null, null, null).hits, 0);

        // merge with topic tag
        String m1 = FixNames.mergeWithTopic("", "Lin Fan => Lin Feng", "Characters", 50).text;
        eq("new set with tag", m1, "# topic: Characters\nLin Fan => Lin Feng\n");
        String m2 = FixNames.mergeWithTopic(m1, "Blue Peek => Blue Peak", "Places", 50).text;
        eq("second topic", m2, "# topic: Characters\nLin Fan => Lin Feng\n# topic: Places\nBlue Peek => Blue Peak\n");
        String m3 = FixNames.mergeWithTopic(m2, "Li Wu => Li Wuji", "Characters", 50).text;
        eq("same topic inserted after its last pair", m3, "# topic: Characters\nLin Fan => Lin Feng\nLi Wu => Li Wuji\n# topic: Places\nBlue Peek => Blue Peak\n");
        NovelBook.Merged m4 = FixNames.mergeWithTopic(m3, "Lin Fan => Lin Fen Wu", "Characters", 50);
        eq("same find+topic replaced in place", m4.text.contains("Lin Fan => Lin Fen Wu") && !m4.text.contains("=> Lin Feng"), true);
        eq("updated 1", m4.updated + "/" + m4.added, "1/0");
        NovelBook.Merged m5 = FixNames.mergeWithTopic(m3, "Lin Fan => Lin Feng", "Places", 50);
        eq("moved to other topic = one copy", m5.text.split("Lin Fan =>", -1).length, 2);
        eq("moved under Places", m5.text.indexOf("Lin Fan => Lin Feng") > m5.text.indexOf("# topic: Places"), true);
        NovelBook.Merged m6 = FixNames.mergeWithTopic("A => B\n", "C => D", "My pairs", 50);
        eq("my pairs topic no tag when file has no tags", m6.text, "A => B\nC => D\n");
        NovelBook.Merged m7 = FixNames.mergeWithTopic("A => B\nC => D\n", "E => F", "Characters", 2);
        eq("overflow", m7.overflow.size() + "/" + m7.added, "1/0");
        eq("bad line unchanged", FixNames.mergeWithTopic("A => B\n", "nothing", "X", 50).text, "A => B\n");

        // rows read the tags of My pairs sets
        String mine = NovelBook.mySetName("Saga", 1);
        List<String> n2 = new ArrayList<String>(Arrays.asList(mine));
        List<String> t2 = new ArrayList<String>(Arrays.asList(m3));
        List<FixNames.Row> r2 = FixNames.rows("Saga", n2, t2);
        eq("mine rows", r2.size(), 3);
        eq("mine topic 0", r2.get(0).topic, "Characters");
        eq("mine topic 2", r2.get(2).topic, "Places");
        eq("mine flag", r2.get(0).mine, true);
        eq("choices from mine tags", FixNames.topicChoices(r2), "[Characters, Places]");
        List<FixNames.Row> r3 = FixNames.rows("Saga", n2, new ArrayList<String>(Arrays.asList("A => B\n")));
        eq("untagged mine = My pairs", r3.get(0).topic, "My pairs");

        System.out.println("FixNamesTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
