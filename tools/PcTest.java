import com.mgb.keyboard.typing.PcState;
import com.mgb.keyboard.typing.PcKeyPlan;

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
        // v0.17.49 (PC1): Shift + Win, real meta, mods mask, idle expiry
        PcState p = new PcState();
        p.tapShift(100);
        t("shift once", p.shift() == PcState.ONCE && p.meta() == PcState.META_SHIFT && p.mods() == PcKeyPlan.MOD_SHIFT);
        p.tapShift(300);
        t("shift lock (2nd tap within 400ms)", p.shift() == PcState.LOCK);
        p.keySent();
        t("shift lock stays", p.shift() == PcState.LOCK);
        p.tapShift(9000);
        t("shift off", p.shift() == PcState.OFF && p.mods() == 0);
        p.tapWin(100);
        t("win once", p.win() == PcState.ONCE && p.meta() == PcState.META_WIN && p.mods() == PcKeyPlan.MOD_META);
        p.keySent();
        t("win once ends", p.win() == PcState.OFF);
        p.tapCtrl(10); p.tapAlt(10); p.tapShift(10); p.tapWin(10);
        t("all four mods mask", p.mods() == 15);
        t("metaReal has plain + left bits", p.metaReal() == (0x1000 | 0x2000 | 0x02 | 0x10 | 0x01 | 0x40 | 0x10000 | 0x20000));
        t("meta() legacy has plain bits only", p.meta() == (0x1000 | 0x02 | 0x01 | 0x10000));
        t("hasOnce true", p.hasOnce());
        t("not expired before 8 s", !p.expireOnce(8009, 8000) && p.mods() == 15);
        t("expired after 8 s", p.expireOnce(8010, 8000) && p.mods() == 0 && !p.hasOnce());
        p.tapCtrl(100); p.tapCtrl(200);
        t("lock never expires", p.ctrl() == PcState.LOCK && !p.expireOnce(900000, 8000) && p.ctrl() == PcState.LOCK);
        p.tapAlt(1000);
        t("expire only the once one", p.expireOnce(9000, 8000) && p.alt() == PcState.OFF && p.ctrl() == PcState.LOCK);
        p.tapAlt(50000); p.tapAlt(49000);
        t("clock back: alt off, no crash", p.alt() == PcState.OFF);
        p.tapShift(1); p.tapWin(1); p.setPc(true); p.setPc(false);
        t("leaving PC clears shift + win + lock", p.mods() == 0 && p.ctrl() == PcState.OFF);
        p.tapShift(5); p.clearMods();
        t("clearMods clears shift", p.shift() == PcState.OFF && p.mods() == 0);
        System.out.println("PcTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
