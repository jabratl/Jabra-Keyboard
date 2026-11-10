import com.mgb.keyboard.typing.PadState;

/** v0.2.3 select pad state test (computer only).  javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/PadTest.java && java -cp out PadTest */
public class PadTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }

    public static void main(String[] a) {
        PadState s = new PadState();
        t("starts OFF", !s.isSelect());
        t("meta 0 when OFF", s.meta() == 0);
        t("toggle returns ON", s.toggle() && s.isSelect());
        t("meta shift when ON", s.meta() == 1 && s.meta() == PadState.META_SHIFT);
        t("toggle returns OFF", !s.toggle() && !s.isSelect());
        t("meta 0 again", s.meta() == 0);
        s.setSelect(true);
        t("setSelect ON", s.isSelect() && s.meta() == 1);
        s.setSelect(false);
        t("setSelect OFF", !s.isSelect() && s.meta() == 0);
        s.toggle(); s.toggle(); s.toggle();
        t("odd toggles = ON", s.isSelect());
        System.out.println("PadTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
