import com.mgb.keyboard.typing.EnterAction;
import com.mgb.keyboard.typing.ShiftState;

/** v0.0.9 logic tests (computer only).  javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/TypingTest.java && java -cp out TypingTest */
public class TypingTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) {
        if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); }
    }

    public static void main(String[] a) {
        // ---- shift ----
        ShiftState s = new ShiftState();
        t("starts off", !s.isShift() && !s.isCaps());
        s.tap(1000); t("tap -> shift", s.isShift() && !s.isCaps());
        s.typed();   t("letter ends shift", !s.isShift());

        s.tap(2000); s.tap(2200); t("double tap -> caps", s.isShift() && s.isCaps());
        s.typed();   t("caps stays after letter", s.isShift() && s.isCaps());
        s.tap(5000); t("tap on caps -> off", !s.isShift() && !s.isCaps());

        s.tap(6000); s.tap(7000); t("slow second tap -> off, not caps", !s.isShift() && !s.isCaps());
        s.tap(8000); t("then tap -> shift again", s.isShift() && !s.isCaps());
        s.reset();

        // ---- auto capital ----
        t("auto on", s.autoCap(true) && s.isShift() && s.isAuto());
        t("auto again = no change", !s.autoCap(true));
        t("auto off when not wanted", s.autoCap(false) && !s.isShift());
        t("auto off again = no change", !s.autoCap(false));

        s.tap(10000);   // manual shift
        t("auto never cancels manual shift", !s.autoCap(false) && s.isShift());
        s.typed();
        s.tap(11000); s.tap(11100);  // caps
        t("auto never touches caps (want)", !s.autoCap(true) && s.isCaps());
        t("auto never touches caps (not want)", !s.autoCap(false) && s.isCaps());
        s.reset();

        s.autoCap(true);             // auto shift on, user taps = off (not caps)
        s.tap(20000);
        t("tap on auto shift -> off", !s.isShift() && !s.isCaps());
        s.autoCap(true); s.typed();
        t("auto shift ends after letter", !s.isShift());

        s.reset(); s.tap(30000); s.setShift(false);
        t("old setShift(false) clears", !s.isShift() && !s.isCaps());
        s.reset(); s.tap(30000); s.tap(30100); s.setShift(false);
        t("setShift(false) clears caps too", !s.isShift() && !s.isCaps());

        // ---- enter action ----
        t("unspecified -> plain enter", EnterAction.decide(0) == -1);
        t("none -> plain enter", EnterAction.decide(EnterAction.IME_ACTION_NONE) == -1);
        t("send(4) -> action 4", EnterAction.decide(4) == 4);
        t("search(3) -> action 3", EnterAction.decide(3) == 3);
        t("go(2) -> action 2", EnterAction.decide(2) == 2);
        t("done(6) -> action 6", EnterAction.decide(6) == 6);
        t("flag no-enter-action wins", EnterAction.decide(4 | EnterAction.IME_FLAG_NO_ENTER_ACTION) == -1);
        t("other flags are ignored", EnterAction.decide(4 | 0x10000000 | 0x2000000) == 4);

        System.out.println((ok + bad) + " checks, " + ok + " passed" + (bad == 0 ? "" : ", " + bad + " FAILED"));
        if (bad > 0) System.exit(1);
    }
}
