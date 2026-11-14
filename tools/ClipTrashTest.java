import com.mgb.keyboard.clip.ClipTrash;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** v0.18.4 tests for the Recently deleted list of the clipboard (computer only). */
public class ClipTrashTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static List<String> L(String... a) { return new ArrayList<String>(Arrays.asList(a)); }
    static List<Boolean> B(Boolean... a) { return new ArrayList<Boolean>(Arrays.asList(a)); }

    public static void main(String[] args) {
        t("sanitize", ClipTrash.sanitize(30) == 30 && ClipTrash.sanitize(0) == 0 && ClipTrash.sanitize(100) == 100 && ClipTrash.sanitize(-1) == 30 && ClipTrash.sanitize(101) == 30);
        t("default is 30", ClipTrash.DEFAULT_LIMIT == 30);

        ClipTrash c = new ClipTrash();
        t("starts empty", c.size() == 0 && c.isEmpty() && c.newestGroup().isEmpty() && c.popNewestGroup().isEmpty());
        t("push 2", c.push(L("a", "b"), B(false, true), L(null, "<b>b</b>"), 1000L, 30) == 2 && c.size() == 2);
        t("order kept inside a group", c.view().get(0).text.equals("a") && c.view().get(1).text.equals("b"));
        t("pinned + html kept", !c.view().get(0).pinned && c.view().get(1).pinned && c.view().get(1).html.equals("<b>b</b>") && c.view().get(0).html == null);
        t("push 1 more = newest group", c.push(L("c"), B(false), null, 2000L, 30) == 1 && c.size() == 3 && c.view().get(0).text.equals("c"));
        t("newestGroup = last action only", c.newestGroup().size() == 1 && c.newestGroup().get(0).text.equals("c"));
        List<ClipTrash.Entry> back = c.popNewestGroup();
        t("pop gives c and removes it", back.size() == 1 && back.get(0).text.equals("c") && c.size() == 2);
        t("next undo = the 2 clips", c.popNewestGroup().size() == 2 && c.isEmpty());

        c = new ClipTrash();
        t("same text twice = one entry (newest group)", c.push(L("x"), null, null, 1L, 30) == 1 && c.push(L("y", "x"), null, null, 2L, 30) == 2 && c.size() == 2);
        t("duplicate inside one push", new ClipTrash().push(L("q", "q"), null, null, 1L, 30) == 1);
        t("empty / null skipped", new ClipTrash().push(L("", "  ", null), null, null, 1L, 30) == 0 && new ClipTrash().push(null, null, null, 1L, 30) == 0);

        c = new ClipTrash();
        for (int i = 0; i < 35; i++) c.push(L("t" + i), null, null, i + 1L, 30);
        t("limit 30 keeps the newest 30", c.size() == 30 && c.view().get(0).text.equals("t34") && c.view().get(29).text.equals("t5"));
        t("lower limit trims", c.trim(10) == 20 && c.size() == 10 && c.view().get(0).text.equals("t34"));
        t("limit 0 empties", c.trim(0) == 10 && c.isEmpty());
        c.push(L("z"), null, null, 1L, 0);
        t("push with limit 0 stores nothing", c.isEmpty());
        t("one big group over the limit keeps newest part", new ClipTrash().push(L("1", "2", "3", "4"), null, null, 1L, 2) == 2);

        c = new ClipTrash();
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 90000; i++) big.append('x');
        String b1 = big.toString() + "1", b2 = big.toString() + "2", b3 = big.toString() + "3";
        c.push(L(b1), null, null, 1L, 30); c.push(L(b2), null, null, 2L, 30); c.push(L(b3), null, null, 3L, 30);
        t("char budget drops the oldest", c.size() == 2 && c.view().get(0).text.equals(b3) && c.view().get(1).text.equals(b2));
        ClipTrash one = new ClipTrash();
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 250000; i++) huge.append('y');
        t("a single huge clip is never dropped", one.push(L(huge.toString()), null, null, 1L, 30) == 1 && one.size() == 1);

        c = new ClipTrash();
        c.push(L("old"), null, null, 1000L, 30); c.push(L("new"), null, null, 9000L, 30); c.push(L("nodate"), null, null, 0L, 30);
        t("expire: ttl 0 = nothing", c.expire(10000L, 0L) == 0 && c.size() == 3);
        t("expire removes only old dated entries", c.expire(10000L, 5000L) == 1 && c.size() == 2 && c.view().get(1).text.equals("new"));

        c = new ClipTrash();
        c.push(L("a,b", "line1\nline2 \u0995\u09BE\u09B2", "p%q+r"), B(true, false, true), L("<i>x,y</i>", null, ""), 1234567890123L, 30);
        c.push(L("second"), null, null, 5L, 30);
        String s = c.serialize();
        ClipTrash d = ClipTrash.parse(s);
        t("round trip size", d.size() == c.size());
        boolean same = true;
        for (int i = 0; i < c.size(); i++) {
            ClipTrash.Entry x = c.view().get(i), y = d.view().get(i);
            if (!x.text.equals(y.text) || x.pinned != y.pinned || x.at != y.at || x.group != y.group || (x.html == null) != (y.html == null) || (x.html != null && !x.html.equals(y.html))) same = false;
        }
        t("round trip content (commas, newlines, Bangla, html)", same);
        t("newest group survives a restart", d.newestGroup().size() == 1 && d.newestGroup().get(0).text.equals("second"));
        d.push(L("third"), null, null, 6L, 30);
        t("group numbers keep rising after parse", d.view().get(0).group > c.view().get(0).group);

        t("parse junk never throws", ClipTrash.parse("junk\n1,2\n,,,,\nx,0,1,a,~\n1,0,zz,a,~\n3,1,5,%ZZ,~").size() == 0 && ClipTrash.parse(null).size() == 0 && ClipTrash.parse("").size() == 0);
        t("parse skips only the bad line", ClipTrash.parse("junk\n7,1,5,ok,~").size() == 1);

        c = new ClipTrash();
        c.push(L("a", "b", "c"), null, null, 1L, 30);
        List<ClipTrash.Entry> v = c.view();
        t("remove one", c.remove(v.get(1)) && c.size() == 2 && !c.remove(v.get(1)) && !c.remove(null));
        t("removeAll", c.removeAll(Arrays.asList(v.get(0), v.get(2))) == 2 && c.isEmpty() && c.removeAll(null) == 0);
        c.push(L("a"), null, null, 1L, 30); c.clear();
        t("clear", c.isEmpty());
        t("quick list has off + 30", ClipTrash.QUICK[0] == 0 && Arrays.binarySearch(ClipTrash.QUICK, 30) >= 0);

        System.out.println("ClipTrashTest: " + ok + " PASS / " + bad + " FAIL");
        if (bad > 0) System.exit(1);
    }
}
