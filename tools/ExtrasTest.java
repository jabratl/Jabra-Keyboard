import com.mgb.keyboard.probhat.ProbhatLayout;
import com.mgb.keyboard.extras.SpaceDrag;
import java.util.HashSet;

/**
 * Run on a computer (v0.0.4 Probhat table + v0.0.5 space-drag logic):
 *   javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/probhat/ProbhatLayout.java app/src/main/java/com/mgb/keyboard/extras/SpaceDrag.java tools/ExtrasTest.java
 *   java -cp out ExtrasTest
 */
public class ExtrasTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String msg) { total++; if (!c) { fail++; System.out.println("FAIL " + msg); } }

    public static void main(String[] a) {
        // every letter key (lower + shift) has an output
        HashSet<String> all = new HashSet<String>();
        for (char c = 'a'; c <= 'z'; c++) {
            String lo = ProbhatLayout.map(c), up = ProbhatLayout.map(Character.toUpperCase(c));
            ok(lo != null && lo.length() > 0, "no output for " + c);
            ok(up != null && up.length() > 0, "no output for shift " + c);
            if (lo != null) all.add(lo);
            if (up != null) all.add(up);
        }
        for (String e : ProbhatLayout.EXTRAS) all.add(e);
        // no key outputs the same thing twice (a typo in the table would show up here)
        int count = 0;
        for (char c = 'a'; c <= 'z'; c++) count += 2;
        ok(all.size() >= count, "duplicate outputs in table: " + all.size() + " distinct of " + count + "+extras");
        // every Bangla consonant is reachable
        String cons = "কখগঘঙচছজঝঞটঠডঢণতথদধনপফবভমযরলশষসহ";
        for (char c : cons.toCharArray()) ok(all.contains(String.valueOf(c)), "consonant not reachable: " + c);
        // ড় ঢ় য় (decomposed with nukta)
        ok(ProbhatLayout.map('p').equals("ড়"), "p should be ড়");
        ok(ProbhatLayout.map('P').equals("ঢ়"), "P should be ঢ়");
        ok(ProbhatLayout.map('W').equals("য়"), "W should be য়");
        // kar signs + hasanta + vowels
        String signs = "াি ীুূৃেৈোৌ্অঋ".replace(" ", "");
        for (char c : signs.toCharArray()) ok(all.contains(String.valueOf(c)), "sign/vowel not reachable: " + c);
        for (String v : new String[]{"আ","ই","ঈ","উ","ঊ","এ","ঐ","ও","ঔ","ং","ঃ","ঁ","ৎ"}) ok(all.contains(v), "missing " + v);
        // a few spot checks
        ok("ক".equals(ProbhatLayout.map('j')), "j=ক");
        ok("া".equals(ProbhatLayout.map('f')), "f=া");
        ok("্".equals(ProbhatLayout.map('g')), "g=্");
        // unknown key -> null (app falls back to normal typing), label never crashes
        ok(ProbhatLayout.map('1') == null, "digit must be null");
        ok(ProbhatLayout.label('1').equals("1"), "label of unknown key");
        ok(ProbhatLayout.label('f').startsWith("◌"), "kar label has dotted circle");
        ok(ProbhatLayout.label('j').equals("ক"), "letter label plain");
        ok(ProbhatLayout.extraLabel("‌").equals("ZWNJ"), "zwnj label");
        ok(ProbhatLayout.extraLabel("ঁ").startsWith("◌"), "chandrabindu label");
        ok(ProbhatLayout.extraLabel("").equals("") && ProbhatLayout.extraLabel(null).equals(""), "empty extra label");

        // ---- SpaceDrag ----
        SpaceDrag d = new SpaceDrag(10f);
        d.down(100f);
        ok(d.move(105f) == 0 && !d.wasDragged(), "small move = tap");
        ok(d.move(111f) == 1 && d.wasDragged(), "1 step right");
        ok(d.move(111f) == 0, "no move no step");
        ok(d.move(141f) == 3, "3 steps right");
        ok(d.move(121f) == -1, "1 step left (121 is 1.9 steps back)");
        ok(d.move(100f) == -3, "3 more steps left");
        d.down(50f);
        ok(!d.wasDragged(), "down resets drag");
        ok(d.move(20f) == -3, "3 steps left");
        SpaceDrag z = new SpaceDrag(0f);       // bad step must not divide by zero
        z.down(0f);
        ok(z.move(5f) == 5, "step clamped to 1");

        System.out.println((total - fail) + "/" + total + " passed");
        if (fail > 0) System.exit(1);
    }
}
