import com.mgb.keyboard.typing.ClipPop;
import com.mgb.keyboard.typing.ReplaceMore;
import java.util.List;

/** v0.42.3: tests of the pure pair-list More menu (typing/ReplaceMore.java). */
public class ReplaceMenuTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static String ids(List<ClipPop.Item> l) { StringBuilder b = new StringBuilder(); for (ClipPop.Item i : l) b.append(i.id).append(','); return b.toString(); }
    static ClipPop.Item find(List<ClipPop.Item> l, String id) { for (ClipPop.Item i : l) if (i.id.equals(id)) return i; return null; }

    public static void main(String[] x) {
        List<ClipPop.Item> l = ReplaceMore.items(3, 0, false);
        t("rows without short words", ids(l).equals("paste,save,clear,"));
        l = ReplaceMore.items(3, 2, false);
        t("Whole word appears when nShort > 0, order", ids(l).equals("paste,save,word,clear,"));
        t("count in label", find(l, "word").label.equals("Whole word (2)"));
        t("Clear is last + danger", l.get(l.size() - 1).id.equals("clear") && l.get(l.size() - 1).danger);
        t("only Clear is danger", !find(l, "paste").danger && !find(l, "save").danger && !find(l, "word").danger);
        t("Clear is in its own group", find(l, "clear").group > find(l, "save").group);
        t("Clear label first tap", find(l, "clear").label.equals("Clear"));
        t("2+ pairs, no ask yet: Clear keeps the menu open", find(l, "clear").keepOpen);
        l = ReplaceMore.items(3, 0, true);
        t("2+ pairs, asked: label Sure? Clear", find(l, "clear").label.equals("Sure? Clear"));
        t("2+ pairs, asked: closes on the 2nd tap", !find(l, "clear").keepOpen);
        l = ReplaceMore.items(1, 0, false);
        t("1 pair: Clear closes at once", find(l, "clear").label.equals("Clear") && !find(l, "clear").keepOpen);
        l = ReplaceMore.items(1, 0, true);
        t("1 pair: never asks", find(l, "clear").label.equals("Clear") && !find(l, "clear").keepOpen);
        l = ReplaceMore.items(0, 0, false);
        t("0 pairs: still all rows, Clear closes", ids(l).equals("paste,save,clear,") && !find(l, "clear").keepOpen);
        l = ReplaceMore.items(-5, -3, false);
        t("negative numbers: no crash, no Whole word", ids(l).equals("paste,save,clear,"));
        t("paste / save / word close the menu", !find(ReplaceMore.items(2, 1, false), "paste").keepOpen && !find(ReplaceMore.items(2, 1, false), "save").keepOpen && !find(ReplaceMore.items(2, 1, false), "word").keepOpen);
        t("no headers, no ticks", !find(l, "paste").header && !find(l, "paste").ticked);
        t("never null for any input", ReplaceMore.items(0, 0, true) != null && ReplaceMore.items(99, 99, true) != null);
        t("width 176", ReplaceMore.WIDTH_DP == 176);
        System.out.println("ReplaceMenuTest ok=" + ok + " bad=" + bad);
        if (bad > 0) { System.out.println("FAIL: " + bad + " failed"); System.exit(1); }
    }
}
