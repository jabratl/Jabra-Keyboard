import com.mgb.keyboard.typing.FeedbackGate;

public class FeedbackGateTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (a.equals(b)) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + a + " want " + b); } }

    public static void main(String[] x) {
        FeedbackGate g = new FeedbackGate();
        // setting OFF: old behaviour (click fires, hint fires, nothing marked)
        eq("off: down no fire", g.onDown(1, false), false);
        eq("off: nothing marked", g.marked(), 0);
        eq("off: click fires", g.onClick(1), true);
        eq("off: hint fires", g.onHint(1), true);
        // setting ON: plain tap = ONE feedback (at down), none at click
        eq("on: down fires", g.onDown(1, true), true);
        eq("on: marked", g.marked(), 1);
        eq("on: click silent", g.onClick(1), false);
        eq("on: mark used up", g.marked(), 0);
        // next tap on same key works again
        eq("on: 2nd down fires", g.onDown(1, true), true);
        eq("on: 2nd click silent", g.onClick(1), false);
        // long-press hint after down: no second buzz
        eq("hint: down fires", g.onDown(2, true), true);
        eq("hint: no 2nd buzz", g.onHint(2), false);
        eq("hint: mark kept (click never comes)", g.marked(), 1);
        // stale mark after hint, next press with setting OFF: old behaviour again
        eq("stale: down off", g.onDown(2, false), false);
        eq("stale: click fires", g.onClick(2), true);
        // stale mark after hint, next press ON: fires once more, then silent click
        g.onDown(3, true); g.onHint(3);
        eq("stale on: down fires", g.onDown(3, true), true);
        eq("stale on: click silent", g.onClick(3), false);
        // slide off the key: UP without click leaves a mark, next down overwrites
        g.onDown(4, true);
        eq("slide: next down fires", g.onDown(4, true), true);
        eq("slide: single click silent", g.onClick(4), false);
        eq("slide: click again would fire (no mark)", g.onClick(4), true);
        // cancel
        g.onDown(5, true); g.onCancel(5);
        eq("cancel: mark gone", g.marked(), 0);
        eq("cancel: click fires", g.onClick(5), true);
        g.onCancel(99);
        eq("cancel unknown key harmless", g.marked(), 0);
        // multi-touch: two keys down together
        eq("mt: key 6 down", g.onDown(6, true), true);
        eq("mt: key 7 down", g.onDown(7, true), true);
        eq("mt: both marked", g.marked(), 2);
        eq("mt: key 7 click silent", g.onClick(7), false);
        eq("mt: key 6 still marked", g.marked(), 1);
        eq("mt: key 6 click silent", g.onClick(6), false);
        eq("mt: none left", g.marked(), 0);
        // keys that never get a touch listener (shift, space, backspace) always fire at click
        eq("no listener key click fires", g.onClick(1000), true);
        eq("no listener key hint fires", g.onHint(1000), true);
        // setting flipped between down and click: down was ON -> click silent (already buzzed once)
        g.onDown(8, true);
        eq("flip: click silent", g.onClick(8), false);
        // flipped OFF then ON
        g.onDown(9, false);
        eq("flip off: click fires", g.onClick(9), true);
        // reset
        g.onDown(10, true); g.onDown(11, true); g.reset();
        eq("reset empties", g.marked(), 0);
        eq("reset: click fires", g.onClick(10), true);
        // negative / large ids
        eq("neg id down", g.onDown(-5, true), true);
        eq("neg id click silent", g.onClick(-5), false);
        eq("big id down", g.onDown(Integer.MAX_VALUE, true), true);
        eq("big id click silent", g.onClick(Integer.MAX_VALUE), false);
        // hint on never-marked key
        eq("hint unmarked fires", g.onHint(55), true);
        eq("hint unmarked leaves no mark", g.marked(), 0);
        // rapid typing: 50 taps on 5 keys, exactly one feedback per tap
        int fired = 0;
        for (int i = 0; i < 50; i++) { int id = i % 5; if (g.onDown(id, true)) fired++; if (g.onClick(id)) fired++; }
        eq("50 taps = 50 feedbacks", fired, 50);
        eq("50 taps leave no marks", g.marked(), 0);

        System.out.println("FeedbackGateTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
