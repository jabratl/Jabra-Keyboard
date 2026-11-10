import com.mgb.keyboard.typing.DoubleSpace;
import com.mgb.keyboard.typing.TypingLevels;

/** v0.1.1 logic tests (computer only).  java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/DoubleSpaceTest.java ; java -Dfile.encoding=UTF-8 -cp out DoubleSpaceTest */
public class DoubleSpaceTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }
    static int d(boolean p, boolean tb, long last, long now, String b) { return DoubleSpace.decide(p, tb, last, now, DoubleSpace.WINDOW_MS, b); }

    public static void main(String[] a) {
        long N = 10000;
        t("both off", d(false, false, N - 100, N, "a ") == DoubleSpace.NONE);
        t("period letter", d(true, false, N - 100, N, "a ") == DoubleSpace.PERIOD);
        t("period digit", d(true, false, N - 100, N, "5 ") == DoubleSpace.PERIOD);
        t("period closing bracket", d(true, false, N - 100, N, ") ") == DoubleSpace.PERIOD);
        t("period bangla letter", d(true, false, N - 100, N, "\u09AC\u09BE".substring(0, 1) + " ") == DoubleSpace.PERIOD);
        t("period after bangla matra (aa-kar)", d(true, false, N - 100, N, "\u09BE ") == DoubleSpace.PERIOD);
        t("period after bangla i-kar", d(true, false, N - 100, N, "\u09BF ") == DoubleSpace.PERIOD);
        t("no period after dot", d(true, false, N - 100, N, ". ") == DoubleSpace.NONE);
        t("no period after danda", d(true, false, N - 100, N, "\u0964 ") == DoubleSpace.NONE);
        t("no period after comma", d(true, false, N - 100, N, ", ") == DoubleSpace.NONE);
        t("no period after question", d(true, false, N - 100, N, "? ") == DoubleSpace.NONE);
        t("too slow", d(true, false, N - 900, N, "a ") == DoubleSpace.NONE);
        t("exactly at window", d(true, false, N - 800, N, "a ") == DoubleSpace.PERIOD);
        t("no previous space tap", d(true, false, 0, N, "a ") == DoubleSpace.NONE);
        t("clock went back", d(true, false, N + 5, N, "a ") == DoubleSpace.NONE);
        t("text has no trailing space", d(true, false, N - 100, N, "ab") == DoubleSpace.NONE);
        t("two spaces already", d(true, false, N - 100, N, "  ") == DoubleSpace.NONE);
        t("only one char", d(true, false, N - 100, N, " ") == DoubleSpace.NONE);
        t("null", d(true, false, N - 100, N, null) == DoubleSpace.NONE);
        t("empty", d(true, false, N - 100, N, "") == DoubleSpace.NONE);
        t("tab only", d(false, true, N - 100, N, "a ") == DoubleSpace.TAB);
        t("tab after dot", d(false, true, N - 100, N, ". ") == DoubleSpace.TAB);
        t("tab not after space", d(false, true, N - 100, N, "  ") == DoubleSpace.NONE);
        t("both on: period wins", d(true, true, N - 100, N, "a ") == DoubleSpace.PERIOD);
        t("both on: dot -> tab", d(true, true, N - 100, N, ". ") == DoubleSpace.TAB);
        t("newline before space", d(true, false, N - 100, N, "\n ") == DoubleSpace.NONE);

        t("lp default = system (0)", TypingLevels.longPressMs(TypingLevels.LP_DEFAULT) == 0);
        t("lp fast", TypingLevels.longPressMs(1) == 250);
        t("lp very slow", TypingLevels.longPressMs(4) == 900);
        t("lp clamp low", TypingLevels.longPressMs(-3) == 0);
        t("lp clamp high", TypingLevels.longPressMs(99) == 900);
        t("lp names count", TypingLevels.LP_NAMES.length == 5);
        t("cursor default = old 16dp", TypingLevels.stepDp(TypingLevels.CS_DEFAULT) == 16f);
        t("cursor fast smaller step", TypingLevels.stepDp(3) < TypingLevels.stepDp(2));
        t("cursor slow bigger step", TypingLevels.stepDp(1) > TypingLevels.stepDp(2));
        t("cursor clamp", TypingLevels.stepDp(-1) == 28f && TypingLevels.stepDp(50) == 7f);
        t("cursor names count", TypingLevels.CS_NAMES.length == 5);
        System.out.println("DoubleSpaceTest: " + ok + " ok, " + bad + " failed");
    }
}
