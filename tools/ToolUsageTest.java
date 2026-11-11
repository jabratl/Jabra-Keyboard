import com.mgb.keyboard.typing.ToolUsage;
import java.util.*;

/** v0.5.4 tests. javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/ToolUsageTest.java && java -cp out ToolUsageTest */
public class ToolUsageTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static List<String> L(String... a) { return new ArrayList<String>(Arrays.asList(a)); }

    public static void main(String[] x) {
        String u = "";
        u = ToolUsage.count(u, "copy"); u = ToolUsage.count(u, "copy"); u = ToolUsage.count(u, "paste");
        t("count text", u.equals("copy:2,paste:1"));
        t("unknown id ignored", ToolUsage.count(u, "zzz").equals(u) && ToolUsage.count(u, "fold").equals(u) && ToolUsage.count(null, "zzz").equals(""));
        t("parse junk", ToolUsage.parse("copy:x,,:3,all:-2,all:0,cut:5,cut:9,bad:4").size() == 1 && ToolUsage.parse("cut:5").get("cut") == 5);
        List<String> ids = L("fmt", "clip", "all", "cut", "copy", "paste", "plain");
        t("empty use = same", ToolUsage.reorder(ids, "").equals(ids) && ToolUsage.reorder(ids, null).equals(ids));
        t("first stays, top order", ToolUsage.reorder(ids, "plain:9,copy:5,paste:7,cut:1").equals(L("fmt", "plain", "paste", "copy", "clip", "all", "cut")));
        t("pinned first never moved", ToolUsage.reorder(ids, "fmt:50,copy:2").equals(L("fmt", "copy", "clip", "all", "cut", "paste", "plain")));
        t("tie = user order", ToolUsage.reorder(ids, "cut:3,all:3").equals(L("fmt", "all", "cut", "clip", "copy", "paste", "plain")));
        t("hidden id in use ignored", ToolUsage.reorder(L("fmt", "clip", "all"), "paste:9").equals(L("fmt", "clip", "all")));
        t("fewer than 3 untouched", ToolUsage.reorder(L("fmt", "clip"), "clip:5").equals(L("fmt", "clip")) && ToolUsage.reorder(L(), "a:1").isEmpty());
        t("same elements", new TreeSet<String>(ToolUsage.reorder(ids, "plain:9,copy:5")).equals(new TreeSet<String>(ids)) && ToolUsage.reorder(ids, "plain:9").size() == ids.size());
        t("idempotent", ToolUsage.reorder(ToolUsage.reorder(ids, "plain:9,copy:5"), "plain:9,copy:5").equals(ToolUsage.reorder(ids, "plain:9,copy:5")));
        String h = "copy:1000,paste:3,all:1";
        h = ToolUsage.count(h, "copy");
        t("halve at max", ToolUsage.parse(h).get("copy") == 500 && ToolUsage.parse(h).get("paste") == 1 && !ToolUsage.parse(h).containsKey("all"));
        System.out.println("ToolUsageTest " + ok + "/" + (ok + bad) + (bad == 0 ? " OK" : " FAILED"));
        if (bad != 0) System.exit(1);
    }
}
