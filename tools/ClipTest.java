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
        // ---- v0.2.5.1: new methods ----
        ClipList h = new ClipList();
        for (int i = 0; i < 5; i++) h.add("n" + i);
        ok(h.viewPinned("").size() == 0 && h.viewRecent("").size() == 5 && h.pinnedCount() == 0, "v1 all recent at start");
        List<ClipList.Item> two2 = new java.util.ArrayList<ClipList.Item>(h.viewRecent("").subList(0, 2));
        ok(h.setPinned(two2, true) == 2 && h.pinnedCount() == 2, "setPinned pins 2");
        ok(h.setPinned(two2, true) == 0, "pin again = 0 changed");
        ok(h.viewPinned("").size() == 2 && h.viewRecent("").size() == 3, "pinned / recent split");
        ok(h.viewPinned("").get(0).text.equals("n4") && h.viewRecent("").get(0).text.equals("n2"), "each group newest first");
        ok(h.viewPinned("N3").size() == 1 && h.viewRecent("N3").size() == 0, "search works in both groups");
        ok(h.view("").size() == 5 && h.view("").get(0).pinned, "old view() unchanged");
        ok(h.setPinned(two2, false) == 2 && h.pinnedCount() == 0, "setPinned unpin");
        ok(h.setPinned(null, true) == 0 && h.setPinned(new java.util.ArrayList<ClipList.Item>(), true) == 0, "setPinned null / empty");
        ClipList k = new ClipList();
        for (int i = 0; i < ClipList.MAX_PINNED + 10; i++) k.add("q" + i);
        ok(k.setPinned(k.viewRecent(""), true) == ClipList.MAX_PINNED && k.pinnedCount() == ClipList.MAX_PINNED, "setPinned stops at 30");
        ok(k.setPinned(k.viewRecent(""), true) == 0, "full: nothing more pinned");
        List<ClipList.Item> pin3 = new java.util.ArrayList<ClipList.Item>(k.viewPinned("").subList(0, 3));
        ok(k.setPinned(pin3, false) == 3 && k.setPinned(k.viewRecent("").subList(0, 5), true) == 3, "unpin 3 then only 3 of 5 fit");
        ClipList m = new ClipList();
        for (int i = 0; i < 6; i++) m.add("r" + i);
        List<ClipList.Item> del = new java.util.ArrayList<ClipList.Item>(m.view("").subList(0, 4));
        ok(m.removeAll(del) == 4 && m.size() == 2, "removeAll 4");
        ok(m.removeAll(del) == 0 && m.size() == 2, "removeAll again = 0");
        ok(m.removeAll(null) == 0, "removeAll null");
        ok(ClipList.check(null) == 1 && ClipList.check("") == 1 && ClipList.check(" \n ") == 1, "check empty");
        ok(ClipList.check("ok") == 0 && ClipList.check("বাংলা") == 0, "check ok");
        ok(ClipList.check(new String(new char[ClipList.MAX_LEN]).replace('\0', 'x')) == 0, "check exactly 2000 ok");
        ok(ClipList.check(new String(new char[ClipList.MAX_LEN + 1]).replace('\0', 'x')) == 2, "check 2001 too long");
        ClipList n2 = new ClipList();
        String longClip = new String(new char[ClipList.MAX_LEN]).replace('\0', 'y');
        ok(n2.add(longClip) && n2.size() == 1, "add of an Add-button clip works");
        ok(ClipList.parse(n2.serialize()).size() == 1, "format unchanged: 2000-char clip round trip");
        // ---- v0.16.15 (E3): copied long text ----
        ClipList q1 = new ClipList();
        String l2001 = new String(new char[ClipList.MAX_LEN + 1]).replace('\0', 'x');
        ok(!q1.add(l2001) && q1.size() == 0, "E3: add() still refuses 2001");
        ok(q1.addCaptured(l2001) && q1.size() == 1, "E3: addCaptured keeps 2001");
        ok(ClipList.check(l2001) == 2, "E3: check() still says too long for the Add button");
        ok(q1.addCaptured(new String(new char[ClipList.MAX_CAPTURE]).replace('\0', 'y')), "E3: exactly MAX_CAPTURE ok");
        ok(!q1.addCaptured(new String(new char[ClipList.MAX_CAPTURE + 1]).replace('\0', 'z')), "E3: MAX_CAPTURE+1 refused");
        ok(!q1.addCaptured(null) && !q1.addCaptured("  \n ") && !q1.addCaptured(""), "E3: blank / null refused");
        ClipList q2 = new ClipList();
        q2.addCaptured("a"); q2.addCaptured("b"); ok(q2.addCaptured("a") && q2.view("").get(0).text.equals("a") && q2.size() == 2, "E3: duplicate moves to front");
        ok(!q2.addCaptured("a"), "E3: same on top = no change");
        ClipList q3 = new ClipList();
        String big = new String(new char[15000]).replace('\0', 'k') + "\nবাংলা, 😀";
        q3.addCaptured(big);
        ClipList q3b = ClipList.parse(q3.serialize());
        ok(q3b.size() == 1 && q3b.view("").get(0).text.equals(big), "E3: 15000-char clip survives save / load");
        ok(ClipList.parse("0," + new String(new char[ClipList.MAX_CAPTURE + 1]).replace('\0', 'x')).size() == 0, "E3: parse skips > MAX_CAPTURE");
        ok(ClipList.parse("0," + new String(new char[ClipList.MAX_CAPTURE]).replace('\0', 'x')).size() == 1, "E3: parse keeps MAX_CAPTURE");
        ClipList q4 = new ClipList();
        String last = null;
        for (int i = 0; i < 12; i++) { last = "n" + i + new String(new char[19990]).replace('\0', 'w'); q4.addCaptured(last); }
        int tot = 0; for (ClipList.Item i : q4.view("")) tot += i.text.length();
        ok(tot <= ClipList.MAX_UNPINNED_CHARS && q4.size() == 7, "E3: char budget keeps 7 big clips, got " + q4.size());
        ok(q4.view("").get(0).text.equals(last), "E3: newest big clip is kept");
        ClipList q5 = new ClipList();
        String keep = "KEEP" + new String(new char[19990]).replace('\0', 'p');
        q5.addCaptured(keep); q5.togglePin(q5.view("").get(0));
        for (int i = 0; i < 12; i++) q5.addCaptured("m" + i + new String(new char[19990]).replace('\0', 'w'));
        boolean kept = false; for (ClipList.Item i : q5.view("")) if (i.pinned && i.text.equals(keep)) kept = true;
        ok(kept, "E3: pinned long clip is never dropped by the budget");
        ClipList q6 = new ClipList();
        for (int i = 0; i < 100; i++) q6.addCaptured("short" + i);
        ok(q6.size() == ClipList.MAX_UNPINNED && q6.view("").get(0).text.equals("short99"), "E3: count limit still applies");
        ClipList.Item big1 = q1.view("").get(0);
        ok(q1.replace(big1, big1.text) == 0 || q1.replace(big1, big1.text) == 2, "E3: replace never throws on a long clip");
        System.out.println((total - fail) + "/" + total + " passed");
        if (fail > 0) System.exit(1);
    }
}
