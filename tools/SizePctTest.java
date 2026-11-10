import com.mgb.keyboard.typing.SizePct;

/** v0.1.4 logic tests.  java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/SizePctTest.java ; java -cp out SizePctTest */
public class SizePctTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    public static void main(String[] a) {
        t("100% = 48dp (old Medium)", SizePct.heightDp(100) == 48);
        t("70% = 34dp", SizePct.heightDp(70) == 34);
        t("150% = 72dp", SizePct.heightDp(150) == 72);
        t("below min clamps", SizePct.heightDp(10) == SizePct.heightDp(70));
        t("above max clamps", SizePct.heightDp(999) == SizePct.heightDp(150));
        t("old dp 40 -> 83%", SizePct.pctFromDp(40) == 83);
        t("old dp 48 -> 100%", SizePct.pctFromDp(48) == 100);
        t("old dp 56 -> 117%", SizePct.pctFromDp(56) == 117);
        t("old dp 64 -> 133%", SizePct.pctFromDp(64) == 133);
        t("old dp survives round trip (40,48,56,64)", SizePct.heightDp(SizePct.pctFromDp(40)) == 40 && SizePct.heightDp(SizePct.pctFromDp(56)) == 56 && SizePct.heightDp(SizePct.pctFromDp(64)) == 64);
        t("unset invalid", !SizePct.heightValid(SizePct.UNSET) && !SizePct.widthValid(SizePct.UNSET));
        t("height valid range", SizePct.heightValid(70) && SizePct.heightValid(150) && !SizePct.heightValid(69) && !SizePct.heightValid(151));
        t("width valid range", SizePct.widthValid(50) && SizePct.widthValid(95) && !SizePct.widthValid(49) && !SizePct.widthValid(96));
        t("width unset portrait = 0.82", SizePct.widthFrac(-1, false) == 0.82f);
        t("width unset landscape = 0.60", SizePct.widthFrac(-1, true) == 0.60f);
        t("width set 70", SizePct.widthFrac(70, false) == 0.70f && SizePct.widthFrac(70, true) == 0.70f);
        t("width garbage -> old", SizePct.widthFrac(5, false) == 0.82f && SizePct.widthFrac(500, true) == 0.60f);
        t("step up 100 -> 105", SizePct.step(100, 1, 70, 150, 5) == 105);
        t("step down 100 -> 95", SizePct.step(100, -1, 70, 150, 5) == 95);
        t("step up from 82 snaps to 85", SizePct.step(82, 1, 50, 95, 5) == 85);
        t("step down from 82 snaps to 80", SizePct.step(82, -1, 50, 95, 5) == 80);
        t("step down from 83 -> 80", SizePct.step(83, -1, 70, 150, 5) == 80);
        t("step up from 117 -> 120", SizePct.step(117, 1, 70, 150, 5) == 120);
        t("max stays", SizePct.step(150, 1, 70, 150, 5) == 150 && SizePct.step(95, 1, 50, 95, 5) == 95);
        t("min stays", SizePct.step(70, -1, 70, 150, 5) == 70 && SizePct.step(50, -1, 50, 95, 5) == 50);
        t("dir 0 keeps", SizePct.step(100, 0, 70, 150, 5) == 100);
        int v = 100; for (int i = 0; i < 40; i++) v = SizePct.step(v, 1, 70, 150, 5);
        t("40x up stops at max", v == 150);
        for (int i = 0; i < 40; i++) v = SizePct.step(v, -1, 70, 150, 5);
        t("40x down stops at min", v == 70);
        System.out.println("SizePctTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
