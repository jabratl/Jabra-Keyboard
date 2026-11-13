import com.mgb.keyboard.typing.PadJump;

public class PadJumpTest {
    static int ok = 0, bad = 0;
    static void eq(String n, int[] got, int s, int e) {
        if (got.length == 2 && got[0] == s && got[1] == e) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got[0] + "," + got[1] + " want " + s + "," + e); }
    }
    public static void main(String[] a) {
        eq("off top", PadJump.jump(100, 40, 40, true, false), 0, 0);
        eq("off bottom", PadJump.jump(100, 40, 40, false, false), 100, 100);
        eq("off top with selection collapses", PadJump.jump(100, 10, 30, true, false), 0, 0);
        eq("off bottom with selection collapses", PadJump.jump(100, 10, 30, false, false), 100, 100);
        eq("on top from cursor", PadJump.jump(100, 40, 40, true, true), 0, 40);
        eq("on bottom from cursor", PadJump.jump(100, 40, 40, false, true), 40, 100);
        eq("on top keeps far end", PadJump.jump(100, 10, 30, true, true), 0, 30);
        eq("on bottom keeps far start", PadJump.jump(100, 10, 30, false, true), 10, 100);
        eq("reversed selection top", PadJump.jump(100, 30, 10, true, true), 0, 30);
        eq("reversed selection bottom", PadJump.jump(100, 30, 10, false, true), 10, 100);
        eq("cursor at start, on top", PadJump.jump(100, 0, 0, true, true), 0, 0);
        eq("cursor at end, on bottom", PadJump.jump(100, 100, 100, false, true), 100, 100);
        eq("cursor at end, on top selects all", PadJump.jump(100, 100, 100, true, true), 0, 100);
        eq("cursor at start, on bottom selects all", PadJump.jump(100, 0, 0, false, true), 0, 100);
        eq("empty text off", PadJump.jump(0, 0, 0, true, false), 0, 0);
        eq("empty text on bottom", PadJump.jump(0, 0, 0, false, true), 0, 0);
        eq("negative len", PadJump.jump(-5, 0, 0, false, false), 0, 0);
        eq("selection beyond len is clamped", PadJump.jump(50, 20, 90, false, true), 20, 50);
        eq("negative selection clamped", PadJump.jump(50, -3, 10, true, true), 0, 10);
        eq("whole selected, top", PadJump.jump(100, 0, 100, true, true), 0, 100);
        System.out.println("PadJumpTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
