import com.mgb.keyboard.typing.BnDesign;
import com.mgb.keyboard.probhat.ProbhatLayout;

/** javac -encoding UTF-8 -d out $(find app -name '*.java') tools/BnDesignTest.java && java -Dfile.encoding=UTF-8 -cp out BnDesignTest */
public class BnDesignTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL " + m); } }

    public static void main(String[] a) {
        ok(BnDesign.ridmik(0), "0 = ridmik (default)");
        ok(!BnDesign.ridmik(1), "1 = original");
        ok(BnDesign.ridmik(-3) && BnDesign.ridmik(7), "bad value = ridmik");
        ok(BnDesign.sanitize(1) == 1 && BnDesign.sanitize(0) == 0 && BnDesign.sanitize(99) == 0, "sanitize");
        ok(BnDesign.NAMES.length == 2 && BnDesign.NAMES[1].equals("Keyboard's original design"), "names: last = original design");
        // dotted circle: original keeps it, ridmik look has none on every letter key, normal + shift
        for (char c = 'a'; c <= 'z'; c++) {
            for (int s = 0; s < 2; s++) {
                char k = s == 0 ? c : Character.toUpperCase(c);
                String bare = ProbhatLayout.labelBare(k), old = ProbhatLayout.label(k);
                ok(bare != null && !bare.isEmpty(), "bare label " + k);
                ok(bare.indexOf('\u25CC') < 0, "no dotted circle " + k);
                ok(old.replace("\u25CC", "").equals(bare.replace("\u200C", "")), "same sign as original " + k);
            }
        }
        String[][][] groups = { ProbhatLayout.EXTRA_ROW1, ProbhatLayout.EXTRA_ROW2, ProbhatLayout.EXTRA_ROW3 };
        for (String[][] g : groups) for (String[] pair : g) for (String s : pair) {
            String bare = ProbhatLayout.extraLabelBare(s), old = ProbhatLayout.extraLabel(s);
            ok(!bare.isEmpty() && bare.indexOf('\u25CC') < 0, "extra bare " + s.length());
            ok(old.equals("ZWNJ") ? bare.equals("ZWNJ") : old.replace("\u25CC", "").equals(bare.replace("\u200C", "")), "extra same sign");
        }
        ok(ProbhatLayout.labelBare('?').equals("?"), "unknown key unchanged");
        ok(ProbhatLayout.extraLabelBare(null).isEmpty() && ProbhatLayout.extraLabelBare("").isEmpty(), "null / empty extra");
        System.out.println((fail == 0 ? "OK " : "FAILED ") + total + " checks");
        if (fail > 0) System.exit(1);
    }
}
