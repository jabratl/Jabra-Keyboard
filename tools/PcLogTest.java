import com.mgb.keyboard.typing.PcKeyPlan;
import com.mgb.keyboard.typing.PcLog;
import java.util.List;

/** v0.17.52 (PC4) tests: PcLog ring, privacy gate, line format, self-test summary (computer only, no Android). */
public class PcLogTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] a) {
        // closed = nothing recorded
        PcLog.setOpen(false);
        PcLog.add("x"); PcLog.sent(54, 5, true, true); PcLog.recv(0, 54, 0); PcLog.hold(21, 0, true); PcLog.testAdd("Home", true);
        t("closed: nothing recorded", PcLog.lines().isEmpty() && PcLog.testTotal() == 0 && !PcLog.isOpen());

        PcLog.setOpen(true);
        t("open", PcLog.isOpen());
        for (int i = 0; i < 25; i++) PcLog.add("line " + i);
        List<String> l = PcLog.lines();
        t("ring keeps 20", l.size() == PcLog.MAX && l.get(0).equals("line 5") && l.get(19).equals("line 24"));
        PcLog.add(null);
        t("null line ignored", PcLog.lines().size() == 20);
        l.clear();
        t("lines() is a copy", PcLog.lines().size() == 20);
        PcLog.clear();
        t("clear", PcLog.lines().isEmpty());

        // names
        t("code names", PcLog.codeName(54).equals("Z") && PcLog.codeName(29).equals("A") && PcLog.codeName(7).equals("0") && PcLog.codeName(16).equals("9")
                && PcLog.codeName(131).equals("F1") && PcLog.codeName(142).equals("F12") && PcLog.codeName(122).equals("Home") && PcLog.codeName(22).equals("Right")
                && PcLog.codeName(113).equals("CTRL") && PcLog.codeName(117).equals("WIN") && PcLog.codeName(144).equals("KP0") && PcLog.codeName(1234).equals("K1234"));
        t("chord names", PcLog.chordName(54, PcKeyPlan.MOD_CTRL | PcKeyPlan.MOD_SHIFT).equals("Ctrl+Shift+Z") && PcLog.chordName(122, 0).equals("Home")
                && PcLog.chordName(134, PcKeyPlan.MOD_ALT).equals("Alt+F4") && PcLog.chordName(32, 15).equals("Ctrl+Alt+Shift+Win+D"));

        // lines
        t("sent line real chord", PcLog.sentLine(54, PcKeyPlan.MOD_CTRL | PcKeyPlan.MOD_SHIFT, true, true)
                .equals("sent Ctrl+Shift+Z -> CTRL down, SHIFT down, Z down, Z up, SHIFT up, CTRL up | accepted: yes"));
        t("sent line plain key", PcLog.sentLine(122, 0, true, true).equals("sent Home -> Home down, Home up | accepted: yes"));
        t("sent line refused", PcLog.sentLine(31, PcKeyPlan.MOD_CTRL, true, false).endsWith("| accepted: NO") && PcLog.sentLine(31, PcKeyPlan.MOD_CTRL, true, false).contains("CTRL down, C down, C up, CTRL up"));
        t("sent line meta only", PcLog.sentLine(31, PcKeyPlan.MOD_CTRL, false, true).equals("sent Ctrl+C -> C down, C up (modifiers as meta bits only) | accepted: yes"));
        t("recv line plain / ctrl+shift", PcLog.recvLine(0, 54, 0).equals("got Z down") && PcLog.recvLine(1, 54, PcKeyPlan.realMeta(PcKeyPlan.MOD_CTRL | PcKeyPlan.MOD_SHIFT)).equals("got Z up (ctrl+shift)"));
        t("recv line alt", PcLog.recvLine(0, 134, PcKeyPlan.realMeta(PcKeyPlan.MOD_ALT)).equals("got F4 down (alt)"));
        t("hold line", PcLog.holdLine(21, 0, true).equals("sent Left (hold, key down) | accepted: yes"));
        PcLog.sent(54, 1, true, true); PcLog.recv(0, 122, 0); PcLog.hold(20, 0, false);
        t("sent / recv / hold add lines", PcLog.lines().size() == 3 && PcLog.lines().get(2).contains("accepted: NO"));

        t("recvWorthy: plain typing keys never", !PcLog.recvWorthy(54, 0) && !PcLog.recvWorthy(7, 0) && !PcLog.recvWorthy(62, 0) && !PcLog.recvWorthy(67, 0));
        t("recvWorthy: modifiers / PC keys yes", PcLog.recvWorthy(54, PcKeyPlan.realMeta(PcKeyPlan.MOD_CTRL)) && PcLog.recvWorthy(122, 0) && PcLog.recvWorthy(131, 0) && PcLog.recvWorthy(21, 0));
        PcLog.clear(); PcLog.recv(0, 54, 0); PcLog.recv(0, 122, 0);
        t("recv() drops plain letter, keeps Home", PcLog.lines().size() == 1 && PcLog.lines().get(0).equals("got Home down"));
        PcLog.clear();

        // listener
        final int[] fired = { 0 };
        PcLog.setListener(new Runnable() { public void run() { fired[0]++; } });
        PcLog.add("x");
        t("listener fired on add", fired[0] == 1);
        PcLog.testAdd("Home", true);
        t("listener fired on test result", fired[0] == 2);
        PcLog.setListener(new Runnable() { public void run() { throw new IllegalStateException("broken page"); } });
        boolean survived = true;
        try { PcLog.add("y"); } catch (RuntimeException e) { survived = false; }
        t("broken listener never breaks the key path", survived);

        // self-test summary
        PcLog.clear();
        t("summary not run", PcLog.summary("Test box").equals("Test box: not run") && PcLog.summary(null).equals("Test box: not run"));
        PcLog.testAdd("Left", true); PcLog.testAdd("Home", true); PcLog.testAdd("Ctrl+A", false); PcLog.testAdd("Ctrl+C then Ctrl+V", false);
        t("counts", PcLog.testTotal() == 4 && PcLog.testPassed() == 2);
        t("summary with failures", PcLog.summary("Jabra box").equals("Jabra box: 2/4 ok, failed: Ctrl+A, Ctrl+C then Ctrl+V"));
        t("test lines", PcLog.testLines().get(0).equals("ok    Left") && PcLog.testLines().get(2).equals("FAIL  Ctrl+A"));
        PcLog.testClear();
        PcLog.testAdd("Left", true);
        t("summary all ok", PcLog.summary("Chrome").equals("Chrome: 1/1 ok"));

        // closing drops everything (privacy)
        PcLog.add("secret-ish");
        PcLog.setOpen(false);
        t("close drops lines + results + listener", PcLog.lines().isEmpty() && PcLog.testTotal() == 0 && !PcLog.isOpen());
        PcLog.add("after close");
        t("closed again: nothing", PcLog.lines().isEmpty());

        System.out.println("PcLogTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
