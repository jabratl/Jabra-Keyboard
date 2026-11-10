import com.mgb.keyboard.typing.PcState;

/** v0.1.2 logic tests (computer only).  java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/PcTest.java ; java -cp out PcTest */
public class PcTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    public static void main(String[] a) {
        PcState s = new PcState();
        t("starts in phone mode", !s.isPc());
        t("toggle -> PC", s.toggleMode() && s.isPc());
        t("toggle -> phone", !s.toggleMode() && !s.isPc());
        t("meta empty", s.meta() == 0);
        s.tapCtrl(1000);
        t("ctrl once", s.ctrl() == PcState.ONCE && s.meta() == PcState.META_CTRL);
        s.keySent();
        t("ctrl once ends after key", s.ctrl() == PcState.OFF && s.meta() == 0);
        s.tapCtrl(2000); s.tapCtrl(2300);
        t("ctrl lock (2nd tap within 400ms)", s.ctrl() == PcState.LOCK);
        s.keySent();
        t("ctrl lock stays after key", s.ctrl() == PcState.LOCK);
        s.tapCtrl(5000);
        t("tap on lock = off", s.ctrl() == PcState.OFF);
        s.tapCtrl(6000); s.tapCtrl(6500);
        t("slow 2nd tap = off (not lock)", s.ctrl() == PcState.OFF);
        s.tapCtrl(7000); s.tapCtrl(6900);
        t("clock went back = off", s.ctrl() == PcState.OFF);
        s.tapAlt(8000);
        t("alt once", s.alt() == PcState.ONCE && s.meta() == PcState.META_ALT);
        s.tapCtrl(8100);
        t("ctrl + alt together", s.meta() == (PcState.META_CTRL | PcState.META_ALT));
        s.keySent();
        t("both once end", s.meta() == 0);
        s.tapAlt(9000); s.tapAlt(9200);
        t("alt lock", s.alt() == PcState.LOCK);
        s.setPc(true); s.setPc(false);
        t("leaving PC clears lock", s.alt() == PcState.OFF && s.ctrl() == PcState.OFF);
        s.setPc(true); s.tapCtrl(1); s.tapCtrl(2);
        t("lock inside PC", s.ctrl() == PcState.LOCK);
        s.toggleMode();
        t("toggle out clears", s.ctrl() == PcState.OFF && !s.isPc());
        t("meta values match Android", PcState.META_CTRL == 0x1000 && PcState.META_ALT == 0x02);
        System.out.println("PcTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
