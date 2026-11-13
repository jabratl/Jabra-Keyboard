import com.mgb.keyboard.typing.ProbhatHints;
import com.mgb.keyboard.probhat.ProbhatLayout;

/** javac -encoding UTF-8 -d out $(find app -name '*.java') tools/ProbhatHintsTest.java && java -Dfile.encoding=UTF-8 -cp out ProbhatHintsTest */
public class ProbhatHintsTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL " + m); } }

    public static void main(String[] a) {
        // every letter key has a hint = its shift output (same as the Ridmik screenshot: q=ধ w=ঊ e=ঈ r=ড় t=ঠ ... a=অ s=ষ d=ঢ f=থ g=ঘ h=ঃ j=ঝ k=খ l=ং)
        String[][] ridmik = { {"q","\u09A7"},{"w","\u098A"},{"e","\u0988"},{"r","\u09A1\u09BC"},{"t","\u09A0"},{"y","\u0990"},{"u","\u0989"},{"i","\u0987"},{"o","\u0994"},{"p","\u09AB"},
            {"a","\u0985"},{"s","\u09B7"},{"d","\u09A2"},{"f","\u09A5"},{"g","\u0998"},{"h","\u0983"},{"j","\u099D"},{"k","\u0996"},{"l","\u0982"} };
        for (String[] r : ridmik) ok(r[1].equals(ProbhatHints.hintFor(r[0].charAt(0))), "hint " + r[0]);
        for (char c = 'a'; c <= 'z'; c++) {
            String h = ProbhatHints.hintFor(c);
            ok(h != null && !h.isEmpty(), "hint exists " + c);
            ok(h.equals(ProbhatLayout.map(Character.toUpperCase(c))), "hint = shift output " + c);
            ok(h.equals(ProbhatHints.hintFor(Character.toUpperCase(c))), "case ignored " + c);
        }
        ok(ProbhatHints.hintFor('1') == null && ProbhatHints.hintFor(' ') == null && ProbhatHints.hintFor('\u09A6') == null, "non letters = none");
        ok(ProbhatHints.active(true, true, true, false), "active");
        ok(!ProbhatHints.active(false, true, true, false), "original design = off");
        ok(!ProbhatHints.active(true, false, true, false), "English = off");
        ok(!ProbhatHints.active(true, true, false, false), "phonetic = off");
        ok(!ProbhatHints.active(true, true, true, true), "latin-only field = off");
        System.out.println((fail == 0 ? "OK " : "FAILED ") + total + " checks");
        if (fail > 0) System.exit(1);
    }
}
