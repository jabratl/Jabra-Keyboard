import com.mgb.keyboard.typing.ClipCompact;

public class ClipCompactTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    public static void main(String[] a) {
        t("OFF = old height 40", ClipCompact.rowDp(40, false) == 40);
        t("ON 40 -> 34", ClipCompact.rowDp(40, true) == 34);
        t("ON never grows (30 stays 30)", ClipCompact.rowDp(30, true) == 30);
        t("ON old 0 stays 0", ClipCompact.rowDp(0, true) == 0);
        t("ON negative stays", ClipCompact.rowDp(-5, true) == -5);
        t("ON 34 stays 34", ClipCompact.rowDp(34, true) == 34);
        t("ON 20 stays 20 (already lower than floor)", ClipCompact.rowDp(20, true) == 20);
        t("ON >= MIN_DP", ClipCompact.rowDp(40, true) >= 28);
        t("OFF weights ABC 1.0", ClipCompact.weight(ClipCompact.ABC, false, false) == 1.0f);
        t("OFF weights Search 1.6", ClipCompact.weight(ClipCompact.SEARCH, false, false) == 1.6f);
        t("OFF weights Search slim 0.9", ClipCompact.weight(ClipCompact.SEARCH, false, true) == 0.9f);
        t("OFF weights Done 1.3", ClipCompact.weight(ClipCompact.DONE, false, false) == 1.3f);
        t("OFF weights Done slim 0.8", ClipCompact.weight(ClipCompact.DONE, false, true) == 0.8f);
        t("ON ABC narrower than OFF", ClipCompact.weight(ClipCompact.ABC, true, false) < ClipCompact.weight(ClipCompact.ABC, false, false));
        t("ON Search wider than OFF (select mode)", ClipCompact.weight(ClipCompact.SEARCH, true, false) > ClipCompact.weight(ClipCompact.SEARCH, false, false));
        t("ON Done narrower than OFF", ClipCompact.weight(ClipCompact.DONE, true, false) < ClipCompact.weight(ClipCompact.DONE, false, false));
        t("unknown which = 1", ClipCompact.weight(9, true, false) == 1.0f && ClipCompact.weight(-1, false, true) == 1.0f);
        t("weights always > 0", ClipCompact.weight(0, true, true) > 0 && ClipCompact.weight(1, true, true) > 0 && ClipCompact.weight(2, true, true) > 0);
        t("padV OFF 4", ClipCompact.padV(false) == 4);
        t("padV ON 2", ClipCompact.padV(true) == 2);
        t("saved 40 -> 10 dp", ClipCompact.savedDp(40) == 10);
        t("saved never negative for low rows", ClipCompact.savedDp(20) >= 0);
        // touch floor with the user size % (ClipBtn): compact row at 70 % must not go under 28 dp
        t("compact + size 70% floor", com.mgb.keyboard.typing.ClipBtn.heightDp(ClipCompact.rowDp(40, true), 70) >= 28);
        t("compact + size 100% = 34", com.mgb.keyboard.typing.ClipBtn.heightDp(ClipCompact.rowDp(40, true), 100) == 34);
        System.out.println("ClipCompactTest " + ok + "/" + (ok + bad));
        if (bad > 0) System.exit(1);
    }
}
