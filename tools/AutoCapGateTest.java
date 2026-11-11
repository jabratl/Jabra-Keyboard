import com.mgb.keyboard.typing.AutoCapGate;
import com.mgb.keyboard.typing.ShiftState;

public class AutoCapGateTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (a.equals(b)) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + a + " want " + b); } }

    public static void main(String[] x) {
        // kindOf
        eq("a", AutoCapGate.kindOf("a"), AutoCapGate.E_LETTER);
        eq("Z", AutoCapGate.kindOf("Z"), AutoCapGate.E_LETTER);
        eq("bangla letter", AutoCapGate.kindOf("\u0986"), AutoCapGate.E_LETTER);
        eq("digit", AutoCapGate.kindOf("5"), AutoCapGate.E_DELIM);
        eq("space", AutoCapGate.kindOf(" "), AutoCapGate.E_DELIM);
        eq("dot", AutoCapGate.kindOf("."), AutoCapGate.E_DELIM);
        eq("question", AutoCapGate.kindOf("?"), AutoCapGate.E_DELIM);
        eq("excl", AutoCapGate.kindOf("!"), AutoCapGate.E_DELIM);
        eq("danda", AutoCapGate.kindOf("\u0964"), AutoCapGate.E_DELIM);
        eq("newline", AutoCapGate.kindOf("\n"), AutoCapGate.E_DELIM);
        eq("comma", AutoCapGate.kindOf(","), AutoCapGate.E_DELIM);
        eq("at", AutoCapGate.kindOf("@"), AutoCapGate.E_DELIM);
        eq("empty", AutoCapGate.kindOf(""), AutoCapGate.E_OTHER);
        eq("null", AutoCapGate.kindOf(null), AutoCapGate.E_OTHER);
        eq("two letters", AutoCapGate.kindOf("ab"), AutoCapGate.E_OTHER);
        eq("paste", AutoCapGate.kindOf("hello world"), AutoCapGate.E_OTHER);
        eq("emoji (surrogate pair, one code point)", AutoCapGate.kindOf("\uD83D\uDE00"), AutoCapGate.E_DELIM);
        eq("two emoji", AutoCapGate.kindOf("\uD83D\uDE00\uD83D\uDE00"), AutoCapGate.E_OTHER);
        eq("letter + space", AutoCapGate.kindOf("a "), AutoCapGate.E_OTHER);
        // shouldCheck, normal field
        eq("start asks", AutoCapGate.shouldCheck(AutoCapGate.E_START, false), true);
        eq("letter skips", AutoCapGate.shouldCheck(AutoCapGate.E_LETTER, false), false);
        eq("delim asks", AutoCapGate.shouldCheck(AutoCapGate.E_DELIM, false), true);
        eq("backspace asks", AutoCapGate.shouldCheck(AutoCapGate.E_BACKSPACE, false), true);
        eq("jump asks", AutoCapGate.shouldCheck(AutoCapGate.E_JUMP, false), true);
        eq("other asks", AutoCapGate.shouldCheck(AutoCapGate.E_OTHER, false), true);
        // all-caps field: always asks
        for (int e = 0; e <= 5; e++) eq("capChars asks " + e, AutoCapGate.shouldCheck(e, true), true);

        // simulation: sentence caps field. wantCaps = text before cursor is empty or ends with ". " (what getCursorCapsMode does for sentences)
        // Typing "hi. ok" with and without the gate must give the same shift state after every key.
        String script = "hi. ok? yes! 3d x. a";
        ShiftState ref = new ShiftState(), gated = new ShiftState();
        StringBuilder text = new StringBuilder();
        ref.reset(); gated.reset();
        ref.autoCap(want(text)); gated.autoCap(want(text));   // field start: both ask
        for (int i = 0; i < script.length(); i++) {
            String s = String.valueOf(script.charAt(i));
            boolean isLetter = Character.isLetter(script.charAt(i));
            // the user key press: letters end one-shot shift (like MgbIme.onLetter)
            if (isLetter) { if (ref.isShift()) ref.typed(); if (gated.isShift()) gated.typed(); }
            text.append(isLetter && ref.isShift() ? s : s);
            // selection update after the commit
            ref.autoCap(want(text));
            if (AutoCapGate.shouldCheck(AutoCapGate.kindOf(s), false)) gated.autoCap(want(text));
            eq("sim state after '" + script.substring(0, i + 1) + "' shift", gated.isShift(), ref.isShift());
            eq("sim state after '" + script.substring(0, i + 1) + "' auto", gated.isAuto(), ref.isAuto());
        }
        // user-set shift is never touched by the gate (letter skips; ShiftState.autoCap never touches manual shift anyway)
        ShiftState m = new ShiftState(); m.tap(1000);
        m.typed();
        eq("manual shift ended by typed()", m.isShift(), false);
        // caps lock stays through letters (gate skip or not)
        ShiftState c = new ShiftState(); c.tap(1000); c.tap(1100);
        eq("caps on", c.isCaps(), true);
        c.typed(); if (AutoCapGate.shouldCheck(AutoCapGate.E_LETTER, false)) c.autoCap(false);
        eq("caps stays after letter", c.isCaps(), true);

        System.out.println("AutoCapGateTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }

    /** Same rule as TextUtils.getCapsMode for CAP_SENTENCES: empty text, or last non-space chars are ". " / "? " / "! " / newline. */
    static boolean want(CharSequence t) {
        int i = t.length();
        if (i == 0) return true;
        int j = i;
        while (j > 0 && t.charAt(j - 1) == ' ') j--;
        if (j == i && !(j > 0 && t.charAt(j - 1) == '\n')) return false;   // no space typed after the mark
        if (j == 0) return true;
        char c = t.charAt(j - 1);
        return c == '.' || c == '?' || c == '!' || c == '\n';
    }
}
