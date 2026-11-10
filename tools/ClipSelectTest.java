import com.mgb.keyboard.clip.ClipList;
import com.mgb.keyboard.typing.ClipSelect;
import java.util.List;

/** javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/clip/ClipList.java app/src/main/java/com/mgb/keyboard/typing/ClipSelect.java tools/ClipSelectTest.java && java -cp out ClipSelectTest */
public class ClipSelectTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL " + m); } }
    public static void main(String[] a) {
        ClipList c = new ClipList();
        for (String s : new String[]{"a", "b", "c", "d", "e"}) c.add(s);
        List<ClipList.Item> all = c.view("");
        ClipSelect s = new ClipSelect();
        ok(!s.isOn() && s.count() == 0, "starts off");
        ok(!s.toggle(all.get(0)) && s.count() == 0, "toggle ignored when off");
        s.enter();
        ok(s.isOn() && s.count() == 0, "enter = 0 selected (Manage)");
        ok(s.toggle(all.get(0)) && s.isSelected(all.get(0)) && s.count() == 1, "toggle on");
        ok(!s.toggle(all.get(0)) && s.count() == 0, "toggle off");
        s.leave();
        s.enterWith(all.get(1));
        ok(s.isOn() && s.count() == 1 && s.isSelected(all.get(1)), "long-press = on + 1 selected");
        s.selectAll(all);
        ok(s.count() == 5, "select all no duplicates " + s.count());
        s.clear();
        ok(s.isOn() && s.count() == 0, "clear keeps mode");
        s.leave();
        ok(!s.isOn() && s.count() == 0, "leave resets");
        s.selectAll(all);
        ok(s.count() == 0, "selectAll ignored when off");
        // pin / unpin through the engine
        s.enter(); s.toggle(all.get(0)); s.toggle(all.get(2));
        ok(c.setPinned(s.selection(), true) == 2 && c.pinnedCount() == 2 && s.pinnedCount() == 2, "pin selected");
        ok(c.setPinned(s.selection(), true) == 0, "pin again = 0 changed");
        ok(c.setPinned(s.selection(), false) == 2 && c.pinnedCount() == 0, "unpin selected");
        // delete + prune
        s.toggle(all.get(4));
        ok(s.count() == 3, "3 selected");
        ok(c.removeAll(s.selection()) == 3 && c.size() == 2, "delete selected");
        s.prune(c.view(""));
        ok(s.count() == 0, "prune drops deleted");
        s.toggle(c.view("").get(0));
        s.prune(c.view(""));
        ok(s.count() == 1, "prune keeps existing");
        List<ClipList.Item> copy = s.selection(); copy.clear();
        ok(s.count() == 1, "selection() is a copy");
        // pin limit: 30
        ClipList d = new ClipList();
        for (int i = 0; i < 35; i++) d.add("x" + i);
        ClipSelect t = new ClipSelect(); t.enter(); t.selectAll(d.view(""));
        ok(t.count() == 35 && d.setPinned(t.selection(), true) == ClipList.MAX_PINNED && d.pinnedCount() == ClipList.MAX_PINNED, "pin stops at limit");
        t.prune(null);
        ok(t.count() == 0, "prune(null) clears");
        System.out.println(total - fail + "/" + total + " passed");
        if (fail > 0) System.exit(1);
    }
}
