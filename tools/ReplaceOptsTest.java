import com.mgb.keyboard.typing.ClipPop;
import com.mgb.keyboard.typing.ReplaceEngine;
import com.mgb.keyboard.typing.ReplaceOpts;
import java.util.List;

/** v0.42.1: tests of the pure Options menu model (typing/ReplaceOpts.java). */
public class ReplaceOptsTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static String ids(List<ClipPop.Item> l) { StringBuilder b = new StringBuilder(); for (ClipPop.Item i : l) b.append(i.id).append(','); return b.toString(); }
    static ClipPop.Item find(List<ClipPop.Item> l, String id) { for (ClipPop.Item i : l) if (i.id.equals(id)) return i; return null; }

    public static void main(String[] x) {
        ReplaceEngine.Opt o = new ReplaceEngine.Opt();
        List<ClipPop.Item> l = ReplaceOpts.items(o, 0);
        t("text mode rows + order", ids(l).equals("h_match,aa,smart,h_text,keep,list,nl,never,"));
        o.mode = ReplaceEngine.MODE_REGEX;
        t("regex mode hides smart / list / nl", ids(ReplaceOpts.items(o, 0)).equals("h_match,aa,h_text,keep,never,"));
        o.mode = ReplaceEngine.MODE_WORD;
        t("word mode has all rows", ids(ReplaceOpts.items(o, 0)).equals("h_match,aa,smart,h_text,keep,list,nl,never,"));
        t("null opt = defaults, no crash", ReplaceOpts.items(null, 0).size() == 8 && ReplaceOpts.activeCount(null, 0) == 0);

        t("tick rows keep the menu open", find(l, "aa").keepOpen && find(l, "smart").keepOpen && find(l, "keep").keepOpen && find(l, "list").keepOpen && find(l, "nl").keepOpen);
        t("Never row closes the menu", !find(l, "never").keepOpen);
        t("headers are headers, not ticked", find(l, "h_match").header && find(l, "h_text").header && !find(l, "h_match").ticked);
        t("tick rows are not headers", !find(l, "aa").header && !find(l, "never").header);
        t("groups: Match 0, Text 1, Never 2", find(l, "aa").group == 0 && find(l, "keep").group == 1 && find(l, "never").group == 2);
        t("no danger rows", !find(l, "never").danger);
        t("never value = count + arrow", find(ReplaceOpts.items(o, 3), "never").value.equals("3 \u203A") && find(ReplaceOpts.items(o, -2), "never").value.equals("0 \u203A"));
        t("width 224", ReplaceOpts.WIDTH_DP == 224);

        ReplaceEngine.Opt p = new ReplaceEngine.Opt();
        p.matchCase = true; p.smart = true; p.escapes = true;
        List<ClipPop.Item> lp = ReplaceOpts.items(p, 0);
        t("ticks follow the options", find(lp, "aa").ticked && find(lp, "smart").ticked && find(lp, "nl").ticked && !find(lp, "keep").ticked && !find(lp, "list").ticked);

        // count dot
        ReplaceEngine.Opt c = new ReplaceEngine.Opt();
        t("count 0", ReplaceOpts.activeCount(c, 0) == 0);
        c.matchCase = true; c.smart = true; c.keepCase = true; c.list = true; c.escapes = true;
        t("count 5 options", ReplaceOpts.activeCount(c, 0) == 5);
        t("count + never list = 6", ReplaceOpts.activeCount(c, 4) == 6);
        c.mode = ReplaceEngine.MODE_REGEX;
        t("regex counts only Aa + Keep (+ never)", ReplaceOpts.activeCount(c, 0) == 2 && ReplaceOpts.activeCount(c, 1) == 3);
        t("label without / with count", ReplaceOpts.buttonLabel(new ReplaceEngine.Opt(), 0).equals("Options") && ReplaceOpts.buttonLabel(p, 2).equals("Options \u00B7 4"));

        // toggle
        ReplaceEngine.Opt q = new ReplaceEngine.Opt();
        t("toggle aa", ReplaceOpts.toggle(q, "aa") && q.matchCase && ReplaceOpts.toggle(q, "aa") && !q.matchCase);
        t("toggle smart", ReplaceOpts.toggle(q, "smart") && q.smart);
        t("toggle list", ReplaceOpts.toggle(q, "list") && q.list);
        t("toggle nl = escapes", ReplaceOpts.toggle(q, "nl") && q.escapes);
        q.matchCase = true;
        t("keep ON turns Aa OFF", ReplaceOpts.toggle(q, "keep") && q.keepCase && !q.matchCase);
        t("keep OFF leaves Aa alone", ReplaceOpts.toggle(q, "keep") && !q.keepCase && !q.matchCase);
        t("unknown / header / never / null ids = false, nothing changes", !ReplaceOpts.toggle(q, "never") && !ReplaceOpts.toggle(q, "h_text") && !ReplaceOpts.toggle(q, "zzz") && !ReplaceOpts.toggle(q, null) && !ReplaceOpts.toggle(null, "aa"));
        t("toggle does not touch mode / protect", q.mode == ReplaceEngine.MODE_TEXT && q.protect == null);

        // old clipboard items keep their behaviour (no keepOpen / header)
        boolean oldOk = true;
        for (ClipPop.Item i : ClipPop.moreItems(true)) if (i.keepOpen || i.header) oldOk = false;
        for (ClipPop.Item i : ClipPop.headerItems(0, false, false, 5, 2)) if (i.keepOpen || i.header) oldOk = false;
        t("clipboard rows: keepOpen / header stay false", oldOk);

        System.out.println("ReplaceOptsTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
