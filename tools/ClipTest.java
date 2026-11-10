import com.mgb.keyboard.clip.ClipList;
import java.util.List;

/** javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/clip/ClipList.java tools/ClipTest.java && java -cp out ClipTest */
public class ClipTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL " + m); } }
    public static void main(String[] a) {
        ClipList c = new ClipList();
        ok(!c.add(null) && !c.add("") && !c.add("   "), "blank ignored");
        ok(c.add("one") && c.add("দুই") && c.add("three"), "add");
        ok(c.view("").get(0).text.equals("three"), "newest first");
        ok(c.add("one") && c.view("").get(0).text.equals("one") && c.size() == 3, "duplicate moves to front");
        ok(!c.add("one"), "same on top = no change");
        ok(c.view("DU").size() == 0 && c.view("দুই").size() == 1 && c.view("ONE").size() == 1, "search case-insens");
        // pin goes first and survives clear
        List<ClipList.Item> v = c.view("");
        ClipList.Item two = null; for (ClipList.Item i : v) if (i.text.equals("দুই")) two = i;
        ok(c.togglePin(two) && c.view("").get(0).text.equals("দুই"), "pinned first");
        ok(c.clearUnpinned() == 2 && c.size() == 1 && c.view("").get(0).pinned, "clear keeps pinned");
        // limits
        ClipList d = new ClipList();
        for (int i = 0; i < 100; i++) d.add("item" + i);
        ok(d.size() == ClipList.MAX_UNPINNED, "max unpinned " + d.size());
        ok(d.view("").get(0).text.equals("item99"), "oldest dropped");
        ok(!d.add(new String(new char[ClipList.MAX_LEN + 1]).replace('\0', 'x')), "too long ignored");
        ClipList.Item p = d.view("").get(0); d.togglePin(p);
        for (int i = 0; i < 100; i++) d.add("more" + i);
        ok(d.view("").get(0).text.equals("item99") && d.view("").get(0).pinned, "pinned survives overflow");
        ok(d.remove(d.view("").get(0)) && d.size() == ClipList.MAX_UNPINNED, "remove");
        // pin limit
        ClipList e = new ClipList();
        for (int i = 0; i < ClipList.MAX_PINNED + 1; i++) e.add("p" + i);
        int pinned = 0; for (ClipList.Item i : e.view("")) if (e.togglePin(i)) pinned++;
        ok(pinned == ClipList.MAX_PINNED, "pin limit " + pinned);
        // save / load round trip with newline, comma, Bangla, emoji
        ClipList f = new ClipList();
        f.add("line1\nline2, with comma"); f.add("বাংলা 😀"); f.add("x");
        f.togglePin(f.view("").get(1));
        ClipList g = ClipList.parse(f.serialize());
        ok(g.size() == 3 && g.serialize().equals(f.serialize()), "round trip");
        boolean found = false; for (ClipList.Item i : g.view("")) if (i.text.equals("line1\nline2, with comma")) found = true;
        ok(found, "multiline text survives");
        // garbage input never crashes
        ok(ClipList.parse("garbage\n\n9,%ZZ\n1,abc").size() == 1, "garbage tolerated");
        ok(ClipList.parse(null).size() == 0, "null");
        System.out.println((total - fail) + "/" + total + " passed");
        if (fail > 0) System.exit(1);
    }
}
