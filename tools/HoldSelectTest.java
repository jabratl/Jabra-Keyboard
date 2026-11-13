import com.mgb.keyboard.typing.HoldSelect;
import com.mgb.keyboard.typing.HoldSelect.Result;
import com.mgb.keyboard.typing.HoldSelect.State;

/** javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/HoldSelect.java tools/HoldSelectTest.java && java -cp out HoldSelectTest */
public class HoldSelectTest {
    static int fail = 0, total = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL " + m); } }
    public static void main(String[] a) {
        // sanitize
        ok(HoldSelect.sanitize(0) == 1500 && HoldSelect.sanitize(-5) == 1500, "unset / negative = default");
        ok(HoldSelect.sanitize(1) == 600 && HoldSelect.sanitize(599) == 600 && HoldSelect.sanitize(600) == 600, "low values go to 600");
        ok(HoldSelect.sanitize(2500) == 2500 && HoldSelect.sanitize(2501) == 2500 && HoldSelect.sanitize(Integer.MAX_VALUE) == 2500, "high values go to 2500");
        ok(HoldSelect.sanitize(1200) == 1200 && HoldSelect.sanitize(Integer.MIN_VALUE) == 1500, "normal / min int");
        ok(HoldSelect.isValid(600) && HoldSelect.isValid(2500) && !HoldSelect.isValid(599) && !HoldSelect.isValid(2501) && !HoldSelect.isValid(0), "isValid");
        ok(new HoldSelect().holdMs() == 1500 && new HoldSelect(99999, 10f).holdMs() == 2500, "constructor sanitizes");

        // plain tap outside select mode
        HoldSelect h = new HoldSelect(1500, 24f);
        ok(h.state() == State.IDLE, "starts idle");
        ok(h.down(1000, 10, 10, false) == Result.NONE && h.state() == State.HOLDING, "down = holding");
        ok(h.up(1100) == Result.TAP_PASTE && h.state() == State.IDLE, "quick tap = paste");

        // hold reached by timer
        h.down(0, 5, 5, false);
        ok(h.tick(1499) == Result.NONE && h.state() == State.HOLDING, "tick 1 ms early = nothing");
        ok(h.tick(1500) == Result.START_SELECT && h.state() == State.STARTED, "tick at exactly 1500 = start");
        ok(h.tick(1600) == Result.NONE, "second tick = no second start");
        ok(h.up(1700) == Result.NONE && h.state() == State.IDLE, "release after start = no paste");

        // early release just before threshold
        h.down(0, 0, 0, false);
        ok(h.up(1499) == Result.TAP_PASTE, "release at 1499 = tap");
        // late up without tick
        h.down(0, 0, 0, false);
        ok(h.up(1500) == Result.START_SELECT, "up at 1500 without tick = start");
        h.down(0, 0, 0, false);
        ok(h.up(9000) == Result.START_SELECT && h.up(9100) == Result.NONE, "very late up = one start only");

        // move cancels
        h.down(0, 100, 100, false);
        h.move(100, 110, 110);
        ok(h.state() == State.HOLDING, "small move (inside slop) keeps holding");
        h.move(200, 100, 130);
        ok(h.state() == State.CANCELLED, "move > slop cancels");
        ok(h.tick(2000) == Result.NONE, "cancelled: tick does nothing");
        ok(h.up(2100) == Result.NONE, "cancelled: up = no paste");
        h.down(0, 0, 0, false);
        h.move(10, 24, 0);
        ok(h.state() == State.HOLDING, "exactly slop = still holding");
        h.move(20, 24.5f, 0);
        ok(h.state() == State.CANCELLED, "just over slop = cancelled");
        h.up(30);
        // diagonal distance
        h.down(0, 0, 0, false);
        h.move(5, 20, 20);
        ok(h.state() == State.CANCELLED, "diagonal 28 px > 24 cancels");
        h.up(10);

        // scroll cancels
        h.down(0, 0, 0, false);
        ok(h.scroll() == Result.NONE && h.state() == State.CANCELLED, "scroll cancels");
        ok(h.up(50) == Result.NONE, "scroll then up = nothing");
        h.down(0, 0, 0, false); h.tick(1600);
        h.scroll();
        ok(h.state() == State.STARTED, "scroll after start does not undo the start");
        h.up(1700);

        // select mode: tap toggles, hold = still one toggle
        h.down(0, 0, 0, true);
        ok(h.tick(1500) == Result.NONE && h.state() == State.HOLDING, "select mode: tick never starts");
        ok(h.up(100) == Result.TAP_TOGGLE, "select mode: tap = toggle");
        h.down(0, 0, 0, true);
        ok(h.up(5000) == Result.TAP_TOGGLE, "select mode: long hold = ONE toggle, no restart");
        h.down(0, 0, 0, true);
        h.move(10, 200, 200);
        ok(h.up(20) == Result.NONE, "select mode: drag = no toggle");
        h.down(0, 0, 0, true);
        h.scroll();
        ok(h.up(20) == Result.NONE, "select mode: scroll = no toggle");

        // cancel / reuse
        h.down(0, 0, 0, false);
        ok(h.cancel() == Result.NONE && h.state() == State.IDLE && h.up(10) == Result.NONE, "ACTION_CANCEL = idle, up = nothing");
        h.down(0, 0, 0, false); h.tick(1500);
        h.down(2000, 0, 0, false);
        ok(h.state() == State.HOLDING && h.tick(3500) == Result.START_SELECT, "new down after a start = new gesture");
        h.up(3600);

        // bad values
        h.down(1000, 0, 0, false);
        ok(h.up(500) == Result.TAP_PASTE, "clock going backwards = tap, no crash");
        h.down(Long.MAX_VALUE - 5, 0, 0, false);
        ok(h.up(Long.MIN_VALUE) == Result.TAP_PASTE, "huge time difference does not overflow");
        h.down(0, Float.NaN, Float.NaN, false);
        h.move(10, 5, 5);
        ok(h.state() == State.HOLDING, "NaN start position: no crash, no cancel");
        h.up(20);
        ok(h.move(5, 1, 1) == Result.NONE && h.tick(5) == Result.NONE && h.scroll() == Result.NONE && h.up(5) == Result.NONE, "calls in idle are harmless");
        HoldSelect s2 = new HoldSelect(1000, Float.NaN);
        s2.down(0, 0, 0, false); s2.move(1, 23, 0);
        ok(s2.state() == State.HOLDING, "NaN slop = default 24");
        HoldSelect s3 = new HoldSelect(700, -3f);
        s3.down(0, 0, 0, false); s3.move(1, 25, 0);
        ok(s3.state() == State.CANCELLED && s3.holdMs() == 700, "negative slop = default 24, custom hold 700");
        HoldSelect s4 = new HoldSelect(600, 0f);
        s4.down(0, 0, 0, false);
        ok(s4.tick(600) == Result.START_SELECT, "minimum hold 600 works");

        System.out.println((total - fail) + "/" + total + " passed");
        if (fail > 0) System.exit(1);
    }
}
