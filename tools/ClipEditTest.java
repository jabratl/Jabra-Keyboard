import com.mgb.keyboard.clip.ClipList;
import com.mgb.keyboard.clip.ClipMeta;
import java.util.List;

public class ClipEditTest {
    static int ok = 0, bad = 0;
    static void check(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static String texts(List<ClipList.Item> l) { StringBuilder s = new StringBuilder(); for (ClipList.Item i : l) s.append(i.text).append('|'); return s.toString(); }

    public static void main(String[] a) {
        ClipList l = new ClipList();
        l.add("one"); l.add("two"); l.add("three");   // view: three, two, one
        List<ClipList.Item> v = l.view("");
        ClipList.Item two = v.get(1);
        check("replace ok", l.replace(two, "TWO edited") == 0);
        check("same position", texts(l.view("")).equals("three|TWO edited|one|"));
        check("old object gone", l.replace(two, "x") == 4);
        ClipList.Item three = l.view("").get(0);
        l.togglePin(three);
        List<ClipList.Item> v2 = l.view("");   // pinned first
        ClipList.Item pinned = v2.get(0);
        check("pinned item", pinned.pinned && pinned.text.equals("three"));
        check("edit pinned keeps pin", l.replace(pinned, "three!") == 0 && l.view("").get(0).pinned && l.view("").get(0).text.equals("three!"));
        ClipList.Item one = l.view("").get(2);
        check("duplicate refused", l.replace(one, "TWO edited") == 3);
        check("unchanged ok", l.replace(one, "one") == 0);
        check("empty refused", l.replace(one, "   ") == 1 && l.replace(one, null) == 1);
        StringBuilder sb = new StringBuilder(); for (int i = 0; i < ClipList.MAX_LEN + 1; i++) sb.append('x');
        check("too long refused", l.replace(one, sb.toString()) == 2);
        check("max length ok", l.replace(one, sb.substring(1)) == 0);
        check("bangla + newline", l.replace(l.view("").get(1), "আমি\nতুমি") == 0 && l.view("").get(1).text.equals("আমি\nতুমি"));
        check("serialize round trip", ClipList.parse(l.serialize()).view("").size() == 3 && texts(ClipList.parse(l.serialize()).view("")).equals(texts(l.view(""))));
        check("size unchanged", l.size() == 3);

        ClipMeta m = new ClipMeta();
        m.used("a", 5); m.used("a", 6);
        m.rename("a", "b");
        check("meta renamed", m.count("b") == 2 && m.count("a") == 0 && m.usedAt("b") == 6);
        m.rename("zzz", "q");
        check("rename unknown = nothing", m.count("q") == 0 && m.size() == 1);
        m.rename("b", "b");
        check("rename same", m.count("b") == 2);

        System.out.println("ClipEditTest " + ok + "/" + (ok + bad) + (bad == 0 ? " OK" : " FAILED"));
        if (bad > 0) System.exit(1);
    }
}
