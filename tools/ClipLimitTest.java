import com.mgb.keyboard.clip.ClipList;

/** v0.49.0: the clipboard limits can be set from Settings (ClipList.configure). Pure Java, no Android. */
public class ClipLimitTest {
    static int total = 0, fail = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL: " + m); } }
    static String rep(char c, int n) { return new String(new char[n]).replace('\0', c); }

    public static void main(String[] a) {
        // defaults
        ok(ClipList.maxUnpinned() == ClipList.MAX_UNPINNED && ClipList.maxCapture() == ClipList.MAX_CAPTURE && ClipList.maxUnpinnedChars() == ClipList.MAX_UNPINNED_CHARS, "defaults before configure");
        ok(ClipList.MAX_UNPINNED == 100 && ClipList.MAX_PINNED == 50 && ClipList.MAX_LEN == 20000 && ClipList.MAX_CAPTURE == 100000 && ClipList.MAX_UNPINNED_CHARS == 300000, "new default numbers");

        // clamping
        ClipList.configure(-5, -5);
        ok(ClipList.maxUnpinned() == 10 && ClipList.maxCapture() == 5000, "low values clamp to 10 / 5000");
        ClipList.configure(100000, 100000000);
        ok(ClipList.maxUnpinned() == ClipList.UNPINNED_CEIL && ClipList.maxCapture() == ClipList.CAPTURE_CEIL, "high values clamp to the ceilings");
        ok(ClipList.maxUnpinnedChars() == ClipList.CHARS_CEIL, "char budget never above CHARS_CEIL");
        ClipList.configure(100, 100000);
        ok(ClipList.maxUnpinnedChars() == 300000, "100k capture = 300k budget");
        ClipList.configure(100, 5000);
        ok(ClipList.maxUnpinnedChars() == ClipList.MAX_UNPINNED_CHARS, "budget never below the default");

        // count limit follows the setting
        ClipList.configure(10, 100000);
        ClipList l = new ClipList();
        for (int i = 0; i < 25; i++) l.addCaptured("c" + i);
        ok(l.size() == 10 && l.view("").get(0).text.equals("c24"), "keeps only 10 when set to 10");
        ClipList.configure(150, 100000);
        for (int i = 0; i < 200; i++) l.addCaptured("d" + i);
        ok(l.size() == 150 && l.view("").get(0).text.equals("d199"), "keeps 150 when set to 150");
        // add() (Add button) follows the count setting too
        ClipList.configure(20, 100000);
        ClipList b = new ClipList();
        for (int i = 0; i < 40; i++) b.add("b" + i);
        ok(b.size() == 20, "add() keeps 20 when set to 20");

        // lowering never deletes by itself, only the next add trims
        ClipList.configure(100, 100000);
        ClipList k = new ClipList();
        for (int i = 0; i < 80; i++) k.addCaptured("k" + i);
        ClipList.configure(30, 100000);
        ok(k.size() == 80, "lowering the limit alone deletes nothing");
        k.addCaptured("new");
        ok(k.size() == 30 && k.view("").get(0).text.equals("new"), "next add trims to the new limit");

        // pinned are never trimmed
        ClipList.configure(10, 100000);
        ClipList p = new ClipList();
        p.addCaptured("PIN"); p.togglePin(p.view("").get(0));
        for (int i = 0; i < 30; i++) p.addCaptured("z" + i);
        ok(p.size() == 11 && p.pinnedCount() == 1 && p.view("").get(0).text.equals("PIN"), "pinned clip survives, 10 recent kept");

        // capture limit follows the setting
        ClipList.configure(100, 30000);
        ClipList c = new ClipList();
        ok(c.addCaptured(rep('a', 30000)) && !c.addCaptured(rep('b', 30001)), "capture limit 30000 exactly");
        ClipList.configure(100, ClipList.CAPTURE_CEIL);
        ok(c.addCaptured(rep('c', 200000)) && !c.addCaptured(rep('d', 200001)), "capture limit at the ceiling 200000");

        // lowering the capture limit never drops saved clips on load
        ClipList.configure(100, 5000);
        ClipList big = new ClipList();
        ClipList.configure(100, 100000);
        big.addCaptured(rep('e', 60000));
        String saved = big.serialize();
        ClipList.configure(100, 5000);
        ok(ClipList.parse(saved).size() == 1, "parse keeps a 60000 clip even when the setting is 5000");
        ok(ClipList.parse("0," + rep('x', ClipList.CAPTURE_CEIL)).size() == 1, "parse keeps exactly CAPTURE_CEIL");

        // char budget: 5000-clips with a big count: budget 300000 / 5001 = 59
        ClipList.configure(300, 5000);
        ClipList t = new ClipList();
        String last = null;
        for (int i = 0; i < 100; i++) { last = "t" + i + rep('w', 4990); t.addCaptured(last); }
        int tot = 0; for (ClipList.Item i : t.view("")) tot += i.text.length();
        ok(tot <= ClipList.MAX_UNPINNED_CHARS && t.size() >= 59 && t.size() <= 60, "budget bounds the total, kept " + t.size());
        ok(t.view("").get(0).text.equals(last), "newest kept by the budget");

        // Add / Edit button limit is MAX_LEN = 20000
        ok(ClipList.check(rep('x', 20000)) == 0 && ClipList.check(rep('x', 20001)) == 2, "check() 20000 ok / 20001 too long");

        // serialize / parse round trip with a Bangla + emoji clip over the old 20000 limit
        ClipList.configure(100, 100000);
        ClipList r = new ClipList();
        String bn = rep('ক', 40000) + "\nবাংলা 😀";
        ok(r.addCaptured(bn), "40000-char Bangla clip accepted");
        ClipList r2 = ClipList.parse(r.serialize());
        ok(r2.size() == 1 && r2.view("").get(0).text.equals(bn), "40000-char Bangla clip survives save / load");

        System.out.println((total - fail) + "/" + total + " passed");
        if (fail > 0) System.exit(1);
    }
}
