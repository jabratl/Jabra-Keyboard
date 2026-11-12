import com.mgb.keyboard.clip.ClipList;
import com.mgb.keyboard.clip.ClipMeta;
import java.util.List;

public class ClipMetaTest {
    static int ok = 0, bad = 0;
    static void check(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static String texts(List<ClipList.Item> l) { StringBuilder s = new StringBuilder(); for (ClipList.Item i : l) s.append(i.text).append('|'); return s.toString(); }

    public static void main(String[] a) {
        ClipList l = new ClipList();
        l.add("banana"); l.add("Apple"); l.add("cherry");   // newest first: cherry, Apple, banana
        List<ClipList.Item> v = l.view("");
        ClipMeta m = new ClipMeta();
        check("view order", texts(v).equals("cherry|Apple|banana|"));
        check("newest = as given", texts(m.sort(v, 0)).equals("cherry|Apple|banana|"));
        check("oldest", texts(m.sort(v, 1)).equals("banana|Apple|cherry|"));
        check("A-Z case-insens", texts(m.sort(v, 2)).equals("Apple|banana|cherry|"));
        m.used("banana", 100); m.used("banana", 200); m.used("Apple", 300);
        check("most used", texts(m.sort(v, 3)).equals("banana|Apple|cherry|"));
        check("count", m.count("banana") == 2 && m.count("Apple") == 1 && m.count("zzz") == 0);
        check("sort does not change input", texts(v).equals("cherry|Apple|banana|"));
        check("sort null", m.sort(null, 2).isEmpty());
        check("usedAt", m.usedAt("banana") == 200);

        ClipMeta e = new ClipMeta();
        e.made("old", 0 + 1000); e.made("new", 9000000);
        l = new ClipList(); l.add("old"); l.add("new"); l.add("unknown"); l.add("pinme");
        l.togglePin(l.view("").get(0));   // pinme is first in view -> pinned
        List<ClipList.Item> all = l.view("");
        long now = 10000000L;
        check("ttl never", e.expired(all, ClipMeta.ttlMs(0), now).isEmpty());
        List<ClipList.Item> ex = e.expired(all, ClipMeta.ttlMs(1), now);   // 1 hour = 3600000
        check("old expired", ex.size() == 1 && ex.get(0).text.equals("old"));
        check("unknown gets made=now, kept", e.madeAt("unknown") == now);
        check("pinned never expires", !texts(ex).contains("pinme"));
        e.made("pinme", 1);
        check("pinned still safe with old made", e.expired(all, ClipMeta.ttlMs(1), now).size() == 1);
        e.used("old", 9900000L);
        check("recent paste keeps clip alive", e.expired(all, ClipMeta.ttlMs(1), now).isEmpty());
        check("ttl values", ClipMeta.ttlMs(2) == 86400000L && ClipMeta.ttlMs(3) == 604800000L && ClipMeta.ttlMs(9) == 0L && ClipMeta.ttlMs(-1) == 0L);

        String s = m.serialize();
        ClipMeta r = ClipMeta.parse(s);
        check("round trip", r.count("banana") == 2 && r.usedAt("Apple") == 300 && r.size() == m.size());
        check("parse null / empty / junk", ClipMeta.parse(null).size() == 0 && ClipMeta.parse("").size() == 0 && ClipMeta.parse("x\na,b,c,d\n,1,2,3\nk,1,2,-5").size() == 0);
        check("key differs for different text", !ClipMeta.key("a").equals(ClipMeta.key("b")));
        check("bangla key", ClipMeta.key("আমি").length() > 2);

        ClipMeta p = ClipMeta.parse(s);
        l = new ClipList(); l.add("banana");
        check("prune drops missing", p.prune(l.view("")) && p.size() == 1 && p.count("banana") == 2);
        check("prune nothing", !p.prune(l.view("")));

        // v0.10.10 (W4): remake = "copied again" restarts the made time
        ClipMeta rm = new ClipMeta();
        rm.made("123456", 1000);
        rm.remake("123456", 5000);
        check("remake sets made to now", rm.madeAt("123456") == 5000 && rm.size() == 1);
        rm.remake("654321", 7000);
        check("remake creates a missing record", rm.madeAt("654321") == 7000 && rm.size() == 2);
        rm.used("123456", 9000);
        rm.remake("123456", 12000);
        check("remake keeps used / count", rm.usedAt("123456") == 9000 && rm.count("123456") == 1 && rm.madeAt("123456") == 12000);

        System.out.println("ClipMetaTest " + ok + "/" + (ok + bad) + (bad == 0 ? " OK" : " FAILED"));
        if (bad > 0) System.exit(1);
    }
}
