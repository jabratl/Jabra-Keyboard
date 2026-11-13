import com.mgb.keyboard.typing.ClipPasteMode;

public class ClipPasteModeTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    public static void main(String[] a) {
        int P = ClipPasteMode.PLAIN, F = ClipPasteMode.FORMAT;
        t("keep ON, body tap, has format = FORMAT", ClipPasteMode.decide(true, false, true) == F);
        t("keep ON, Aa tap = PLAIN", ClipPasteMode.decide(true, true, true) == P);
        t("keep OFF, body tap = PLAIN", ClipPasteMode.decide(false, false, true) == P);
        t("keep OFF, Aa tap = FORMAT", ClipPasteMode.decide(false, true, true) == F);
        t("no format: always PLAIN (4 combos)", ClipPasteMode.decide(true, false, false) == P && ClipPasteMode.decide(true, true, false) == P && ClipPasteMode.decide(false, false, false) == P && ClipPasteMode.decide(false, true, false) == P);
        t("constants differ", P != F && P == 0 && F == 1);
        System.out.println("ClipPasteModeTest " + ok + "/" + (ok + bad));
        if (bad > 0) System.exit(1);
    }
}
